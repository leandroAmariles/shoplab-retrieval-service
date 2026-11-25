package com.shoplab.retrieval.support;

import com.shoplab.retrieval.application.port.out.IndexingLockPort;
import java.util.UUID;
import reactor.core.publisher.Mono;

/** The lock for the tests of the use cases: same rules as the database one, without expiry. */
public class InMemoryIndexingLock implements IndexingLockPort {

    private String owner;

    /** Takes the lock as another run would. */
    public void takenByAnotherRun() {
        owner = "another-run";
    }

    public boolean isFree() {
        return owner == null;
    }

    @Override
    public Mono<String> tryAcquire() {
        return Mono.defer(() -> {
            if (owner != null) {
                return Mono.empty();
            }
            owner = UUID.randomUUID().toString();
            return Mono.just(owner);
        });
    }

    @Override
    public Mono<Boolean> renew(String holder) {
        return Mono.fromSupplier(() -> holder.equals(owner));
    }

    @Override
    public Mono<Void> release(String holder) {
        return Mono.fromRunnable(() -> {
            if (holder.equals(owner)) {
                owner = null;
            }
        });
    }
}
