package com.shoplab.retrieval.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The codes consumers already depend on, with the statuses they had in the monolith (specs/LAB-129, FR-008). */
class ResponseCodeEnumTests {

    @Test
    void keepsEveryCodeAndStatusOfTheMonolith() {
        assertThat(Arrays.stream(ResponseCodeEnum.values())
                .map(code -> code.getCode() + " " + code.getHttpStatus().value()))
                .containsExactly(
                        "SLHD_RETR_00001 415", "SLHD_RETR_00002 413", "SLHD_RETR_00003 422", "SLHD_RETR_00004 503",
                        "SLHD_RETR_00005 502", "SLHD_RETR_00006 503", "SLHD_RETR_00007 502", "SLHD_RETR_00008 502",
                        "SLHD_RETR_00009 422", "SLHD_RETR_00010 429", "SLHD_DBAS_00001 503", "SLHD_DBAS_00002 500",
                        "SLHD_GENE_00001 500", "SLHD_GENE_00002 400");
    }
}
