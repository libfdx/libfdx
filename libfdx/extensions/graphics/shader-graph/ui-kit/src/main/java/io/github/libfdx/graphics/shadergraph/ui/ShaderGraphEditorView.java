package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironment;
import io.github.libfdx.graphics.shader.target.ShaderTargetEnvironments;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTextAreaOptions;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional UI Kit shader graph editor composition.
 *
 * <p>The view does not own the session, compiler, target adapter, or provider.
 * Call {@link #dispose()} when the view is no longer used so its session
 * listener is released.</p>
 */
public final class ShaderGraphEditorView implements UiContent {
    private static final UiModifier ROOT = UiModifier.none().fill()
            .padding(10.0f).gap(8.0f);
    private static final UiModifier TOOLBAR = UiModifier.none()
            .fillWidth().height(40.0f).gap(6.0f);
    private static final UiModifier BODY = UiModifier.none()
            .fillWidth().weight(1.0f).gap(8.0f);
    private static final UiModifier PALETTE = UiModifier.none()
            .width(210.0f).fillHeight().padding(8.0f);
    private static final UiModifier CANVAS = UiModifier.none()
            .weight(1.0f).fillHeight().minWidth(320.0f)
            .focusable(true).clip();
    private static final UiModifier INSPECTOR = UiModifier.none()
            .width(310.0f).fillHeight().padding(8.0f);
    private static final UiModifier OUTPUT = UiModifier.none()
            .fillWidth().height(210.0f).gap(8.0f);
    private static final UiModifier OUTPUT_PANEL = UiModifier.none()
            .weight(1.0f).fillHeight().padding(6.0f);
    private static final UiModifier PREVIEW = UiModifier.none()
            .width(260.0f).fillHeight().focusable(true).clip();
    private static final UiTextAreaOptions READ_ONLY =
            UiTextAreaOptions.defaults().readOnly(true)
                    .minHeight(145.0f).maxHeight(165.0f);

    private final ShaderGraphEditorSession session;
    private final ShaderGraphEditorCompiler compiler;
    private final ShaderGraphEditorPalette palette;
    private final ShaderGraphEditorCanvas canvas;
    private final ShaderGraphEditorPreviewSurface preview;
    private final ShaderGraphEditorSessionListener listener;
    private final UiState<String> status = Ui.state("");
    private final UiState<String> wgsl = Ui.state("");
    private final UiState<String> diagnostics = Ui.state("");
    private final UiState<String> artifacts = Ui.state("");
    private ShaderGraphEditorCompileSettings settings;
    private int nextNodeIndex = 1;
    private boolean disposed;

    public ShaderGraphEditorView(ShaderGraphEditorSession session,
            ShaderGraphEditorCompiler compiler) {
        this(session, compiler, ShaderGraphEditorPalette.standard(), null,
                ShaderGraphEditorCompileSettings.builder()
                        .previewMode(ShaderGraphEditorPreviewMode
                                .defaultFor(session != null
                                        ? session.document() : null))
                        .build());
    }

    public ShaderGraphEditorView(ShaderGraphEditorSession session,
            ShaderGraphEditorCompiler compiler,
            ShaderGraphEditorPalette palette,
            ShaderGraphEditorPreviewSurface preview,
            ShaderGraphEditorCompileSettings settings) {
        if (session == null || compiler == null || palette == null
                || settings == null) {
            throw new FdxException(
                    "Shader graph editor view is incomplete");
        }
        this.session = session;
        this.compiler = compiler;
        this.palette = palette;
        this.preview = preview;
        this.settings = settings;
        canvas = new ShaderGraphEditorCanvas(session);
        listener = ignored -> refreshText();
        session.addListener(listener);
        refreshText();
    }

    public ShaderGraphEditorSession session() {
        return session;
    }

    public ShaderGraphEditorCanvas canvas() {
        return canvas;
    }

    public ShaderGraphEditorCompileSettings settings() {
        return settings;
    }

    public void settings(ShaderGraphEditorCompileSettings value) {
        if (value == null) {
            throw new FdxException(
                    "Shader graph editor compile settings cannot be null");
        }
        settings = value;
        refreshText();
    }

    public ShaderGraphEditorCompilation compileNow() {
        ShaderGraphEditorCompileRequest request =
                session.beginCompilation(settings);
        ShaderGraphEditorCompilation result = compiler.compile(request);
        session.completeCompilation(result, preview,
                settings.previewMode());
        refreshText();
        return result;
    }

    /**
     * Releases the listener retained by the session. The session and preview
     * host remain caller-owned.
     */
    public void dispose() {
        if (!disposed) {
            session.removeListener(listener);
            disposed = true;
        }
    }

    @Override
    public void build(UiScope ui) {
        ui.column(ROOT, root -> {
            root.row(TOOLBAR, this::toolbar);
            root.row(BODY, body -> {
                body.panel(PALETTE,
                        panel -> panel.scroll(
                                UiModifier.none().fill(),
                                this::palette));
                body.custom("shader-graph-editor-canvas", CANVAS, canvas);
                body.panel(INSPECTOR,
                        panel -> panel.scroll(
                                UiModifier.none().fill(),
                                this::inspector));
            });
            root.row(OUTPUT, this::output);
        });
    }

    private void toolbar(UiScope ui) {
        ui.text("Shader Graph - " + session.document().id(),
                UiModifier.none().weight(1.0f));
        if (session.document().graphs().length > 1) {
            ui.button("Graph: " + session.layout().activeGraphId(),
                    this::nextGraph);
        }
        ui.button("Undo", UiModifier.none()
                .enabled(session.canUndo()), () -> session.undo());
        ui.button("Redo", UiModifier.none()
                .enabled(session.canRedo()), () -> session.redo());
        ui.button(settings.profile().name(), this::nextProfile);
        ui.button(settings.target().value(), this::nextTarget);
        ui.button(settings.previewMode().name(), this::nextPreview);
        ui.button("Compile", this::compileNow);
    }

    private void palette(UiScope ui) {
        ui.column(UiModifier.none().fillWidth().gap(5.0f), column -> {
            column.text("Node Palette");
            ShaderGraph graph = session.activeGraph();
            List<ShaderGraphEditorNodeTemplate> templates =
                    new ArrayList<>();
            for (ShaderGraphEditorNodeTemplate template
                    : palette.templates(graph.kind(), "")) {
                templates.add(template);
            }
            for (ShaderGraphParameter parameter : graph.parameters()) {
                templates.add(ShaderGraphEditorPalette.parameter(parameter));
            }
            for (ShaderGraphResource resource : graph.resources()) {
                templates.add(ShaderGraphEditorPalette.resource(resource));
            }
            for (ShaderGraphEditorNodeTemplate template : templates) {
                column.button(shortText(template.category() + " - "
                                + template.label(), 28),
                        () -> add(template));
            }
            column.spacer(UiModifier.none().height(10.0f));
            column.text("Document Graphs");
            for (ShaderGraph embedded : session.document().graphs()) {
                column.button(embedded.kind() + " - " + embedded.id(),
                        () -> session.selectGraph(
                                embedded.id().value()));
            }
        });
    }

    private void inspector(UiScope ui) {
        ShaderGraphEditorInspectorModel model =
                ShaderGraphEditorInspectorModel.inspect(session, settings);
        ui.column(UiModifier.none().fillWidth().gap(4.0f), column -> {
            column.text("Inspector");
            for (ShaderGraphEditorInspectorSection section
                    : model.sections()) {
                column.text(section.title(),
                        UiModifier.none().fillWidth().height(24.0f));
                for (ShaderGraphEditorInspectorField field
                        : section.fields()) {
                    column.text(field.label() + ": "
                                    + shortText(field.value(), 46),
                            UiModifier.none().fillWidth()
                                    .minHeight(20.0f));
                }
                column.spacer(UiModifier.none().height(5.0f));
            }
        });
    }

    private void output(UiScope ui) {
        ui.panel(OUTPUT_PANEL, panel -> panel.column(
                UiModifier.none().fill().gap(4.0f), column -> {
                    column.text(status.get());
                    column.text("Diagnostics");
                    column.textArea(UiModifier.none().fillWidth()
                                    .weight(1.0f),
                            diagnostics, READ_ONLY);
                }));
        ui.panel(OUTPUT_PANEL, panel -> panel.column(
                UiModifier.none().fill().gap(4.0f), column -> {
                    column.text("Canonical WGSL");
                    column.textArea(UiModifier.none().fill()
                                    .weight(1.0f),
                            wgsl, READ_ONLY);
                }));
        ui.panel(OUTPUT_PANEL, panel -> panel.column(
                UiModifier.none().fill().gap(4.0f), column -> {
                    column.text("Target Artifacts");
                    column.textArea(UiModifier.none().fill()
                                    .weight(1.0f),
                            artifacts, READ_ONLY);
                }));
        if (preview != null) {
            ui.custom("shader-graph-editor-preview", PREVIEW, preview);
        }
    }

    private void add(ShaderGraphEditorNodeTemplate template) {
        String nodeId;
        do {
            nodeId = "node_" + nextNodeIndex++;
        } while (session.activeGraph().node(
                ShaderGraphId.of(nodeId)) != null);
        float offset = (nextNodeIndex % 6) * 24.0f;
        session.addNode(template.create(nodeId),
                canvas.graphX(70.0f + offset),
                canvas.graphY(70.0f + offset));
    }

    private void nextGraph() {
        ShaderGraph[] graphs = session.document().graphs();
        String active = session.layout().activeGraphId();
        for (int i = 0; i < graphs.length; i++) {
            if (graphs[i].id().value().equals(active)) {
                session.selectGraph(
                        graphs[(i + 1) % graphs.length].id().value());
                return;
            }
        }
    }

    private void nextProfile() {
        ShaderProfile[] profiles = ShaderProfile.values();
        ShaderProfile next = profiles[
                (settings.profile().ordinal() + 1) % profiles.length];
        settings = copy(next, settings.environment(),
                settings.previewMode());
        refreshText();
    }

    private void nextTarget() {
        ShaderTargetEnvironment[] environments =
                ShaderTargetEnvironments.standard();
        int current = 0;
        for (int i = 0; i < environments.length; i++) {
            if (environments[i].equals(settings.environment())) {
                current = i;
                break;
            }
        }
        settings = copy(settings.profile(),
                environments[(current + 1) % environments.length],
                settings.previewMode());
        refreshText();
    }

    private void nextPreview() {
        ShaderGraphEditorPreviewMode[] modes =
                ShaderGraphEditorPreviewMode.values();
        int index = settings.previewMode().ordinal();
        for (int count = 0; count < modes.length; count++) {
            index = (index + 1) % modes.length;
            if (modes[index].supports(session.document())) {
                settings = copy(settings.profile(),
                        settings.environment(), modes[index]);
                refreshText();
                return;
            }
        }
    }

    private ShaderGraphEditorCompileSettings copy(ShaderProfile profile,
            ShaderTargetEnvironment environment,
            ShaderGraphEditorPreviewMode mode) {
        ShaderGraphEditorCompileSettings.Builder builder =
                ShaderGraphEditorCompileSettings.builder()
                        .profile(profile)
                        .capabilities(settings.capabilities())
                        .output(environment.target(), environment.format(),
                                environment)
                        .previewMode(mode);
        if (settings.compiler() != null) {
            builder.compiler(settings.compiler());
        }
        if (settings.verifier() != null) {
            builder.verifier(settings.verifier());
        }
        return builder.build();
    }

    private void refreshText() {
        ShaderGraphEditorCompilation compilation =
                session.latestCompilation();
        if (compilation == null) {
            wgsl.set("");
            artifacts.set("");
            diagnostics.set(capabilityText());
        } else {
            wgsl.set(compilation.canonicalWgsl());
            diagnostics.set(diagnosticText(compilation));
            artifacts.set(artifactText(compilation));
        }
        String selected = session.selectedNodeId().isEmpty()
                ? "none" : session.selectedNodeId();
        status.set("Graph " + session.layout().activeGraphId()
                + " - selected " + selected
                + " - semantic r" + session.semanticRevision()
                + " - layout r" + session.layoutRevision());
    }

    private String capabilityText() {
        ShaderGraphDiagnostic[] values =
                ShaderGraphEditorCapabilityValidator.validate(
                        session.document(), settings);
        if (values.length == 0) {
            return "Ready for " + settings.profile() + " / "
                    + settings.environment().id();
        }
        StringBuilder text = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic : values) {
            text.append(diagnostic.severity()).append(' ')
                    .append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return text.toString();
    }

    private static String diagnosticText(
            ShaderGraphEditorCompilation compilation) {
        if (compilation.diagnostics().length == 0) {
            return compilation.success()
                    ? "Compilation succeeded." : "Compilation failed.";
        }
        StringBuilder text = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic
                : compilation.diagnostics()) {
            text.append(diagnostic.severity()).append(' ')
                    .append(diagnostic.code()).append(": ")
                    .append(diagnostic.message()).append('\n');
        }
        return text.toString();
    }

    private static String artifactText(
            ShaderGraphEditorCompilation compilation) {
        if (compilation.artifacts().length == 0) {
            return "No translated artifact requested or produced.";
        }
        StringBuilder text = new StringBuilder();
        for (ShaderGraphEditorArtifact artifact
                : compilation.artifacts()) {
            text.append(artifact.targetId()).append(" - ")
                    .append(artifact.stage()).append(" - ")
                    .append(artifact.formatId()).append(" - ")
                    .append(artifact.verified()
                            ? "verified" : "unverified")
                    .append('\n');
            if (artifact.encoding() == ShaderArtifactEncoding.TEXT) {
                text.append(artifact.text()).append('\n');
            } else {
                text.append(artifact.payload().length)
                        .append(" binary bytes\n");
            }
        }
        return text.toString();
    }

    private static String shortText(String value, int maximum) {
        if (value == null || value.length() <= maximum) {
            return value != null ? value : "";
        }
        return value.substring(0, Math.max(0, maximum - 3)) + "...";
    }
}
