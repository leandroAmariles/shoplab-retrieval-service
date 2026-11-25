package com.shoplab.retrieval.application.port.out;

import com.shoplab.retrieval.domain.model.enums.IndexOutcome;
import java.time.Duration;

public interface EmbeddingMetricsPort {

    void recordBatch(IndexOutcome outcome, Duration elapsed);

    void recordDocument(IndexOutcome outcome);

    void recordSearch(Duration elapsed);
}
