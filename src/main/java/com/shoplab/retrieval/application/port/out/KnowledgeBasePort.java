package com.shoplab.retrieval.application.port.out;

import com.shoplab.retrieval.domain.model.IngestedDocument;
import com.shoplab.retrieval.domain.model.ParsedDocument;
import reactor.core.publisher.Mono;

public interface KnowledgeBasePort {

    /**
     * Stores the document and its chunks, without vectors yet, replacing the chunks of a document with the same name.
     * Either everything is stored or nothing is.
     */
    Mono<IngestedDocument> replaceDocument(ParsedDocument parsed);
}
