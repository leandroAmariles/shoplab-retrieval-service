package com.shoplab.retrieval;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** The rules the module had in the monolith, plus the one of an extracted service (ADR-0015, ADR-0031). */
class ArchitectureTests {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.shoplab.retrieval");

    @Test
    void theDomainDoesNotKnowProvidersMessagingPersistenceOrReactor() {
        noClasses().that().resideInAPackage("com.shoplab.retrieval.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.ai..", "org.apache.kafka..",
                        "jakarta.persistence..", "reactor..", "..infrastructure..", "..application..")
                .check(CLASSES);
    }

    @Test
    void theApplicationLayerDoesNotUseAdapters() {
        noClasses().that().resideInAPackage("com.shoplab.retrieval.application..")
                .should().dependOnClassesThat().resideInAPackage("com.shoplab.retrieval.infrastructure..")
                .check(CLASSES);
    }

    @Test
    void nothingComesFromTheMonolith() {
        noClasses().should().dependOnClassesThat().resideInAPackage("com.shoplab.helpdesk..").check(CLASSES);
    }
}
