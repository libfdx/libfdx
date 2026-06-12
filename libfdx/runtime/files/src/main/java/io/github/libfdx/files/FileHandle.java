package io.github.libfdx.files;

import io.github.libfdx.core.FdxFuture;

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
     * Returns the read bytes.
     *
     * @return the read bytes
     */
    FdxFuture<byte[]> readBytes();

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
