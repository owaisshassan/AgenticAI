package com.aira.guardian.rules;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassSpecValidatorTest {

    private ClassSpecValidator validator;
    private RuleSet rules;

    @BeforeEach
    void setUp() {
        validator = new ClassSpecValidator();
        rules = new RuleSet(
                List.of(
                        new RuleSet.Layer("controller", "**/controller/**", List.of("service")),
                        new RuleSet.Layer("service", "**/service/**", List.of("repository")),
                        new RuleSet.Layer("repository", "**/repository/**", List.of())
                ),
                Map.of(
                        "service", "^[A-Z][A-Za-z0-9]*Service$",
                        "repository", "^[A-Z][A-Za-z0-9]*Repository$",
                        "controller", "^[A-Z][A-Za-z0-9]*Controller$"
                ),
                Map.of(
                        "service", List.of("org.springframework.stereotype.Service"),
                        "repository", List.of("org.springframework.data.jpa.repository.JpaRepository")
                ),
                new RuleSet.TestGate(List.of("**/service/**"), List.of("**/dto/**"), 80, 1, true)
        );
    }

    @Test
    void validSpecPasses() {
        ClassSpec spec = new ClassSpec(
                "InvoiceService", "com.acme.service", "service",
                List.of(new ClassSpec.Field("repo", "InvoiceRepository")),
                List.of("org.springframework.stereotype.Service"));

        ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

        assertThat(result.valid()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void badNamingIsCaughtWithNamingViolationCode() {
        ClassSpec spec = new ClassSpec(
                "InvoiceHandler", "com.acme.service", "service",
                List.of(), List.of("org.springframework.stereotype.Service"));

        ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting(ClassSpecValidator.Violation::code)
                .contains("naming_violation");
    }

    @Test
    void wrongLayerCallDirectionIsCaughtWithIllegalLayerCallCode() {
        // repository may not depend on service - direction reversed on purpose.
        ClassSpec spec = new ClassSpec(
                "InvoiceRepository", "com.acme.repository", "repository",
                List.of(new ClassSpec.Field("svc", "InvoiceService")),
                List.of("org.springframework.data.jpa.repository.JpaRepository"));

        ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting(ClassSpecValidator.Violation::code)
                .contains("illegal_layer_call");
    }

    @Test
    void missingRequiredAnnotationIsCaughtWithMissingRequiredAnnotationCode() {
        ClassSpec spec = new ClassSpec(
                "InvoiceService", "com.acme.service", "service",
                List.of(), List.of());

        ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting(ClassSpecValidator.Violation::code)
                .contains("missing_required_annotation");
    }

    @Test
    void unknownLayerIsCaughtWithUnknownLayerCode() {
        ClassSpec spec = new ClassSpec("Whatever", "com.acme.x", "presentation", List.of(), List.of());

        ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations()).extracting(ClassSpecValidator.Violation::code)
                .contains("unknown_layer");
    }

    @Test
    void everyViolationHasAnActionableHintNeverBlank() {
        ClassSpec spec = new ClassSpec("bad_name", "com.acme.service", "service", List.of(), List.of());

        ClassSpecValidator.ValidationResult result = validator.validate(spec, rules);

        assertThat(result.violations()).isNotEmpty();
        assertThat(result.violations()).allSatisfy(v -> assertThat(v.hint()).isNotBlank());
    }
}
