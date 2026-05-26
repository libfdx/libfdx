package io.github.libfdx.files;

public interface FileWatchListener {
    void changed(FileHandle file);

    void deleted(FileHandle file);
}
