package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ShaderGraphOrganizerTest {
    @Test
    void placesSourcesByConsumerAndSocketAndSeparatesUnconnectedWork() {
        var builder = new ShaderGraphBuilder("placement", ShaderGraphKind.SURFACE);
        var first = builder.constant("z_first", ShaderGraphLiteral.f32(1));
        var second = builder.constant("a_second", ShaderGraphLiteral.f32(2));
        var sum = builder.add("sum", first, second);
        var late = builder.constant("late", ShaderGraphLiteral.f32(3));
        builder.output("alpha", "alpha", builder.multiply("product", sum, late));
        builder.constant("scratch", ShaderGraphLiteral.f32(0));
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(builder.build()));
        var layout = session.layout().activeGraph();
        var a = node(layout, "z_first");
        var b = node(layout, "a_second");
        assertEquals(a.x(), b.x());
        assertTrue(a.y() + a.height() < b.y(), "Sources follow input order even when their names sort the other way");
        assertEquals(node(layout, "sum").x(), node(layout, "late").x());
        assertTrue(node(layout, "late").x() > b.x() + b.width());
        assertTrue(layout.materialOutput().x() > node(layout, "product").x() + node(layout, "product").width());
        assertTrue(node(layout, "scratch").y() > layout.materialOutput().y() + ShaderGraphEditorCanvas.OUTPUT_HEIGHT);
        assertSeparated(layout);
        session.organizeGraph();
        assertEquals(0, session.undoCount());
    }

    @Test
    void preservesAndSavesCustomRoutingAndRestoresTheWholeLayoutOnUndo() {
        var graph = ShaderGraphEditorFixtures.editableFunction("saved");
        var doc = ShaderGraphEditorDocument.of(graph);
        var layout = ShaderGraphEditorLayout.forDocument(doc);
        var wire = ShaderGraphWireRoute.Wire.of(graph.edges()[0]);
        for (var card : layout.activeGraph().nodes()) {
            layout = layout.withNode("saved", ShaderGraphEditorNode.of(card.nodeId().value(),
                    card.x() + 300, card.y() + 60, card.width(), card.height(), true));
        }
        var from = node(layout.activeGraph(), wire.source().nodeId().value());
        var route = ShaderGraphWireRoute.DEFAULT.insert(0, from.x() + from.width() + 12, from.y() + 55)
                .withRotation(0, 90).withStraight(1, true);
        layout = layout.withWireRoute(wire, route).withViewport("saved", -45, 72, 0.65f);
        var session = new ShaderGraphEditorSession(doc, layout);
        session.selectNode("sum");
        session.organizeGraph();
        var organized = session.layout();
        var moved = organized.activeGraph().routes().get(wire);
        assertEquals(route.move(0, route.pins().getFirst().x() - 300, route.pins().getFirst().y() - 60), moved);
        assertTrue(node(organized.activeGraph(), "sum").collapsed());
        assertEquals("sum", session.selectedNodeId());
        assertEquals(1, session.layoutRevision());
        assertEquals(0, session.semanticRevision());
        assertEquals(doc, session.document());
        assertEquals(organized, ShaderGraphEditorPersistence.read(ShaderGraphEditorPersistence.write(doc, organized)).layout());
        session.organizeGraph();
        assertEquals(1, session.undoCount());
        assertTrue(session.undo());
        assertEquals(layout, session.layout());
        assertTrue(session.redo());
        assertEquals(organized, session.layout());
    }

    @Test
    void cyclicDraftsAndEmptyGraphsCanStillBeOrganized() {
        var source = ShaderGraphEditorFixtures.editableFunction("cycle");
        var graph = ShaderGraph.builder("cycle", ShaderGraphKind.FUNCTION).nodes(source.nodes())
                .outputs(source.outputs()).edges(ShaderEdge.of(ShaderEndpoint.of("sum", "value"),
                        ShaderEndpoint.of("sum", "in000000"))).build();
        var session = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(graph));
        assertSeparated(session.layout().activeGraph());
        session.organizeGraph();
        assertEquals(0, session.undoCount());
        var empty = new ShaderGraphEditorSession(ShaderGraphEditorDocument.of(
                ShaderGraph.builder("empty", ShaderGraphKind.FUNCTION).build()));
        empty.organizeGraph();
        assertEquals(0, empty.undoCount());
    }

    private static void assertSeparated(ShaderGraphEditorData layout) {
        var nodes = layout.nodes();
        for (int i = 0; i < nodes.length; i++) {
            for (int j = 0; j < i; j++) {
                var a = nodes[i];
                var b = nodes[j];
                assertTrue(a.x() >= b.x() + b.width() || b.x() >= a.x() + a.width()
                        || a.y() >= b.y() + b.height() || b.y() >= a.y() + a.height());
            }
        }
    }

    private static ShaderGraphEditorNode node(ShaderGraphEditorData layout, String id) {
        return Arrays.stream(layout.nodes()).filter(node -> node.nodeId().value().equals(id)).findFirst().orElseThrow();
    }

}
