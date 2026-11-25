package com.shoplab.retrieval.application.port.out;

import com.shoplab.retrieval.domain.model.DocumentSummary;
import reactor.core.publisher.Flux;


public interface DocumentCatalogPort {

    Flux<DocumentSummary> listDocuments();
}
