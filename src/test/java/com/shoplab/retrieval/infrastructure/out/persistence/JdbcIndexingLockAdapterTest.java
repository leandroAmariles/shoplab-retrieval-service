package com.shoplab.retrieval.infrastructure.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoplab.retrieval.support.PostgresTest;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** One run at a time across instances, with a lease that expires (specs/LAB-129, data-model). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "shoplab.retrieval.embedding.auto-index=false")
@PostgresTest
class JdbcIndexingLockAdapterTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void freeTheLock() {
        jdbc.update("DELETE FROM retrieval.indexing_lock");
    }

    private JdbcIndexingLockAdapter instance(Duration lease) {
        return new JdbcIndexingLockAdapter(jdbc, lease);
    }

    private double secondsLeft() {
        return jdbc.queryForObject(
                "SELECT extract(epoch FROM locked_until - now()) FROM retrieval.indexing_lock", Double.class);
    }

    @Test
    void onlyOneRunTakesItAtATime() {
        var first = instance(Duration.ofSeconds(90));
        var second = instance(Duration.ofSeconds(90));

        String owner = first.tryAcquire().block();

        assertThat(owner).isNotBlank();
        assertThat(second.tryAcquire().blockOptional()).isEmpty();
        assertThat(first.tryAcquire().blockOptional()).as("not even the same instance").isEmpty();
        assertThat(secondsLeft()).isBetween(80.0, 90.0);
    }

    @Test
    void onlyItsOwnerRenewsAndReleasesIt() {
        var lock = instance(Duration.ofSeconds(90));
        String owner = lock.tryAcquire().block();

        assertThat(lock.renew("someone-else").block()).isFalse();
        lock.release("someone-else").block();
        assertThat(lock.tryAcquire().blockOptional()).isEmpty();

        assertThat(lock.renew(owner).block()).isTrue();
        lock.release(owner).block();
        assertThat(lock.tryAcquire().blockOptional()).isPresent();
    }

    @Test
    void anExpiredLeaseCanBeTakenAndItsFormerOwnerLosesIt() throws InterruptedException {
        var dead = instance(Duration.ofMillis(200));
        var alive = instance(Duration.ofSeconds(90));
        String former = dead.tryAcquire().block();

        Thread.sleep(400);

        assertThat(alive.tryAcquire().blockOptional()).isPresent();
        assertThat(dead.renew(former).block()).isFalse();
    }

    @Test
    void aShutdownReleasesWhatTheInstanceHolds() {
        var stopping = instance(Duration.ofSeconds(90));
        stopping.tryAcquire().block();

        stopping.releaseHeld();

        assertThat(instance(Duration.ofSeconds(90)).tryAcquire().blockOptional()).isPresent();
    }
}
