package com.aira.guardian.rules;

import java.util.List;

/**
 * A proposed class, as given to {@code validate_class_spec} / {@code scaffold_class}.
 * Plain input data - validation logic lives in {@link ClassSpecValidator}.
 *
 * Deviation from the literal task spec: adds {@code annotations} beyond the
 * documented {className, packageName, layer, fields[]} shape. Without it,
 * "missing required annotation" (one of the three violation kinds the tool
 * must distinguish) has nothing to check against - a proposed class has no
 * annotations of its own until the caller states which ones it plans to
 * carry. Recorded in README.md's deviations section.
 */
public record ClassSpec(
        String className,
        String packageName,
        String layer,
        List<Field> fields,
        List<String> annotations
) {

    public ClassSpec(String className, String packageName, String layer, List<Field> fields) {
        this(className, packageName, layer, fields, List.of());
    }

    public record Field(String name, String type) {
    }
}
