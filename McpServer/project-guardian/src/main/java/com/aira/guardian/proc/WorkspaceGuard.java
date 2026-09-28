package com.aira.guardian.proc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Confines every filesystem-touching tool to one root: the target Spring
 * Boot project this server was pointed at. Mirrors the aira-ops reference
 * server's discipline of never letting a secret/resource leak outside its
 * intended scope - here the "secret" is write access to the rest of the
 * filesystem.
 */
public class WorkspaceGuard {

    private final Path root;

    public WorkspaceGuard(Path root) {
        try {
            this.root = root.toRealPath();
        } catch (IOException e) {
            throw new IllegalArgumentException("workspace root does not exist or is not accessible: " + root, e);
        }
    }

    public Path root() {
        return root;
    }

    /**
     * Resolves a caller-supplied relative path against the workspace root
     * and verifies the result does not escape it (via "..", a symlink, or
     * an absolute path pointing elsewhere). Throws IllegalArgumentException
     * on escape rather than returning null, so callers can't accidentally
     * proceed with an unchecked path.
     */
    public Path resolve(String relativePath) {
        Path candidate = root.resolve(relativePath).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException(
                    "path escapes workspace root " + root + ": " + relativePath);
        }
        return candidate;
    }

    /** Same as {@link #resolve} but also verifies the resolved path exists. */
    public Path resolveExisting(String relativePath) {
        Path candidate = resolve(relativePath);
        if (!Files.exists(candidate)) {
            throw new IllegalArgumentException("path does not exist: " + relativePath);
        }
        return candidate;
    }

    public boolean isWithinWorkspace(Path path) {
        try {
            return path.toRealPath().startsWith(root);
        } catch (IOException e) {
            return path.normalize().startsWith(root);
        }
    }
}
