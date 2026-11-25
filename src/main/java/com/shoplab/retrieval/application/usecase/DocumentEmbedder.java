package com.shoplab.retrieval.application.usecase;

import com.shoplab.retrieval.application.port.out.EmbeddingMetricsPort;
import com.shoplab.retrieval.application.port.out.EmbeddingPort;
import com.shoplab.retrieval.application.port.out.EmbeddingStorePort;
import com.shoplab.retrieval.application.port.out.IndexingLockPort;
import com.shoplab.retrieval.config.EmbeddingProperties;
import com.shoplab.retrieval.domain.model.ChunkEmbedding;
import com.shoplab.retrieval.domain.model.ChunkToEmbed;
import com.shoplab.retrieval.domain.model.IndexingSummary;
import com.shoplab.retrieval.domain.model.PendingDocument;
import com.shoplab.retrieval.domain.model.enums.EmbeddingPurpose;
import com.shoplab.retrieval.domain.model.enums.IndexOutcome;
import com.shoplab.retrieval.domain.model.enums.IndexScope;
import com.shoplab.retrieval.shared.exception.ExceptionUtil;
import com.shoplab.retrieval.shared.exception.HelpdeskException;
import com.shoplab.retrieval.shared.exception.ResponseCodeEnum;
import java.io.Serial;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The algorithm both the indexing and the reindexing use: for each document of a scope, compute the vectors of all its
 * chunks in batches and store them together, so a document is either fully on the new vectors or untouched. A failure
 * of the model marks that document and goes on with the next one; nothing here logs the text of a chunk.
 * Only one run works at a time across all instances: it renews its turn before each batch, and a run that lost it
 * stops without storing.
 */
@Component
public class DocumentEmbedder {

    private static final Logger LOG = LoggerFactory.getLogger(DocumentEmbedder.class);

    private final EmbeddingStorePort store;
    private final EmbeddingPort embedding;
    private final EmbeddingMetricsPort metrics;
    private final IndexingLockPort lock;
    private final EmbeddingProperties properties;

    public DocumentEmbedder(EmbeddingStorePort store, EmbeddingPort embedding, EmbeddingMetricsPort metrics,
            IndexingLockPort lock, EmbeddingProperties properties) {
        this.store = store;
        this.embedding = embedding;
        this.metrics = metrics;
        this.lock = lock;
        this.properties = properties;
    }

    /** Goes through the documents of the scope; when another run is in progress it does nothing. */
    public Mono<IndexingSummary> run(IndexScope scope) {
        return lock.tryAcquire()
                .flatMap(owner -> Mono.usingWhen(Mono.just(owner), held -> runHolding(scope, held),
                        this::release, (held, error) -> release(held), this::release))
                .onErrorResume(LockLost.class, lost -> {
                    LOG.warn("Embeddings run stopped, it no longer holds the lock: scope={}", scope);
                    return Mono.just(IndexingSummary.skippedRun());
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    LOG.info("Embeddings run skipped: another one is in progress, scope={}", scope);
                    return IndexingSummary.skippedRun();
                }));
    }

    private Mono<IndexingSummary> runHolding(IndexScope scope, String owner) {
        String model = embedding.modelName();
        return store.findDocuments(scope, model)
                .concatMap(document -> process(document, model, owner))
                .reduce(IndexingSummary.empty(), IndexingSummary::plus)
                .doOnNext(summary -> LOG.info("Embeddings run finished: scope={}, model={}, documents={}, "
                        + "chunks={}, failed={}", scope, model, summary.documents(), summary.chunks(),
                        summary.failed().size()));
    }

    /** A release that fails only delays the next run: the lease expires by itself. */
    private Mono<Void> release(String owner) {
        return lock.release(owner)
                .onErrorResume(error -> {
                    LOG.warn("The indexing lock could not be released, it will expire: error={}", error.toString());
                    return Mono.empty();
                });
    }

    private Mono<IndexingSummary> process(PendingDocument document, String model, String owner) {
        if (document.chunks().isEmpty()) {
            metrics.recordDocument(IndexOutcome.SKIPPED);
            return Mono.just(IndexingSummary.empty());
        }
        return computeVectors(document, owner)
                .map(Computed::ok)
                .onErrorResume(HelpdeskException.class, error -> Mono.just(Computed.failed(error)))
                .flatMap(computed -> computed.error() == null
                        ? store(document, computed.vectors(), model)
                        : fail(document, computed.error()));
    }

    private Mono<List<ChunkEmbedding>> computeVectors(PendingDocument document, String owner) {
        return Flux.fromIterable(EmbeddingBatches.split(document.chunks(), properties.batchSize()))
                .concatMap(batch -> lock.renew(owner)
                        .filter(Boolean::booleanValue)
                        .switchIfEmpty(Mono.error(LockLost::new))
                        .thenMany(embedBatch(batch)))
                .collectList();
    }

    private Flux<ChunkEmbedding> embedBatch(List<ChunkToEmbed> batch) {
        long startedAt = System.nanoTime();
        return embedding.embed(batch.stream().map(ChunkToEmbed::text).toList(), EmbeddingPurpose.DOCUMENT_INDEX)
                .doOnSuccess(vectors -> metrics.recordBatch(IndexOutcome.INDEXED, elapsedSince(startedAt)))
                .doOnError(error -> metrics.recordBatch(IndexOutcome.FAILED, elapsedSince(startedAt)))
                .flatMapMany(vectors -> {
                    if (vectors.size() != batch.size()) {
                        return Flux.error(ExceptionUtil.technical(ResponseCodeEnum.EMBEDDING_SERVICE_FAILED,
                                new IllegalStateException("The model returned " + vectors.size() + " vectors for "
                                        + batch.size() + " texts")));
                    }
                    List<ChunkEmbedding> pairs = new ArrayList<>(batch.size());
                    for (int index = 0; index < batch.size(); index++) {
                        pairs.add(new ChunkEmbedding(batch.get(index).chunkId(), vectors.get(index)));
                    }
                    return Flux.fromIterable(pairs);
                });
    }

    private Mono<IndexingSummary> store(PendingDocument document, List<ChunkEmbedding> vectors, String model) {
        return store.saveEmbeddings(document, vectors, model).map(saved -> {
            if (saved) {
                metrics.recordDocument(IndexOutcome.INDEXED);
                LOG.info("Document indexed: source={}, documentId={}, chunks={}", document.source(),
                        document.documentId(), vectors.size());
                return IndexingSummary.indexed(vectors.size());
            }
            metrics.recordDocument(IndexOutcome.SKIPPED);
            LOG.info("Vectors discarded, the document changed meanwhile: source={}, documentId={}",
                    document.source(), document.documentId());
            return IndexingSummary.empty();
        });
    }

    private Mono<IndexingSummary> fail(PendingDocument document, HelpdeskException error) {
        String code = error.getResponseCode().getCode();
        LOG.warn("Embeddings failed: source={}, documentId={}, code={}", document.source(), document.documentId(),
                code);
        metrics.recordDocument(IndexOutcome.FAILED);
        return store.markFailed(document, error.getResponseCode().getDefaultMessage())
                .thenReturn(IndexingSummary.failure(document.source(), code));
    }

    private static Duration elapsedSince(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    /** Another run took the turn: nothing of this one may be stored from here on. */
    private static final class LockLost extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    private record Computed(List<ChunkEmbedding> vectors, HelpdeskException error) {

        static Computed ok(List<ChunkEmbedding> vectors) {
            return new Computed(vectors, null);
        }

        static Computed failed(HelpdeskException error) {
            return new Computed(null, error);
        }
    }
}
