package io.github.libfdx.testsupport.graphics;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileLocation;
import io.github.libfdx.files.FileMetadata;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.files.FileWatch;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.TextureRegion;

import java.nio.charset.Charset;

/** Resource and delayed-I/O fixtures for the cargo loading scenario. */
public final class AssetLoadingFixtures {
    private AssetLoadingFixtures() {}
    public static final class Card implements Disposable {
        public final Texture tile;
        public final TextureRegion logo;
        public Card(Texture tile, TextureRegion logo) { this.tile = tile; this.logo = logo; }
        @Override
        public void dispose() { tile.dispose(); }
        @Override
        public boolean isDisposed() { return tile.isDisposed(); }
    }

    /** The worker receives a pending future immediately; the scenario controls when I/O is published. */
    public static final class DelayedImage implements FileHandle {
        public final FileHandle delegate;
        public final FdxFuture<byte[]> result = FdxFuture.pending();
        byte[] received;
        Throwable failure;
        public volatile int reads;
        public DelayedImage(FileHandle delegate) { this.delegate = delegate; }
        @Override
        public FdxFuture<byte[]> readBytes() {
            reads++;
            delegate.readBytes().onSuccess(this::received).onFailure(this::failed);
            return result;
        }
        private synchronized void received(byte[] bytes) { received = bytes; }
        private synchronized void failed(Throwable error) { failure = error; }
        public void pump(boolean release) {
            if (!release || result.isDone()) { return; }
            byte[] bytes;
            Throwable error;
            synchronized (this) { bytes = received; error = failure; }
            if (error != null) { result.completeExceptionally(error); }
            else if (bytes != null) { result.complete(bytes); }
        }
        @Override
        public FileLocation location() { return delegate.location(); }
        @Override
        public String path() { return delegate.path(); }
        @Override
        public String name() { return delegate.name(); }
        @Override
        public String extension() { return delegate.extension(); }
        @Override
        public FileHandle parent() { return delegate.parent(); }
        @Override
        public FileHandle child(String path) { return delegate.child(path); }
        @Override
        public boolean exists() { return delegate.exists(); }
        @Override
        public boolean isDirectory() { return delegate.isDirectory(); }
        @Override
        public FdxFuture<FileMetadata> metadata() { return delegate.metadata(); }
        @Override
        public FdxFuture<String> readString(Charset charset) { return delegate.readString(charset); }
        @Override
        public FdxFuture<Void> writeBytes(byte[] bytes, boolean append) { return delegate.writeBytes(bytes, append); }
        @Override
        public FdxFuture<Void> writeString(String text, Charset charset, boolean append) {
            return delegate.writeString(text, charset, append);
        }
    }

    public static final class DelayedFiles implements FileSystem {
        public final FileSystem delegate;
        public final FileHandle delayed;
        public DelayedFiles(FileSystem delegate, FileHandle delayed) { this.delegate = delegate; this.delayed = delayed; }
        @Override
        public FileHandle internal(String path) { return delayed.path().equals(path) ? delayed : delegate.internal(path); }
        @Override
        public FileHandle classpath(String path) { return delegate.classpath(path); }
        @Override
        public FileHandle local(String path) { return delegate.local(path); }
        @Override
        public FileHandle external(String path) { return delegate.external(path); }
        @Override
        public FileHandle cache(String path) { return delegate.cache(path); }
        @Override
        public FileHandle temp(String prefix, String suffix) { return delegate.temp(prefix, suffix); }
        @Override
        public FdxFuture<FileWatch> watch(FileHandle file) { return delegate.watch(file); }
        @Override
        public ProviderId providerId() { return delegate.providerId(); }
        @Override
        public <T> T as() { return delegate.as(); }
    }
}
