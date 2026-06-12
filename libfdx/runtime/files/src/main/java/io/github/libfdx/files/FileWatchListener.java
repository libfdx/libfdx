package io.github.libfdx.files;

/**
 * Receives callbacks for file watch events.
 *
 * @author xpenatan
 */
public interface FileWatchListener {
    /**
     * Runs the changed step.
     *
     * @param file the file handle or path
     */
    void changed(FileHandle file);

    /**
     * Runs the deleted step.
     *
     * @param file the file handle or path
     */
    void deleted(FileHandle file);
}
