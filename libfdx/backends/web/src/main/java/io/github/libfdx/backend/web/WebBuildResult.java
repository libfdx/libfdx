package io.github.libfdx.backend.web;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class WebBuildResult {
    private final WebTarget target;
    private final Path webappDirectory;
    private final Path targetFile;
    private final List<WebAsset> assets;
    private final Set<Path> generatedFiles;

    WebBuildResult(WebTarget target, Path webappDirectory, Path targetFile, List<WebAsset> assets,
            Set<Path> generatedFiles) {
        this.target = target;
        this.webappDirectory = webappDirectory;
        this.targetFile = targetFile;
        this.assets = List.copyOf(assets);
        this.generatedFiles = Set.copyOf(generatedFiles);
    }

    public WebTarget getTarget() {
        return target;
    }

    public Path getWebappDirectory() {
        return webappDirectory;
    }

    public Path getTargetFile() {
        return targetFile;
    }

    public List<WebAsset> getAssets() {
        return assets;
    }

    public Set<Path> getGeneratedFiles() {
        return generatedFiles;
    }
}
