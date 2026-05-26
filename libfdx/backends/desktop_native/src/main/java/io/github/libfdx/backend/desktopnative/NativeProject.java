package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.backend.teavm.shared.BuilderException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class NativeProject {
    private final Path buildRoot;
    private final Path generatedSourcesDirectory;
    private final Path releaseDirectory;
    private final String projectName;
    private final String buildType;
    private final boolean showConsole;
    private final List<Path> nativeResourceClasspath;

    private NativeProject(Builder builder) {
        this.buildRoot = requirePath(builder.buildRoot, "buildRoot");
        this.generatedSourcesDirectory = requirePath(builder.generatedSourcesDirectory, "generatedSourcesDirectory");
        this.releaseDirectory = requirePath(builder.releaseDirectory, "releaseDirectory");
        this.projectName = requireText(builder.projectName, "projectName");
        this.buildType = requireText(builder.buildType, "buildType");
        this.showConsole = builder.showConsole;
        this.nativeResourceClasspath = builder.nativeResourceClasspath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    public static Builder builder() {
        return new Builder();
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

    public String getProjectName() {
        return projectName;
    }

    public String getBuildType() {
        return buildType;
    }

    public boolean isShowConsole() {
        return showConsole;
    }

    public List<Path> getNativeResourceClasspath() {
        return nativeResourceClasspath;
    }

    private static Path requirePath(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BuilderException(name + " must be set");
        }
        return value;
    }

    public static final class Builder {
        private Path buildRoot;
        private Path generatedSourcesDirectory;
        private Path releaseDirectory;
        private String projectName = "app";
        private String buildType = "Debug";
        private boolean showConsole = true;
        private final ArrayList<Path> nativeResourceClasspath = new ArrayList<>();

        private Builder() {
        }

        public Builder buildRoot(Path buildRoot) {
            this.buildRoot = buildRoot;
            return this;
        }

        public Builder generatedSourcesDirectory(Path generatedSourcesDirectory) {
            this.generatedSourcesDirectory = generatedSourcesDirectory;
            return this;
        }

        public Builder releaseDirectory(Path releaseDirectory) {
            this.releaseDirectory = releaseDirectory;
            return this;
        }

        public Builder projectName(String projectName) {
            this.projectName = Objects.requireNonNull(projectName, "projectName");
            return this;
        }

        public Builder buildType(String buildType) {
            this.buildType = Objects.requireNonNull(buildType, "buildType");
            return this;
        }

        public Builder showConsole(boolean showConsole) {
            this.showConsole = showConsole;
            return this;
        }

        public Builder nativeResourceClasspath(Path entry) {
            this.nativeResourceClasspath.add(Objects.requireNonNull(entry, "entry"));
            return this;
        }

        public Builder nativeResourceClasspath(Collection<Path> entries) {
            this.nativeResourceClasspath.addAll(Objects.requireNonNull(entries, "entries"));
            return this;
        }

        public NativeProject build() {
            return new NativeProject(this);
        }
    }
}
