package io.github.libfdx.tools.project.generator.ui;

import io.github.libfdx.tools.project.generator.GeneratedProject;

/**
 * Represents a project export request.
 *
 * @author xpenatan
 */
public final class ProjectExportRequest {
    private final GeneratedProject project;
    private final String destination;
    private final boolean overwriteExisting;

    /**
     * Creates a project export request.
     *
     * @param project the project
     * @param destination the destination
     * @param overwriteExisting the overwrite existing
     */
    public ProjectExportRequest(GeneratedProject project, String destination, boolean overwriteExisting) {
        this.project = project;
        this.destination = destination != null ? destination.trim() : "";
        this.overwriteExisting = overwriteExisting;
    }

    /**
     * Returns the project.
     *
     * @return the project
     */
    public GeneratedProject project() {
        return project;
    }

    /**
     * Returns the destination.
     *
     * @return the destination
     */
    public String destination() {
        return destination;
    }

    /**
     * Returns the overwrite existing.
     *
     * @return true if overwrite existing succeeds or is active; false otherwise
     */
    public boolean overwriteExisting() {
        return overwriteExisting;
    }
}
