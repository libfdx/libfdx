package io.github.libfdx.backend.psp;

import io.github.libfdx.backend.cshared.BuilderException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents a psp project.
 *
 * @author xpenatan
 */
public final class PspProject {
    private final Path buildRoot;
    private final Path generatedSourcesDirectory;
    private final Path releaseDirectory;
    private final String projectName;
    private final boolean debugMemory;
    private final List<Path> nativeResourceClasspath;
    private final List<Path> assets;

    private PspProject(Builder builder) {
        this.buildRoot = requirePath(builder.buildRoot, "buildRoot");
        this.generatedSourcesDirectory = requirePath(builder.generatedSourcesDirectory, "generatedSourcesDirectory");
        this.releaseDirectory = requirePath(builder.releaseDirectory, "releaseDirectory");
        this.projectName = requireText(builder.projectName, "projectName");
        this.debugMemory = builder.debugMemory;
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
     * Returns the project name.
     *
     * @return the get project name
     */
    public String getProjectName() {
        return projectName;
    }

    /**
     * Returns whether debug memory is enabled or true.
     *
     * @return true if debug memory is enabled or true; false otherwise
     */
    public boolean isDebugMemory() {
        return debugMemory;
    }

    /**
     * Returns the native resource classpath.
     *
     * @return the get native resource classpath
     */
    public List<Path> getNativeResourceClasspath() {
        return nativeResourceClasspath;
    }

    /**
     * Returns the assets.
     *
     * @return the get assets
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
        private String projectName = "app";
        private boolean debugMemory;
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
         * Sets the debug memory and returns this builder.
         *
         * @param debugMemory the debug memory
         * @return this builder for chaining
         */
        public Builder debugMemory(boolean debugMemory) {
            this.debugMemory = debugMemory;
            return this;
        }

        /**
         * Sets the native resource classpath and returns this builder.
         *
         * @param entry the entry
         * @return this builder for chaining
         */
        public Builder nativeResourceClasspath(Path entry) {
            this.nativeResourceClasspath.add(Objects.requireNonNull(entry, "entry"));
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
         * Sets the asset and returns this builder.
         *
         * @param asset the asset
         * @return this builder for chaining
         */
        public Builder asset(Path asset) {
            this.assets.add(Objects.requireNonNull(asset, "asset"));
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
        public PspProject build() {
            return new PspProject(this);
        }
    }
}
