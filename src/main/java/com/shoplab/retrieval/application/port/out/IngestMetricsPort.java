package com.shoplab.retrieval.application.port.out;

import com.shoplab.retrieval.domain.model.enums.IngestOutcome;
import java.time.Duration;

public interface IngestMetricsPort {

    void recordParse(IngestOutcome outcome, Duration elapsed);
}
