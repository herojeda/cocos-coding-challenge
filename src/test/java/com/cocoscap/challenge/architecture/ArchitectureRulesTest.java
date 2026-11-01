package com.cocoscap.challenge.architecture;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cocoscap.challenge.architecture.fixtures.adapter.in.web.WebDependingOnPersistence;
import com.cocoscap.challenge.architecture.fixtures.adapter.out.observability.ObservabilityMarker;
import com.cocoscap.challenge.architecture.fixtures.adapter.out.persistence.PersistenceDependingOnObservability;
import com.cocoscap.challenge.architecture.fixtures.adapter.out.persistence.PersistenceMarker;
import com.cocoscap.challenge.architecture.fixtures.application.ApplicationDependingOnAdapter;
import com.cocoscap.challenge.architecture.fixtures.application.ApplicationUsingService;
import com.cocoscap.challenge.architecture.fixtures.application.CompliantTransactionalUseCase;
import com.cocoscap.challenge.architecture.fixtures.application.ConcreteSearchPort;
import com.cocoscap.challenge.architecture.fixtures.application.MisplacedController;
import com.cocoscap.challenge.architecture.fixtures.domain.CompliantDomainType;
import com.cocoscap.challenge.architecture.fixtures.domain.DomainDependingOnSpring;
import com.cocoscap.challenge.architecture.fixtures.domain.PriceVO;
import com.cocoscap.challenge.architecture.fixtures.domain.cyclea.CycleA;
import com.cocoscap.challenge.architecture.fixtures.domain.cycleb.CycleB;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ArchitectureRulesTest {

    @Test
    @DisplayName("compliant fixtures satisfy all hexagonal rules including @Transactional use case")
    void compliantFixturesSatisfyAllRules() {
        JavaClasses classes = importClasses(CompliantDomainType.class, CompliantTransactionalUseCase.class);

        assertThatCode(() -> {
                    HexagonalArchitectureRules.DOMAIN_DOES_NOT_DEPEND_ON_FORBIDDEN_TYPES.check(classes);
                    HexagonalArchitectureRules.APPLICATION_DEPENDS_ONLY_ON_DOMAIN_AND_TRANSACTIONAL.check(classes);
                    HexagonalArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER.check(classes);
                    HexagonalArchitectureRules.DOMAIN_AND_APPLICATION_DO_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG.check(
                            classes);
                    HexagonalArchitectureRules.DOMAIN_SUBPACKAGES_ARE_FREE_OF_CYCLES.check(classes);
                    HexagonalArchitectureRules.DOMAIN_TYPES_HAVE_NO_STEREOTYPE_SUFFIX.check(classes);
                    HexagonalArchitectureRules.ROLE_SUFFIXES_LIVE_IN_THEIR_LAYER.check(classes);
                })
                .doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("violations")
    void ruleDetectsViolation(String scenario, ArchRule rule, Class<?>... classesToImport) {
        JavaClasses classes = importClasses(classesToImport);

        assertThatThrownBy(() -> rule.check(classes)).isInstanceOf(AssertionError.class);
    }

    static Stream<Arguments> violations() {
        return Stream.of(
                Arguments.of(
                        "domain depending on Spring violates domain isolation",
                        HexagonalArchitectureRules.DOMAIN_DOES_NOT_DEPEND_ON_FORBIDDEN_TYPES,
                        new Class<?>[] {DomainDependingOnSpring.class}),
                Arguments.of(
                        "application using @Service violates application dependency rule",
                        HexagonalArchitectureRules.APPLICATION_DEPENDS_ONLY_ON_DOMAIN_AND_TRANSACTIONAL,
                        new Class<?>[] {ApplicationUsingService.class}),
                Arguments.of(
                        "web adapter depending on persistence violates adapter independence",
                        HexagonalArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER,
                        new Class<?>[] {WebDependingOnPersistence.class, PersistenceMarker.class}),
                Arguments.of(
                        "persistence depending on observability violates adapter independence",
                        HexagonalArchitectureRules.ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER,
                        new Class<?>[] {PersistenceDependingOnObservability.class, ObservabilityMarker.class}),
                Arguments.of(
                        "application depending on adapter violates inward dependency rule",
                        HexagonalArchitectureRules.DOMAIN_AND_APPLICATION_DO_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG,
                        new Class<?>[] {ApplicationDependingOnAdapter.class, PersistenceMarker.class}),
                Arguments.of(
                        "domain subpackages depending on each other violate the no-cycles rule",
                        HexagonalArchitectureRules.DOMAIN_SUBPACKAGES_ARE_FREE_OF_CYCLES,
                        new Class<?>[] {CycleA.class, CycleB.class}),
                Arguments.of(
                        "domain type with a VO suffix violates domain naming",
                        HexagonalArchitectureRules.DOMAIN_TYPES_HAVE_NO_STEREOTYPE_SUFFIX,
                        new Class<?>[] {PriceVO.class}),
                Arguments.of(
                        "controller outside the web adapter violates role suffixes",
                        HexagonalArchitectureRules.ROLE_SUFFIXES_LIVE_IN_THEIR_LAYER,
                        new Class<?>[] {MisplacedController.class}),
                Arguments.of(
                        "port that is not an interface violates role suffixes",
                        HexagonalArchitectureRules.ROLE_SUFFIXES_LIVE_IN_THEIR_LAYER,
                        new Class<?>[] {ConcreteSearchPort.class}));
    }

    private static JavaClasses importClasses(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes);
    }
}
