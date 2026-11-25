package com.shoplab.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.shoplab.retrieval.application.port.out.EmbeddingMetricsPort;
import com.shoplab.retrieval.application.port.out.EmbeddingStorePort;
import com.shoplab.retrieval.application.usecase.DocumentEmbedder;
import com.shoplab.retrieval.config.EmbeddingProperties;
import com.shoplab.retrieval.domain.model.IndexingSummary;
import com.shoplab.retrieval.domain.model.enums.IndexScope;
import com.shoplab.retrieval.infrastructure.out.persistence.JdbcIndexingLockAdapter;
import com.shoplab.retrieval.support.DeterministicEmbeddingPort;
import com.shoplab.retrieval.support.PostgresTest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Two instances of the service over the same database (specs/LAB-129, US5). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "shoplab.retrieval.embedding.auto-index=false")
@PostgresTest
class IndexingAcrossInstancesIntegrationTest {

    private static final String MODEL = "bge-m3";
    private static final String SOURCE = "instances-test.md";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EmbeddingStorePort store;

    private final DeterministicEmbeddingPort model = new DeterministicEmbeddingPort(MODEL);

    @BeforeEach
    void aPendingDocumentOfTwoChunks() {
        clean();
        UUID document = jdbc.queryForObject(
                "INSERT INTO retrieval.document (source, title) VALUES (?, 'Prueba') RETURNING id", UUID.class, SOURCE);
        for (int index = 0; index < 2; index++) {
            jdbc.update("INSERT INTO retrieval.chunk (document_id, chunk_index, content) VALUES (?, ?, ?)", document,
                    index, "fragmento " + index);
        }
    }

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM retrieval.indexing_lock");
        jdbc.update("DELETE FROM retrieval.document WHERE source = ?", SOURCE);
    }

    private DocumentEmbedder instance(JdbcIndexingLockAdapter lock) {
        return new DocumentEmbedder(store, model, mock(EmbeddingMetricsPort.class), lock,
                new EmbeddingProperties(1, 5, false, "false"));
    }

    private JdbcIndexingLockAdapter lock(Duration lease) {
        return new JdbcIndexingLockAdapter(jdbc, lease);
    }

    private String status() {
        return jdbc.queryForObject("SELECT index_status FROM retrieval.document WHERE source = ?", String.class, SOURCE);
    }

    @Test
    void aSecondInstanceDoesNothingWhileTheFirstOneIndexes() {
        DocumentEmbedder first = instance(lock(Duration.ofSeconds(90)));
        DocumentEmbedder second = instance(lock(Duration.ofSeconds(90)));
        List<IndexingSummary> meanwhile = new ArrayList<>();
        model.beforeEachBatch(() -> {
            model.beforeEachBatch(null);
            meanwhile.add(second.run(IndexScope.PENDING).block());
        });

        IndexingSummary summary = first.run(IndexScope.PENDING).block();

        assertThat(meanwhile).singleElement().satisfies(skipped -> assertThat(skipped.skipped()).isTrue());
        assertThat(summary.documents()).isEqualTo(1);
        assertThat(model.textsEmbedded()).isEqualTo(2);
        assertThat(status()).isEqualTo("INDEXED");
        assertThat(second.run(IndexScope.PENDING).block().documents()).isZero();
    }

    @Test
    void anotherInstanceGoesOnWhenTheOwnerDiedAndItsLeaseExpired() throws InterruptedException {
        lock(Duration.ofMillis(300)).tryAcquire().block();
        DocumentEmbedder alive = instance(lock(Duration.ofSeconds(90)));

        assertThat(alive.run(IndexScope.PENDING).block().skipped()).isTrue();
        Thread.sleep(500);

        assertThat(alive.run(IndexScope.PENDING).block().documents()).isEqualTo(1);
        assertThat(status()).isEqualTo("INDEXED");
    }

    @Test
    void aRunThatLostTheLockStopsWithoutStoring() {
        model.beforeEachBatch(() -> {
            model.beforeEachBatch(null);
            jdbc.update("UPDATE retrieval.indexing_lock SET owner = 'another-instance'");
        });

        IndexingSummary summary = instance(lock(Duration.ofSeconds(90))).run(IndexScope.PENDING).block();

        assertThat(summary.skipped()).isTrue();
        assertThat(status()).isEqualTo("PENDING");
        assertThat(model.textsEmbedded()).as("the second batch is never asked").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT owner FROM retrieval.indexing_lock", String.class))
                .isEqualTo("another-instance");
    }

    @Test
    void theLockDoesNotBlockWritingTheDocuments() {
        lock(Duration.ofSeconds(90)).tryAcquire().block();

        assertThat(jdbc.update("UPDATE retrieval.document SET title = 'Otra' WHERE source = ?", SOURCE)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM retrieval.chunk", Integer.class)).isPositive();
    }
}
