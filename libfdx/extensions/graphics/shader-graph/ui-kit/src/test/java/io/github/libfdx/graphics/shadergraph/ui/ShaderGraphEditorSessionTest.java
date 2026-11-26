package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialInstance;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiToolkit;
import java.util.Arrays;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorSessionTest {
    @Test
    void graphCanBeCompiledWithoutProfileOrTargetButtons() {
        var doc = ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("toolbar"));
        var session = new ShaderGraphEditorSession(doc);
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true);
        try {
            root.resize(1280, 720);
            root.setContent(editor);
            root.update(0);
            var settings = editor.settings();
            assertNull(findNode(root.rootNode(), node -> node.type() == UiNodeType.BUTTON
                    && (settings.profile().name().equals(node.text()) || settings.target().value().equals(node.text()))));
            var compile = findNode(root.rootNode(), node -> node.type() == UiNodeType.BUTTON && "Compile".equals(node.text()));
            assertNotNull(compile);
            compile.activate();
            assertTrue(session.latestCompilation().success());
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void canvasUsesTheFormerPaletteSpaceAndEffectControlsStayAboveTheInspector() {
        var session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("palette")));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        var selected = Ui.state(0);
        try {
            root.resize(1280, 480);
            root.setContent(ui -> editor.build(ui, sidebar -> sidebar.radioButton("Second effect", selected, 1)));
            root.update(0);
            var effect = findNode(root.rootNode(), node -> "Second effect".equals(node.text()));
            var inspector = findNode(root.rootNode(), node -> "Inspector".equals(node.text()));
            var canvas = findNode(root.rootNode(), node -> "shader-graph-editor-canvas".equals(node.key()));
            assertNotNull(effect);
            assertNotNull(inspector);
            assertNotNull(canvas);
            assertNull(findNode(root.rootNode(), node -> "Find a node".equals(node.text())));
            var undo = findNode(root.rootNode(), node -> "Undo".equals(node.text()));
            assertEquals(undo.bounds().x(), canvas.bounds().x(), 1);
            assertTrue(effect.bounds().x() >= canvas.bounds().right());
            int x = root.displayX(effect.bounds().x() + 8);
            int y = root.displayY(effect.bounds().y() + 8);
            input.dispatchScrolled(x, root.displayY(inspector.bounds().y() + 8), 0, 5);
            root.update(0);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            assertEquals(1, selected.get());
            session.viewport(-3000, -3000, 1);
            x = root.displayX(canvas.bounds().x() + 24);
            y = root.displayY(canvas.bounds().y() + 100);
            int nodes = session.activeGraph().nodes().length;
            input.dispatchPointerDown(MouseButton.RIGHT, x, y);
            input.dispatchPointerUp(MouseButton.RIGHT, x, y);
            input.dispatchTextInput("cosine");
            input.dispatchKeyDown(Key.ENTER);
            input.dispatchKeyUp(Key.ENTER);
            assertEquals(nodes + 1, session.activeGraph().nodes().length);
            assertEquals("math.cosine", session.activeGraph().node(ShaderGraphId.of(session.selectedNodeId())).definitionId().value());
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void canvasReceivesScrollBesideInspectorAfterResize() {
        var graph = ShaderGraphEditorFixtures.editableFunction("resize");
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input).uiScale(1.5f);
        try {
            root.setContent(editor);
            for (int width : new int[] {1280, 2560, 1280}) {
                root.resize(width, width == 2560 ? 1440 : 720);
                root.update(0);
                // The inspector occupies 310 logical units at the right of the editor.
                int x = root.displayX(root.rootNode().bounds().right() - 340);
                float zoom = session.layout().activeGraph().zoom();
                input.dispatchScrolled(x, root.displayY(200), 0, -1);
                assertTrue(session.layout().activeGraph().zoom() > zoom, "Canvas zoom at width " + width);
            }
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void applyingMaterialDefaultFromInspectorCompilesAndSupportsUndo() {
        var graph = new ShaderGraphBuilder("mask", ShaderGraphKind.SURFACE);
        graph.parameter(ShaderGraphParameter.of("threshold", ShaderGraphEditorFixtures.F32,
                ShaderGraphParameterKind.MATERIAL, ShaderGraphLiteral.f32(0.5f)));
        graph.output("alpha", "alpha", graph.parameter("threshold_node", "threshold"));
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph.build()));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        try {
            root.resize(1280, 720);
            root.setContent(editor);
            root.update(0);
            UiNode field = findFloatField(root.rootNode());
            assertNotNull(field, "threshold text field");
            var bounds = field.bounds();
            int x = root.displayX(bounds.x() + 8);
            int y = root.displayY(bounds.y() + 8);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            input.dispatchKeyDown(Key.CONTROL_LEFT);
            input.dispatchKeyDown(Key.A);
            input.dispatchKeyUp(Key.A);
            input.dispatchKeyUp(Key.CONTROL_LEFT);
            input.dispatchTextInput("0.8");
            input.dispatchKeyDown(Key.ENTER);
            input.dispatchKeyUp(Key.ENTER);
            var threshold = ShaderGraphId.of("threshold");
            assertEquals(ShaderGraphLiteral.f32(0.8f), session.activeGraph().parameter(threshold).defaultValue());
            assertTrue(session.latestCompilation().success());
            assertTrue(session.undo());
            assertEquals(ShaderGraphLiteral.f32(0.5f), session.activeGraph().parameter(threshold).defaultValue());
            root.update(0);
            assertNotNull(findFloatField(root.rootNode()));
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void exposesAConstantAndEditsTheDefaultUsedByRuntimeMaterials() {
        var graph = new ShaderGraphBuilder("value_sources", ShaderGraphKind.SURFACE);
        var source = graph.constant("opacity", ShaderGraphLiteral.f32(0.25f));
        graph.parameter(ShaderGraphParameter.of("existing", ShaderGraphEditorFixtures.F32,
                ShaderGraphParameterKind.MATERIAL, ShaderGraphLiteral.f32(1)));
        graph.output("alpha", "alpha", graph.add("sum", source, source));
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph.build()));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        try {
            root.resize(1280, 800);
            root.setContent(editor);
            session.viewport(0, 0, 0.5f);
            session.selectNode("opacity");
            root.update(0);
            var edges = session.activeGraph().edges();
            assertNotNull(findNode(root.rootNode(), n -> "0.25".equals(n.text())));
            int history = session.undoCount();
            replaceField(input, root, "Material parameter name", "existing");
            clickNode(input, root, findNode(root.rootNode(), n -> "Expose as material parameter".equals(n.text())));
            root.update(0);
            assertEquals(history, session.undoCount());
            assertNotNull(findNode(root.rootNode(), n -> "Parameter already exists: existing".equals(n.text())));
            replaceField(input, root, "Material parameter name", "fade");
            clickNode(input, root, findNode(root.rootNode(), n -> "Expose as material parameter".equals(n.text())));
            root.update(0);
            assertTrue(session.latestCompilation().success());
            assertEquals(history + 1, session.undoCount());
            assertTrue(Arrays.equals(edges, session.activeGraph().edges()));
            assertNotNull(findNode(root.rootNode(), n -> "fade = 0.25".equals(n.text())));
            replaceField(input, root, "Default", "0.4");
            clickNode(input, root, findNode(root.rootNode(), n -> "Apply default".equals(n.text())));
            root.update(0);
            assertNotNull(findNode(root.rootNode(), n -> "fade = 0.4".equals(n.text())));
            var saved = ShaderGraphEditorPersistence.write(session.document(), session.layout());
            var loaded = ShaderGraphEditorPersistence.read(saved).document().graph();
            var definition = ShaderGraphMaterialDefinition.compile(loaded, null, ShaderGraphCompileOptions.builder().build());
            var first = new ShaderGraphMaterialInstance(definition);
            var second = new ShaderGraphMaterialInstance(definition);
            int fade = definition.parameterIndex("fade");
            first.set("fade", ShaderGraphLiteral.f32(0.9f));
            assertEquals(ShaderGraphLiteral.f32(0.9f), first.value(fade));
            assertEquals(ShaderGraphLiteral.f32(0.4f), second.value(fade));
            assertTrue(session.undo());
            assertTrue(session.undo());
            assertNull(session.activeGraph().parameter(ShaderGraphId.of("fade")));
            assertEquals(graph.build(), session.activeGraph());
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void replacingAStageReadWithAConstantOnlyChangesThatNode() {
        var graph = new ShaderGraphBuilder("stage", ShaderGraphKind.SURFACE);
        graph.parameter(ShaderGraphParameter.semantic("mesh_alpha", ShaderGraphEditorFixtures.F32,
                ShaderGraphParameterKind.STAGE_INPUT, null, "alpha"));
        graph.output("alpha", "alpha", graph.add("sum", graph.parameter("local", "mesh_alpha"),
                graph.parameter("from_mesh", "mesh_alpha")));
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph.build()));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        try {
            root.resize(1280, 800);
            root.setContent(editor);
            session.selectNode("local");
            root.update(0);
            assertNotNull(findNode(root.rootNode(), n -> "Supplied by the shader stage.".equals(n.text())));
            replaceField(input, root, "Constant override", "0.6");
            clickNode(input, root, findNode(root.rootNode(), n -> "Replace with constant".equals(n.text())));
            assertTrue(session.latestCompilation().success());
            assertEquals(ShaderGraphLiteral.f32(0.6f),
                    session.activeGraph().node(ShaderGraphId.of("local")).property("literal").literalValue());
            assertEquals("mesh_alpha", session.activeGraph().node(ShaderGraphId.of("from_mesh")).property("parameter").stringValue());
            assertTrue(session.undo());
            assertEquals(graph.build(), session.activeGraph());
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    private static void replaceField(DefaultInput input, UiRoot root, String label, String text) {
        var field = findNode(root.rootNode(), n -> n.type() == UiNodeType.TEXT_FIELD && label.equals(n.modifier().semanticLabel()));
        clickNode(input, root, field);
        shortcut(input, Key.A);
        input.dispatchTextInput(text);
    }

    @Test
    void editPannerVelocityInInspector() {
        var graph = new ShaderGraphBuilder("flow", ShaderGraphKind.SURFACE);
        var velocity = graph.constant("velocity", ShaderGraphLiteral.composite(
                ShaderGraphType.vector(ShaderScalarType.F32, 2),
                ShaderGraphLiteral.f32(0.5f), ShaderGraphLiteral.f32(-1)));
        var time = graph.time("clock");
        var noise = graph.noise2D("field", graph.panner("pan", velocity, velocity, time));
        graph.output("mask", "alpha", noise);
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph.build()));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        try {
            root.resize(1440, 1500);
            root.setContent(editor);
            session.selectNode("velocity");
            root.update(0);
            var field = findFloatField(root.rootNode());
            assertNotNull(field);
            clickNode(input, root, field);
            input.dispatchKeyDown(Key.CONTROL_LEFT);
            input.dispatchKeyDown(Key.A);
            input.dispatchKeyUp(Key.A);
            input.dispatchKeyUp(Key.CONTROL_LEFT);
            input.dispatchTextInput("0.75");
            input.dispatchKeyDown(Key.ENTER);
            input.dispatchKeyUp(Key.ENTER);
            var literal = session.activeGraph().node(ShaderGraphId.of("velocity")).property("literal").literalValue();
            assertEquals(ShaderGraphLiteral.f32(0.75f), literal.element(0));
            assertEquals(ShaderGraphLiteral.f32(-1), literal.element(1));
            assertTrue(session.latestCompilation().success());
            assertTrue(session.undo());
            assertEquals(ShaderGraphLiteral.f32(0.5f), session.activeGraph().node(ShaderGraphId.of("velocity"))
                    .property("literal").literalValue().element(0));
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void inspectorRotatesOnlyTheSelectedPinAndClearsOnDeselect() {
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("pins")));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        var wire = new ShaderGraphWireRoute.Wire(ShaderEndpoint.of("a", "value"), ShaderEndpoint.of("sum", "in000000"), null);
        try {
            session.viewport(0, 0, 1);
            session.moveNode("a", 20, 20);
            session.moveNode("sum", 520, 20);
            session.moveNode("b", 0, 420);
            session.wireRoute("Two pins", wire, ShaderGraphWireRoute.DEFAULT.insert(0, 310, 180).insert(1, 400, 160));
            root.resize(1280, 720);
            root.setContent(editor);
            root.update(0);
            var surface = findNode(root.rootNode(), n -> "shader-graph-editor-canvas".equals(n.key())).bounds();
            Predicate<UiNode> rotation = n -> "Pin rotation in degrees".equals(n.modifier().semanticLabel());
            assertNull(findNode(root.rootNode(), rotation));
            int x = root.displayX(surface.x() + 400), y = root.displayY(surface.y() + 160);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            root.update(0);
            clickNode(input, root, findNode(root.rootNode(), n -> "Down".equals(n.text())));
            var pins = session.layout().activeGraph().routes().get(wire).pins();
            assertEquals(0, pins.get(0).rotationDegrees());
            assertEquals(90, pins.get(1).rotationDegrees());
            root.update(0);
            clickNode(input, root, findNode(root.rootNode(), rotation));
            input.dispatchKeyDown(Key.CONTROL_LEFT);
            input.dispatchKeyDown(Key.A);
            input.dispatchKeyUp(Key.A);
            input.dispatchKeyUp(Key.CONTROL_LEFT);
            input.dispatchTextInput("-42.5");
            input.dispatchKeyDown(Key.ENTER);
            input.dispatchKeyUp(Key.ENTER);
            assertEquals(-42.5f, session.layout().activeGraph().routes().get(wire).pins().get(1).rotationDegrees());
            assertTrue(session.undo());
            assertEquals(90, editor.canvas().selectedRoutingPin().rotationDegrees());
            y = root.displayY(surface.y() + 300);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            root.update(0);
            assertNull(findNode(root.rootNode(), rotation));
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void incompleteMaterialOutputKeepsWorkingSourceAndMarksEachMissingInput() {
        var builder = new ShaderGraphBuilder("wiring", ShaderGraphKind.SURFACE);
        builder.output("alpha", "alpha", builder.constant("opacity", ShaderGraphLiteral.f32(0.4f)));
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(builder.build()));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true);
        try {
            var working = editor.compileNow();
            assertTrue(working.success());
            var add = Arrays.stream(ShaderGraphEditorPalette.standard().templates())
                    .filter(t -> t.label().equals("Add")).findFirst().orElseThrow().create("sum");
            session.addNode(add, 200, 150);
            session.viewport(0, 0, 1);
            session.bindMaterialOutput(MaterialOutputChannel.ALPHA, ShaderEndpoint.of("sum", "value"));
            root.resize(1400, 900);
            root.setContent(editor);
            assertFalse(editor.compileNow().success());
            root.update(0);
            var source = findNode(root.rootNode(), n -> n.type() == UiNodeType.TEXT_AREA
                    && "Canonical WGSL".equals(n.modifier().semanticLabel()));
            assertNotNull(source);
            assertEquals(working.canonicalWgsl(), source.value());
            assertNotNull(findNode(root.rootNode(), n -> n.text() != null && n.text().contains("last successful")));
            var messages = findNode(root.rootNode(), n -> n.type() == UiNodeType.TEXT_AREA
                    && "Diagnostics".equals(n.modifier().semanticLabel()));
            assertTrue(messages.value().toString().contains("sum · A"));
            assertTrue(messages.value().toString().contains("sum · B"));
            assertTrue(inputMarkedMissing(root, "A"));
            assertTrue(inputMarkedMissing(root, "B"));

            var value = ShaderEndpoint.of("opacity", "value");
            session.connect(value, ShaderEndpoint.of("sum", add.inputs()[0].id().value()));
            root.update(0);
            assertNull(session.latestCompilation());
            assertEquals(working.canonicalWgsl(), source.value());
            assertFalse(editor.compileNow().success());
            root.update(0);
            assertEquals(working.canonicalWgsl(), source.value());
            assertFalse(inputMarkedMissing(root, "A"));
            assertTrue(inputMarkedMissing(root, "B"));
            assertFalse(messages.value().toString().contains("sum · A"));

            session.connect(value, ShaderEndpoint.of("sum", add.inputs()[1].id().value()));
            var fixed = editor.compileNow();
            root.update(0);
            assertTrue(fixed.success());
            assertNotEquals(working.canonicalWgsl(), fixed.canonicalWgsl());
            assertEquals(fixed.canonicalWgsl(), source.value());
            assertFalse(inputMarkedMissing(root, "A"));
            assertFalse(inputMarkedMissing(root, "B"));
            assertEquals("Compilation succeeded.", messages.value());
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    private static boolean inputMarkedMissing(UiRoot root, String name) {
        var label = findNode(root.rootNode(), n -> name.equals(n.text()));
        assertNotNull(label);
        var card = findNode(root.rootNode(), n -> n.children().contains(label, true));
        assertNotNull(card);
        var children = card.children();
        var detail = children.get(children.indexOf(label, true) + 1);
        return "Missing · Float".equals(detail.text());
    }

    @Test
    void compiledWgslSupportsMouseSelectionAndCopyAcrossFrames() {
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("copy")));
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input).uiScale(1.5f);
        try {
            root.resize(1600, 900);
            root.setContent(editor);
            root.update(0);
            clickNode(input, root, findNode(root.rootNode(), n -> "Compile".equals(n.text())));
            clickNode(input, root, findNode(root.rootNode(), n -> "Show output".equals(n.text())));
            root.update(0);
            String source = session.latestCompilation().canonicalWgsl();
            assertTrue(source.length() > 8);
            var area = findNode(root.rootNode(), n -> n.type() == UiNodeType.TEXT_AREA && source.equals(n.value()));
            assertNotNull(area);
            var start = root.textCaretBounds(area, 1);
            int x = root.displayX(start.x()), y = root.displayY(start.y() + start.height() / 2);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            root.update(1f / 60);
            var end = root.textCaretBounds(area, 8);
            x = root.displayX(end.x());
            y = root.displayY(end.y() + end.height() / 2);
            input.dispatchPointerMoved(x, y);
            root.update(1f / 60);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            shortcut(input, Key.C);
            assertEquals(source.substring(1, 8), input.clipboard().getText());
            // Place the caret, then extend the selection into a different line.
            var first = root.textCaretBounds(area, 2);
            x = root.displayX(first.x());
            y = root.displayY(first.y() + first.height() / 2);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            int nextLine = source.indexOf('\n') + 2;
            assertTrue(nextLine > 2);
            var last = root.textCaretBounds(area, nextLine);
            x = root.displayX(last.x());
            y = root.displayY(last.y() + last.height() / 2);
            input.dispatchKeyDown(Key.SHIFT_LEFT);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
            input.dispatchKeyUp(Key.SHIFT_LEFT);
            shortcut(input, Key.C);
            assertEquals(source.substring(2, nextLine), input.clipboard().getText());
            shortcut(input, Key.A);
            shortcut(input, Key.C);
            assertEquals(source, input.clipboard().getText());
            input.dispatchTextInput("replacement");
            input.dispatchKeyDown(Key.DELETE);
            input.dispatchKeyUp(Key.DELETE);
            root.requestCompose();
            root.update(0);
            assertEquals(source, area.value());
            for (String text : new String[]{"Canonical WGSL", "Diagnostics", "Target Artifacts",
                    "Semantic hash: " + session.document().semanticHash()}) {
                clickNode(input, root, findNode(root.rootNode(), n -> text.equals(n.text())));
                shortcut(input, Key.A);
                shortcut(input, Key.C);
                assertEquals(text, input.clipboard().getText());
            }
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    @Test
    void doubleClickSelectsAWordAndTripleClickSelectsTheWholeOutputWidget() {
        var document = ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("copy"));
        var session = new ShaderGraphEditorSession(document);
        var editor = new ShaderGraphEditorView(session, new DefaultShaderGraphEditorCompiler());
        var input = new DefaultInput();
        var root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input);
        try {
            String source = editor.compileNow().canonicalWgsl();
            assertTrue(source.contains("\n"));
            root.resize(1280, 720);
            root.setContent(editor);
            root.update(0);
            clickNode(input, root, findNode(root.rootNode(), n -> "Show output".equals(n.text())));
            root.update(0);
            var heading = findNode(root.rootNode(), n -> "Canonical WGSL".equals(n.text()));
            var area = findNode(root.rootNode(), n -> n.type() == UiNodeType.TEXT_AREA && source.equals(n.value()));
            clickAndCopy(input, root, heading, 2, 2);
            assertEquals("Canonical", input.clipboard().getText());
            clickAndCopy(input, root, heading, 11, 2);
            assertEquals("WGSL", input.clipboard().getText());
            clickAndCopy(input, root, heading, 11, 3);
            assertEquals("Canonical WGSL", input.clipboard().getText());
            int word = source.indexOf("fn ");
            assertTrue(word >= 0);
            clickAndCopy(input, root, area, word + 1, 2);
            assertEquals("fn", input.clipboard().getText());
            clickAndCopy(input, root, area, word + 1, 3);
            assertEquals(source, input.clipboard().getText());
            assertEquals(0f, area.scrollState().y());
        } finally {
            root.dispose();
            editor.dispose();
        }
    }

    private static void clickAndCopy(DefaultInput input, UiRoot root, UiNode node, int offset, int clicks) {
        assertNotNull(node);
        var caret = root.textCaretBounds(node, offset);
        int x = root.displayX(caret.x() + 1);
        int y = root.displayY(caret.y() + caret.height() / 2);
        for (int i = 0; i < clicks; i++) {
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
        }
        input.clipboard().setText("");
        shortcut(input, Key.C);
    }

    private static void shortcut(DefaultInput input, Key key) {
        input.dispatchKeyDown(Key.CONTROL_LEFT);
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
        input.dispatchKeyUp(Key.CONTROL_LEFT);
    }

    private static void clickNode(DefaultInput input, UiRoot root, UiNode node) {
        assertNotNull(node);
        var bounds = node.bounds();
        int x = root.displayX(bounds.x() + bounds.width() / 2);
        int y = root.displayY(bounds.y() + bounds.height() / 2);
        input.dispatchPointerDown(MouseButton.LEFT, x, y);
        input.dispatchPointerUp(MouseButton.LEFT, x, y);
    }

    private static UiNode findFloatField(UiNode node) {
        return findNode(node, field -> field.type() == UiNodeType.TEXT_FIELD && "0.5".equals(field.value()));
    }

    private static UiNode findNode(UiNode node, Predicate<UiNode> matches) {
        if (matches.test(node)) {
            return node;
        }
        for (int i = 0; i < node.children().size(); i++) {
            UiNode found = findNode(node.children().get(i), matches);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Test
    void navigatingEmbeddedGraphsKeepsTheirSavedViewsWithoutAdvancingRevisions() {
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.program("stages")));
        session.selectGraph("stages_vertex");
        session.viewport(40, -25, 1.5f);
        session.selectGraph("stages_fragment");
        session.viewport(-60, 100, 0.75f);
        session.selectGraph("stages_vertex");
        assertEquals(0, session.layoutRevision());
        assertEquals(0, session.semanticRevision());
        var saved = ShaderGraphEditorPersistence.write(session.document(), session.layout());
        var loaded = ShaderGraphEditorPersistence.read(saved).layout();
        assertEquals(session.layout(), loaded);
        assertEquals(1.5f, loaded.activeGraph().zoom());
        assertEquals(0.75f, loaded.graph("stages_fragment").zoom());
    }

    @Test
    void separatesSemanticAndLayoutHistoryAndCoalescesDrag() {
        ShaderGraph graph =
                ShaderGraphEditorFixtures.editableFunction("history");
        ShaderGraphEditorSession session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(graph));
        String hash = session.document().semanticHash();
        ShaderGraphEditorNode original = node(session, "a");
        assertTrue(node(session, "sum").x() > original.x());
        assertTrue(node(session, "sum").x() > node(session, "b").x());

        session.beginTransaction("Move node");
        session.moveNode("a", 80.0f, 90.0f);
        session.moveNode("a", 130.0f, 150.0f);
        assertEquals(0, session.layoutRevision());
        assertTrue(session.commitTransaction());

        assertEquals(hash, session.document().semanticHash());
        assertEquals(0, session.semanticRevision());
        assertEquals(1, session.layoutRevision());
        assertEquals(1, session.undoCount());
        assertEquals(130.0f, node(session, "a").x());

        assertTrue(session.undo());
        assertEquals(original, node(session, "a"));
        assertEquals(2, session.layoutRevision());
        assertEquals(hash, session.document().semanticHash());
        assertTrue(session.redo());
        assertEquals(3, session.layoutRevision());
        assertEquals(130.0f, node(session, "a").x());

        session.beginTransaction("Canceled move");
        session.moveNode("a", 60, 70);
        assertTrue(session.cancelTransaction());
        assertEquals(130.0f, node(session, "a").x());
        assertEquals(3, session.layoutRevision());

        session.beginTransaction("Return to start");
        session.moveNode("a", 60, 70);
        session.moveNode("a", 130, 150);
        assertFalse(session.commitTransaction());
        assertEquals(3, session.layoutRevision());

        session.setNodeProperty("a", ShaderNodeProperty.literal(
                "literal", ShaderGraphLiteral.f32(9.0f)));
        assertNotEquals(hash, session.document().semanticHash());
        assertTrue(session.semanticRevision() > 0);
        assertTrue(session.undo());
        assertEquals(hash, session.document().semanticHash());
    }

    @Test
    void replacesConnectionsAtomicallyAndRestoresThemOnUndo() {
        ShaderGraphEditorSession session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures
                                .editableFunction("connections")));
        ShaderEndpoint target =
                ShaderEndpoint.of("sum", "in000000");
        session.connect(ShaderEndpoint.of("b", "value"), target);

        ShaderEdge replacement = edgeTo(session.activeGraph(), target);
        assertEquals(ShaderGraphId.of("b"),
                replacement.source().nodeId());
        assertEquals(2, session.activeGraph().edges().length);

        assertTrue(session.undo());
        assertEquals(ShaderGraphId.of("a"),
                edgeTo(session.activeGraph(), target)
                        .source().nodeId());
        assertTrue(session.redo());
        assertEquals(ShaderGraphId.of("b"),
                edgeTo(session.activeGraph(), target)
                        .source().nodeId());
    }

    @Test
    void supportsRenameDuplicateRemoveAndCanvasCoordinates() {
        ShaderGraphEditorSession session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(
                        ShaderGraphEditorFixtures
                                .editableFunction("nodes")));
        session.selectNode("a");
        session.renameNode("a", "renamed");
        assertEquals("renamed", session.selectedNodeId());
        assertTrue(session.activeGraph().node(
                ShaderGraphId.of("renamed")) != null);
        assertTrue(Arrays.stream(session.activeGraph().edges())
                .anyMatch(edge -> edge.source().nodeId()
                        .equals(ShaderGraphId.of("renamed"))));

        session.duplicateNode("renamed", "copy", 25.0f, 35.0f);
        assertTrue(session.activeGraph().node(
                ShaderGraphId.of("copy")) != null);
        session.removeNodes("copy");
        assertTrue(session.activeGraph().node(
                ShaderGraphId.of("copy")) == null);
        assertTrue(session.undo());
        assertTrue(session.activeGraph().node(
                ShaderGraphId.of("copy")) != null);

        ShaderGraphEditorCanvas canvas =
                new ShaderGraphEditorCanvas(session);
        ShaderGraphEditorNode renamed = node(session, "renamed");
        float localX = canvas.localX(renamed.x() + 4.0f);
        float localY = canvas.localY(renamed.y() + 4.0f);
        assertEquals("renamed", canvas.nodeAt(localX, localY));
        assertEquals(renamed.x() + 4.0f, canvas.graphX(localX),
                0.0001f);
        assertEquals(renamed.y() + 4.0f, canvas.graphY(localY),
                0.0001f);
    }

    private static ShaderGraphEditorNode node(
            ShaderGraphEditorSession session, String id) {
        return Arrays.stream(session.layout().activeGraph().nodes())
                .filter(value -> value.nodeId().value().equals(id))
                .findFirst().orElseThrow();
    }

    private static ShaderEdge edgeTo(ShaderGraph graph,
            ShaderEndpoint target) {
        return Arrays.stream(graph.edges())
                .filter(edge -> edge.target().equals(target))
                .findFirst().orElseThrow();
    }
}
