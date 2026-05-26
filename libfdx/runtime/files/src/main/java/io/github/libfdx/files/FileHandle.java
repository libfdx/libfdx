package io.github.libfdx.files;

import io.github.libfdx.core.FdxFuture;

import java.nio.charset.Charset;

public interface FileHandle {
    FileLocation location();

    String path();

    String name();

    String extension();

    FileHandle parent();

    FileHandle child(String relativePath);

    boolean exists();

    boolean isDirectory();

    FdxFuture<FileMetadata> metadata();

    FdxFuture<byte[]> readBytes();

    FdxFuture<String> readString(Charset charset);

    FdxFuture<Void> writeBytes(byte[] bytes, boolean append);

    FdxFuture<Void> writeString(String text, Charset charset, boolean append);
}
