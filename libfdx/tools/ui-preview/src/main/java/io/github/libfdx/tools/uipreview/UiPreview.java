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
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Direct-call desktop UI runner. Invoke from an ordinary Java main; no game listener is started. */
public final class UiPreview {
    private static final String CONTROLS = " | F5 reset, F6 pause, F7 step, F8 capture";

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
        openWindow(config, setup, List.of(), List.of(), 0, null);
    }

    /**
     * Opens named compositions with a class picker. F4 advances to the next target and Shift+F4 goes back.
     * The watcher retains the selected target ID on refresh. Set libfdx.uiPreview.target for the initial launch.
     */
    public static void openTargets(UiPreviewConfig config, UiPreviewTarget... targets) {
        if (config == null) throw new IllegalArgumentException("Preview config required");
        List<UiPreviewTarget> registered = List.of(targets);
        if (registered.isEmpty()) throw new IllegalArgumentException("At least one preview target required");
        var ids = new HashSet<String>();
        for (UiPreviewTarget target : registered) {
            if (!ids.add(target.id())) throw new IllegalArgumentException("Duplicate preview target: " + target.id());
        }
        int initial = initialTarget(registered, System.getProperty("libfdx.uiPreview.target", ""),
                System.getProperty(PreviewSessionFiles.RESTORED_TARGET, ""));
        openWindow(config, null, registered, registered.stream().map(UiPreviewTarget::label).toList(), initial, null);
    }

    static void openEntry(UiPreviewConfig config, UiPreviewEntry entry) {
        String catalogFile = System.getProperty(PreviewEntries.CATALOG_PROPERTY);
        if (catalogFile == null) {
            open(config, entry);
            return;
        }
        Path catalog = Path.of(catalogFile);
        try {
            List<String> entries = Files.readAllLines(catalog);
            int initial = entries.indexOf(System.getProperty(PreviewEntries.CLASS_PROPERTY));
            if (initial < 0) throw new IllegalArgumentException("Active preview entry is missing from its catalog");
            List<String> names = entries.stream().map(name -> name.substring(name.lastIndexOf('.') + 1)).toList();
            List<String> labels = java.util.stream.IntStream.range(0, names.size())
                    .mapToObj(i -> names.indexOf(names.get(i)) == names.lastIndexOf(names.get(i)) ? names.get(i) : entries.get(i))
                    .toList();
            openWindow(config, entry, List.of(), labels, initial,
                    new EntrySelection(entries, catalog.resolveSibling(PreviewEntries.REQUEST_FILE)));
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read preview entry catalog", error);
        }
    }

    private record EntrySelection(List<String> entries, Path requestFile) { }

    static int initialTarget(List<UiPreviewTarget> targets, String requested, String restored) {
        String id = restored.isEmpty() ? requested : restored;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).id().equals(id)) return i;
        }
        if (!id.isEmpty() && restored.isEmpty()) {
            throw new IllegalArgumentException("Unknown preview target: " + id);
        }
        return 0;
    }

    private static void openWindow(UiPreviewConfig config, UiPreviewSetup setup, List<UiPreviewTarget> targets,
                                   List<String> labels, int initial, EntrySelection entries) {
        String title = title(config, labels, initial);
        try (PreviewStopSignal signal = stopSignal()) {
            new DesktopApplicationBackend().start(new DesktopApplicationConfig()
                    .title(title)
                    .size(config.width, config.height).vSync(true).foregroundFps(60)
                    .graphics(config.graphics != null ? config.graphics : new DesktopOpenGLProvider()),
                    new Listener(config, setup, targets, labels, initial, entries, signal));
        }
    }

    private static String title(UiPreviewConfig config, List<String> labels, int active) {
        if (labels.isEmpty()) return config.title + CONTROLS;
        String selected = config.title + " | " + labels.get(active) + " (" + (active + 1) + "/" + labels.size() + ")";
        return selected + (labels.size() > 1 ? " | F4 next, Shift+F4 previous" : "") + CONTROLS;
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
                    if (capture != null) captureFrame(runtime, capture);
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

    private static void captureFrame(Fdx fdx, Path output) {
        GraphicsFrame frame = fdx.graphics().main().currentFrame();
        if (!frame.frameBuffer().supportsReadPixelsRgba8()) {
            throw new IllegalStateException("Preview capture requires framebuffer RGBA8 readback support");
        }
        try {
            PreviewPng.write(output, frame.width(), frame.height(), frame.frameBuffer().readPixelsRgba8());
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
        private final UiPreviewSetup setup;
        private final List<UiPreviewTarget> targets;
        private final List<String> labels;
        private final EntrySelection entries;
        private final PreviewStopSignal signal;
        private final InputRouter router = new InputRouter();
        private Fdx fdx;
        private UiPreviewContext context;
        private UiRoot root;
        private PreviewClassPicker picker;
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
        private Path captureFile;
        private Path targetFile;
        private long exitFrames;

        Listener(UiPreviewConfig config, UiPreviewSetup setup, List<UiPreviewTarget> targets, List<String> labels,
                 int initial, EntrySelection entries, PreviewStopSignal signal) {
            this.config = config;
            this.setup = setup;
            this.targets = targets;
            this.labels = labels;
            this.entries = entries;
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
                if (!labels.isEmpty() && targetPath != null) targetFile = Path.of(targetPath);
                router.add(new InputAdapter() {
                    public boolean keyDown(KeyEvent event) {
                        if (event.key() == Key.F4 && labels.size() > 1) {
                            if (!event.repeat()) {
                                Input input = fdx.input();
                                boolean previous = input.isKeyPressed(Key.SHIFT_LEFT) || input.isKeyPressed(Key.SHIFT_RIGHT);
                                requested = Math.floorMod(requested + (previous ? -1 : 1), labels.size());
                                picker.closeMenu();
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
                if (labels.size() > 1) {
                    UiRoot controls = new UiToolkit(fdx.files()).root(fdx.displays().main(), fdx.graphics().main());
                    picker = new PreviewClassPicker(controls, fdx.input(), labels, active, index -> requested = index);
                    router.add(picker);
                }
                fdx.input().addProcessor(router);
                startSession(active);
            } catch (RuntimeException | Error failure) {
                try { dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        private void startSession(int index) {
            UiRoot nextRoot = createRoot(config, fdx.files(), fdx.displays().main(), fdx.graphics().main(), fdx.input(), router);
            UiPreviewContext nextContext = null;
            UiContent content;
            try {
                nextContext = new UiPreviewContext(nextRoot, nextRoot.graphics());
                UiPreviewSetup nextSetup = targets.isEmpty() ? setup : targets.get(index).setup();
                content = nextSetup.create(nextContext);
                if (content == null) throw new IllegalArgumentException("Preview setup returned null content");
                closeSession();
            } catch (RuntimeException | Error failure) {
                try { disposeSession(nextRoot, nextContext); }
                catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                if (root == null || index == active || failure instanceof Error) throw failure;
                System.err.println("[ui-preview] Cannot open target " + targets.get(index).id());
                failure.printStackTrace(System.err);
                requested = active;
                return;
            }
            root = nextRoot;
            context = nextContext;
            pendingContent = content;
            active = index;
            fdx.displays().main().title(title(config, labels, active));
            frames = 0;
        }

        public void resize(int width, int height) {
            if (root != null) root.resize(width, height);
            if (picker != null) picker.resize(width, height);
        }

        public void render() {
            if (signal.requested) { fdx.app().requestExit(); return; }
            if (entries != null && requested != active) {
                try {
                    PreviewSessionFiles.write(entries.requestFile(), ++entryRequests + "\n" + entries.entries().get(requested));
                } catch (IOException error) {
                    System.err.println("[ui-preview] Cannot request preview entry: " + error);
                }
                requested = active;
            }
            if (reset || requested != active) {
                reset = false;
                startSession(requested);
            }
            fdx.graphics().main().clear(0.045f, 0.052f, 0.066f, 1);
            if (context.updateAssets()) {
                if (pendingContent != null) {
                    root.setContent(pendingContent);
                    pendingContent = null;
                }
                root.update(step ? 1.0f / 60 : paused ? 0 : fdx.app().deltaTime());
                step = false;
                root.render();
                frames++;
                if (exitFrames > 0 && frames >= exitFrames) fdx.app().requestExit();
            }
            if (picker != null) picker.render(active);
        }

        public void onFrameEnd() {
            if (frames == 0 || signal.requested) return;
            if (frames == 1 && targetFile != null) {
                String id = entries == null ? targets.get(active).id() : entries.entries().get(active);
                try { PreviewSessionFiles.write(targetFile, id); }
                catch (IOException error) { throw new IllegalStateException("Cannot publish preview target", error); }
            }
            if (capture) {
                capture = false;
                Path output = captureFile != null ? captureFile
                        : Path.of("build", "ui-preview", "capture-" + System.currentTimeMillis() + ".png");
                captureFrame(fdx, output);
            }
            if (!ready) {
                ready = true;
                markReady();
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
            try { closeSession(); }
            finally {
                if (fdx != null) fdx.input().removeProcessor(router);
                if (picker != null) picker.close();
            }
        }

    }
}
