package com.shoplab.retrieval.infrastructure.in.event;

import com.shoplab.retrieval.domain.model.IndexingSummary;
import java.time.Duration;
import java.util.function.Supplier;
import reactor.core.publisher.Mono;

/**
 * Tries again a run that found the lock taken. The attempts must outlast the lease of 90 seconds: after a restart the
 * dead instance may still hold it.
 */
final class RetryWhileSkipped {

    static final Duration DEFAULT_DELAY = Duration.ofSeconds(10);
    private static final int MAX_ATTEMPTS = 12;

    private RetryWhileSkipped() {
    }

    static Mono<IndexingSummary> run(Supplier<Mono<IndexingSummary>> indexing, Duration delay) {
        return attempt(indexing, delay, MAX_ATTEMPTS);
    }

    private static Mono<IndexingSummary> attempt(Supplier<Mono<IndexingSummary>> indexing, Duration delay,
            int attemptsLeft) {
        return indexing.get().flatMap(summary -> {
            if (summary.skipped() && attemptsLeft > 1) {
                return Mono.delay(delay).then(Mono.defer(() -> attempt(indexing, delay, attemptsLeft - 1)));
            }
            return Mono.just(summary);
        });
    }
}
