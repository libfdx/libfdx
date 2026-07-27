package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorSessionTest {
    @Test
    void separatesSemanticAndLayoutHistoryAndCoalescesDrag() {
        ShaderGraph graph =
                ShaderGraphEditorFixtures.editableFunction("history");
        ShaderGraphEditorSession session = new ShaderGraphEditorSession(
                ShaderGraphEditorDocument.of(graph));
        String hash = session.document().semanticHash();
        ShaderGraphEditorNode original = node(session, "a");

        session.beginTransaction("Move node");
        session.moveNode("a", 80.0f, 90.0f);
        session.moveNode("a", 130.0f, 150.0f);
        assertTrue(session.commitTransaction());

        assertEquals(hash, session.document().semanticHash());
        assertEquals(0, session.semanticRevision());
        assertTrue(session.layoutRevision() >= 2);
        assertEquals(1, session.undoCount());
        assertEquals(130.0f, node(session, "a").x());

        assertTrue(session.undo());
        assertEquals(original, node(session, "a"));
        assertEquals(hash, session.document().semanticHash());
        assertTrue(session.redo());
        assertEquals(130.0f, node(session, "a").x());

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
