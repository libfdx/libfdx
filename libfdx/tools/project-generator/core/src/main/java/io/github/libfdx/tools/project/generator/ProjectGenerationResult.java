package io.github.libfdx.tools.project.generator;

public final class ProjectGenerationResult {
    private final ProjectGenerationSettings settings;
    private final GeneratedProject project;

    public ProjectGenerationResult(ProjectGenerationSettings settings, GeneratedProject project) {
        this.settings = settings;
        this.project = project;
    }

    public ProjectGenerationSettings settings() {
        return settings;
    }

    public GeneratedProject project() {
        return project;
    }
}
