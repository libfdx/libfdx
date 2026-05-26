package io.github.libfdx.backend.desktopnative;

import java.nio.file.Path;
import java.util.Set;

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

    public Path getBuildRoot() {
        return buildRoot;
    }

    public Path getGeneratedSourcesDirectory() {
        return generatedSourcesDirectory;
    }

    public Path getReleaseDirectory() {
        return releaseDirectory;
    }

    public Set<Path> getGeneratedFiles() {
        return generatedFiles;
    }

    public Set<Path> getProjectFiles() {
        return projectFiles;
    }
}
