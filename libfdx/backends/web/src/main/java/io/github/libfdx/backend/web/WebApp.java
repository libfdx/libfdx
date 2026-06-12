package io.github.libfdx.backend.web;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Represents a web app.
 *
 * @author xpenatan
 */
public final class WebApp {
    private final Path webappDirectory;
    private final String title;
    private final int width;
    private final int height;
    private final String canvasId;
    private final String entryPointName;
    private final String mainClassArgs;
    private final String targetFileName;
    private final boolean wasm;
    private final List<Path> assets;
    private final List<Path> runtimeClasspath;

    private WebApp(Builder builder) {
        this.webappDirectory = requirePath(builder.webappDirectory, "webappDirectory");
        this.title = builder.title;
        this.width = builder.width;
        this.height = builder.height;
        this.canvasId = builder.canvasId;
        this.entryPointName = builder.entryPointName;
        this.mainClassArgs = builder.mainClassArgs;
        this.targetFileName = builder.targetFileName;
        this.wasm = builder.wasm;
        this.assets = List.copyOf(builder.assets);
        this.runtimeClasspath = List.copyOf(builder.runtimeClasspath);
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
     * Returns the webapp directory.
     *
     * @return the get webapp directory
     */
    public Path getWebappDirectory() {
        return webappDirectory;
    }

    /**
     * Returns the title.
     *
     * @return the get title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the width.
     *
     * @return the get width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height.
     *
     * @return the get height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the canvas ID.
     *
     * @return the get canvas ID
     */
    public String getCanvasId() {
        return canvasId;
    }

    /**
     * Returns the entry point name.
     *
     * @return the get entry point name
     */
    public String getEntryPointName() {
        return entryPointName;
    }

    /**
     * Returns the main class args.
     *
     * @return the get main class args
     */
    public String getMainClassArgs() {
        return mainClassArgs;
    }

    /**
     * Returns the target file name.
     *
     * @return the get target file name
     */
    public String getTargetFileName() {
        return targetFileName;
    }

    /**
     * Returns whether Wasm is enabled or true.
     *
     * @return true if Wasm is enabled or true; false otherwise
     */
    public boolean isWasm() {
        return wasm;
    }

    /**
     * Returns the assets.
     *
     * @return the get assets
     */
    public List<Path> getAssets() {
        return assets;
    }

    /**
     * Returns the runtime classpath.
     *
     * @return the get runtime classpath
     */
    public List<Path> getRuntimeClasspath() {
        return runtimeClasspath;
    }

    private static Path requirePath(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

    /**
     * Builds value instances and related output.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private Path webappDirectory;
        private String title = "libfdx";
        private int width = 640;
        private int height = 480;
        private String canvasId = "libfdx-canvas";
        private String entryPointName = "main";
        private String mainClassArgs = "";
        private String targetFileName = "app.js";
        private boolean wasm;
        private final ArrayList<Path> assets = new ArrayList<>();
        private final ArrayList<Path> runtimeClasspath = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the webapp directory and returns this builder.
         *
         * @param webappDirectory the webapp directory
         * @return this builder for chaining
         */
        public Builder webappDirectory(Path webappDirectory) {
            this.webappDirectory = webappDirectory;
            return this;
        }

        /**
         * Sets the title and returns this builder.
         *
         * @param title the title
         * @return this builder for chaining
         */
        public Builder title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        /**
         * Sets the width and returns this builder.
         *
         * @param width the width in pixels
         * @return this builder for chaining
         */
        public Builder width(int width) {
            this.width = width;
            return this;
        }

        /**
         * Sets the height and returns this builder.
         *
         * @param height the height in pixels
         * @return this builder for chaining
         */
        public Builder height(int height) {
            this.height = height;
            return this;
        }

        /**
         * Returns whether this instance can vas ID.
         *
         * @param canvasId the canvas ID
         * @return this builder for chaining
         */
        public Builder canvasId(String canvasId) {
            this.canvasId = Objects.requireNonNull(canvasId, "canvasId");
            return this;
        }

        /**
         * Sets the entry point name and returns this builder.
         *
         * @param entryPointName the entry point name
         * @return this builder for chaining
         */
        public Builder entryPointName(String entryPointName) {
            this.entryPointName = Objects.requireNonNull(entryPointName, "entryPointName");
            return this;
        }

        /**
         * Sets the main class args and returns this builder.
         *
         * @param mainClassArgs the main class args
         * @return this builder for chaining
         */
        public Builder mainClassArgs(String mainClassArgs) {
            this.mainClassArgs = Objects.requireNonNull(mainClassArgs, "mainClassArgs");
            return this;
        }

        /**
         * Sets the target file name and returns this builder.
         *
         * @param targetFileName the target file name
         * @return this builder for chaining
         */
        public Builder targetFileName(String targetFileName) {
            this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
            return this;
        }

        /**
         * Sets the Wasm and returns this builder.
         *
         * @param wasm the Wasm
         * @return this builder for chaining
         */
        public Builder wasm(boolean wasm) {
            this.wasm = wasm;
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
         * Sets the runtime classpath and returns this builder.
         *
         * @param entry the entry
         * @return this builder for chaining
         */
        public Builder runtimeClasspath(Path entry) {
            this.runtimeClasspath.add(Objects.requireNonNull(entry, "entry"));
            return this;
        }

        /**
         * Sets the runtime classpath and returns this builder.
         *
         * @param entries the entries
         * @return this builder for chaining
         */
        public Builder runtimeClasspath(Collection<Path> entries) {
            this.runtimeClasspath.addAll(Objects.requireNonNull(entries, "entries"));
            return this;
        }

        /**
         * Returns the build.
         *
         * @return the created value
         */
        public WebApp build() {
            return new WebApp(this);
        }
    }
}
