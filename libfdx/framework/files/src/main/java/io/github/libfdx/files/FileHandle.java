package io.github.libfdx.files;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxException;

import java.nio.charset.Charset;

/**
 * Defines a typed handle for file state.
 *
 * @author xpenatan
 */
public interface FileHandle {
    /**
     * Returns the location.
     *
     * @return the location
     */
    FileLocation location();

    /**
     * Returns the path.
     *
     * @return the path
     */
    String path();

    /**
     * Returns the name.
     *
     * @return the name
     */
    String name();

    /**
     * Returns the extension.
     *
     * @return the extension
     */
    String extension();

    /**
     * Returns the parent.
     *
     * @return this file handle for chaining
     */
    FileHandle parent();

    /**
     * Sets the child and returns this file handle.
     *
     * @param relativePath the relative path
     * @return this file handle for chaining
     */
    FileHandle child(String relativePath);

    /**
     * Returns the exists.
     *
     * @return true if exists succeeds or is active; false otherwise
     */
    boolean exists();

    /**
     * Returns whether directory is enabled or true.
     *
     * @return true if directory is enabled or true; false otherwise
     */
    boolean isDirectory();

    /**
     * Returns the metadata.
     *
     * @return the metadata
     */
    FdxFuture<FileMetadata> metadata();

    /**
     * Reads an owned whole-file byte array. Completion may be inline or asynchronous;
     * compose the future instead of assuming it is ready. Use bounded input for
     * large files. This method does not itself promise a worker thread.
     *
     * @return the read bytes
     */
    FdxFuture<byte[]> readBytes();

    /** Opens owned bounded input with a 64 KiB request limit. Support is provider-specific. */
    default FdxFuture<FileDataSource> openRead() { return openRead(64 * 1024); }

    /**
     * Opens owned bounded input. The caller must close a successful result, including
     * when a late open is no longer needed. Unsupported providers fail explicitly;
     * no whole-file buffering fallback is supplied. See {@link FileDataSource}.
     */
    default FdxFuture<FileDataSource> openRead(int maxReadBytes) {
        FileDataSource.validateLimit(maxReadBytes);
        return FdxFuture.failed(new FdxException("Bounded input is unsupported for " + path()));
    }

    /**
     * Runs the read string step.
     *
     * @param charset the charset
     * @return the read string
     */
    FdxFuture<String> readString(Charset charset);

    /**
     * Runs the write bytes step.
     *
     * @param bytes the bytes
     * @param append the append
     * @return the write bytes
     */
    FdxFuture<Void> writeBytes(byte[] bytes, boolean append);

    /**
     * Runs the write string step.
     *
     * @param text the text
     * @param charset the charset
     * @param append the append
     * @return the write string
     */
    FdxFuture<Void> writeString(String text, Charset charset, boolean append);
}
