package io.github.libfdx.tools.project.generator;

/**
 * Signals project generation failures.
 *
 * @author xpenatan
 */
public final class ProjectGenerationException extends RuntimeException {
    /**
     * Creates a project generation exception.
     *
     * @param message the message
     */
    public ProjectGenerationException(String message) {
        super(message);
    }

    /**
     * Creates a project generation exception.
     *
     * @param message the message
     * @param cause the cause
     */
    public ProjectGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
