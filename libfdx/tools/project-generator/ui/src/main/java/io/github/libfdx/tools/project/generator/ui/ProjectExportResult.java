package io.github.libfdx.tools.project.generator.ui;

public final class ProjectExportResult {
    private final boolean success;
    private final String message;

    private ProjectExportResult(boolean success, String message) {
        this.success = success;
        this.message = message != null ? message : "";
    }

    public static ProjectExportResult success(String message) {
        return new ProjectExportResult(true, message);
    }

    public static ProjectExportResult failure(String message) {
        return new ProjectExportResult(false, message);
    }

    public boolean success() {
        return success;
    }

    public String message() {
        return message;
    }
}
