package com.shoplab.retrieval.application.port.out;

import reactor.core.publisher.Mono;

/**
 * The turn to compute and store embeddings, shared by every instance. It is a lease: its holder renews it while it
 * works, and it frees itself if the holder dies.
 */
public interface IndexingLockPort {

    /** Takes the turn and emits the token that identifies this run; empty when someone else holds it. Never waits. */
    Mono<String> tryAcquire();

    /** Extends the lease; emits false when the run no longer holds it. */
    Mono<Boolean> renew(String owner);

    /** Frees the turn if the run still holds it. */
    Mono<Void> release(String owner);
}
