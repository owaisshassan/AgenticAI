package Lab5;

import java.nio.file.Path;

/**
 * A newly created service the agent is asked to enforce test coverage on.
 * Plain input data - kept separate from the tools that act on it, same
 * pattern as project-guardian's ClassSpec.
 */
public record ServiceTarget(
        Path sourceFile,
        String packageName,
        String className
) {
    public String qualifiedName() {
        return packageName == null || packageName.isBlank() ? className : packageName + "." + className;
    }

    /** Qualified name of the JUnit test class this target's test lives in, e.g. "com.acme.FooServiceTest". */
    public String qualifiedTestClassName() {
        return qualifiedName() + "Test";
    }
}
