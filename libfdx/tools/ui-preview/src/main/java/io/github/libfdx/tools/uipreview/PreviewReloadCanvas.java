package io.github.libfdx.tools.uipreview;

import io.github.libfdx.Fdx;
import io.github.libfdx.input.InputRouter;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiRoot;
import java.io.IOException;
import java.util.List;

/** A candidate owns its classes/assets until its first successful rendered frame. */
final class PreviewReloadCanvas implements AutoCloseable {
    static final class WindowChange extends RuntimeException { }

    final PreviewCompositionLoader loader;
    final List<PreviewHierarchy.Item> items;
    final int active;
    final UiPreviewConfig config;
    final UiRoot root;
    final UiPreviewContext context;
    private UiContent content;
    private boolean transferred;

    PreviewReloadCanvas(PreviewReloadRequest request, PreviewHierarchy.Item selected, UiPreviewConfig host,
            Fdx fdx, PreviewDock dock) throws ReflectiveOperationException, IOException {
        loader = new PreviewCompositionLoader(request.classpath(), UiPreviewEntry.class.getClassLoader());
        UiRoot nextRoot = null;
        UiPreviewContext nextContext = null;
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            var type = loader.loadClass(selected.entry().className()).asSubclass(UiPreviewEntry.class);
            var constructor = type.getDeclaredConstructor();
            if (!constructor.trySetAccessible()) throw new IllegalAccessException("Preview constructor is not accessible");
            UiPreviewEntry entry = constructor.newInstance();
            UiPreviewConfig entryConfig = entry.previewConfig();
            if (!host.sharesWindowWith(entryConfig)) throw new WindowChange();
            items = PreviewHierarchy.entries(entry, entryConfig, request.entries());
            active = PreviewHierarchy.initial(items, selected.entry().className(), "", selected.id());
            PreviewHierarchy.Item item = items.get(active);
            config = item.entry().config();
            nextRoot = UiPreview.createRoot(config, fdx.files(), fdx.displays().main(), fdx.graphics().main(),
                    fdx.input(), new InputRouter());
            dock.layout(nextRoot, nextRoot.display().framebufferWidth(), nextRoot.display().framebufferHeight());
            nextContext = new UiPreviewContext(nextRoot, nextRoot.graphics());
            content = item.setup().create(nextContext);
            if (content == null) throw new IllegalArgumentException("Preview setup returned null content");
            root = nextRoot;
            context = nextContext;
        } catch (ReflectiveOperationException | RuntimeException | Error failure) {
            try { if (nextRoot != null) nextRoot.dispose(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { if (nextContext != null) nextContext.dispose(); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { loader.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    boolean render(PreviewDock dock, UiRoot previousRoot) {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(loader);
            if (!context.updateAssets()) return false;
            if (content != null) {
                root.setContent(content);
                content = null;
            }
            root.update(0);
            PreviewScrollState.capture(previousRoot).restore(root);
            dock.renderCanvas(root);
            return true;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    void transfer() {
        transferred = true;
    }

    @Override
    public void close() throws IOException {
        if (transferred) return;
        try { root.dispose(); }
        finally {
            try { context.dispose(); }
            finally { loader.close(); }
        }
    }

}
