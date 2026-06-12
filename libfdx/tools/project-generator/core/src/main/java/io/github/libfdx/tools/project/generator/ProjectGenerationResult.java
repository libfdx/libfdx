package io.github.libfdx.tools.project.generator;

/**
 * Represents the result of a project generation operation.
 *
 * @author xpenatan
 */
public final class ProjectGenerationResult {
    private final ProjectGenerationSettings settings;
    private final GeneratedProject project;

    /**
     * Creates a project generation result.
     *
     * @param settings the settings
     * @param project the project
     */
    public ProjectGenerationResult(ProjectGenerationSettings settings, GeneratedProject project) {
        this.settings = settings;
        this.project = project;
    }

    /**
     * Sets the tings.
     *
     * @return the settings
     */
    public ProjectGenerationSettings settings() {
        return settings;
    }

    /**
     * Returns the project.
     *
     * @return the project
     */
    public GeneratedProject project() {
        return project;
    }
}
