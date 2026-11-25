package com.shoplab.retrieval.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EmbeddingBatchesTests {

    @Test
    void splitsInBatchesOfTheGivenSizeKeepingTheOrder() {
        List<List<Integer>> batches = EmbeddingBatches.split(List.of(1, 2, 3, 4, 5), 2);

        assertThat(batches).containsExactly(List.of(1, 2), List.of(3, 4), List.of(5));
    }

    @Test
    void anEmptyListHasNoBatchesAndABigBatchHoldsEverything() {
        assertThat(EmbeddingBatches.split(List.<Integer>of(), 3)).isEmpty();
        assertThat(EmbeddingBatches.split(List.of(1, 2), 16)).containsExactly(List.of(1, 2));
    }
}
