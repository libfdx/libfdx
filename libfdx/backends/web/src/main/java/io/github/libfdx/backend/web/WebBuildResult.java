package io.github.libfdx.backend.web;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Represents the result of a web build operation.
 *
 * @author xpenatan
 */
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

    /**
     * Returns the target.
     *
     * @return the get target
     */
    public WebTarget getTarget() {
        return target;
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
     * Returns the target file.
     *
     * @return the get target file
     */
    public Path getTargetFile() {
        return targetFile;
    }

    /**
     * Returns the assets.
     *
     * @return the get assets
     */
    public List<WebAsset> getAssets() {
        return assets;
    }

    /**
     * Returns the generated files.
     *
     * @return the get generated files
     */
    public Set<Path> getGeneratedFiles() {
        return generatedFiles;
    }
}
