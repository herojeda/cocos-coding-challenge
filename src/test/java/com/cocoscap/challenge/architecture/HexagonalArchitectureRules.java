package com.cocoscap.challenge.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import java.util.List;

/**
 * Reusable ArchUnit rules for ADR-003 hexagonal package boundaries.
 */
public final class HexagonalArchitectureRules {

    private HexagonalArchitectureRules() {
    }

    private static final DescribedPredicate<JavaClass> ALLOWED_DOMAIN_DEPENDENCIES =
            resideInAnyPackage("..domain..", "java..")
                    .and(DescribedPredicate.not(resideInAnyPackage("java.sql..", "javax.sql..")));

    /**
     * ADR-003: the only Spring dependency allowed in {@code application} is {@code @Transactional}.
     * ArchUnit also sees {@code Isolation} and {@code Propagation} as annotation members of that type,
     * so the whole {@code org.springframework.transaction.annotation} package is the explicit exception.
     */
    private static final DescribedPredicate<JavaClass> ALLOWED_APPLICATION_DEPENDENCIES =
            resideInAnyPackage("..application..", "..domain..", "java..")
                    .and(DescribedPredicate.not(resideInAnyPackage("java.sql..", "javax.sql..")))
                    .or(resideInAPackage("org.springframework.transaction.annotation"));

    public static final ArchRule DOMAIN_DOES_NOT_DEPEND_ON_FORBIDDEN_TYPES = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat(ALLOWED_DOMAIN_DEPENDENCIES)
            .because(
                    "ADR-003: domain must not depend on other project packages, Spring, JDBC, Jackson, or Jakarta")
            .allowEmptyShould(true);

    public static final ArchRule APPLICATION_DEPENDS_ONLY_ON_DOMAIN_AND_TRANSACTIONAL = classes()
            .that()
            .resideInAPackage("..application..")
            .should()
            .onlyDependOnClassesThat(ALLOWED_APPLICATION_DEPENDENCIES)
            .because(
                    "ADR-003: application may depend on domain and JDK only;"
                            + " the sole Spring exception is org.springframework.transaction.annotation"
                            + " (@Transactional and its annotation member types)")
            .allowEmptyShould(true);

    public static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER = CompositeArchRule.of(List.of(
                    noAdapterDependency(
                            "..adapter.in.web..",
                            "..adapter.out.persistence..",
                            "..adapter.out.observability.."),
                    noAdapterDependency(
                            "..adapter.out.persistence..",
                            "..adapter.in.web..",
                            "..adapter.out.observability.."),
                    noAdapterDependency(
                            "..adapter.out.observability..",
                            "..adapter.in.web..",
                            "..adapter.out.persistence..")))
            .because("ADR-003 / ADR-009: adapters must not depend on each other")
            .allowEmptyShould(true);

    public static final ArchRule DOMAIN_AND_APPLICATION_DO_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..adapter..", "..config..")
            .because("ADR-003: domain and application must not depend on adapters or config")
            .allowEmptyShould(true);

    private static ArchRule noAdapterDependency(String fromPackage, String... forbiddenPackages) {
        return noClasses()
                .that()
                .resideInAPackage(fromPackage)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(forbiddenPackages)
                .allowEmptyShould(true);
    }
}
