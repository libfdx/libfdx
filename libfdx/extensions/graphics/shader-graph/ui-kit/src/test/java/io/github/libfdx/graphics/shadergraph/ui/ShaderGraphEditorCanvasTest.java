package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiPath;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiToolkit;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ShaderGraphEditorCanvasTest {
    @Test
    void doubleClickEditsTheValueOnTheCardAndEscapeDiscardsTheDraft() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(35, 50, 0.75f);
            var original = session.activeGraph();
            var readout = view.drawing().labels.get("1.0");
            float x = readout.x() + 4, y = readout.y() + readout.height() / 2;
            int history = session.undoCount();
            long layout = session.layoutRevision();
            view.click(x, y);
            assertEquals("a", session.selectedNodeId());
            assertNull(view.field("Node value"));
            view.click(x, y);
            assertTrue(view.field("Node value").focused());
            view.input.dispatchTextInput("3.5");
            assertEquals("3.5", view.field("Node value").value());
            assertEquals(original, session.activeGraph());
            view.key(Key.ENTER);
            assertNull(view.field("Node value"));
            assertEquals("3.5", view.canvas.selectedValue().readout());
            assertEquals(history + 1, session.undoCount());
            assertEquals(layout, session.layoutRevision());

            view.click(x, y);
            view.click(x, y);
            view.input.dispatchTextInput("-");
            view.key(Key.ENTER);
            assertNotNull(view.field("Node value"));
            assertTrue(view.drawing().labels.containsKey("Enter a valid Float value."));
            view.key(Key.ESCAPE);
            assertNull(view.field("Node value"));
            assertEquals(history + 1, session.undoCount());
            view.click(x, y);
            view.click(x, y);
            view.input.dispatchTextInput("6");
            view.click(800, 440);
            assertNull(view.field("Node value"));
            assertEquals(history + 1, session.undoCount());
            assertTrue(session.undo());
            assertEquals(original, session.activeGraph());

            view.drag(x, y, x + 30, y + 10);
            view.click(x + 30, y + 10);
            assertNull(view.field("Node value"));
        }
    }

    @Test
    void inlineVectorEditsUpdateTheSharedMaterialDefaultAndStageInputsKeepTheirSource() {
        var vector = ShaderGraphType.vector(ShaderScalarType.F32, 2);
        var graph = new ShaderGraphBuilder("values", ShaderGraphKind.SURFACE);
        graph.parameter(ShaderGraphParameter.of("speed", vector, ShaderGraphParameterKind.MATERIAL, ShaderGraphLiteral.zero(vector)));
        graph.parameter(ShaderGraphParameter.semantic("coord", vector, ShaderGraphParameterKind.STAGE_INPUT, null, "uv"));
        graph.parameter("velocity", "speed");
        graph.parameter("uv", "coord");
        try (var view = new WheelFixture(graph.build())) {
            view.session.viewport(0, 0, 1);
            view.session.moveNode("velocity", 40, 40);
            view.session.moveNode("uv", 320, 40);
            view.session.moveMaterialOutput(700, 40);
            view.doubleClickReadout("speed = (0.0, 0.0)");
            assertTrue(view.field("Node value X").focused());
            view.input.dispatchTextInput("2");
            view.key(Key.TAB);
            assertTrue(view.field("Node value Y").focused());
            view.shortcut(Key.A);
            view.input.dispatchTextInput("-1");
            view.key(Key.ENTER);
            assertEquals("speed = (2.0, -1.0)", view.canvas.selectedValue().readout());
            view.doubleClickReadout("coord");
            assertNull(view.field("Node value X"));
            assertEquals("uv", view.session.selectedNodeId());
            assertTrue(view.canvas.selectedValue().externallySupplied());
        }
    }

    @Test
    void booleanConstantUsesACheckboxAndDiscardsTheDraftWhenSelectionChanges() {
        var graph = new ShaderGraphBuilder("switches", ShaderGraphKind.FUNCTION);
        graph.constant("flag", ShaderGraphLiteral.bool(false));
        try (var view = new WheelFixture(graph.build())) {
            view.session.viewport(30, 30, 1);
            view.doubleClickReadout("false");
            view.key(Key.SPACE);
            view.key(Key.ENTER);
            assertEquals("true", view.canvas.selectedValue().readout());
            view.doubleClickReadout("true");
            view.key(Key.SPACE);
            view.session.selectNode("");
            assertFalse(view.drawing().labels.containsKey("Apply"));
            view.session.selectNode("flag");
            assertEquals("true", view.canvas.selectedValue().readout());
        }
    }

    @Test
    void separatesTheNodeTypeFromItsTitleAndFirstSocketAtEachZoom() {
        try (var view = new WheelFixture()) {
            for (float zoom : new float[] {0.6f, 1.0f, 2.5f}) {
                view.session.viewport(0, 0, zoom);
                var draw = view.drawing();
                var type = draw.labels.get("math.add");
                var result = draw.labels.get("Sum");
                assertTrue(draw.labels.containsKey("Float"));
                assertTrue(result.x() > draw.labels.get("A").right(), "Input and output labels overlap");
                assertTrue(draw.labels.get("A").y() - type.bottom() >= 4 * zoom,
                        "Gap above the first socket at zoom " + zoom);
                assertTrue(type.y() > draw.labels.get("sum").bottom(),
                        "Gap between title and type at zoom " + zoom);
            }
        }
    }

    @Test
    void fractionalWheelEventsKeepTheCursorAnchorAndReverseWithoutDrift() {
        try (var view = new WheelFixture()) {
            view.session.viewport(-160, 80, 1);
            float graphX = view.canvas.graphX(260);
            float graphY = view.canvas.graphY(180);
            long revision = view.session.layoutRevision();
            view.input.dispatchScrolled(view.displayX(260), view.displayY(180), 1, 0);
            assertEquals(1, view.zoom());
            view.wheel(260, 180, -0.25f);
            assertTrue(view.zoom() > 1 && view.zoom() < 1.1f);
            for (int i = 0; i < 3; i++) {
                view.wheel(260, 180, -0.25f);
            }
            assertEquals(1.1f, view.zoom(), 0.00001f);
            assertEquals(260, view.canvas.localX(graphX), 0.001f);
            assertEquals(180, view.canvas.localY(graphY), 0.001f);
            for (int i = 0; i < 10; i++) {
                view.wheel(260, 180, 1);
                view.wheel(260, 180, -1);
            }
            view.wheel(260, 180, 1);
            assertEquals(1, view.zoom(), 0.00001f);
            assertEquals(-160, view.session.layout().activeGraph().panX(), 0.001f);
            assertEquals(80, view.session.layout().activeGraph().panY(), 0.001f);
            assertEquals(revision, view.session.layoutRevision());
            assertEquals(0, view.session.semanticRevision());
        }
    }

    @Test
    void movingAfterZoomDuringAPanContinuesFromTheZoomedViewport() {
        try (var view = new WheelFixture()) {
            view.session.viewport(-900, -700, 1);
            view.input.dispatchPointerDown(MouseButton.RIGHT, view.displayX(120), view.displayY(100));
            view.input.dispatchPointerMoved(view.displayX(180), view.displayY(124));
            view.wheel(180, 124, -1);
            var afterWheel = view.session.layout().activeGraph();
            view.input.dispatchPointerMoved(view.displayX(192), view.displayY(140));
            assertEquals(afterWheel.panX() + 12, view.session.layout().activeGraph().panX(), 0.001f);
            assertEquals(afterWheel.panY() + 16, view.session.layout().activeGraph().panY(), 0.001f);
            view.input.dispatchPointerUp(MouseButton.RIGHT, view.displayX(192), view.displayY(140));
        }
    }

    @Test
    void rightClickWaitsForReleaseAndUsesThePressedTargetDespiteSmallMovement() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("a", 30, 80);
            int history = session.undoCount();
            // The press is just inside the node header. The release is just outside its left edge.
            view.input.dispatchPointerDown(MouseButton.RIGHT, view.displayX(31), view.displayY(88));
            assertFalse(view.drawing().labels.containsKey("Duplicate node"));
            view.input.dispatchPointerMoved(view.displayX(28), view.displayY(88));
            assertFalse(session.transactionActive());
            view.input.dispatchPointerUp(MouseButton.RIGHT, view.displayX(28), view.displayY(88));
            assertTrue(view.drawing().labels.containsKey("Duplicate node"));
            assertEquals(history, session.undoCount());
            assertEquals(0, session.layout().activeGraph().panX());
            view.key(Key.ESCAPE);
            view.rightClick(680, 410);
            assertTrue(view.drawing().labels.containsKey("Search nodes…"));
        }
    }

    @Test
    void rightDragOverASocketPansOnceAndLeftDragOnTheBackgroundLeavesTheViewInPlace() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("a", 30, 80);
            session.selectNode("b");
            var node = view.node("a");
            var graph = session.activeGraph();
            int history = session.undoCount();
            long revision = session.layoutRevision();
            float x = node.x() + node.width(), y = node.y() + ShaderGraphEditorLayout.socketY(0);
            view.input.dispatchPointerDown(MouseButton.RIGHT, view.displayX(x), view.displayY(y));
            view.input.dispatchPointerMoved(view.displayX(x + 40), view.displayY(y + 20));
            view.input.dispatchPointerUp(MouseButton.RIGHT, view.displayX(x + 64), view.displayY(y + 32));
            var layout = session.layout().activeGraph();
            assertEquals(64, layout.panX(), 0.01f);
            assertEquals(32, layout.panY(), 0.01f);
            assertEquals(node, view.node("a"));
            assertEquals("b", session.selectedNodeId());
            assertEquals(graph, session.activeGraph());
            assertFalse(view.drawing().labels.containsKey("Add node…"));
            assertFalse(view.drawing().labels.containsKey("Search nodes…"));
            assertEquals(history + 1, session.undoCount());
            assertEquals(revision, session.layoutRevision());
            assertTrue(session.undo());
            assertEquals(0, session.layout().activeGraph().panX());
            assertEquals(revision, session.layoutRevision());
            assertTrue(session.redo());
            assertEquals(revision, session.layoutRevision());
            view.drag(680, 410, 750, 490);
            assertEquals(layout, session.layout().activeGraph());
            assertEquals(history + 1, session.undoCount());
        }
    }

    @Test
    void canceledOrReturnedRightDragsDoNotOpenTheClickMenu() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(0, 0, 1);
            int history = session.undoCount();
            view.input.dispatchPointerDown(MouseButton.RIGHT, view.displayX(650), view.displayY(400));
            view.input.dispatchPointerMoved(view.displayX(740), view.displayY(480));
            view.key(Key.ESCAPE);
            view.input.dispatchPointerUp(MouseButton.RIGHT, view.displayX(740), view.displayY(480));
            assertEquals(0, session.layout().activeGraph().panX());
            assertFalse(session.transactionActive());
            assertFalse(view.drawing().labels.containsKey("Search nodes…"));
            view.input.dispatchPointerDown(MouseButton.RIGHT, view.displayX(650), view.displayY(400));
            view.input.dispatchPointerMoved(view.displayX(690), view.displayY(430));
            view.input.dispatchPointerUp(MouseButton.RIGHT, view.displayX(650), view.displayY(400));
            assertFalse(view.drawing().labels.containsKey("Search nodes…"));
            assertEquals(history, session.undoCount());
            // A captured release outside the canvas can carry the only motion sample.
            view.input.dispatchPointerDown(MouseButton.RIGHT, view.displayX(20), view.displayY(400));
            view.input.dispatchPointerUp(MouseButton.RIGHT, view.displayX(-20), view.displayY(410));
            assertEquals(-40, session.layout().activeGraph().panX(), 0.01f);
            assertFalse(view.drawing().labels.containsKey("Search nodes…"));
        }
    }

    @Test
    void wheelZoomsSmoothlyFromFitOnAWideGraph() {
        try (var view = new WheelFixture()) {
            view.session.moveNode("sum", 8000, 0);
            view.canvas.draw(new TextBounds(), view.surface.bounds());
            view.canvas.fitGraph();
            float fit = view.zoom();
            assertTrue(fit < 0.25f);
            float graphX = view.canvas.graphX(260);
            view.wheel(260, 180, 1);
            assertEquals(fit, view.zoom(), 0.00001f, "Scrolling out at Fit must not zoom in");
            view.wheel(260, 180, -1);
            assertEquals(fit * 1.1f, view.zoom(), 0.00001f);
            view.wheel(260, 180, 1);
            assertEquals(fit, view.zoom(), 0.00001f);
            assertEquals(260, view.canvas.localX(graphX), 0.001f);
        }
    }

    @Test
    void wiresTypedMaterialSocketsAndPersistsTheirLayoutAndBindings() {
        var graph = new ShaderGraphBuilder("material", ShaderGraphKind.SURFACE);
        var scalar = graph.floatValue(0.7f);
        var rgb = graph.constant("rgb", ShaderGraphLiteral.zero(ShaderGraphType.vector(ShaderScalarType.F32, 3)));
        var initial = graph.floatValue(1);
        graph.output("custom_opacity", "alpha", initial);
        try (var view = new WheelFixture(graph.build())) {
            var session = view.session;
            var canvas = view.canvas;
            session.viewport(32, 20, 0.9f);
            view.wire("rgb", "Base Color");
            view.wire("rgb", "Emissive");
            view.wire(scalar.endpoint().nodeId().value(), "Alpha");
            var material = session.activeGraph();
            var alpha = MaterialOutputChannel.ALPHA.binding(material.outputs());
            assertEquals("custom_opacity", alpha.id().value());
            assertEquals(scalar.endpoint(), alpha.source());
            assertEquals(rgb.endpoint(), MaterialOutputChannel.BASE_COLOR.binding(material.outputs()).source());
            assertEquals(rgb.endpoint(), MaterialOutputChannel.EMISSIVE.binding(material.outputs()).source());
            view.wire("rgb", "Alpha");
            assertTrue(canvas.lastInteractionError().contains("Float"));
            assertEquals(material, session.activeGraph());
            var drawing = new TextBounds();
            canvas.draw(drawing, new UiRect(0, 0, 900, 600));
            assertEquals(3, drawing.wires);
            var original = session.layout().activeGraph().materialOutput();
            long layoutRevision = session.layoutRevision();
            float x = canvas.localX(original.x() + 40);
            float y = canvas.localY(original.y() + 12);
            view.drag(x, y, x + 90, y + 45);
            var moved = session.layout().activeGraph().materialOutput();
            assertEquals(original.x() + 100, moved.x(), 1);
            assertEquals(original.y() + 50, moved.y(), 1);
            assertEquals(layoutRevision + 1, session.layoutRevision());
            assertEquals(material.semanticHash(), session.activeGraph().semanticHash());
            assertTrue(session.undo());
            assertEquals(original, session.layout().activeGraph().materialOutput());
            assertTrue(session.redo());
            var loaded = ShaderGraphEditorPersistence.read(ShaderGraphEditorPersistence.write(session.document(), session.layout()));
            assertEquals(moved, loaded.layout().activeGraph().materialOutput());
            assertEquals(material, loaded.document().graph());
            int sx = view.displayX(canvas.localX(moved.x()));
            int sy = view.displayY(view.socketY("Alpha"));
            view.input.dispatchPointerDown(MouseButton.RIGHT, sx, sy);
            view.input.dispatchPointerUp(MouseButton.RIGHT, sx, sy);
            assertEquals(alpha, MaterialOutputChannel.ALPHA.binding(session.activeGraph().outputs()));
            view.choose("Disconnect Alpha");
            assertNull(MaterialOutputChannel.ALPHA.binding(session.activeGraph().outputs()));
            assertTrue(session.undo());
            assertEquals(alpha, MaterialOutputChannel.ALPHA.binding(session.activeGraph().outputs()));
        }
        try (var function = new WheelFixture()) {
            assertNull(function.session.layout().activeGraph().materialOutput());
            var drawing = new TextBounds();
            function.canvas.draw(drawing, new UiRect(0, 0, 900, 600));
            assertNull(drawing.labels.get("Material Output"));
        }
    }

    @Test
    void browsesCategoriesAndRestoresTheirExpansionAfterSearching() {
        try (var view = new WheelFixture()) {
            view.rightClick(710, 380);
            assertTrue(view.drawing().labels.containsKey("Math"));
            assertFalse(view.drawing().labels.containsKey("Cosine"));
            view.choose("Math");
            var labels = view.drawing().labels;
            assertTrue(labels.get("Cosine").x() > labels.get("Math").x());
            view.key(Key.LEFT);
            assertFalse(view.drawing().labels.containsKey("Cosine"));
            view.key(Key.RIGHT);
            view.input.dispatchTextInput("noise");
            labels = view.drawing().labels;
            assertTrue(labels.containsKey("Procedural"));
            assertTrue(labels.containsKey("Noise 2D"));
            assertFalse(labels.containsKey("Math"));
            for (int i = 0; i < 5; i++) view.key(Key.BACKSPACE);
            assertTrue(view.drawing().labels.containsKey("Cosine"));
            view.choose("Cosine");
            assertEquals(4, view.session.activeGraph().nodes().length);
        }
    }

    @Test
    void nativeSearchFieldEditsSupplementaryCharactersAndRestoresCanvasFocus() {
        try (var view = new WheelFixture()) {
            view.rightClick(710, 380);
            view.input.dispatchTextInput("🌊osine");
            view.key(Key.HOME);
            view.key(Key.DELETE);
            view.input.dispatchTextInput("c");
            view.key(Key.END);
            view.key(Key.LEFT);
            view.input.dispatchTextInput("x");
            assertEquals("cosinxe", view.searchField().value());
            view.key(Key.BACKSPACE);
            assertEquals("cosine", view.searchField().value());
            assertTrue(view.drawing().labels.containsKey("Cosine"));
            view.key(Key.ENTER);
            assertEquals(4, view.session.activeGraph().nodes().length);
            view.root.update(0);
            assertTrue(view.surface.focused());
            assertNull(view.field("Search nodes"));
        }
    }

    @Test
    void pickerUsesPanelKeyboardAndPointerSelectionAndClipboard() {
        try (var view = new WheelFixture()) {
            view.rightClick(710, 380);
            view.input.dispatchTextInput("noise");
            view.input.dispatchKeyDown(Key.SHIFT_LEFT);
            view.key(Key.LEFT);
            view.key(Key.LEFT);
            view.input.dispatchKeyUp(Key.SHIFT_LEFT);
            view.shortcut(Key.C);
            assertEquals("se", view.input.clipboard().getText());
            var bounds = view.searchField().bounds();
            int y = view.root.displayY(bounds.y() + bounds.height() / 2);
            int end = view.root.displayX(bounds.right() - 2);
            view.input.dispatchPointerDown(MouseButton.LEFT, view.root.displayX(bounds.x() + 2), y);
            view.input.dispatchPointerMoved(end, y);
            view.input.dispatchPointerUp(MouseButton.LEFT, end, y);
            view.shortcut(Key.X);
            assertEquals("noise", view.input.clipboard().getText());
            assertEquals("", view.searchField().value());
            view.shortcut(Key.V);
            assertTrue(view.drawing().labels.containsKey("Noise 2D"));
            view.shortcut(Key.A);
            view.input.dispatchTextInput("cosine");
            assertTrue(view.drawing().labels.containsKey("Cosine"));
            view.key(Key.ESCAPE);
            view.root.update(0);
            assertTrue(view.surface.focused());
            view.rightClick(710, 380);
            assertEquals("", view.searchField().value());
        }
    }

    @Test
    void searchesAtTheClickedGraphPositionAndUndoesContextEdits() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(36, 24, 1.25f);
            float x = 710, y = 380;
            view.rightClick(x, y);
            view.input.dispatchTextInput("cosine");
            assertTrue(view.drawing().labels.containsKey("Cosine"));
            view.key(Key.ENTER);
            String id = session.selectedNodeId();
            assertEquals("math.cosine", session.activeGraph().node(io.github.libfdx.graphics.shadergraph.model.ShaderGraphId.of(id)).definitionId().value());
            var created = java.util.Arrays.stream(session.layout().activeGraph().nodes())
                    .filter(node -> node.nodeId().value().equals(id)).findFirst().orElseThrow();
            assertEquals(view.canvas.graphX(x), created.x(), 0.6);
            assertEquals(view.canvas.graphY(y), created.y(), 0.6);
            view.rightClick(x + 8, y + 6);
            view.choose("Duplicate node");
            assertEquals(5, session.activeGraph().nodes().length);
            assertTrue(session.undo());
            view.rightClick(x + 8, y + 6);
            view.choose("Delete node");
            assertEquals(3, session.activeGraph().nodes().length);
            assertTrue(session.undo());
            view.rightClick(15, 460);
            view.input.dispatchTextInput("missing_xyz");
            assertTrue(view.drawing().labels.containsKey("No matching nodes"));
            view.key(Key.ESCAPE);
            assertTrue(!view.drawing().labels.containsKey("No matching nodes"));
        }
    }

    @Test
    void disconnectsOnlyTheChosenWireFromACurveOrSocket() {
        var graph = new ShaderGraphBuilder("wires", ShaderGraphKind.SURFACE);
        var value = graph.constant("a", ShaderGraphLiteral.f32(0.5f));
        graph.output("alpha", "alpha", value);
        graph.add("sum", value, value);
        try (var view = new WheelFixture(graph.build())) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("a", 20, 20);
            session.moveNode("sum", 400, 160);
            session.moveMaterialOutput(520, 0);
            view.rightClick(200, 68);
            view.choose("Disconnect sum · B");
            assertEquals(1, session.activeGraph().edges().length);
            assertEquals("in000000", session.activeGraph().edges()[0].target().portId().value());
            assertEquals(1, session.activeGraph().outputs().length);
            // Midpoint of the remaining cubic with horizontal tangents.
            view.rightClick(300, 138);
            view.choose("Disconnect wire");
            assertEquals(0, session.activeGraph().edges().length);
            assertTrue(session.undo());
            view.rightClick(400, 208);
            view.choose("Disconnect a · Value");
            assertEquals(0, session.activeGraph().edges().length);
            view.rightClick(360, 67);
            view.choose("Disconnect Alpha");
            assertEquals(0, session.activeGraph().outputs().length);
            assertTrue(session.undo());
            assertTrue(session.undo());
            view.rightClick(30, 30);
            view.choose("Disconnect all wires");
            assertEquals(0, session.activeGraph().edges().length);
            assertEquals(0, session.activeGraph().outputs().length);
            assertEquals(2, session.activeGraph().nodes().length);
            assertTrue(session.undo());
            view.rightClick(30, 30);
            view.choose("Delete node");
            assertEquals(0, session.activeGraph().edges().length);
            assertEquals(0, session.activeGraph().outputs().length);
            assertTrue(session.undo());
            assertEquals(1, session.activeGraph().edges().length);
            assertEquals(1, session.activeGraph().outputs().length);
        }
    }

    @Test
    void droppingAnOutputOffersOnlyCompatibleInputsAndInsertsAtTheWireTip() {
        var builder = new ShaderGraphBuilder("flow", ShaderGraphKind.FUNCTION);
        var uv = builder.constant("uv", ShaderGraphLiteral.zero(ShaderGraphType.vector(ShaderScalarType.F32, 2)));
        try (var view = new WheelFixture(builder.build())) {
            var session = view.session;
            session.viewport(20, 35, 1.25f);
            session.moveNode("uv", 10, 20);
            int history = session.undoCount();
            long revision = session.semanticRevision();
            float dropX = 570, dropY = 330;
            view.drag(view.canvas.localX(190), view.canvas.localY(68), dropX, dropY);
            var labels = view.drawing().labels;
            assertTrue(labels.containsKey("Search nodes with Vec2 input…"));
            assertTrue(labels.containsKey("Noise 2D (UV)"));
            assertFalse(labels.containsKey("Cosine (Radians)"));
            assertEquals(1, view.drawing().wires, "The pending wire should stay visible while searching");
            view.input.dispatchTextInput("cosine");
            assertTrue(view.drawing().labels.containsKey("No matching nodes"));
            view.key(Key.ENTER);
            assertEquals(history, session.undoCount());
            view.key(Key.ESCAPE);
            assertEquals(0, view.drawing().wires);
            view.drag(view.canvas.localX(190), view.canvas.localY(68), dropX, dropY);
            view.input.clipboard().setText("noise");
            view.shortcut(Key.V);
            assertEquals("noise", view.searchField().value());
            view.choose("Noise 2D (UV)");
            String id = session.selectedNodeId();
            var edge = session.activeGraph().edges()[0];
            assertEquals(uv.endpoint(), edge.source());
            assertEquals(ShaderEndpoint.of(id, "uv"), edge.target());
            var placed = view.node(id);
            assertEquals(dropX, view.canvas.localX(placed.x()), 0.7f);
            assertEquals(dropY, view.canvas.localY(placed.y() + ShaderGraphEditorLayout.socketY(0)), 0.7f);
            assertEquals(history + 1, session.undoCount());
            assertEquals(revision + 1, session.semanticRevision());
            assertTrue(session.undo());
            assertEquals(1, session.activeGraph().nodes().length);
            assertEquals(0, session.activeGraph().edges().length);
            assertTrue(session.redo());
            assertEquals(edge, session.activeGraph().edges()[0]);
            var saved = ShaderGraphEditorPersistence.read(ShaderGraphEditorPersistence.write(session.document(), session.layout()));
            assertEquals(session.activeGraph(), saved.document().graph());
        }
    }

    @Test
    void anchorsTheChosenNodeByItsFirstTypeCompatibleSocket() {
        var builder = new ShaderGraphBuilder("panning", ShaderGraphKind.FUNCTION);
        var time = builder.constant("seconds", ShaderGraphLiteral.f32(1));
        try (var view = new WheelFixture(builder.build())) {
            view.session.viewport(0, 0, 1);
            view.session.moveNode("seconds", 20, 30);
            var source = view.node("seconds");
            view.drag(source.x() + source.width(), source.y() + ShaderGraphEditorLayout.socketY(0), 615, 390);
            view.input.dispatchTextInput("panner");
            view.choose("Panner (Time)");
            var placed = view.node(view.session.selectedNodeId());
            var edge = view.session.activeGraph().edges()[0];
            assertEquals(time.endpoint(), edge.source());
            assertEquals(ShaderEndpoint.of(placed.nodeId().value(), "time"), edge.target());
            assertEquals(615, placed.x(), 0.7f);
            assertEquals(390, placed.y() + ShaderGraphEditorLayout.socketY(1), 0.7f);
        }
    }

    @Test
    void draggingAWiredInputReplacesItsSourceOnlyOnSelection() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("sum", 410, 40);
            float x = 410, y = 40 + ShaderGraphEditorLayout.socketY(1);
            var original = session.activeGraph();
            int history = session.undoCount();
            view.drag(x, y, -20, 300);
            assertEquals(original, session.activeGraph());
            assertFalse(view.drawing().labels.containsKey("Search nodes with Float output…"));
            view.drag(x, y, 330, 370);
            view.input.dispatchTextInput("noise");
            assertTrue(view.drawing().labels.containsKey("Noise 2D (Noise)"));
            view.key(Key.ESCAPE);
            assertEquals(original, session.activeGraph());
            view.drag(x, y, 330, 370);
            view.input.dispatchTextInput("float");
            view.key(Key.ENTER);
            String id = session.selectedNodeId();
            var placed = view.node(id);
            assertEquals(330, view.canvas.localX(placed.x() + placed.width()), 1f);
            var wanted = ShaderEndpoint.of("sum", "in000001");
            var edges = session.activeGraph().edges();
            assertEquals(2, edges.length);
            assertTrue(java.util.Arrays.stream(edges).anyMatch(e -> e.source().equals(ShaderEndpoint.of(id, "value")) && e.target().equals(wanted)));
            assertEquals(history + 1, session.undoCount());
            assertTrue(session.undo());
            assertEquals(original, session.activeGraph());
            // The same reverse drag can also terminate on an existing output socket.
            var a = view.node("a");
            view.drag(x, y, a.x() + a.width(), a.y() + ShaderGraphEditorLayout.socketY(0));
            assertTrue(java.util.Arrays.stream(session.activeGraph().edges())
                    .allMatch(e -> e.source().equals(ShaderEndpoint.of("a", "value"))));
        }
    }

    @Test
    void aMaterialInputFindsDocumentParametersOfTheRightTypeAndKeepsTheOutputId() {
        var builder = new ShaderGraphBuilder("surface", ShaderGraphKind.SURFACE);
        var rgb = ShaderGraphType.vector(ShaderScalarType.F32, 3);
        builder.parameter(ShaderGraphParameter.of("tint", rgb, ShaderGraphParameterKind.MATERIAL, ShaderGraphLiteral.zero(rgb)));
        builder.output("authored_color", "baseColor", builder.constant("base", ShaderGraphLiteral.zero(rgb)));
        try (var view = new WheelFixture(builder.build())) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveMaterialOutput(570, 40);
            var original = session.activeGraph();
            view.drag(570, view.socketY("Base Color"), 320, 330);
            view.input.dispatchTextInput("float");
            assertTrue(view.drawing().labels.containsKey("No matching nodes"));
            view.key(Key.ESCAPE);
            assertEquals(original, session.activeGraph());
            view.drag(570, view.socketY("Base Color"), 320, 330);
            view.input.dispatchTextInput("tint");
            view.key(Key.ENTER);
            var binding = session.activeGraph().outputs()[0];
            assertEquals("authored_color", binding.id().value());
            assertEquals(ShaderEndpoint.of(session.selectedNodeId(), "value"), binding.source());
            assertTrue(session.undo());
            assertEquals(original, session.activeGraph());
        }
    }

    @Test
    void tracesBothSocketsAndPicksTheEditedWireAcrossStraightAndCurvedSegments() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("a", 20, 20);
            session.moveNode("b", 1020, 1020);
            session.moveNode("sum", 520, 260);
            var key = new ShaderGraphWireRoute.Wire(ShaderEndpoint.of("a", "value"), ShaderEndpoint.of("sum", "in000000"), null);
            String trace = "a · Value  to  sum · A";
            long layoutRevision = session.layoutRevision();
            long revision = session.semanticRevision();
            int history = session.undoCount();
            view.drag(360, 188, 360, 188);
            var label = view.drawing().labels.get(trace);
            assertNotNull(label);
            view.drag(label.x() + 12, label.y() + 8, label.x() + 12, label.y() + 8);
            view.shortcut(Key.A);
            view.shortcut(Key.C);
            assertEquals(trace, view.input.clipboard().getText());
            assertEquals(history, session.undoCount());
            view.rightClick(360, 188);
            view.choose("Add routing pin");
            var added = session.layout().activeGraph().routes().get(key);
            assertFalse(added.straight(0));
            assertFalse(added.straight(1));
            view.drag(360, 188, 340, 70);
            var moved = session.layout().activeGraph().routes().get(key);
            assertEquals(340, moved.pins().get(0).x(), 0.7);
            assertEquals(68, moved.pins().get(0).y(), 0.01);
            assertEquals(layoutRevision + 2, session.layoutRevision());
            assertEquals(history + 2, session.undoCount());
            assertTrue(session.undo());
            assertEquals(added, session.layout().activeGraph().routes().get(key));
            assertTrue(session.redo());
            view.key(Key.ESCAPE);
            assertFalse(view.drawing().labels.containsKey(trace));
            view.drag(270, 68, 270, 68);
            assertTrue(view.drawing().labels.containsKey(trace));
            view.rightClick(430, 188);
            view.choose("Make segment straight");
            var mixed = session.layout().activeGraph().routes().get(key);
            assertFalse(mixed.straight(0));
            assertTrue(mixed.straight(1));
            view.rightClick(430, 188);
            view.choose("Add routing pin");
            var splitStraight = session.layout().activeGraph().routes().get(key);
            assertFalse(splitStraight.straight(0));
            assertTrue(splitStraight.straight(1));
            assertTrue(splitStraight.straight(2));
            assertTrue(session.undo());
            view.rightClick(270, 68);
            view.choose("Add routing pin");
            var splitCurve = session.layout().activeGraph().routes().get(key);
            assertFalse(splitCurve.straight(0));
            assertFalse(splitCurve.straight(1));
            assertTrue(splitCurve.straight(2));
            assertTrue(session.undo());
            view.rightClick(430, 188);
            view.choose("Make segment curved");
            assertEquals(moved, session.layout().activeGraph().routes().get(key));
            view.rightClick(270, 68);
            view.choose("Add routing pin");
            var two = session.layout().activeGraph().routes().get(key);
            assertEquals(2, two.pins().size());
            assertEquals(moved.pins().get(0), two.pins().get(1));
            view.rightClick(270, 68);
            view.choose("Remove routing pin");
            assertEquals(moved, session.layout().activeGraph().routes().get(key));
            int beforeCancel = session.undoCount();
            view.input.dispatchPointerDown(MouseButton.LEFT, view.displayX(340), view.displayY(68));
            view.input.dispatchPointerMoved(view.displayX(370), view.displayY(140));
            view.key(Key.ESCAPE);
            view.input.dispatchPointerUp(MouseButton.LEFT, view.displayX(370), view.displayY(140));
            assertEquals(beforeCancel, session.undoCount());
            assertEquals(moved, session.layout().activeGraph().routes().get(key));
            view.rightClick(270, 68);
            view.choose("Reset wire route");
            assertTrue(session.layout().activeGraph().routes().isEmpty());
            assertTrue(session.undo());
            assertEquals(moved, session.layout().activeGraph().routes().get(key));
            view.rightClick(270, 68);
            view.choose("Show destination");
            assertEquals(view.surface.bounds().width() / 2, view.canvas.localX(520), 1);
            assertTrue(view.drawing().labels.containsKey(trace));
            assertEquals(revision, session.semanticRevision());
        }
    }

    @Test
    void pinDirectionTurnsBothCurvesAndTheirHitAreas() {
        try (var view = new WheelFixture()) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("a", 20, 20);
            session.moveNode("b", 1020, 1020);
            session.moveNode("sum", 520, 20);
            var key = new ShaderGraphWireRoute.Wire(ShaderEndpoint.of("a", "value"), ShaderEndpoint.of("sum", "in000000"), null);
            long revision = session.semanticRevision();
            view.rightClick(360, 68);
            view.choose("Add routing pin");
            var horizontal = session.layout().activeGraph().routes().get(key);
            var legacy = ShaderGraphEditorPersistence.write(session.document(), session.layout());
            assertEquals(horizontal, ShaderGraphEditorPersistence.read(legacy).layout().activeGraph().routes().get(key));
            assertEquals(0, horizontal.pins().get(0).rotationDegrees());

            view.drag(360, 68, 360, 68);
            view.canvas.rotateSelectedPin(90);
            var down = session.layout().activeGraph().routes().get(key);
            assertEquals(90, down.pins().get(0).rotationDegrees());
            // Both midpoints lie outside the old horizontal wire's pick radius.
            view.rightClick(307, 54.5f);
            view.choose("Make segment straight");
            view.rightClick(413, 81.5f);
            view.choose("Make segment straight");
            var straight = session.layout().activeGraph().routes().get(key);
            assertTrue(straight.straight(0) && straight.straight(1));
            assertEquals(90, straight.pins().get(0).rotationDegrees());
            assertTrue(session.undo());
            assertTrue(session.undo());

            view.drag(360, 68, 360, 68);
            view.canvas.rotateSelectedPin(-90);
            var up = session.layout().activeGraph().routes().get(key);
            assertEquals(-90, up.pins().get(0).rotationDegrees());
            view.rightClick(307, 81.5f);
            assertTrue(view.drawing().labels.containsKey("Add routing pin"));
            view.key(Key.ESCAPE);
            view.rightClick(413, 54.5f);
            assertTrue(view.drawing().labels.containsKey("Add routing pin"));
            view.key(Key.ESCAPE);
            assertTrue(session.undo());
            assertEquals(down, session.layout().activeGraph().routes().get(key));
            assertTrue(session.redo());
            assertEquals(up, session.layout().activeGraph().routes().get(key));
            view.drag(360, 68, 360, 68);
            view.canvas.rotateSelectedPin(360);
            assertEquals(horizontal, session.layout().activeGraph().routes().get(key));
            assertEquals(revision, session.semanticRevision());
        }
    }

    @Test
    void repeatedClicksTraceDifferentWiresAlongACoincidentRun() {
        var builder = new ShaderGraphBuilder("overlap", ShaderGraphKind.FUNCTION);
        var value = builder.constant("a", ShaderGraphLiteral.f32(1));
        builder.add("first", value, value);
        builder.add("second", value, value);
        try (var view = new WheelFixture(builder.build())) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("a", 20, 20);
            session.moveNode("first", 520, 260);
            session.moveNode("second", 520, 260);
            view.drag(360, 188, 360, 188);
            var first = view.drawing().labels;
            view.drag(360, 188, 360, 188);
            var second = view.drawing().labels;
            String toFirst = "a · Value  to  first · A", toSecond = "a · Value  to  second · A";
            assertTrue(first.containsKey(toFirst) && second.containsKey(toSecond)
                    || first.containsKey(toSecond) && second.containsKey(toFirst));
            view.rightClick(360, 188);
            view.choose("Disconnect wire");
            String removed = second.containsKey(toSecond) ? "second" : "first";
            assertFalse(java.util.Arrays.stream(session.activeGraph().edges())
                    .anyMatch(edge -> edge.target().equals(ShaderEndpoint.of(removed, "in000000"))));
        }
    }

    @Test
    void materialRouteFollowsItsBindingThroughSaveRenameAndUndo() {
        var builder = new ShaderGraphBuilder("routes", ShaderGraphKind.SURFACE);
        var old = builder.constant("opacity", ShaderGraphLiteral.f32(1));
        var other = builder.constant("other", ShaderGraphLiteral.f32(0.5f));
        builder.output("custom_alpha", "alpha", old);
        try (var view = new WheelFixture(builder.build())) {
            var session = view.session;
            session.viewport(0, 0, 1);
            session.moveNode("opacity", 20, 160);
            session.moveNode("other", 0, 420);
            session.moveMaterialOutput(600, 0);
            var key = ShaderGraphWireRoute.Wire.of(session.activeGraph().outputs()[0]);
            view.rightClick(400, 137);
            view.choose("Add routing pin");
            view.drag(400, 137, 400, 137);
            view.canvas.rotateSelectedPin(-42.5f);
            view.drag(400, 137, 370, 208);
            assertTrue(view.drawing().labels.containsKey("opacity · Value  to  Material Output · Alpha"));
            var route = session.layout().activeGraph().routes().get(key);
            assertEquals(-42.5f, route.pins().get(0).rotationDegrees());
            var save = ShaderGraphEditorPersistence.write(session.document(), session.layout());
            var loaded = ShaderGraphEditorPersistence.read(save);
            assertEquals(route, loaded.layout().activeGraph().routes().get(key));
            assertEquals(session.activeGraph().semanticHash(), loaded.document().graph().semanticHash());
            assertEquals(save, ShaderGraphEditorPersistence.write(loaded.document(), loaded.layout()));
            session.renameNode("opacity", "renamed");
            var renamed = ShaderGraphWireRoute.Wire.of(session.activeGraph().outputs()[0]);
            assertEquals("custom_alpha", renamed.output().value());
            assertEquals(route, session.layout().activeGraph().routes().get(renamed));
            session.bindMaterialOutput(MaterialOutputChannel.ALPHA, other.endpoint());
            assertTrue(session.layout().activeGraph().routes().isEmpty());
            assertTrue(session.undo());
            assertEquals(route, session.layout().activeGraph().routes().get(renamed));
            session.removeNodes("renamed");
            assertTrue(session.layout().activeGraph().routes().isEmpty());
            assertTrue(session.undo());
            assertEquals(route, session.layout().activeGraph().routes().get(renamed));
        }
    }

    private static final class WheelFixture implements AutoCloseable {
        final ShaderGraphEditorSession session;
        final ShaderGraphEditorCanvas canvas;
        final DefaultInput input = new DefaultInput();
        final UiRoot root = new UiToolkit(null).root(null, null).allowFontFallback(true).input(input).uiScale(1.5f);
        UiNode surface;

        WheelFixture() {
            this(ShaderGraphEditorFixtures.editableFunction("wheel"));
        }

        WheelFixture(ShaderGraph graph) {
            session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph));
            canvas = new ShaderGraphEditorCanvas(session);
            root.resize(1600, 900);
            root.setContent(ui -> ui.column(UiModifier.none().fill().padding(60, 40, 0, 0), column ->
                    surface = column.custom("canvas", UiModifier.none().fill().focusable(true), canvas)));
            root.update(0);
        }

        int displayX(float local) {
            return root.displayX(surface.bounds().x() + local);
        }

        int displayY(float local) {
            return root.displayY(surface.bounds().y() + local);
        }

        float zoom() {
            return session.layout().activeGraph().zoom();
        }

        void drag(float x, float y, float endX, float endY) {
            input.dispatchPointerDown(MouseButton.LEFT, displayX(x), displayY(y));
            input.dispatchPointerMoved(displayX(endX), displayY(endY));
            input.dispatchPointerUp(MouseButton.LEFT, displayX(endX), displayY(endY));
        }

        void click(float x, float y) {
            input.dispatchPointerDown(MouseButton.LEFT, displayX(x), displayY(y));
            input.dispatchPointerUp(MouseButton.LEFT, displayX(x), displayY(y));
        }

        void doubleClickReadout(String text) {
            var bounds = drawing().labels.get(text);
            assertNotNull(bounds);
            float x = bounds.x() + 4, y = bounds.y() + bounds.height() / 2;
            click(x, y);
            click(x, y);
        }

        void key(Key key) {
            input.dispatchKeyDown(key);
            input.dispatchKeyUp(key);
        }

        UiNode searchField() {
            root.update(0);
            var field = field("Search nodes");
            assertTrue(field != null);
            assertEquals(UiNodeType.TEXT_FIELD, field.type());
            assertTrue(field.focused());
            return field;
        }

        UiNode field(String label) {
            root.update(0);
            return findField(surface, label);
        }

        private UiNode findField(UiNode node, String label) {
            if (label.equals(node.modifier().semanticLabel())) return node;
            for (int i = 0; i < node.children().size(); i++) {
                UiNode match = findField(node.children().get(i), label);
                if (match != null) return match;
            }
            return null;
        }

        void shortcut(Key key) {
            input.dispatchKeyDown(Key.CONTROL_LEFT);
            key(key);
            input.dispatchKeyUp(Key.CONTROL_LEFT);
        }

        ShaderGraphEditorNode node(String id) {
            for (var node : session.layout().activeGraph().nodes()) {
                if (node.nodeId().value().equals(id)) return node;
            }
            throw new AssertionError("Missing node " + id);
        }

        void rightClick(float x, float y) {
            input.dispatchPointerDown(MouseButton.RIGHT, displayX(x), displayY(y));
            input.dispatchPointerUp(MouseButton.RIGHT, displayX(x), displayY(y));
        }

        TextBounds drawing() {
            var result = new TextBounds();
            canvas.draw(result, new UiRect(0, 0, surface.bounds().width(), surface.bounds().height()));
            root.update(0);
            collectLabels(surface, result);
            return result;
        }

        private void collectLabels(UiNode node, TextBounds result) {
            if (node.text() != null) {
                var bounds = node.bounds();
                result.labels.put(node.text(), new UiRect(bounds.x() - surface.bounds().x(),
                        bounds.y() - surface.bounds().y(), bounds.width(), bounds.height()));
            }
            for (int i = 0; i < node.children().size(); i++) collectLabels(node.children().get(i), result);
        }

        void choose(String action) {
            var labels = drawing().labels;
            assertTrue(labels.containsKey(action), () -> "Missing " + action + " in " + labels.keySet());
            var row = labels.get(action);
            int x = displayX(row.x() + 12), y = displayY(row.y() + row.height() / 2);
            input.dispatchPointerDown(MouseButton.LEFT, x, y);
            input.dispatchPointerUp(MouseButton.LEFT, x, y);
        }

        float socketY(String channel) {
            var label = drawing().labels.get(channel);
            return label.y() + label.height() / 2;
        }

        void wire(String nodeId, String channel) {
            ShaderGraphEditorData layout = session.layout().activeGraph();
            for (ShaderGraphEditorNode node : layout.nodes()) {
                if (node.nodeId().value().equals(nodeId)) {
                    drag(canvas.localX(node.x() + node.width()), canvas.localY(node.y() + 48),
                            canvas.localX(layout.materialOutput().x()), socketY(channel));
                    return;
                }
            }
            throw new AssertionError("Missing source " + nodeId);
        }

        void wheel(float x, float y, float delta) {
            input.dispatchScrolled(displayX(x), displayY(y), 0, delta);
        }

        @Override
        public void close() {
            root.dispose();
        }

    }

    private static final class TextBounds implements UiDrawContext {
        private int wires;
        private final Map<String, UiRect> labels = new HashMap<>();

        @Override
        public void text(String text, UiRect bounds, UiTextStyle style) {
            labels.put(text, bounds);
        }

        @Override
        public void rect(UiRect bounds, UiColor color) {
        }

        @Override
        public void line(float x1, float y1, float x2, float y2, float width, UiColor color) {
        }

        @Override
        public void path(UiPath path, float width, UiColor color) {
            wires++;
        }

    }
}
