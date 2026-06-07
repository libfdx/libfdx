package io.github.libfdx.tools.project.generator;

public final class ProjectGenerationException extends RuntimeException {
    public ProjectGenerationException(String message) {
        super(message);
    }

    public ProjectGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
