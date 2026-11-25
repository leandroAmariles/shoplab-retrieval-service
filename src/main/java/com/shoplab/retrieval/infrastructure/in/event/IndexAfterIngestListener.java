package com.shoplab.retrieval.infrastructure.in.event;

import com.shoplab.retrieval.application.port.in.IndexDocumentsServicePort;
import com.shoplab.retrieval.domain.model.DocumentIngestedEvent;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Starts the embeddings step when a document was just ingested, without making the ingestion wait for it or fail
 * with it. If another run is in progress (it may not have seen this document) it tries again a few times.
 */
@Component
@ConditionalOnProperty(name = "shoplab.retrieval.embedding.auto-index", havingValue = "true", matchIfMissing = true)
public class IndexAfterIngestListener {

    private static final Logger LOG = LoggerFactory.getLogger(IndexAfterIngestListener.class);

    private final IndexDocumentsServicePort indexDocuments;
    private final Duration retryDelay;

    @Autowired
    public IndexAfterIngestListener(IndexDocumentsServicePort indexDocuments) {
        this(indexDocuments, RetryWhileSkipped.DEFAULT_DELAY);
    }

    IndexAfterIngestListener(IndexDocumentsServicePort indexDocuments, Duration retryDelay) {
        this.indexDocuments = indexDocuments;
        this.retryDelay = retryDelay;
    }

    @EventListener
    public void onDocumentIngested(DocumentIngestedEvent event) {
        RetryWhileSkipped.run(() -> indexDocuments.indexPending(false), retryDelay).subscribe(
                summary -> LOG.debug("Indexing after ingestion finished: source={}, documents={}", event.source(),
                        summary.documents()),
                error -> LOG.warn("Indexing after ingestion failed: source={}, error={}", event.source(),
                        error.toString()));
    }
}
