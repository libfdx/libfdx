package io.github.libfdx.tools.project.generator.ui;

import io.github.libfdx.tools.project.generator.GeneratedProject;

public final class ProjectExportRequest {
    private final GeneratedProject project;
    private final String destination;
    private final boolean overwriteExisting;

    public ProjectExportRequest(GeneratedProject project, String destination, boolean overwriteExisting) {
        this.project = project;
        this.destination = destination != null ? destination.trim() : "";
        this.overwriteExisting = overwriteExisting;
    }

    public GeneratedProject project() {
        return project;
    }

    public String destination() {
        return destination;
    }

    public boolean overwriteExisting() {
        return overwriteExisting;
    }
}
