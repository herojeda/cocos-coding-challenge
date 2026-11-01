package com.cocoscap.challenge.architecture;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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

    public static final ArchRule DOMAIN_SUBPACKAGES_ARE_FREE_OF_CYCLES = slices()
            .matching("..domain.(*)..")
            .should()
            .beFreeOfCycles()
            .because("ADR-003: a concept with its own API never depends on the aggregate that uses it")
            .allowEmptyShould(true);

    public static final ArchRule DOMAIN_TYPES_HAVE_NO_STEREOTYPE_SUFFIX = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .haveSimpleNameEndingWith("Domain")
            .orShould().haveSimpleNameEndingWith("Entity")
            .orShould().haveSimpleNameEndingWith("VO")
            .orShould().haveSimpleNameEndingWith("UseCase")
            .orShould().haveSimpleNameEndingWith("Port")
            .orShould().haveSimpleNameEndingWith("Adapter")
            .orShould().haveSimpleNameEndingWith("Row")
            .orShould().haveSimpleNameEndingWith("Request")
            .orShould().haveSimpleNameEndingWith("Response")
            .because("ADR-003: domain classes are named with the business term, without stereotype suffixes")
            .allowEmptyShould(true);

    public static final ArchRule ROLE_SUFFIXES_LIVE_IN_THEIR_LAYER = CompositeArchRule.of(List.of(
                    suffixResidesIn("UseCase", "..application.."),
                    suffixResidesIn("Port", "..application.."),
                    classes().that().haveSimpleNameEndingWith("Port").should().beInterfaces()
                            .allowEmptyShould(true),
                    suffixResidesIn("Controller", "..adapter.in.web.."),
                    suffixResidesIn("Request", "..adapter.in.web.."),
                    suffixResidesIn("Response", "..adapter.in.web.."),
                    suffixResidesIn("Row", "..adapter.out.persistence.."),
                    classes().that().haveSimpleNameStartingWith("Jdbc").and().haveSimpleNameEndingWith("Adapter")
                            .should().resideInAPackage("..adapter.out.persistence..")
                            .allowEmptyShould(true),
                    suffixResidesIn("Config", "..config..")))
            .because("ADR-003: the suffix states the architectural role, so it must match the layer")
            .allowEmptyShould(true);

    private static ArchRule suffixResidesIn(String suffix, String packageIdentifier) {
        return classes()
                .that()
                .haveSimpleNameEndingWith(suffix)
                .should()
                .resideInAPackage(packageIdentifier)
                .allowEmptyShould(true);
    }

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
