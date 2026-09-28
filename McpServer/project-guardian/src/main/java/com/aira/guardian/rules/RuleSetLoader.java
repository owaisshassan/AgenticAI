package com.aira.guardian.rules;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code guardian-rules.yaml} into a {@link RuleSet}. Kept separate
 * from {@link RuleSet} itself so parsing/defaulting logic doesn't leak into
 * the plain data record - makes both independently testable.
 */
public class RuleSetLoader {

    private final ResourceLoader resourceLoader;

    public RuleSetLoader() {
        this(new DefaultResourceLoader());
    }

    public RuleSetLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public RuleSet load(String location) throws IOException {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream in = resource.getInputStream()) {
            return parse(in);
        }
    }

    @SuppressWarnings("unchecked")
    public RuleSet parse(InputStream yamlInput) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(yamlInput);
        if (root == null) {
            root = new LinkedHashMap<>();
        }

        List<RuleSet.Layer> layers = parseLayers((List<Map<String, Object>>) root.getOrDefault("layers", List.of()));
        Map<String, String> naming = (Map<String, String>) root.getOrDefault("naming", Map.of());
        Map<String, List<String>> requiredAnnotations =
                (Map<String, List<String>>) root.getOrDefault("requiredAnnotations", Map.of());
        RuleSet.TestGate testGate = parseTestGate((Map<String, Object>) root.getOrDefault("testGate", Map.of()));

        return new RuleSet(layers, naming, requiredAnnotations, testGate);
    }

    @SuppressWarnings("unchecked")
    private List<RuleSet.Layer> parseLayers(List<Map<String, Object>> rawLayers) {
        List<RuleSet.Layer> layers = new ArrayList<>();
        for (Map<String, Object> raw : rawLayers) {
            String name = (String) raw.get("name");
            String pkg = (String) raw.get("package");
            List<String> mayCall = (List<String>) raw.getOrDefault("mayCallLayers", List.of());
            layers.add(new RuleSet.Layer(name, pkg, mayCall));
        }
        return layers;
    }

    @SuppressWarnings("unchecked")
    private RuleSet.TestGate parseTestGate(Map<String, Object> raw) {
        List<String> requireTestForPackages = (List<String>) raw.getOrDefault("requireTestForPackages", List.of());
        List<String> skipPackages = (List<String>) raw.getOrDefault("skipPackages", List.of());
        int minCoveragePercent = asInt(raw.get("minCoveragePercent"), 0);
        int minAssertionsPerTestClass = asInt(raw.get("minAssertionsPerTestClass"), 0);
        boolean forbidDisabledWithoutReason = Boolean.TRUE.equals(raw.get("forbidDisabledWithoutReason"));
        return new RuleSet.TestGate(
                requireTestForPackages, skipPackages, minCoveragePercent,
                minAssertionsPerTestClass, forbidDisabledWithoutReason);
    }

    private int asInt(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        return fallback;
    }
}
