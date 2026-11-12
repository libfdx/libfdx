package io.github.libfdx.tools.uipreview;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.InputRouter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Direct-call desktop UI runner. Invoke from an ordinary Java main; no game listener is started. */
public final class UiPreview {
    private static final String CONTROLS = " | F5 reset, F6 pause, F7 step, F8 capture";
    private record EntryFiles(Path request, Path selection) { }

    private UiPreview() { }

    /** Opens a design preview with visual pointer feedback and blocks until it closes. F5 resets the session. */
    public static void open(int width, int height, UiPreviewFactory factory) {
        open(new UiPreviewConfig(width, height), factory);
    }

    /** Opens a configured window; content/state creation occurs on the application thread. */
    public static void open(UiPreviewConfig config, UiPreviewFactory factory) {
        if (factory == null) throw new IllegalArgumentException("Preview factory required");
        open(config, context -> factory.create());
    }

    /** Opens a session with explicit asset/resource ownership. Setup is called once per start/reset. */
    public static void open(UiPreviewConfig config, UiPreviewSetup setup) {
        if (config == null || setup == null) throw new IllegalArgumentException("Config and setup required");
        openWindow(config, List.of(new PreviewHierarchy.Item("", "", 0, setup)), 0, null);
    }

    /**
     * Opens named compositions with a hierarchy dock. F4 advances to the next target and Shift+F4 goes back.
     * The watcher retains the selected target ID on refresh. Set libfdx.uiPreview.target for the initial launch.
     */
    public static void openTargets(UiPreviewConfig config, UiPreviewTarget... targets) {
        if (config == null) throw new IllegalArgumentException("Preview config required");
        List<PreviewHierarchy.Item> items = PreviewHierarchy.targets(List.of(targets));
        int initial = PreviewHierarchy.initial(items, System.getProperty("libfdx.uiPreview.target", ""),
                System.getProperty(PreviewSessionFiles.RESTORED_TARGET, ""));
        openWindow(config, items, initial, null);
    }

    static void openEntry(UiPreviewConfig config, UiPreviewEntry entry) {
        String catalogFile = System.getProperty(PreviewEntries.CATALOG_PROPERTY);
        try {
            Path catalog = catalogFile == null ? null : Path.of(catalogFile);
            String entryClass = entry.getClass().getName();
            List<String> classes = catalog == null ? List.of(entryClass) : Files.readAllLines(catalog);
            if (!classes.contains(entryClass)) {
                throw new IllegalArgumentException("Active preview entry is missing from its catalog");
            }
            List<PreviewHierarchy.Item> items = PreviewHierarchy.entries(entry, config, classes);
            Path selectionFile = PreviewSessionFiles.entrySelectionFile();
            var saved = PreviewSessionFiles.readSelection(selectionFile, entryClass);
            String restored = saved.entryClass().equals(entryClass) ? saved.target() : "";
            int initial = PreviewHierarchy.initial(items, entryClass, entry.initialPreviewTarget(), restored);
            EntryFiles selection = catalog == null ? null : new EntryFiles(
                    catalog.resolveSibling(PreviewEntries.REQUEST_FILE), selectionFile);
            openWindow(config, items, initial, selection);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read preview entry catalog", error);
        }
    }

    private static void openWindow(UiPreviewConfig config, List<PreviewHierarchy.Item> items, int initial,
                                   EntryFiles entry) {
        GraphicsAttachmentProvider provider = config.graphics != null ? config.graphics : new DesktopOpenGLProvider();
        try (PreviewStopSignal signal = stopSignal()) {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig()
                    .title(title(config, items.get(initial)))
                    .size(config.width, config.height).vSync(true).foregroundFps(60)
                    .graphics(provider),
                    new Listener(config, items, initial, entry, signal));
        }
    }

    private static String title(UiPreviewConfig config, PreviewHierarchy.Item item) {
        return config.title + (item.label().isEmpty() ? "" : " | " + item.label()) + CONTROLS;
    }

    /**
     * Connects an existing application listener to the preview watcher. The application
     * retains its backend and UI lifecycle. Select a design root in the application for visual input feedback.
     *
     * @param listener the application listener to run
     * @param rendered reports when the application has drawn its first usable frame
     * @return a listener for the existing backend to run on the application thread
     */
    public static ApplicationListener attach(ApplicationListener listener, BooleanSupplier rendered) {
        if (listener == null || rendered == null) {
            throw new IllegalArgumentException("Application listener and frame readiness required");
        }
        return new ApplicationAdapter() {
            private Fdx runtime;
            private PreviewStopSignal signal;
            private Path capture;
            private long frameLimit;
            private long frames;
            private boolean renderedThisFrame;

            public void create(Fdx fdx) {
                runtime = fdx;
                signal = stopSignal();
                try {
                    addAssets(fdx, List.of());
                    capture = capturePath(null);
                    frameLimit = Long.getLong("libfdx.uiPreview.frames", 0);
                    listener.create(fdx);
                } catch (RuntimeException | Error failure) {
                    signal.close();
                    throw failure;
                }
            }

            public void resize(int width, int height) { listener.resize(width, height); }

            public void render() {
                renderedThisFrame = !signal.requested;
                if (!renderedThisFrame) {
                    runtime.app().requestExit();
                } else {
                    listener.render();
                }
            }

            public void onFrameEnd() {
                if (!renderedThisFrame) return;
                listener.onFrameEnd();
                if (signal.requested || !rendered.getAsBoolean()) return;
                if (++frames == 1) {
                    if (capture != null) captureFrame(runtime, capture, 0);
                    markReady();
                }
                if (frameLimit > 0 && frames >= frameLimit) runtime.app().requestExit();
            }

            public void pause() { listener.pause(); }

            public void resume() { listener.resume(); }

            public void dispose() {
                try {
                    listener.dispose();
                } finally {
                    signal.close();
                }
            }

        };
    }

    private static PreviewStopSignal stopSignal() {
        String stop = System.getProperty("libfdx.uiPreview.stopFile");
        return new PreviewStopSignal(stop == null ? null : Path.of(stop));
    }

    private static void addAssets(Fdx fdx, List<Path> roots) {
        if (fdx.files() instanceof DefaultFileSystem files) {
            int count = Integer.getInteger("libfdx.uiPreview.assets.count", 0);
            for (int i = count - 1; i >= 0; i--) {
                files.addInternalRootFirst(Path.of(System.getProperty("libfdx.uiPreview.assets." + i)).toFile());
            }
            for (int i = roots.size() - 1; i >= 0; i--) {
                files.addInternalRootFirst(roots.get(i).toFile());
            }
        }
    }

    private static Path capturePath(Path fallback) {
        String path = System.getProperty("libfdx.uiPreview.capture");
        return path == null ? fallback : Path.of(path.replace("{generation}",
                System.getProperty("libfdx.uiPreview.generation", "0")));
    }

    private static void captureFrame(Fdx fdx, Path output, int left) {
        GraphicsFrame frame = fdx.graphics().main().currentFrame();
        if (!frame.frameBuffer().supportsReadPixelsRgba8()) {
            throw new IllegalStateException("Preview capture requires framebuffer RGBA8 readback support");
        }
        try {
            PreviewPng.write(output, frame.width(), frame.height(), frame.frameBuffer().readPixelsRgba8(), left);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot capture preview to " + output, error);
        }
        System.out.println("[ui-preview] CAPTURE " + output.toAbsolutePath());
    }

    private static void markReady() {
        String marker = System.getProperty("libfdx.uiPreview.readyFile");
        try {
            if (marker != null) Files.writeString(Path.of(marker), "ready\n");
        } catch (IOException error) {
            throw new IllegalStateException("Cannot signal preview readiness", error);
        }
        System.out.println("[ui-preview] READY generation=" + System.getProperty("libfdx.uiPreview.generation", "0"));
    }

    static UiRoot createRoot(UiPreviewConfig config, FileSystem files, Display display,
                             GraphicsContext graphics, Input input, InputRouter router) {
        UiToolkit toolkit = new UiToolkit(files).theme(config.theme);
        UiRoot root = config.inputMode == UiPreviewConfig.InputMode.LIVE
                ? toolkit.root(display, graphics) : toolkit.previewRoot(display, graphics);
        if (config.inputMode != UiPreviewConfig.InputMode.NONE) root.input(input, router);
        return root.uiScale(config.uiScale);
    }

    private static final class Listener extends ApplicationAdapter {
        private final UiPreviewConfig config;
        private List<PreviewHierarchy.Item> items;
        private final EntryFiles entry;
        private final PreviewStopSignal signal;
        private final InputRouter router = new InputRouter();
        private final InputRouter canvasInput = new InputRouter();
        private Fdx fdx;
        private UiPreviewContext context;
        private UiRoot root;
        private PreviewDock dock;
        private UiContent pendingContent;
        private int active;
        private int requested;
        private boolean reset;
        private boolean paused;
        private boolean step;
        private boolean capture;
        private boolean ready;
        private boolean disposed;
        private long frames;
        private long entryRequests;
        private boolean replacementRequested;
        private Path captureFile;
        private Path targetFile;
        private long exitFrames;
        private Path reloadDirectory;
        private long nextReloadPoll;
        private long reloadRevision;
        private long reloadAcknowledgement;
        private PreviewReloadCanvas reloadCanvas;
        private PreviewCompositionLoader compositionLoader;
        private Path compositionDirectory;
        private PreviewScrollHandoff scrollHandoff;

        Listener(UiPreviewConfig config, List<PreviewHierarchy.Item> items, int initial, EntryFiles entry,
                 PreviewStopSignal signal) {
            this.config = config;
            this.items = items;
            this.entry = entry;
            this.active = initial;
            this.requested = initial;
            this.signal = signal;
        }

        public void create(Fdx fdx) {
            this.fdx = fdx;
            try {
                addAssets(fdx, config.assets);
                captureFile = capturePath(config.capture);
                capture = captureFile != null;
                exitFrames = Long.getLong("libfdx.uiPreview.frames", config.exitAfterFrames);
                String targetPath = System.getProperty(PreviewSessionFiles.TARGET_FILE);
                if (!items.get(active).id().isEmpty() && targetPath != null) targetFile = Path.of(targetPath);
                String scrollRestore = System.getProperty(PreviewScrollHandoff.RESTORE_PROPERTY);
                scrollHandoff = new PreviewScrollHandoff(targetFile == null ? null
                        : targetFile.resolveSibling(PreviewScrollHandoff.FILE),
                        scrollRestore == null ? null : Path.of(scrollRestore));
                router.add(new InputAdapter() {
                    @Override
                    public boolean keyDown(KeyEvent event) {
                        if (event.key() == Key.F4 && items.size() > 1) {
                            if (!event.repeat()) {
                                Input input = fdx.input();
                                boolean previous = input.isKeyPressed(Key.SHIFT_LEFT) || input.isKeyPressed(Key.SHIFT_RIGHT);
                                requested = dock.step(requested, previous);
                            }
                            return true;
                        }
                        if (event.repeat()) return false;
                        if (event.key() == Key.F5) reset = true;
                        else if (event.key() == Key.F6) paused = !paused;
                        else if (event.key() == Key.F7) { paused = true; step = true; }
                        else if (event.key() == Key.F8) capture = true;
                        else return false;
                        return true;
                    }

                });
                dock = new PreviewDock(fdx, canvasInput, items, active, index -> requested = index);
                router.add(dock == null ? canvasInput : dock);
                fdx.input().addProcessor(router);
                startSession(active);
                if (entry != null && Boolean.getBoolean(PreviewReloadRequest.ENABLED)) {
                    reloadDirectory = entry.selection().getParent();
                    Files.writeString(reloadDirectory.resolve(PreviewReloadRequest.AVAILABLE), "ready\n");
                }
            } catch (IOException failure) {
                dispose();
                throw new IllegalStateException("Cannot enable composition reload", failure);
            } catch (RuntimeException | Error failure) {
                try { dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private void startSession(int index) {
            PreviewHierarchy.Item item = items.get(index);
            UiPreviewConfig canvas = item.entry() == null ? config : item.entry().config();
            UiRoot nextRoot = createRoot(canvas, fdx.files(), fdx.displays().main(), fdx.graphics().main(),
                    fdx.input(), canvasInput);
            UiPreviewContext nextContext = null;
            UiContent content;
            try {
                if (dock != null) {
                    Display display = nextRoot.display();
                    dock.layout(nextRoot, display.framebufferWidth(), display.framebufferHeight());
                }
                nextContext = new UiPreviewContext(nextRoot, nextRoot.graphics());
                content = item.setup().create(nextContext);
                if (content == null) throw new IllegalArgumentException("Preview setup returned null content");
                closeSession();
            } catch (RuntimeException | Error failure) {
                try { disposeSession(nextRoot, nextContext); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                if (root == null || index == active || failure instanceof Error) throw failure;
                System.err.println("[ui-preview] Cannot open target " + items.get(index).id());
                failure.printStackTrace(System.err);
                requested = active;
                return;
            }
            root = nextRoot;
            context = nextContext;
            pendingContent = content;
            active = index;
            fdx.displays().main().title(title(canvas, item));
            frames = 0;
        }

        public void resize(int width, int height) {
            if (root != null) root.resize(width, height);
        }

        public void render() {
            if (signal.requested) { fdx.app().requestExit(); return; }
            if (reloadCanvas != null && (reset || requested != active)) {
                rejectReload("restart", "Target changed while preparing a composition");
            }
            if (requested != active && items.get(requested).setup() == null) {
                requestReplacement(items.get(requested).id());
                requested = active;
            }
            if (reset || requested != active) {
                scrollHandoff.discardRestore();
                if (requested != active && replacementRequested) requestReplacement("");
                reset = false;
                startSession(requested);
            }
            pollReload();
            if (dock != null) {
                GraphicsFrame frame = fdx.graphics().main().currentFrame();
                dock.layout(root, frame.width(), frame.height());
            }
            fdx.graphics().main().clear(0.045f, 0.052f, 0.066f, 1);
            boolean reloaded = renderReload();
            if (!reloaded && context.updateAssets()) {
                if (pendingContent != null) {
                    root.setContent(pendingContent);
                    pendingContent = null;
                }
                root.update(step ? 1.0f / 60 : paused ? 0 : fdx.app().deltaTime());
                step = false;
                PreviewHierarchy.Item selected = items.get(active);
                scrollHandoff.restore(root, selected.entry() == null ? "" : selected.entry().className(), selected.id());
                dock.renderCanvas(root);
                frames++;
                if (exitFrames > 0 && frames >= exitFrames) fdx.app().requestExit();
            }
            if (dock != null) dock.render(active, fdx.app().deltaTime());
            PreviewHierarchy.Item selected = items.get(active);
            if (frames > 0) scrollHandoff.publish(root, selected.entry() == null ? "" : selected.entry().className(),
                    selected.id(), System.nanoTime());
        }

        private void pollReload() {
            if (reloadDirectory == null || reloadCanvas != null || System.nanoTime() < nextReloadPoll) return;
            nextReloadPoll = System.nanoTime() + 50_000_000L;
            Path file = reloadDirectory.resolve(PreviewReloadRequest.REQUEST);
            if (!Files.isRegularFile(file)) return;
            try {
                PreviewReloadRequest request = PreviewReloadRequest.read(file);
                if (request.revision() <= reloadRevision) return;
                reloadRevision = request.revision();
                reloadCanvas = new PreviewReloadCanvas(request, items.get(active), config, fdx, dock);
            } catch (PreviewReloadCanvas.WindowChange changed) {
                rejectReload("restart", "Window settings changed");
            } catch (LinkageError failure) {
                failure.printStackTrace(System.err);
                rejectReload("restart", "Class loading requires a fresh runtime: " + failure);
            } catch (IOException | ReflectiveOperationException | RuntimeException failure) {
                failure.printStackTrace(System.err);
                rejectReload("failed", "Cannot reload composition: " + failure + "; previous preview retained");
            }
        }

        private boolean renderReload() {
            if (reloadCanvas == null) return false;
            try {
                if (!reloadCanvas.render(dock, root)) return false;
            } catch (LinkageError failure) {
                return failReload("restart", failure);
            } catch (RuntimeException failure) {
                return failReload("failed", failure);
            }
            activateReload();
            return true;
        }

        private boolean failReload(String state, Throwable failure) {
            failure.printStackTrace(System.err);
            rejectReload(state, "Composition failed: " + failure + "; previous preview retained");
            // A custom draw may have emitted part of a frame before failing.
            fdx.graphics().main().clear(0.045f, 0.052f, 0.066f, 1);
            return false;
        }

        private void activateReload() {
            PreviewReloadCanvas next = reloadCanvas;
            UiRoot retiredRoot = root;
            UiPreviewContext retiredContext = context;
            PreviewCompositionLoader retiredLoader = compositionLoader;
            Path retiredDirectory = compositionDirectory;
            dock.items(items, next.items, next.active, index -> requested = index);
            items = next.items;
            root = next.root;
            context = next.context;
            pendingContent = null;
            active = next.active;
            requested = active;
            if (next.config.inputMode != UiPreviewConfig.InputMode.NONE) root.input(fdx.input(), canvasInput);
            compositionLoader = next.loader;
            compositionDirectory = reloadDirectory.resolve("reload-" + reloadRevision);
            Thread.currentThread().setContextClassLoader(compositionLoader);
            next.transfer();
            reloadCanvas = null;
            frames = 1;
            fdx.displays().main().title(title(next.config, items.get(active)));
            reloadAcknowledgement = reloadRevision;
            try { disposeSession(retiredRoot, retiredContext); }
            catch (RuntimeException failure) { failure.printStackTrace(System.err); }
            finally {
                try {
                    if (retiredLoader != null) retiredLoader.close();
                    if (retiredDirectory != null) PreviewSnapshot.deleteOwned(reloadDirectory, retiredDirectory);
                } catch (IOException failure) { failure.printStackTrace(System.err); }
            }
        }

        private void rejectReload(String state, String message) {
            try {
                if (reloadCanvas != null) reloadCanvas.close();
                reloadCanvas = null;
                if (reloadDirectory != null) {
                    PreviewReloadRequest.respond(reloadDirectory, reloadRevision, state, message);
                    PreviewSnapshot.deleteOwned(reloadDirectory, reloadDirectory.resolve("reload-" + reloadRevision));
                }
            } catch (IOException | RuntimeException failure) {
                throw new IllegalStateException("Cannot retire failed composition", failure);
            }
        }

        private void closeCompositionLoader() throws IOException {
            if (compositionLoader == null) return;
            Thread.currentThread().setContextClassLoader(UiPreviewEntry.class.getClassLoader());
            compositionLoader.close();
            compositionLoader = null;
            PreviewSnapshot.deleteOwned(reloadDirectory, compositionDirectory);
            compositionDirectory = null;
        }

        private void requestReplacement(String className) {
            try {
                PreviewSessionFiles.write(entry.request(), ++entryRequests + "\n" + className);
                replacementRequested = !className.isEmpty();
            } catch (IOException error) {
                System.err.println("[ui-preview] Cannot request preview entry: " + error);
            }
        }

        public void onFrameEnd() {
            if (signal.requested) return;
            if (frames > 0) finishCanvasFrame();
        }

        private void finishCanvasFrame() {
            if (frames == 1 && targetFile != null) {
                PreviewHierarchy.Item item = items.get(active);
                String id = item.id();
                try {
                    if (entry != null) {
                        String entryClass = item.entry().className();
                        PreviewSessionFiles.writeSelection(entry.selection(),
                                new PreviewSessionFiles.EntrySelection(entryClass, id));
                        id = entryClass;
                    }
                    PreviewSessionFiles.write(targetFile, id);
                }
                catch (IOException error) { throw new IllegalStateException("Cannot publish preview target", error); }
            }
            if (capture) {
                capture = false;
                Path output = captureFile != null ? captureFile
                        : Path.of("build", "ui-preview", "capture-" + System.currentTimeMillis() + ".png");
                captureFrame(fdx, output, dock == null ? 0 : dock.leftPixels());
            }
            if (!ready) {
                ready = true;
                markReady();
            }
            if (reloadAcknowledgement != 0) {
                try {
                    PreviewReloadRequest.respond(reloadDirectory, reloadAcknowledgement, "applied", "First updated frame rendered");
                    reloadAcknowledgement = 0;
                } catch (IOException failure) {
                    throw new IllegalStateException("Cannot acknowledge composition frame", failure);
                }
            }
        }

        private void closeSession() {
            pendingContent = null;
            try { disposeSession(root, context); }
            finally { root = null; context = null; }
        }

        private static void disposeSession(UiRoot root, UiPreviewContext context) {
            try { if (root != null) root.dispose(); }
            finally { if (context != null) context.dispose(); }
        }

        public void dispose() {
            if (disposed) return;
            disposed = true;
            try {
                closeSession();
                if (reloadCanvas != null) reloadCanvas.close();
                closeCompositionLoader();
            } catch (IOException failure) {
                throw new IllegalStateException("Cannot close composition classes", failure);
            }
            finally {
                if (fdx != null) fdx.input().removeProcessor(router);
                if (dock != null) dock.close();
            }
        }

    }
}
