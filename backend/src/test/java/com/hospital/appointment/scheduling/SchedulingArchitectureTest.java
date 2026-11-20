package com.hospital.appointment.scheduling;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.hospital.appointment.scheduling", importOptions = ImportOption.DoNotIncludeTests.class)
class SchedulingArchitectureTest {
    @ArchTest
    static final ArchRule domainIsIndependent = noClasses().that().resideInAPackage("..scheduling.domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "..scheduling.infrastructure..", "..scheduling.api..");

    @ArchTest
    static final ArchRule apiIsSeparated = noClasses().that().resideInAPackage("..scheduling.api..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.jdbc..");

    @ArchTest
    static final ArchRule layersHaveOwners = classes().that().resideInAPackage("..scheduling..")
            .should().resideInAnyPackage("..scheduling.api..", "..scheduling.application..", "..scheduling.domain..", "..scheduling.infrastructure..");

    @ArchTest
    static final ArchRule doctorsDoNotDependOnAccounts = noClasses().that().resideInAPackage("..scheduling..")
            .should().dependOnClassesThat().resideInAPackage("..identity.domain..");
}
