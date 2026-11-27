package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.target.ShaderArtifactEncoding;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphDiagnostic;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiTextAreaOptions;
import io.github.libfdx.ui.UiTextInputFilter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional UI Kit shader graph editor composition.
 *
 * <p>The view does not own the session, compiler, target adapter, or provider.
 * Call {@link #dispose()} when the view is no longer used so its session
 * listener is released.</p>
 */
public final class ShaderGraphEditorView implements UiContent {
    private static final ShaderGraphType FLOAT = ShaderGraphType.scalar(ShaderScalarType.F32);
    private static final UiModifier ROOT = UiModifier.none().fill()
            .padding(10.0f).gap(8.0f);
    private static final UiModifier TOOLBAR = UiModifier.none()
            .fillWidth().height(40.0f).gap(6.0f);
    private static final UiModifier BODY = UiModifier.none()
            .fillWidth().weight(1.0f).gap(8.0f);
    private static final UiModifier SCROLL = UiModifier.none().fill().style(ShaderGraphEditorTheme.SCROLL_STYLE);
    private static final UiModifier SIDEBAR_CONTENT = UiModifier.none().fillWidth().padding(0, 0, 8, 0);
    private static final UiModifier GRAPH_BUTTON = UiModifier.none().fillWidth().height(30);
    private static final UiModifier HEADING = UiModifier.none().style(ShaderGraphEditorTheme.HEADING_STYLE);
    private static final UiModifier CANVAS = UiModifier.none()
            .fill().minWidth(320.0f)
            .focusable(true).clip();
    private static final UiModifier INSPECTOR = UiModifier.none()
            .width(310.0f).fillHeight().padding(8.0f);
    private static final UiModifier OUTPUT = UiModifier.none()
            .fillWidth().height(210.0f).gap(8.0f);
    private static final UiModifier OUTPUT_PANEL = UiModifier.none()
            .weight(1.0f).fillHeight().padding(6.0f);
    private static final UiModifier PREVIEW = UiModifier.none()
            .fillWidth().height(190.0f).clip();
    private static final UiTextAreaOptions READ_ONLY =
            UiTextAreaOptions.defaults().readOnly(true).selectAllOnTripleClick(true)
                    .minHeight(145.0f).maxHeight(165.0f);

    private final ShaderGraphEditorSession session;
    private final ShaderGraphEditorCompiler compiler;
    private final ShaderGraphEditorCanvas canvas;
    private final ShaderGraphEditorPreviewSurface preview;
    private ShaderGraphEditorMaterialPreview ownedPreview;
    private final ShaderGraphEditorSessionListener listener;
    private ShaderGraphWireRoute.Pin inspectedPin;
    private final UiState<String> pinRotation = Ui.state("");
    private final UiBooleanState invalidPinRotation = Ui.state(false);
    private final UiState<String> status = Ui.state("");
    private final UiState<String> wgsl = Ui.state("");
    private final UiState<String> diagnostics = Ui.state("");
    private final UiState<String> artifacts = Ui.state("");
    private final Map<ShaderGraphId, UiState<String>> materialFields = new LinkedHashMap<>();
    private final ShaderGraphEditorValueControls valueControls = new ShaderGraphEditorValueControls();
    private final UiState<String> materialError = Ui.state("");
    private ShaderGraph inspectedGraph;
    private final UiIntState viewRevision = Ui.state(0);
    private final UiBooleanState showOutput = Ui.state(false);
    private ShaderGraphEditorCompileSettings settings;
    private ShaderGraphEditorCompileSettings compiledSettings;
    private String previewProblem = "";
    private ShaderGraphEditorCompilation displayedCompilation;
    private String displayedPreviewProblem;
    private long compiledRevision = -1;
    private boolean disposed;

    /** Creates an editor with a built-in 3D surface preview. The graphics context and session are borrowed. */
    public ShaderGraphEditorView(GraphicsContext graphics, ShaderGraphEditorSession session,
            ShaderGraphEditorCompileSettings settings) {
        this(session, new DefaultShaderGraphEditorCompiler(), ShaderGraphEditorPalette.standard(),
                builtInPreview(graphics, session), settings);
        ownedPreview = (ShaderGraphEditorMaterialPreview) preview;
    }

    private static ShaderGraphEditorMaterialPreview builtInPreview(GraphicsContext graphics, ShaderGraphEditorSession session) {
        if (graphics == null || session == null) {
            throw new FdxException("The built-in shader preview requires graphics and an editor session");
        }
        return ShaderGraphEditorPreviewMode.defaultFor(session.document()) == ShaderGraphEditorPreviewMode.MATERIAL_BALL
                ? new ShaderGraphEditorMaterialPreview(graphics, session) : null;
    }

    /** Returns the built-in surface preview, or null for other document types and externally supplied hosts. */
    public ShaderGraphEditorMaterialPreview materialPreview() {
        return ownedPreview;
    }

    /** Call on the graphics thread before drawing the UI to update and draw the built-in model preview. */
    public void renderPreview(float deltaSeconds) {
        if (!disposed && ownedPreview != null && updatePreview()) {
            ownedPreview.render(deltaSeconds);
        }
    }

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
        this.preview = preview;
        this.settings = settings;
        canvas = new ShaderGraphEditorCanvas(session, palette, this::compileNow);
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

    /** Call once per frame before drawing a live preview. Compiles changed semantics or settings once,
     * and returns whether the host should render its preview this frame. */
    public boolean updatePreview() {
        if (preview == null || settings.previewMode() == ShaderGraphEditorPreviewMode.NONE) return false;
        if (compiledRevision != session.semanticRevision() || !settings.equals(compiledSettings)) {
            compileNow();
        }
        return previewProblem.isEmpty();
    }

    public ShaderGraphEditorCompilation compileNow() {
        ShaderGraphEditorCompileRequest request =
                session.beginCompilation(settings);
        ShaderGraphEditorCompilation result = compiler.compile(request);
        boolean live = preview != null && settings.previewMode() != ShaderGraphEditorPreviewMode.NONE;
        String problem = live ? previewProblem(request) : "";
        if (session.completeCompilation(result, live && problem.isEmpty() ? preview : null, settings.previewMode())) {
            previewProblem = problem;
            compiledRevision = result.semanticRevision();
            compiledSettings = request.settings();
        }
        if (!result.success() || !previewProblem.isEmpty()) {
            showOutput.set(true);
        }
        refreshText();
        return result;
    }

    /**
     * Releases the session listener and any built-in preview resources. A supplied preview host is borrowed.
     */
    public void dispose() {
        if (!disposed) {
            session.removeListener(listener);
            if (ownedPreview != null) ownedPreview.dispose();
            disposed = true;
        }
    }

    @Override
    public void build(UiScope ui) {
        build(ui, null);
    }

    /** Builds the editor with optional host controls above the scrollable inspector. */
    public void build(UiScope ui, UiContent inspectorHeader) {
        // Session selection, history and settings also change while the output panel is closed.
        viewRevision.get();
        ui.column(ROOT, root -> {
            root.row(TOOLBAR, this::toolbar);
            root.row(BODY, body -> {
                body.custom("shader-graph-editor-canvas", CANVAS, canvas);
                body.panel(INSPECTOR, panel -> panel.column(UiModifier.none().fill().gap(8), sidebar -> {
                    if (inspectorHeader != null) inspectorHeader.build(sidebar);
                    pinControls(sidebar);
                    valueControls.build(sidebar, session, canvas.selectedValue(), this::compileNow);
                    sidebar.scroll(SCROLL.weight(1), this::inspector);
                }));
            });
            if (showOutput.get()) {
                root.row(OUTPUT, this::output);
            }
        });
    }

    private void toolbar(UiScope ui) {
        if (session.document().graphs().length > 1) {
            ui.button("Graph: " + session.layout().activeGraphId(),
                    this::nextGraph);
        }
        ui.button("Undo", UiModifier.none()
                .enabled(session.canUndo()), () -> session.undo());
        ui.button("Redo", UiModifier.none()
                .enabled(session.canRedo()), () -> session.redo());
        ui.button("Fit", canvas::fitGraph);
        ui.button("Organize", canvas::organizeGraph);
        if (session.activeGraph().kind() == ShaderGraphKind.SURFACE) ui.button("Frame Output", canvas::frameMaterialOutput);
        if (preview != null) {
            ui.button(settings.previewMode() == ShaderGraphEditorPreviewMode.NONE ? "Preview off" : "Preview on",
                    this::nextPreview);
        }
        ui.button(showOutput.get() ? "Hide output" : "Show output", () -> showOutput.set(!showOutput.get()));
        ui.button("Compile", this::compileNow);
    }

    private void pinControls(UiScope ui) {
        var pin = canvas.selectedRoutingPin();
        if (pin != inspectedPin) {
            inspectedPin = pin;
            invalidPinRotation.set(false);
            if (pin != null) pinRotation.set(Float.toString(pin.rotationDegrees()));
        }
        if (pin == null) return;
        ui.column(SIDEBAR_CONTENT.gap(4), group -> {
            group.selectableText("Routing pin", HEADING);
            group.row(Ui.modifier().fillWidth().gap(4), row -> {
                row.selectableText("Rotation (\u00b0)");
                row.textField(Ui.modifier().weight(1).semanticLabel("Pin rotation in degrees"),
                        pinRotation, UiTextInputFilter.FLOAT, this::applyPinRotation);
                row.button("Apply", this::applyPinRotation);
            });
            group.row(Ui.modifier().fillWidth().gap(4), row -> {
                var button = Ui.modifier().weight(1);
                row.button("Left", button, () -> setPinRotation(180));
                row.button("Up", button, () -> setPinRotation(-90));
                row.button("Down", button, () -> setPinRotation(90));
                row.button("Right", button, () -> setPinRotation(0));
            });
            if (invalidPinRotation.get()) group.selectableText("Enter a finite angle in degrees.");
        });
    }

    private void applyPinRotation() {
        try {
            float degrees = Float.parseFloat(pinRotation.get());
            if (Float.isFinite(degrees)) {
                setPinRotation(degrees);
                return;
            }
        } catch (NumberFormatException invalid) {
            // The field can contain an incomplete number while editing.
        }
        invalidPinRotation.set(true);
    }

    private void setPinRotation(float degrees) {
        canvas.rotateSelectedPin(degrees);
        var pin = canvas.selectedRoutingPin();
        if (pin != null) pinRotation.set(Float.toString(pin.rotationDegrees()));
        invalidPinRotation.set(false);
    }

    private void inspector(UiScope ui) {
        ShaderGraphEditorInspectorModel model =
                ShaderGraphEditorInspectorModel.inspect(session, settings);
        ui.column(SIDEBAR_CONTENT.gap(4.0f), column -> {
            var graphs = session.document().graphs();
            if (graphs.length > 1) {
                column.selectableText("Document Graphs", HEADING);
                for (ShaderGraph graph : graphs) {
                    column.button(graph.kind() + " - " + graph.id(), GRAPH_BUTTON, () -> session.selectGraph(graph.id().value()));
                }
            }
            if (preview != null && settings.previewMode() != ShaderGraphEditorPreviewMode.NONE) {
                column.selectableText("Live preview", HEADING);
                column.custom("shader-graph-editor-preview", PREVIEW, preview);
                preview.buildControls(column);
            }
            materialDefaults(column);
            column.selectableText("Inspector", HEADING);
            for (ShaderGraphEditorInspectorSection section
                    : model.sections()) {
                column.selectableText(section.title(),
                        UiModifier.none().fillWidth().height(24.0f));
                for (ShaderGraphEditorInspectorField field
                        : section.fields()) {
                    column.selectableText(field.label() + ": " + field.value(),
                            UiModifier.none().fillWidth()
                                    .minHeight(20.0f));
                }
                column.spacer(UiModifier.none().height(5.0f));
            }
        });
    }

    private void syncMaterialDefaults() {
        ShaderGraph graph = session.activeGraph();
        if (inspectedGraph != graph) {
            Map<ShaderGraphId, UiState<String>> previous = new LinkedHashMap<>(materialFields);
            materialFields.clear();
            for (ShaderGraphParameter parameter : graph.parameters()) {
                if (parameter.kind() != ShaderGraphParameterKind.MATERIAL || !FLOAT.equals(parameter.type())) {
                    continue;
                }
                UiState<String> draft = previous.get(parameter.id());
                if (draft == null || inspectedGraph == null || !graph.id().equals(inspectedGraph.id())
                        || !parameter.equals(inspectedGraph.parameter(parameter.id()))) {
                    float value = parameter.defaultValue() == null ? 0 :
                            Float.intBitsToFloat((int) parameter.defaultValue().bits());
                    draft = Ui.state(Float.toString(value));
                }
                materialFields.put(parameter.id(), draft);
            }
            inspectedGraph = graph;
        }
    }

    private void materialDefaults(UiScope ui) {
        if (materialFields.isEmpty()) return;
        var selected = canvas.selectedValue();
        var editing = selected == null || selected.parameter() == null ? null : selected.parameter().id();
        if (materialFields.size() == 1 && materialFields.containsKey(editing)) return;
        ui.selectableText("Material defaults", HEADING);
        for (var entry : materialFields.entrySet()) {
            var id = entry.getKey();
            if (id.equals(editing)) continue;
            var draft = entry.getValue();
            ui.row(UiModifier.none().fillWidth().gap(5), row -> {
                row.selectableText(id.value(), UiModifier.none().weight(1));
                Runnable apply = () -> applyDefault(id, draft.get());
                row.textField(UiModifier.none().width(64), draft, UiTextInputFilter.FLOAT, apply);
                row.button("Apply", apply);
            });
        }
        if (!materialError.get().isEmpty()) {
            ui.selectableText(materialError.get());
        }
    }

    private void applyDefault(ShaderGraphId id, String text) {
        float value;
        try {
            value = Float.parseFloat(text);
        } catch (NumberFormatException invalid) {
            value = Float.NaN;
        }
        if (!Float.isFinite(value)) {
            materialError.set("Enter a finite number for " + id.value());
            return;
        }
        materialError.set("");
        var literal = ShaderGraphLiteral.f32(value);
        session.editActiveGraph("Edit parameter default", graph -> ShaderGraphSemanticEdits.parameterDefault(graph, id, literal));
        compileNow();
    }

    private void output(UiScope ui) {
        ui.panel(OUTPUT_PANEL, panel -> panel.column(
                UiModifier.none().fill().gap(4.0f), column -> {
                    column.selectableText(status.get());
                    column.selectableText("Diagnostics", HEADING);
                    column.textArea(UiModifier.none().semanticLabel("Diagnostics").fillWidth()
                                    .weight(1.0f),
                            diagnostics, READ_ONLY);
                }));
        ui.panel(OUTPUT_PANEL, panel -> panel.column(
                UiModifier.none().fill().gap(4.0f), column -> {
                    var good = session.lastGoodCompilation();
                    String heading = good != null && good != session.latestCompilation()
                            ? "Canonical WGSL · last successful (r" + good.semanticRevision() + ")" : "Canonical WGSL";
                    column.selectableText(heading, HEADING);
                    column.textArea(UiModifier.none().semanticLabel("Canonical WGSL").fill()
                                    .weight(1.0f),
                            wgsl, READ_ONLY);
                }));
        ui.panel(OUTPUT_PANEL, panel -> panel.column(
                UiModifier.none().fill().gap(4.0f), column -> {
                    column.selectableText("Target Artifacts", HEADING);
                    column.textArea(UiModifier.none().semanticLabel("Target Artifacts").fill()
                                    .weight(1.0f),
                            artifacts, READ_ONLY);
                }));
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

    private void nextPreview() {
        ShaderGraphEditorPreviewMode[] modes =
                ShaderGraphEditorPreviewMode.values();
        int index = settings.previewMode().ordinal();
        for (int count = 0; count < modes.length; count++) {
            index = (index + 1) % modes.length;
            if (modes[index].supports(session.document())) {
                settings = settingsWithPreviewMode(modes[index]);
                if (modes[index] != ShaderGraphEditorPreviewMode.NONE) {
                    compileNow();
                }
                refreshText();
                return;
            }
        }
    }

    private ShaderGraphEditorCompileSettings settingsWithPreviewMode(ShaderGraphEditorPreviewMode mode) {
        return ShaderGraphEditorCompileSettings.builder()
                .profile(settings.profile())
                .capabilities(settings.capabilities())
                .output(settings.target(), settings.format(), settings.environment())
                .compiler(settings.compiler())
                .verifier(settings.verifier())
                .previewMode(mode)
                .build();
    }

    private void refreshText() {
        syncMaterialDefaults();
        viewRevision.set(viewRevision.get() + 1);
        refreshCompilationText();
        String selected = session.selectedNodeId().isEmpty()
                ? "none" : session.selectedNodeId();
        status.set("Graph " + session.layout().activeGraphId()
                + " - selected " + selected
                + " - semantic r" + session.semanticRevision()
                + " - layout r" + session.layoutRevision());
    }

    private void refreshCompilationText() {
        var compilation = session.latestCompilation();
        if (compilation == displayedCompilation && previewProblem.equals(displayedPreviewProblem)) return;
        displayedCompilation = compilation;
        displayedPreviewProblem = previewProblem;
        var good = session.lastGoodCompilation();
        wgsl.set(good == null ? "" : good.canonicalWgsl());
        diagnostics.set(compilation == null ? "Ready to compile." : diagnosticText(compilation) + previewProblem);
        artifacts.set(compilation == null ? "" : artifactText(compilation));
    }

    private static String previewProblem(ShaderGraphEditorCompileRequest request) {
        StringBuilder text = new StringBuilder();
        for (ShaderGraphDiagnostic diagnostic : ShaderGraphEditorCapabilityValidator.validate(
                request.document(), request.settings())) {
            text.append("\nPreview unavailable: ").append(diagnostic.message());
        }
        return text.toString();
    }

    private String diagnosticText(
            ShaderGraphEditorCompilation compilation) {
        var messages = compilation.diagnostics();
        if (messages.length == 0) {
            return compilation.success()
                    ? "Compilation succeeded." : "Compilation failed.";
        }
        StringBuilder text = new StringBuilder();
        for (var diagnostic : messages) {
            if (diagnostic.nodeId() != null) {
                text.append(diagnostic.nodeId());
                if (diagnostic.portId() != null) {
                    text.append(" · ").append(diagnosticPortName(diagnostic));
                }
                text.append(" — ");
            }
            text.append(diagnostic.message()).append(" [").append(diagnostic.severity())
                    .append(' ').append(diagnostic.code()).append("]\n");
        }
        return text.toString();
    }

    private String diagnosticPortName(ShaderGraphDiagnostic diagnostic) {
        var graph = session.document().graph(diagnostic.graphId().value());
        var node = graph == null ? null : graph.node(diagnostic.nodeId());
        if (node != null) {
            var labels = ShaderGraphEditorPortLabels.of(node, session.document());
            for (var socket : labels.inputs()) {
                if (socket.port().id().equals(diagnostic.portId())) return socket.name();
            }
            for (var socket : labels.outputs()) {
                if (socket.port().id().equals(diagnostic.portId())) return socket.name();
            }
        }
        return diagnostic.portId().value();
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

}
