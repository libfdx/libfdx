package io.github.libfdx.backend.web;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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

    public static Builder builder() {
        return new Builder();
    }

    public Path getWebappDirectory() {
        return webappDirectory;
    }

    public String getTitle() {
        return title;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getCanvasId() {
        return canvasId;
    }

    public String getEntryPointName() {
        return entryPointName;
    }

    public String getMainClassArgs() {
        return mainClassArgs;
    }

    public String getTargetFileName() {
        return targetFileName;
    }

    public boolean isWasm() {
        return wasm;
    }

    public List<Path> getAssets() {
        return assets;
    }

    public List<Path> getRuntimeClasspath() {
        return runtimeClasspath;
    }

    private static Path requirePath(Path path, String name) {
        return Objects.requireNonNull(path, name).toAbsolutePath().normalize();
    }

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

        public Builder webappDirectory(Path webappDirectory) {
            this.webappDirectory = webappDirectory;
            return this;
        }

        public Builder title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder canvasId(String canvasId) {
            this.canvasId = Objects.requireNonNull(canvasId, "canvasId");
            return this;
        }

        public Builder entryPointName(String entryPointName) {
            this.entryPointName = Objects.requireNonNull(entryPointName, "entryPointName");
            return this;
        }

        public Builder mainClassArgs(String mainClassArgs) {
            this.mainClassArgs = Objects.requireNonNull(mainClassArgs, "mainClassArgs");
            return this;
        }

        public Builder targetFileName(String targetFileName) {
            this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
            return this;
        }

        public Builder wasm(boolean wasm) {
            this.wasm = wasm;
            return this;
        }

        public Builder asset(Path asset) {
            this.assets.add(Objects.requireNonNull(asset, "asset"));
            return this;
        }

        public Builder assets(Collection<Path> assets) {
            this.assets.addAll(Objects.requireNonNull(assets, "assets"));
            return this;
        }

        public Builder runtimeClasspath(Path entry) {
            this.runtimeClasspath.add(Objects.requireNonNull(entry, "entry"));
            return this;
        }

        public Builder runtimeClasspath(Collection<Path> entries) {
            this.runtimeClasspath.addAll(Objects.requireNonNull(entries, "entries"));
            return this;
        }

        public WebApp build() {
            return new WebApp(this);
        }
    }
}
