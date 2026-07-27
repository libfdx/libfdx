package io.github.libfdx.samples.shadergraph.editor;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLibrary;
import io.github.libfdx.graphics.shadergraph.ui.DefaultShaderGraphEditorCompiler;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorCompilation;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorCompileSettings;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorLoadResult;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorPersistence;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorPreviewMode;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorSession;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorSaveResult;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorView;
import io.github.libfdx.samples.shadergraph.ShaderGraphFramebufferCapture;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleGraphs;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiToolkit;

import java.nio.charset.StandardCharsets;

/**
 * Optional UI Kit editor host for the same semantic graph used by the
 * headless sample.
 *
 * <p>File selection and persistence stay in this host. The reusable editor
 * receives strings, a session, and compile settings; it does not own project
 * files or the graphics provider.</p>
 */
public final class ShaderGraphEditorSampleApplication
        extends ApplicationAdapter {
    private final long exitAfterFrames;
    private final UiState<String> status =
            Ui.state("Loading shader graph...");

    private Application application;
    private FileSystem files;
    private GraphicsContext graphics;
    private Logger logger;
    private UiRoot root;
    private ShaderGraphEditorSession session;
    private ShaderGraphEditorView editor;
    private FileHandle graphFile;
    private String capturePath;
    private long captureFrame;
    private long renderedFrames;
    private boolean captured;

    /**
     * Creates an interactive editor sample.
     */
    public ShaderGraphEditorSampleApplication() {
        this(0L);
    }

    /**
     * Creates an editor sample with an optional finite validation run.
     *
     * @param exitAfterFrames frames to render, or zero to run interactively
     */
    public ShaderGraphEditorSampleApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        files = fdx.files();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        graphFile =
                files.local(ShaderGraphSampleGraphs.SURFACE_LOCAL_PATH);

        String source =
                ShaderGraphSampleGraphs.loadSurfaceSource(files);
        ShaderGraphEditorLoadResult loaded =
                ShaderGraphEditorPersistence.read(source);
        session = new ShaderGraphEditorSession(
                loaded.document(), loaded.layout());
        ShaderGraphEditorCompileSettings settings =
                ShaderGraphEditorCompileSettings.builder()
                        .profile(preferredProfile())
                        .capabilities(
                                graphics.device().capabilities())
                        .previewMode(
                                ShaderGraphEditorPreviewMode.MATERIAL_BALL)
                        .build();
        editor = new ShaderGraphEditorView(session,
                new DefaultShaderGraphEditorCompiler(),
                io.github.libfdx.graphics.shadergraph.ui
                        .ShaderGraphEditorPalette.standard(),
                null, settings);
        ShaderGraphEditorCompilation compilation =
                editor.compileNow();
        status.set(compilation.success()
                ? "Compiled canonical WGSL. Save writes one self-contained .fdxgraph."
                : "Initial compile failed; inspect diagnostics below.");
        if (loaded.recoveredEditorState()) {
            status.set(loaded.editorWarning());
        }

        root = new UiToolkit(files)
                .root(fdx.displays().main(), graphics)
                .input(fdx.input());
        root.setContent(this::buildUi);
        capturePath =
                System.getProperty("libfdx.sample.capture", "");
        captureFrame = Long.parseLong(System.getProperty(
                "libfdx.sample.captureFrame", "3"));
        logger.info("Shader graph editor sample opened "
                + session.document().id() + " for provider "
                + graphics.providerId().value());
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        graphics.clear(0.020f, 0.026f, 0.044f, 1.0f);
        root.update(application.deltaTime());
        root.render();
        if (!captured && capturePath != null
                && !capturePath.isBlank()
                && renderedFrames >= captureFrame) {
            capture(capturePath);
            captured = true;
        }
        renderedFrames++;
        if (exitAfterFrames > 0L
                && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (editor != null) {
            editor.dispose();
            editor = null;
        }
        if (root != null) {
            root.dispose();
            root = null;
        }
        if (capturePath != null && !capturePath.isBlank()
                && !captured) {
            throw new FdxException(
                    "Shader graph editor sample did not capture "
                            + capturePath);
        }
        if (exitAfterFrames > 0L
                && renderedFrames < exitAfterFrames) {
            throw new FdxException(
                    "Shader graph editor sample rendered "
                            + renderedFrames + " of " + exitAfterFrames
                            + " required frames");
        }
        if (logger != null) {
            logger.info("Shader graph editor sample rendered "
                    + renderedFrames + " frames");
        }
    }

    private void buildUi(UiScope ui) {
        ui.column(UiModifier.none().fill().gap(6.0f), column -> {
            column.row(UiModifier.none().fillWidth()
                    .height(36.0f).gap(8.0f).padding(4.0f), toolbar -> {
                toolbar.text(status.get(),
                        UiModifier.none().weight(1.0f));
                toolbar.button("Compile", this::compile);
                toolbar.button("Save", this::save);
                toolbar.button("Save with compiled cache",
                        this::saveWithCompiledCache);
            });
            editor.build(column);
        });
    }

    private void compile() {
        ShaderGraphEditorCompilation result = editor.compileNow();
        status.set(result.success()
                ? "Compilation succeeded; the last-good result is active."
                : "Compilation failed; the last-good result remains active.");
    }

    private void save() {
        try {
            writeGraph(ShaderGraphEditorPersistence.write(
                    session.document(), session.layout()));
            status.set("Saved semantic graph and editor state in one .fdxgraph.");
        } catch (RuntimeException failure) {
            status.set("Save failed: " + message(failure));
            logger.error("Could not save shader graph editor sample",
                    failure);
        }
    }

    private void saveWithCompiledCache() {
        try {
            ShaderGraphEditorSaveResult result =
                    ShaderGraphEditorPersistence
                            .writeWithCompiledCache(
                                    session.document(),
                                    session.layout(),
                                    cacheContext());
            if (!result.success()) {
                String diagnostic = result.diagnostics().length > 0
                        ? result.diagnostics()[0].message()
                        : "unknown compilation error";
                status.set("Compiled-cache save failed: "
                        + diagnostic);
                return;
            }
            writeGraph(result.source());
            session.adoptSavedDocument(
                    result.savedDocument());
            status.set(result.cacheHits() > 0
                    ? "Saved one .fdxgraph; compiled cache was already current."
                    : "Saved one .fdxgraph with an embedded compiled cache.");
        } catch (RuntimeException failure) {
            status.set("Compiled-cache save failed: "
                    + message(failure));
            logger.error(
                    "Could not save compiled shader graph sample",
                    failure);
        }
    }

    private ShaderGraphCacheContext cacheContext() {
        return ShaderGraphCacheContext.wgpu(
                ShaderGraphCompileOptions.builder()
                        .profile(preferredProfile())
                        .capabilities(
                                graphics.device().capabilities())
                        .library(ShaderGraphLibrary.of(
                                session.document().graphs()))
                        .build());
    }

    private void writeGraph(String source) {
        graphFile.writeString(source + System.lineSeparator(),
                StandardCharsets.UTF_8, false).get();
    }

    private ShaderProfile preferredProfile() {
        if (graphics.device().capabilities().supports(
                ShaderProfile.PORTABLE_WEBGPU)) {
            return ShaderProfile.PORTABLE_WEBGPU;
        }
        if (graphics.device().capabilities().supports(
                ShaderProfile.PORTABLE_WEBGL2)) {
            return ShaderProfile.PORTABLE_WEBGL2;
        }
        return ShaderProfile.NATIVE;
    }

    private void capture(String path) {
        try {
            int width = graphics.currentFrame().frameBuffer().width();
            int height = graphics.currentFrame().frameBuffer().height();
            ShaderGraphFramebufferCapture.writePpm(path, width, height,
                    graphics.currentFrame().frameBuffer()
                            .readPixelsRgba8());
            logger.info("Shader graph editor sample captured " + path);
        } catch (Exception failure) {
            throw new FdxException(
                    "Could not capture shader graph editor sample",
                    failure);
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
    }
}
