package io.github.libfdx.tools.project.generator.ui;

/**
 * Represents the result of a project export operation.
 *
 * @author xpenatan
 */
public final class ProjectExportResult {
    private final boolean success;
    private final String message;

    private ProjectExportResult(boolean success, String message) {
        this.success = success;
        this.message = message != null ? message : "";
    }

    /**
     * Creates a project export result.
     *
     * @param message the message
     * @return a new project export result
     */
    public static ProjectExportResult success(String message) {
        return new ProjectExportResult(true, message);
    }

    /**
     * Creates a project export result.
     *
     * @param message the message
     * @return a new project export result
     */
    public static ProjectExportResult failure(String message) {
        return new ProjectExportResult(false, message);
    }

    /**
     * Returns the success.
     *
     * @return true if success succeeds or is active; false otherwise
     */
    public boolean success() {
        return success;
    }

    /**
     * Returns the message.
     *
     * @return the message
     */
    public String message() {
        return message;
    }
}
