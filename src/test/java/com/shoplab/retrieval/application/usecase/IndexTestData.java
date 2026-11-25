package com.shoplab.retrieval.application.usecase;

import com.shoplab.retrieval.domain.model.PendingDocument;

/** Shared builders of the use case tests. */
final class IndexTestData {

    private IndexTestData() {
    }

    static PendingDocument document(String source, int chunks) {
        return IndexDocumentsUseCaseTests.document(source, chunks);
    }
}
