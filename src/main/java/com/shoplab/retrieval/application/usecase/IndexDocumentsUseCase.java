package com.shoplab.retrieval.application.usecase;

import com.shoplab.retrieval.application.port.in.IndexDocumentsServicePort;
import com.shoplab.retrieval.domain.model.IndexingSummary;
import com.shoplab.retrieval.domain.model.enums.IndexScope;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class IndexDocumentsUseCase implements IndexDocumentsServicePort {

    private final DocumentEmbedder embedder;

    public IndexDocumentsUseCase(DocumentEmbedder embedder) {
        this.embedder = embedder;
    }

    @Override
    public Mono<IndexingSummary> indexPending(boolean retryFailed) {
        return embedder.run(retryFailed ? IndexScope.RETRY : IndexScope.PENDING);
    }
}
