package com.shoplab.retrieval.application.port.in;

import com.shoplab.retrieval.domain.model.DocumentSummary;
import reactor.core.publisher.Flux;

public interface ListDocumentsServicePort {

    /** Every document of the knowledge base with its state and chunk count, the most recently ingested first. */
    Flux<DocumentSummary> list();
}
