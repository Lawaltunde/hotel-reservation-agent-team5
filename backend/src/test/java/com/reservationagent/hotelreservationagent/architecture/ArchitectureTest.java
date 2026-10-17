package com.reservationagent.hotelreservationagent.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Layer rules for the modular monolith (README §2). Layers are identified by package
 * name anywhere under the base package: {@code ..domain..}, {@code ..application..},
 * {@code ..infrastructure..}, {@code ..presentation..}.
 *
 * <p>{@code allowEmptyShould(true)} keeps the rules green while a layer has no classes yet.
 */
@AnalyzeClasses(
        packages = "com.reservationagent.hotelreservationagent",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String PRESENTATION = "..presentation..";

    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "tools.jackson..",
                    "com.fasterxml..")
            .because("the domain model must stay plain Java")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDependsOnNoOtherLayer = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE, PRESENTATION)
            .because("the domain is the innermost layer")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdapters = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, PRESENTATION)
            .because("use cases reach adapters only through ports")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule presentationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(PRESENTATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .because("controllers go through the application layer")
            .allowEmptyShould(true);
}
