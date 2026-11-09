package io.github.libfdx.tools.uipreview;

import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.ui.UiRoot;
import java.util.ArrayList;

/** Session-scoped services. Borrow these on the application thread; reset/close invalidates the session. */
public final class UiPreviewContext {
    private final UiRoot root;
    private final GraphicsContext graphics;
    private final DefaultAssetManager assets;
    private final ArrayList<Disposable> owned = new ArrayList<>();
    private boolean disposed;

    UiPreviewContext(UiRoot root, GraphicsContext graphics) {
        this.root = root;
        this.graphics = graphics;
        assets = new DefaultAssetManager(root.files());
        G2DAssetLoaders.register(assets, graphics);
    }

    /** Returns the borrowed project file service. */
    public FileSystem files() { check(); return root.files(); }

    /** Returns the borrowed host root for theme and layout setup. The runner owns its lifecycle and content. */
    public UiRoot root() { check(); return root; }

    /** Returns the borrowed active graphics context; do not retain frame-owned handles. */
    public GraphicsContext graphics() { check(); return graphics; }

    /** Returns the session-owned asset manager, driven by the runner before composition/rendering. */
    public AssetManager assets() { check(); return assets; }

    /** Transfers disposal ownership of a resource to this session and returns that resource. */
    public <T extends Disposable> T own(T resource) {
        check();
        if (resource == null) throw new IllegalArgumentException("Resource required");
        for (Disposable existing : owned) if (existing == resource) return resource;
        owned.add(resource);
        return resource;
    }

    boolean updateAssets() { return assets.update(8, 2_000_000); }

    void dispose() {
        if (disposed) return;
        disposed = true;
        RuntimeException failure = null;
        for (int i = owned.size() - 1; i >= 0; i--) {
            try { owned.get(i).dispose(); }
            catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        }
        owned.clear();
        try { assets.dispose(); }
        catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        if (failure != null) throw failure;
    }

    private void check() { if (disposed) throw new IllegalStateException("Preview session has been disposed"); }

}
