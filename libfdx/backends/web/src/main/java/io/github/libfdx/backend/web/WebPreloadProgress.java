package io.github.libfdx.backend.web;

/**
 * Stores web startup preload progress, including assets and the native runtime.
 *
 * @author xpenatan
 */
public final class WebPreloadProgress {
    private int loadedFiles;
    private int totalFiles;
    private double loadedBytes;
    private double totalBytes;
    private boolean complete;
    private boolean failed;
    private String errorMessage = "";

    WebPreloadProgress() {
    }

    /**
     * Returns the number of loaded files.
     *
     * @return the loaded file count
     */
    public int loadedFiles() {
        return loadedFiles;
    }

    /**
     * Returns the total number of files.
     *
     * @return the total file count
     */
    public int totalFiles() {
        return totalFiles;
    }

    /**
     * Returns the number of loaded bytes.
     *
     * @return the loaded byte count
     */
    public double loadedBytes() {
        return loadedBytes;
    }

    /**
     * Returns the total number of bytes.
     *
     * @return the total byte count
     */
    public double totalBytes() {
        return totalBytes;
    }

    /**
     * Returns the normalized progress value from zero to one.
     *
     * @return the normalized progress
     */
    public float progress() {
        if (complete && !failed) {
            return 1.0f;
        }
        if (totalBytes > 0.0) {
            return clamp((float) (loadedBytes / totalBytes));
        }
        if (totalFiles > 0) {
            return clamp((float) loadedFiles / (float) totalFiles);
        }
        return 0.0f;
    }

    /**
     * Returns whether preloading has completed.
     *
     * @return true if complete
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Returns whether preloading failed.
     *
     * @return true if failed
     */
    public boolean isFailed() {
        return failed;
    }

    /**
     * Returns the preload error message, if any.
     *
     * @return the error message
     */
    public String errorMessage() {
        return errorMessage;
    }

    void refresh() {
        loadedFiles = WebAssetPreloader.loadedFiles();
        totalFiles = WebAssetPreloader.totalFiles();
        loadedBytes = WebAssetPreloader.loadedBytes();
        totalBytes = WebAssetPreloader.totalBytes();
        complete = WebAssetPreloader.isComplete();
        failed = WebAssetPreloader.isFailed();
        errorMessage = WebAssetPreloader.errorMessage();
    }

    private static float clamp(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }
}
