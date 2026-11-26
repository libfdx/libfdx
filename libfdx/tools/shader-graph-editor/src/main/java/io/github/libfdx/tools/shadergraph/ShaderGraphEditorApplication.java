package io.github.libfdx.tools.shadergraph;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorCompilation;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorCompileSettings;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorDocument;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorLayout;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorLoadResult;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorMaterialPreview;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorPersistence;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorPreviewMode;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorSession;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorSaveResult;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorTheme;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorView;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiToolkit;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Desktop host for saved and Java-authored shader graphs.
 *
 * <p>File selection and persistence stay in this host. The reusable editor
 * receives strings, a session, and compile settings; it does not own project
 * files or the graphics provider.</p>
 */
final class ShaderGraphEditorApplication
        extends ApplicationAdapter {
    private static final float EFFECT_ROW_HEIGHT = 28;
    private static final UiModifier EFFECT_ROW = UiModifier.none().fillWidth().height(EFFECT_ROW_HEIGHT);
    private final List<EditorEffect> effects = new ArrayList<>();
    private final UiIntState selected;
    private final UiScrollState effectScroll = new UiScrollState();
    private final long exitAfterFrames;
    private final UiState<String> status =
            Ui.state("Choose one or more .fdxgraph files to edit and preview.");

    private Application application;
    private FileSystem files;
    private GraphicsContext graphics;
    private Logger logger;
    private UiRoot root;
    private int activeIndex = -1;
    private ShaderGraphEditorView editor;
    private CompletableFuture<File[]> selection;
    private Consumer<File[]> onSelection;
    private JFileChooser chooser; // Accessed on the desktop event thread.
    private volatile boolean disposed;
    private long renderedFrames;

    ShaderGraphEditorApplication(List<ShaderGraphDesktopEditor.Effect> effects, int initialIndex, long exitAfterFrames) {
        if (!effects.isEmpty() || initialIndex != -1) Objects.checkIndex(initialIndex, effects.size());
        for (var effect : effects) this.effects.add(new EditorEffect(effect));
        selected = Ui.state(initialIndex);
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        files = fdx.files();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        if (!effects.isEmpty()) selectEffect(selected.get());
        root = new UiToolkit(files)
                .theme(ShaderGraphEditorTheme.darkGray())
                .root(fdx.displays().main(), graphics)
                .input(fdx.input());
        root.setContent(this::buildUi);
    }

    private void selectEffect(int index) {
        var effect = effects.get(index);
        try {
            activate(effect, index);
        } catch (IOException | RuntimeException failure) {
            selected.set(activeIndex);
            status.set("Could not open " + effect.title() + ": " + message(failure));
            logger.error("Could not open shader graph " + effect.path, failure);
        }
    }

    private void activate(EditorEffect effect, int index) throws IOException {
        status.set(effect.description());
        if (effect.view == null) effect.view = open(effect);
        if (index == effects.size()) effects.add(effect);
        editor = effect.view;
        activeIndex = index;
        selected.set(index);
        effectScroll.scrollYRangeIntoView(index * EFFECT_ROW_HEIGHT, (index + 1) * EFFECT_ROW_HEIGHT);
    }

    private ShaderGraphEditorView open(EditorEffect effect) throws IOException {
        ShaderGraphEditorSession session;
        String warning = "";
        var preset = effect.preset;
        if (preset == null || preset.builder() == null) {
            ShaderGraphEditorLoadResult loaded = ShaderGraphEditorPersistence.read(effect.read(files),
                    preset == null ? null : preset.previewAnimation());
            session = new ShaderGraphEditorSession(loaded.document(), loaded.layout());
            warning = loaded.editorWarning();
        } else {
            var document = ShaderGraphEditorDocument.of(preset.builder().get());
            var layout = ShaderGraphEditorLayout.forDocument(document).withPreviewAnimation(preset.previewAnimation());
            session = new ShaderGraphEditorSession(document, layout);
        }
        ShaderGraphEditorCompileSettings settings =
                ShaderGraphEditorCompileSettings.builder()
                        .previewMode(ShaderGraphEditorPreviewMode.defaultFor(session.document()))
                        .build();
        var view = new ShaderGraphEditorView(graphics, session, settings);
        try {
            var preview = view.materialPreview();
            if (preview != null) {
                preview.modelChooser(() -> chooseModel(preview));
            }
            ShaderGraphEditorCompilation compilation = view.compileNow();
            if (!compilation.success()) status.set("Initial compile failed. See diagnostics below.");
            if (!warning.isEmpty()) status.set(warning);
            logger.info("Shader graph editor opened " + session.document().id() + " from "
                    + (preset != null && preset.builder() != null ? "Java" : effect.description()) + " for provider "
                    + graphics.providerId().value());
            return view;
        } catch (RuntimeException failure) {
            view.dispose();
            throw failure;
        }
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        var background = root.theme().backgroundColor();
        graphics.clear(background.red(), background.green(), background.blue(), background.alpha());
        if (selected.get() != activeIndex) {
            selectEffect(selected.get());
            root.requestCompose();
        }
        if (selection != null && selection.isDone()) {
            var result = selection;
            var action = onSelection;
            selection = null;
            onSelection = null;
            try {
                File[] picked = result.join();
                if (picked.length > 0) action.accept(picked);
            } catch (RuntimeException failure) {
                status.set("Could not open selection: " + message(failure));
            }
            root.requestCompose();
        }
        root.update(application.deltaTime());
        if (editor != null) editor.renderPreview(application.deltaTime());
        root.render();
        renderedFrames++;
        if (exitAfterFrames > 0L
                && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        if (selection != null) EventQueue.invokeLater(() -> {
            if (chooser != null) chooser.cancelSelection();
        });
        for (var effect : effects) {
            if (effect.view != null) effect.view.dispose();
        }
        if (root != null) {
            root.dispose();
            root = null;
        }
        if (exitAfterFrames > 0L
                && renderedFrames < exitAfterFrames) {
            throw new FdxException(
                    "Shader graph editor rendered "
                            + renderedFrames + " of " + exitAfterFrames
                            + " required frames");
        }
        if (logger != null) {
            logger.info("Shader graph editor rendered "
                    + renderedFrames + " frames");
        }
    }

    private void chooseModel(ShaderGraphEditorMaterialPreview preview) {
        chooseFiles("Open preview model", new FileNameExtensionFilter("glTF models (*.gltf, *.glb)", "gltf", "glb"),
                false, picked -> {
                    File file = picked[0];
                    var modelFiles = new DefaultFileSystem().addInternalRootFirst(file.getParentFile());
                    preview.loadModel(modelFiles, file.getName());
                });
    }

    private void chooseEffects() {
        chooseFiles("Open shader effects", new FileNameExtensionFilter("Shader graphs (*.fdxgraph)", "fdxgraph"),
                true, this::openFiles);
    }

    private void openFiles(File[] picked) {
        String firstError = null;
        for (File file : picked) {
            try {
                Path path = file.toPath().toRealPath();
                int index = 0;
                while (index < effects.size() && !effects.get(index).matches(path)) index++;
                var effect = index < effects.size() ? effects.get(index) : new EditorEffect(path);
                activate(effect, index);
            } catch (IOException | RuntimeException failure) {
                String error = "Could not open " + file.getName() + ": " + message(failure);
                if (firstError == null) firstError = error;
                logger.error(error, failure);
            }
        }
        if (firstError != null) status.set(firstError);
    }

    private void chooseFiles(String title, FileNameExtensionFilter filter, boolean multiple, Consumer<File[]> action) {
        if (selection != null) return;
        onSelection = action;
        selection = CompletableFuture.supplyAsync(() -> {
            if (disposed) return new File[0];
            chooser = new JFileChooser();
            try {
                chooser.setDialogTitle(title);
                chooser.setAcceptAllFileFilterUsed(false);
                chooser.setFileFilter(filter);
                chooser.setMultiSelectionEnabled(multiple);
                if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return new File[0];
                File[] picked = chooser.getSelectedFiles();
                if (multiple && picked.length > 0) return picked;
                File file = chooser.getSelectedFile();
                return file == null ? new File[0] : new File[]{file.getAbsoluteFile()};
            } finally {
                chooser = null;
            }
        }, EventQueue::invokeLater);
    }

    private void buildUi(UiScope ui) {
        if (effects.isEmpty()) {
            ui.column(UiModifier.none().fill().padding(16), page -> {
                page.spacer(UiModifier.none().weight(1));
                page.panel(UiModifier.none().width(380).align(UiAlign.CENTER), panel ->
                        panel.column(UiModifier.none().fillWidth().gap(10), content -> {
                            content.selectableText("Shader Graph Editor");
                            content.selectableText(status.get(), UiModifier.none().fillWidth());
                            content.button("Open effect…", this::chooseEffects);
                        }));
                page.spacer(UiModifier.none().weight(1));
            });
            return;
        }
        ui.column(UiModifier.none().fill().gap(6.0f), column -> {
            column.row(UiModifier.none().fillWidth()
                    .height(36.0f).gap(8.0f).padding(4.0f), toolbar -> {
                toolbar.button("Open effect…", this::chooseEffects);
                toolbar.selectableText(status.get(),
                        UiModifier.none().weight(1.0f));
                var saveButton = UiModifier.none().enabled(editor != null);
                toolbar.button("Save", saveButton, this::save);
                toolbar.button("Save with compiled cache", saveButton, this::saveWithCompiledCache);
            });
            if (editor == null) {
                column.panel(UiModifier.none().width(210), this::effectPanel);
            } else {
                editor.build(column, effects.size() > 1 ? this::effectPanel : null);
            }
        });
    }

    private void effectPanel(UiScope ui) {
        ui.column(UiModifier.none().fillWidth().gap(4), panel -> {
            panel.selectableText("Effects");
            var viewport = UiModifier.none().fillWidth().height(EFFECT_ROW_HEIGHT * Math.min(4, effects.size()));
            panel.scroll(viewport, effectScroll, list -> {
                for (int i = 0; i < effects.size(); i++) {
                    list.radioButton(effects.get(i).title(), EFFECT_ROW, selected, i);
                }
            });
        });
    }

    private void save() {
        try {
            var session = editor.session();
            effects.get(activeIndex).save(ShaderGraphEditorPersistence.write(
                    session.document(), session.layout()));
            status.set("Saved semantic graph and editor state in one .fdxgraph.");
        } catch (IOException | RuntimeException failure) {
            status.set("Save failed: " + message(failure));
            logger.error("Could not save shader graph",
                    failure);
        }
    }

    private void saveWithCompiledCache() {
        try {
            var session = editor.session();
            ShaderGraphEditorSaveResult result =
                    ShaderGraphEditorPersistence
                            .writeWithCompiledCache(
                                    session.document(),
                                    session.layout());
            if (!result.success()) {
                String diagnostic = result.diagnostics().length > 0
                        ? result.diagnostics()[0].message()
                        : "unknown compilation error";
                status.set("Compiled-cache save failed: "
                        + diagnostic);
                return;
            }
            effects.get(activeIndex).save(result.source());
            session.adoptSavedDocument(
                    result.savedDocument());
            status.set(result.cacheHits() > 0
                    ? "Saved one .fdxgraph; compiled cache was already current."
                    : "Saved one .fdxgraph with an embedded compiled cache.");
        } catch (IOException | RuntimeException failure) {
            status.set("Compiled-cache save failed: "
                    + message(failure));
            logger.error(
                    "Could not save compiled shader graph",
                    failure);
        }
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
    }

}
