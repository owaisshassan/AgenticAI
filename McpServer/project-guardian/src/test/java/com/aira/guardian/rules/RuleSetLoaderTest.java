package com.aira.guardian.rules;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RuleSetLoaderTest {

    private static final String YAML = """
            layers:
              - name: controller
                package: "**/controller/**"
                mayCallLayers: [service]
              - name: service
                package: "**/service/**"
                mayCallLayers: [repository]
              - name: repository
                package: "**/repository/**"
                mayCallLayers: []

            naming:
              service: "^[A-Z][A-Za-z0-9]*Service$"
              repository: "^[A-Z][A-Za-z0-9]*Repository$"
              controller: "^[A-Z][A-Za-z0-9]*Controller$"

            requiredAnnotations:
              service: ["org.springframework.stereotype.Service"]
              repository: ["org.springframework.data.jpa.repository.JpaRepository"]

            testGate:
              requireTestForPackages: ["**/service/**", "**/controller/**"]
              skipPackages: ["**/dto/**"]
              minCoveragePercent: 80
              minAssertionsPerTestClass: 1
              forbidDisabledWithoutReason: true
            """;

    private final RuleSetLoader loader = new RuleSetLoader();

    @Test
    void parsesLayersNamingAndAnnotations() {
        RuleSet rules = parse(YAML);

        assertThat(rules.layers()).hasSize(3);
        assertThat(rules.layer("service").mayCallLayers()).containsExactly("repository");
        assertThat(rules.naming().get("service")).isEqualTo("^[A-Z][A-Za-z0-9]*Service$");
        assertThat(rules.requiredAnnotations().get("service"))
                .containsExactly("org.springframework.stereotype.Service");
    }

    @Test
    void parsesTestGate() {
        RuleSet rules = parse(YAML);

        assertThat(rules.testGate().minCoveragePercent()).isEqualTo(80);
        assertThat(rules.testGate().minAssertionsPerTestClass()).isEqualTo(1);
        assertThat(rules.testGate().forbidDisabledWithoutReason()).isTrue();
        assertThat(rules.testGate().requireTestForPackages()).containsExactly("**/service/**", "**/controller/**");
        assertThat(rules.testGate().skipPackages()).containsExactly("**/dto/**");
    }

    @Test
    void emptyYamlProducesEmptyRuleSetRatherThanThrowing() {
        RuleSet rules = parse("");

        assertThat(rules.layers()).isEmpty();
        assertThat(rules.naming()).isEmpty();
        assertThat(rules.testGate().minCoveragePercent()).isZero();
    }

    @Test
    void layerForPackageMatchesGlobPattern() {
        RuleSet rules = parse(YAML);

        assertThat(rules.layerForPackage("com.acme.billing.service").name()).isEqualTo("service");
        assertThat(rules.layerForPackage("com.acme.billing.controller").name()).isEqualTo("controller");
        assertThat(rules.layerForPackage("com.acme.billing.domain")).isNull();
    }

    private RuleSet parse(String yaml) {
        return loader.parse(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
    }
}
