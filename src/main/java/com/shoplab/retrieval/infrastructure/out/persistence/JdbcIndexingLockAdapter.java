package com.shoplab.retrieval.infrastructure.out.persistence;

import com.shoplab.retrieval.application.port.out.IndexingLockPort;
import com.shoplab.retrieval.shared.exception.ExceptionUtil;
import com.shoplab.retrieval.shared.exception.ResponseCodeEnum;
import com.shoplab.retrieval.shared.infrastructure.in.rest.filter.RequestIdFilter;
import com.shoplab.retrieval.shared.infrastructure.out.blocking.BlockingCalls;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The lease in {@code retrieval.indexing_lock}. Each operation is one short statement, and every time is computed by
 * the database. On an orderly shutdown the instance frees what it holds, so its replacement does not have to wait.
 */
@Component
@DependsOn("moduleSchemaMigrator")
public class JdbcIndexingLockAdapter implements IndexingLockPort {

    private static final String NAME = "embeddings";
    private static final Duration DEFAULT_LEASE = Duration.ofSeconds(90);

    private static final String ACQUIRE = """
            INSERT INTO retrieval.indexing_lock AS held (name, owner, locked_until)
            VALUES (?, ?, now() + make_interval(secs => ?))
            ON CONFLICT (name) DO UPDATE SET owner = excluded.owner, locked_until = excluded.locked_until
            WHERE held.locked_until < now()
            """;
    private static final String RENEW = """
            UPDATE retrieval.indexing_lock SET locked_until = now() + make_interval(secs => ?)
            WHERE name = ? AND owner = ?
            """;
    private static final String RELEASE = "DELETE FROM retrieval.indexing_lock WHERE name = ? AND owner = ?";

    private final JdbcTemplate jdbc;
    private final double leaseSeconds;
    private final Set<String> held = ConcurrentHashMap.newKeySet();

    @Autowired
    public JdbcIndexingLockAdapter(JdbcTemplate jdbc) {
        this(jdbc, DEFAULT_LEASE);
    }

    public JdbcIndexingLockAdapter(JdbcTemplate jdbc, Duration lease) {
        this.jdbc = jdbc;
        this.leaseSeconds = lease.toMillis() / 1000.0;
    }

    @Override
    public Mono<String> tryAcquire() {
        return Mono.defer(() -> {
            String owner = RequestIdFilter.newRequestId();
            return BlockingCalls.call(() -> {
                boolean acquired = jdbc.update(ACQUIRE, NAME, owner, leaseSeconds) == 1;
                if (acquired) {
                    held.add(owner);
                }
                return acquired;
            }).filter(Boolean::booleanValue).map(acquired -> owner);
        }).onErrorMap(DataAccessException.class, JdbcIndexingLockAdapter::unavailable);
    }

    @Override
    public Mono<Boolean> renew(String owner) {
        return BlockingCalls.call(() -> jdbc.update(RENEW, leaseSeconds, NAME, owner) == 1)
                .onErrorMap(DataAccessException.class, JdbcIndexingLockAdapter::unavailable);
    }

    @Override
    public Mono<Void> release(String owner) {
        return BlockingCalls.run(() -> {
            jdbc.update(RELEASE, NAME, owner);
            held.remove(owner);
        }).onErrorMap(DataAccessException.class, JdbcIndexingLockAdapter::unavailable);
    }

    @PreDestroy
    public void releaseHeld() {
        held.forEach(owner -> jdbc.update(RELEASE, NAME, owner));
        held.clear();
    }

    private static Throwable unavailable(Throwable error) {
        return ExceptionUtil.technical(ResponseCodeEnum.DATABASE_UNAVAILABLE, error);
    }
}
