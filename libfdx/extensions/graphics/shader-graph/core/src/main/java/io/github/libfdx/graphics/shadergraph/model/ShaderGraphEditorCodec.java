package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Codec for optional editor-only sidecar data.
 */
public final class ShaderGraphEditorCodec {
    private ShaderGraphEditorCodec() {
    }

    public static String write(ShaderGraphEditorData data) {
        if (data == null) {
            throw new FdxException("Shader graph editor data cannot be null");
        }
        JsonValue nodes = JsonValue.array();
        for (ShaderGraphEditorNode node : data.nodes()) {
            nodes.add(JsonValue.object()
                    .put("id", node.nodeId().value())
                    .put("x", node.x())
                    .put("y", node.y())
                    .put("width", node.width())
                    .put("height", node.height())
                    .put("collapsed", node.collapsed()));
        }
        JsonValue root = JsonValue.object()
                .put("format", 1)
                .put("graph", data.graphId().value())
                .put("panX", data.panX())
                .put("panY", data.panY())
                .put("zoom", data.zoom())
                .put("nodes", nodes);
        var position = data.materialOutput();
        if (position != null) root.put("materialOutput", JsonValue.object().put("x", position.x()).put("y", position.y()));
        if (!data.routes().isEmpty()) {
            var routes = JsonValue.array();
            data.routes().entrySet().stream().sorted(Map.Entry.comparingByKey(ShaderGraphWireRoute.Wire.ORDER))
                    .forEach(entry -> routes.add(writeRoute(entry.getKey(), entry.getValue())));
            root.put("wireRoutes", routes);
        }
        return JsonWriter.compact(root);
    }

    private static JsonValue writeRoute(ShaderGraphWireRoute.Wire wire, ShaderGraphWireRoute route) {
        var value = JsonValue.object().put("source", ShaderGraphCodec.endpoint(wire.source()));
        if (wire.target() != null) value.put("target", ShaderGraphCodec.endpoint(wire.target()));
        else value.put("output", wire.output().value());
        var pins = JsonValue.array();
        for (var pin : route.pins()) {
            var point = JsonValue.object().put("x", pin.x()).put("y", pin.y()).put("straightIn", pin.straightIn());
            if (pin.rotationDegrees() != 0) point.put("rotationDegrees", pin.rotationDegrees());
            pins.add(point);
        }
        return value.put("pins", pins).put("straightEnd", route.straightEnd());
    }

    public static ShaderGraphEditorData read(String source) {
        JsonValue root = new JsonReader().parse(source);
        if (root.require("format").intValue() != 1) {
            throw new FdxException("Unsupported shader graph editor format");
        }
        JsonValue nodes = root.require("nodes");
        if (!nodes.isArray()) {
            throw new FdxException("Shader graph editor nodes must be an array");
        }
        ShaderGraphEditorNode[] decoded = new ShaderGraphEditorNode[nodes.size()];
        for (int i = 0; i < decoded.length; i++) {
            JsonValue node = nodes.require(i);
            decoded[i] = ShaderGraphEditorNode.of(node.requireString("id"),
                    node.require("x").floatValue(), node.require("y").floatValue(),
                    node.require("width").floatValue(),
                    node.require("height").floatValue(),
                    node.require("collapsed").booleanValue());
        }
        var data = ShaderGraphEditorData.of(root.requireString("graph"), decoded,
                root.require("panX").floatValue(),
                root.require("panY").floatValue(),
                root.require("zoom").floatValue());
        var output = root.get("materialOutput");
        if (output != null) data = data.withMaterialOutput(new ShaderGraphEditorData.Position(
                output.require("x").floatValue(), output.require("y").floatValue()));
        var routes = root.get("wireRoutes");
        return routes == null ? data : data.withRoutes(readRoutes(routes));
    }

    private static Map<ShaderGraphWireRoute.Wire, ShaderGraphWireRoute> readRoutes(JsonValue routes) {
        if (!routes.isArray()) throw new FdxException("Wire routes must be an array");
        var result = new HashMap<ShaderGraphWireRoute.Wire, ShaderGraphWireRoute>();
        for (int i = 0; i < routes.size(); i++) {
            var value = routes.require(i);
            var target = value.get("target");
            var wire = new ShaderGraphWireRoute.Wire(ShaderGraphCodec.readEndpoint(value.require("source")),
                    target == null ? null : ShaderGraphCodec.readEndpoint(target),
                    value.get("output") == null ? null : ShaderGraphId.of(value.requireString("output")));
            var points = value.require("pins");
            if (!points.isArray()) throw new FdxException("Wire pins must be an array");
            var pins = new ArrayList<ShaderGraphWireRoute.Pin>(points.size());
            for (int p = 0; p < points.size(); p++) {
                var pin = points.require(p);
                var rotation = pin.get("rotationDegrees");
                pins.add(new ShaderGraphWireRoute.Pin(pin.require("x").floatValue(), pin.require("y").floatValue(),
                        pin.require("straightIn").booleanValue(), rotation == null ? 0 : rotation.floatValue()));
            }
            if (result.put(wire, new ShaderGraphWireRoute(pins, value.require("straightEnd").booleanValue())) != null)
                throw new FdxException("Duplicate wire route");
        }
        return result;
    }

}
