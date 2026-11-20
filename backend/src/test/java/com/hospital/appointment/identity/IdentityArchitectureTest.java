package com.hospital.appointment.identity;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.hospital.appointment.identity", importOptions = ImportOption.DoNotIncludeTests.class)
class IdentityArchitectureTest {
    @ArchTest
    static final ArchRule domainIsIndependent = noClasses().that().resideInAPackage("..identity.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..identity.infrastructure..", "..identity.api..");

    @ArchTest
    static final ArchRule apiIsSeparated = noClasses().that().resideInAPackage("..identity.api..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");

    @ArchTest
    static final ArchRule layersHaveOwners = classes().that().resideInAPackage("..identity..")
            .should().resideInAnyPackage("..identity.api..", "..identity.application..", "..identity.domain..", "..identity.infrastructure..");
}
