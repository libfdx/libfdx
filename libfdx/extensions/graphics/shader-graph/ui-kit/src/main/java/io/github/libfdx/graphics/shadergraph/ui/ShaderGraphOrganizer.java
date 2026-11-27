package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute.Wire;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Layered layout working backwards from consumers, with socket-aware ordering within each column. */
final class ShaderGraphOrganizer {
    private static final float COLUMN_GAP = 84, ROW_GAP = 32, COMPONENT_GAP = 96;
    private final Map<ShaderGraphId, Card> cards = new LinkedHashMap<>();
    private final List<Link> links = new ArrayList<>();
    private final List<Card> roots = new ArrayList<>();
    private final Card material;

    private static final class Card {
        final ShaderGraphEditorNode old;
        final float width, height;
        final ShaderGraphPort[] inputPorts, outputPorts;
        final List<Link> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        boolean visited;
        int rank, pending;
        float x, y, desired;

        Card(ShaderGraphEditorNode old, ShaderNode node) {
            this.old = old;
            width = old == null ? ShaderGraphEditorCanvas.OUTPUT_WIDTH : old.width();
            height = old == null ? ShaderGraphEditorCanvas.OUTPUT_HEIGHT : old.height();
            inputPorts = node == null ? null : node.inputs();
            outputPorts = node == null ? null : node.outputs();
        }

    }

    private record Link(Wire wire, Card from, Card to, float sourceY, float targetY) {}

    ShaderGraphOrganizer(ShaderGraph graph, ShaderGraphEditorData data) {
        var saved = new HashMap<ShaderGraphId, ShaderGraphEditorNode>();
        for (var node : data.nodes()) saved.put(node.nodeId(), node);
        for (ShaderNode node : graph.nodes()) {
            var box = saved.get(node.id());
            if (box == null) box = ShaderGraphEditorLayout.newNode(node, 0, 0);
            cards.put(node.id(), new Card(box, node));
        }
        material = graph.kind() == ShaderGraphKind.SURFACE ? new Card(null, null) : null;
        for (var edge : graph.edges()) {
            var wire = Wire.of(edge);
            var from = cards.get(wire.source().nodeId());
            var to = cards.get(wire.target().nodeId());
            if (from != null && to != null) connect(wire, from, to,
                    socket(from.outputPorts, wire.source().portId()), socket(to.inputPorts, wire.target().portId()));
        }
        var outputs = graph.outputs();
        if (material != null) {
            roots.add(material);
            for (var channel : MaterialOutputChannel.values()) {
                var output = channel.binding(outputs);
                if (output == null) continue;
                var from = cards.get(output.source().nodeId());
                if (from != null) connect(Wire.of(output), from, material,
                        socket(from.outputPorts, output.source().portId()), ShaderGraphEditorCanvas.materialSocketY(channel.ordinal()));
            }
        }
        for (var output : outputs) {
            var from = cards.get(output.source().nodeId());
            if (from != null) roots.add(from);
        }
    }

    private static float socket(ShaderGraphPort[] ports, ShaderGraphId id) {
        for (int i = 0; i < ports.length; i++) {
            if (ports[i].id().equals(id)) return ShaderGraphEditorLayout.socketY(i);
        }
        return ShaderGraphEditorLayout.socketY(0);
    }

    private void connect(Wire wire, Card from, Card to, float sourceY, float targetY) {
        var link = new Link(wire, from, to, sourceY, targetY);
        from.outputs.add(link);
        to.inputs.add(link);
        links.add(link);
    }

    ShaderGraphEditorData arrange(ShaderGraphEditorData data) {
        float top = 0;
        for (Card root : roots) {
            if (!root.visited) top = arrangeComponent(root, top);
        }
        // Keep unfinished branches together and below the outputs. Pack loose cards on a shelf.
        for (Card card : cards.values()) {
            if (!card.visited && (!card.inputs.isEmpty() || !card.outputs.isEmpty())) top = arrangeComponent(card, top);
        }
        float x = 0, rowHeight = 0;
        int loose = 0;
        for (Card card : cards.values()) {
            if (card.visited) continue;
            if (loose > 0 && loose % 4 == 0) {
                top += rowHeight + ROW_GAP;
                x = rowHeight = 0;
            }
            card.x = x;
            card.y = top;
            x += card.width + COLUMN_GAP;
            rowHeight = Math.max(rowHeight, card.height);
            loose++;
        }
        var nodes = new ShaderGraphEditorNode[cards.size()];
        int i = 0;
        for (Card card : cards.values()) {
            nodes[i++] = ShaderGraphEditorNode.of(card.old.nodeId().value(), card.x, card.y,
                    card.width, card.height, card.old.collapsed());
        }
        var positioned = data.withNodes(nodes);
        if (material != null) positioned = positioned.withMaterialOutput(new ShaderGraphEditorData.Position(material.x, material.y));
        return repositionPins(data, positioned);
    }

    private float arrangeComponent(Card seed, float top) {
        var component = new ArrayList<Card>();
        seed.visited = true;
        component.add(seed);
        for (int i = 0; i < component.size(); i++) {
            Card card = component.get(i);
            for (Link link : card.inputs) visit(component, link.from);
            for (Link link : card.outputs) visit(component, link.to);
        }
        var ready = new ArrayDeque<Card>();
        for (Card card : component) {
            card.pending = card.outputs.size();
            if (card.pending == 0) ready.add(card);
        }
        int depth = 0;
        while (!ready.isEmpty()) {
            Card to = ready.remove();
            for (Link link : to.inputs) {
                Card from = link.from;
                from.rank = Math.max(from.rank, to.rank + 1);
                depth = Math.max(depth, from.rank);
                if (--from.pending == 0) ready.add(from);
            }
        }
        // Cyclic drafts stay visible in a separate column, even when the shader cannot compile.
        var columns = new ArrayList<List<Card>>();
        for (int i = 0; i <= depth + 1; i++) columns.add(new ArrayList<>());
        for (Card card : component) columns.get(card.pending == 0 ? card.rank : depth + 1).add(card);
        float x = 0;
        for (var column : columns.reversed()) {
            float y = 0, width = 0;
            for (Card card : column) {
                card.x = x;
                card.y = y;
                y += card.height + ROW_GAP;
                width = Math.max(width, card.width);
            }
            if (!column.isEmpty()) x += width + COLUMN_GAP;
        }
        for (int sweep = 0; sweep < 4; sweep++) {
            for (var column : columns) align(column, true);
            for (var column : columns.reversed()) align(column, false);
        }
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        for (Card card : component) {
            min = Math.min(min, card.y);
            max = Math.max(max, card.y + card.height);
        }
        for (Card card : component) card.y = Math.round(card.y - min) + top;
        return top + Math.round(max - min) + COMPONENT_GAP;
    }

    private static void visit(List<Card> component, Card card) {
        if (card.visited) return;
        card.visited = true;
        component.add(card);
    }

    private static void align(List<Card> column, boolean towardsConsumers) {
        if (column.isEmpty()) return;
        for (Card card : column) {
            float sum = 0, weight = 0;
            for (Link link : towardsConsumers ? card.outputs : card.inputs) {
                float dx = link.to.x - link.from.x;
                if (dx <= 0) continue;
                float wanted = towardsConsumers ? link.to.y + link.targetY - link.sourceY
                        : link.from.y + link.sourceY - link.targetY;
                sum += wanted / dx;
                weight += 1 / dx;
            }
            card.desired = weight == 0 ? card.y : sum / weight;
        }
        column.sort(Comparator.comparingDouble(card -> card.desired));
        // Pool overlapping runs. Each run is centered on its preferred socket positions
        // while retaining the minimum clearance between consecutive cards.
        int n = column.size(), runs = 0;
        var start = new int[n];
        var means = new float[n];
        float offset = 0;
        for (int i = 0; i < n; i++) {
            var card = column.get(i);
            start[runs] = i;
            means[runs++] = card.desired - offset;
            offset += card.height + ROW_GAP;
            while (runs > 1 && means[runs - 2] > means[runs - 1]) {
                int a = runs - 2, b = runs - 1;
                means[a] += (means[b] - means[a]) * (i + 1 - start[b]) / (i + 1 - start[a]);
                runs--;
            }
        }
        int run = 0;
        offset = 0;
        for (int i = 0; i < n; i++) {
            if (run + 1 < runs && start[run + 1] == i) run++;
            var card = column.get(i);
            card.y = means[run] + offset;
            offset += card.height + ROW_GAP;
        }
    }

    private ShaderGraphEditorData repositionPins(ShaderGraphEditorData before, ShaderGraphEditorData after) {
        if (before.routes().isEmpty()) return after;
        var routes = new HashMap<>(before.routes());
        for (Link link : links) {
            var route = routes.get(link.wire);
            if (route == null || route.pins().isEmpty()) continue;
            float dx = link.from.x - link.from.old.x(), dy = link.from.y - link.from.old.y();
            var oldOutput = before.materialOutput();
            float endX = link.to.old != null ? link.to.old.x() : oldOutput == null ? link.to.x : oldOutput.x();
            float endY = link.to.old != null ? link.to.old.y() : oldOutput == null ? link.to.y : oldOutput.y();
            float ex = link.to.x - endX, ey = link.to.y - endY;
            if (dx == 0 && dy == 0 && ex == 0 && ey == 0) continue;
            float sx = link.from.old.x() + link.from.width, sy = link.from.old.y() + link.sourceY;
            float spanX = endX - sx, spanY = endY + link.targetY - sy;
            float lengthSquared = spanX * spanX + spanY * spanY;
            var pins = new ArrayList<ShaderGraphWireRoute.Pin>();
            for (var pin : route.pins()) {
                float fraction = lengthSquared == 0 ? (pins.size() + 1f) / (route.pins().size() + 1f)
                        : Math.clamp(((pin.x() - sx) * spanX + (pin.y() - sy) * spanY) / lengthSquared, 0f, 1f);
                pins.add(new ShaderGraphWireRoute.Pin(pin.x() + dx + fraction * (ex - dx),
                        pin.y() + dy + fraction * (ey - dy), pin.straightIn(), pin.rotationDegrees()));
            }
            routes.put(link.wire, new ShaderGraphWireRoute(pins, route.straightEnd()));
        }
        return after.withRoutes(routes);
    }

}
