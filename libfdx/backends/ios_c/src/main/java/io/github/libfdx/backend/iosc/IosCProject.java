package io.github.libfdx.backend.iosc;

import io.github.libfdx.backend.cshared.BuilderException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents an iOS C project.
 *
 * @author xpenatan
 */
public final class IosCProject {
    private final Path buildRoot;
    private final Path generatedSourcesDirectory;
    private final Path releaseDirectory;
    private final Path xcodeProjectDirectory;
    private final String projectName;
    private final String bundleIdentifier;
    private final IosCGraphicsApi graphicsApi;
    private final List<Path> nativeResourceClasspath;
    private final List<Path> assets;

    private IosCProject(Builder builder) {
        this.buildRoot = requirePath(builder.buildRoot, "buildRoot");
        this.generatedSourcesDirectory = requirePath(builder.generatedSourcesDirectory, "generatedSourcesDirectory");
        this.releaseDirectory = requirePath(builder.releaseDirectory, "releaseDirectory");
        this.xcodeProjectDirectory = requirePath(builder.xcodeProjectDirectory, "xcodeProjectDirectory");
        this.projectName = requireText(builder.projectName, "projectName");
        this.bundleIdentifier = requireText(builder.bundleIdentifier, "bundleIdentifier");
        this.graphicsApi = Objects.requireNonNull(builder.graphicsApi, "graphicsApi");
        this.nativeResourceClasspath = builder.nativeResourceClasspath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        this.assets = builder.assets.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    /**
     * Returns the builder.
     *
     * @return the created value
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the build root.
     *
     * @return the build root
     */
    public Path getBuildRoot() {
        return buildRoot;
    }

    /**
     * Returns the generated sources directory.
     *
     * @return the generated sources directory
     */
    public Path getGeneratedSourcesDirectory() {
        return generatedSourcesDirectory;
    }

    /**
     * Returns the release directory.
     *
     * @return the release directory
     */
    public Path getReleaseDirectory() {
        return releaseDirectory;
    }

    /**
     * Returns the Xcode project directory.
     *
     * @return the Xcode project directory
     */
    public Path getXcodeProjectDirectory() {
        return xcodeProjectDirectory;
    }

    /**
     * Returns the project name.
     *
     * @return the project name
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Returns the bundle identifier.
     *
     * @return the bundle identifier
     */
    public String getBundleIdentifier() {
        return bundleIdentifier;
    }

    /**
     * Returns the graphics API used by generated host files.
     *
     * @return the graphics API
     */
    public IosCGraphicsApi getGraphicsApi() {
        return graphicsApi;
    }

    /**
     * Returns the native resource classpath.
     *
     * @return the native resource classpath
     */
    public List<Path> getNativeResourceClasspath() {
        return nativeResourceClasspath;
    }

    /**
     * Returns the assets.
     *
     * @return the assets
     */
    public List<Path> getAssets() {
        return assets;
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

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private Path buildRoot;
        private Path generatedSourcesDirectory;
        private Path releaseDirectory;
        private Path xcodeProjectDirectory;
        private String projectName = "app";
        private String bundleIdentifier = "io.github.libfdx.iosc.app";
        private IosCGraphicsApi graphicsApi = IosCGraphicsApi.GLES;
        private final ArrayList<Path> nativeResourceClasspath = new ArrayList<>();
        private final ArrayList<Path> assets = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the build root and returns this builder.
         *
         * @param buildRoot the build root
         * @return this builder for chaining
         */
        public Builder buildRoot(Path buildRoot) {
            this.buildRoot = buildRoot;
            return this;
        }

        /**
         * Sets the generated sources directory and returns this builder.
         *
         * @param generatedSourcesDirectory the generated sources directory
         * @return this builder for chaining
         */
        public Builder generatedSourcesDirectory(Path generatedSourcesDirectory) {
            this.generatedSourcesDirectory = generatedSourcesDirectory;
            return this;
        }

        /**
         * Sets the release directory and returns this builder.
         *
         * @param releaseDirectory the release directory
         * @return this builder for chaining
         */
        public Builder releaseDirectory(Path releaseDirectory) {
            this.releaseDirectory = releaseDirectory;
            return this;
        }

        /**
         * Sets the Xcode project directory and returns this builder.
         *
         * @param xcodeProjectDirectory the Xcode project directory
         * @return this builder for chaining
         */
        public Builder xcodeProjectDirectory(Path xcodeProjectDirectory) {
            this.xcodeProjectDirectory = xcodeProjectDirectory;
            return this;
        }

        /**
         * Sets the project name and returns this builder.
         *
         * @param projectName the project name
         * @return this builder for chaining
         */
        public Builder projectName(String projectName) {
            this.projectName = Objects.requireNonNull(projectName, "projectName");
            return this;
        }

        /**
         * Sets the bundle identifier and returns this builder.
         *
         * @param bundleIdentifier the bundle identifier
         * @return this builder for chaining
         */
        public Builder bundleIdentifier(String bundleIdentifier) {
            this.bundleIdentifier = Objects.requireNonNull(bundleIdentifier, "bundleIdentifier");
            return this;
        }

        /**
         * Sets the graphics API and returns this builder.
         *
         * @param graphicsApi the graphics API
         * @return this builder for chaining
         */
        public Builder graphicsApi(IosCGraphicsApi graphicsApi) {
            this.graphicsApi = Objects.requireNonNull(graphicsApi, "graphicsApi");
            return this;
        }

        /**
         * Sets the native resource classpath and returns this builder.
         *
         * @param entries the entries
         * @return this builder for chaining
         */
        public Builder nativeResourceClasspath(Collection<Path> entries) {
            this.nativeResourceClasspath.addAll(Objects.requireNonNull(entries, "entries"));
            return this;
        }

        /**
         * Sets the assets and returns this builder.
         *
         * @param assets the assets
         * @return this builder for chaining
         */
        public Builder assets(Collection<Path> assets) {
            this.assets.addAll(Objects.requireNonNull(assets, "assets"));
            return this;
        }

        /**
         * Returns the build.
         *
         * @return the created value
         */
        public IosCProject build() {
            return new IosCProject(this);
        }
    }
}
