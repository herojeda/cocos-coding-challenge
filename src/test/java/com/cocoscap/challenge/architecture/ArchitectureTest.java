package com.cocoscap.challenge.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.cocoscap.challenge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnForbiddenTypes =
            HexagonalArchitectureRules.DOMAIN_DOES_NOT_DEPEND_ON_FORBIDDEN_TYPES;

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomainAndTransactional =
            HexagonalArchitectureRules.APPLICATION_DEPENDS_ONLY_ON_DOMAIN_AND_TRANSACTIONAL;

    @ArchTest
    static final ArchRule adaptersDoNotDependOnEachOther =
            HexagonalArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER;

    @ArchTest
    static final ArchRule domainAndApplicationDoNotDependOnAdaptersOrConfig =
            HexagonalArchitectureRules.DOMAIN_AND_APPLICATION_DO_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG;
}
