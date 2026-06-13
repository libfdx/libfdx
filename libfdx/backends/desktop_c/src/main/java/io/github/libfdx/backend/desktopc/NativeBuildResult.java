package io.github.libfdx.backend.desktopc;

import java.nio.file.Path;
import java.util.Set;

/**
 * Represents the result of a native build operation.
 *
 * @author xpenatan
 */
public final class NativeBuildResult {
    private final Path buildRoot;
    private final Path generatedSourcesDirectory;
    private final Path releaseDirectory;
    private final Set<Path> generatedFiles;
    private final Set<Path> projectFiles;

    NativeBuildResult(Path buildRoot, Path generatedSourcesDirectory, Path releaseDirectory, Set<Path> generatedFiles,
            Set<Path> projectFiles) {
        this.buildRoot = buildRoot;
        this.generatedSourcesDirectory = generatedSourcesDirectory;
        this.releaseDirectory = releaseDirectory;
        this.generatedFiles = Set.copyOf(generatedFiles);
        this.projectFiles = Set.copyOf(projectFiles);
    }

    /**
     * Returns the build root.
     *
     * @return the get build root
     */
    public Path getBuildRoot() {
        return buildRoot;
    }

    /**
     * Returns the generated sources directory.
     *
     * @return the get generated sources directory
     */
    public Path getGeneratedSourcesDirectory() {
        return generatedSourcesDirectory;
    }

    /**
     * Returns the release directory.
     *
     * @return the get release directory
     */
    public Path getReleaseDirectory() {
        return releaseDirectory;
    }

    /**
     * Returns the generated files.
     *
     * @return the get generated files
     */
    public Set<Path> getGeneratedFiles() {
        return generatedFiles;
    }

    /**
     * Returns the project files.
     *
     * @return the get project files
     */
    public Set<Path> getProjectFiles() {
        return projectFiles;
    }
}
