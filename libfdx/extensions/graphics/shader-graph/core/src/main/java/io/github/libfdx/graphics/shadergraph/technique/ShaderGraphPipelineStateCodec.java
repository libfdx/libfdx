package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.BlendComponent;
import io.github.libfdx.graphics.BlendFactor;
import io.github.libfdx.graphics.BlendOperation;
import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.CompareFunction;
import io.github.libfdx.graphics.CullMode;
import io.github.libfdx.graphics.DepthStencilState;
import io.github.libfdx.graphics.FrontFace;
import io.github.libfdx.graphics.MultisampleState;
import io.github.libfdx.graphics.PrimitiveState;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.StencilFaceState;
import io.github.libfdx.graphics.StencilOperation;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import io.github.libfdx.json.JsonWriter;

import java.util.Locale;

/**
 * Deterministic codec for complete graph-owned render-pipeline state.
 */
public final class ShaderGraphPipelineStateCodec {
    public static final int CURRENT_VERSION = 1;

    private ShaderGraphPipelineStateCodec() {
    }

    public static String write(ShaderGraphPipelineState state) {
        if (state == null) {
            throw new FdxException(
                    "Shader graph pipeline state cannot be null");
        }
        return JsonWriter.compact(value(state));
    }

    public static ShaderGraphPipelineState read(String source) {
        return readValue(new JsonReader().parse(source));
    }

    static JsonValue value(ShaderGraphPipelineState state) {
        JsonValue colors = JsonValue.array();
        for (ColorTargetState target : state.colorTargets()) {
            colors.add(color(target));
        }
        JsonValue layouts = JsonValue.array();
        for (VertexLayout layout : state.vertexLayouts()) {
            layouts.add(layout(layout));
        }
        MultisampleState multisample = state.multisample();
        JsonValue root = JsonValue.object()
                .put("format", CURRENT_VERSION)
                .put("primitive", primitive(state.primitive()))
                .put("colorTargets", colors)
                .put("multisample", JsonValue.object()
                        .put("count", multisample.count())
                        .put("mask", multisample.mask())
                        .put("alphaToCoverage",
                                multisample.alphaToCoverageEnabled()))
                .put("vertexLayouts", layouts);
        root.put("depthStencil",
                state.depthStencil() != null
                        ? depth(state.depthStencil())
                        : JsonValue.nullValue());
        return root;
    }

    static ShaderGraphPipelineState readValue(JsonValue root) {
        if (root == null || !root.isObject()
                || root.require("format").intValue()
                        != CURRENT_VERSION) {
            throw new FdxException(
                    "Unsupported shader graph pipeline-state format");
        }
        JsonValue colors = root.require("colorTargets");
        JsonValue layouts = root.require("vertexLayouts");
        requireArray(colors, "color targets");
        requireArray(layouts, "vertex layouts");
        ColorTargetState[] colorValues =
                new ColorTargetState[colors.size()];
        for (int i = 0; i < colorValues.length; i++) {
            colorValues[i] = readColor(colors.require(i));
        }
        VertexLayout[] layoutValues =
                new VertexLayout[layouts.size()];
        for (int i = 0; i < layoutValues.length; i++) {
            layoutValues[i] = readLayout(layouts.require(i));
        }
        JsonValue multisample = root.require("multisample");
        ShaderGraphPipelineState.Builder builder =
                ShaderGraphPipelineState.builder()
                        .primitive(readPrimitive(
                                root.require("primitive")))
                        .colorTargets(colorValues)
                        .multisample(MultisampleState.of(
                                multisample.require("count")
                                        .intValue(),
                                multisample.require("mask")
                                        .intValue(),
                                multisample.require(
                                                "alphaToCoverage")
                                        .booleanValue()))
                        .vertexLayouts(layoutValues);
        JsonValue depth = root.require("depthStencil");
        if (!depth.isNull()) {
            builder.depthStencil(readDepth(depth));
        }
        return builder.build();
    }

    private static JsonValue primitive(PrimitiveState value) {
        return JsonValue.object()
                .put("topology", name(value.topology()))
                .put("frontFace", name(value.frontFace()))
                .put("cullMode", name(value.cullMode()));
    }

    private static PrimitiveState readPrimitive(JsonValue value) {
        return PrimitiveState.of(
                enumValue(PrimitiveTopology.class,
                        value.requireString("topology")),
                enumValue(FrontFace.class,
                        value.requireString("frontFace")),
                enumValue(CullMode.class,
                        value.requireString("cullMode")));
    }

    private static JsonValue color(ColorTargetState value) {
        JsonValue result = JsonValue.object()
                .put("format", name(value.format()))
                .put("writeMask", value.writeMask());
        result.put("blend", value.blend() != null
                ? blend(value.blend()) : JsonValue.nullValue());
        return result;
    }

    private static ColorTargetState readColor(JsonValue value) {
        JsonValue blend = value.require("blend");
        return ColorTargetState.of(
                enumValue(TextureFormat.class,
                        value.requireString("format")),
                blend.isNull() ? null : readBlend(blend),
                value.require("writeMask").intValue());
    }

    private static JsonValue blend(BlendState value) {
        return JsonValue.object()
                .put("color", component(value.color()))
                .put("alpha", component(value.alpha()));
    }

    private static BlendState readBlend(JsonValue value) {
        return BlendState.of(
                readComponent(value.require("color")),
                readComponent(value.require("alpha")));
    }

    private static JsonValue component(BlendComponent value) {
        return JsonValue.object()
                .put("source", name(value.sourceFactor()))
                .put("destination",
                        name(value.destinationFactor()))
                .put("operation", name(value.operation()));
    }

    private static BlendComponent readComponent(JsonValue value) {
        return BlendComponent.of(
                enumValue(BlendFactor.class,
                        value.requireString("source")),
                enumValue(BlendFactor.class,
                        value.requireString("destination")),
                enumValue(BlendOperation.class,
                        value.requireString("operation")));
    }

    private static JsonValue depth(DepthStencilState value) {
        return JsonValue.object()
                .put("format", name(value.format()))
                .put("depthWrite",
                        value.depthWriteEnabled())
                .put("depthCompare",
                        name(value.depthCompare()))
                .put("front", stencil(value.stencilFront()))
                .put("back", stencil(value.stencilBack()))
                .put("readMask", value.stencilReadMask())
                .put("writeMask", value.stencilWriteMask())
                .put("depthBias", value.depthBias())
                .put("depthBiasSlopeScale",
                        value.depthBiasSlopeScale())
                .put("depthBiasClamp",
                        value.depthBiasClamp());
    }

    private static DepthStencilState readDepth(JsonValue value) {
        return DepthStencilState.builder(
                        enumValue(TextureFormat.class,
                                value.requireString("format")))
                .depthWriteEnabled(value.require(
                                "depthWrite")
                        .booleanValue())
                .depthCompare(enumValue(CompareFunction.class,
                        value.requireString("depthCompare")))
                .stencil(readStencil(value.require("front")),
                        readStencil(value.require("back")),
                        value.require("readMask").intValue(),
                        value.require("writeMask").intValue())
                .depthBias(value.require("depthBias").intValue(),
                        value.require("depthBiasSlopeScale")
                                .floatValue(),
                        value.require("depthBiasClamp")
                                .floatValue())
                .build();
    }

    private static JsonValue stencil(StencilFaceState value) {
        return JsonValue.object()
                .put("compare", name(value.compare()))
                .put("fail", name(value.fail()))
                .put("depthFail", name(value.depthFail()))
                .put("pass", name(value.pass()));
    }

    private static StencilFaceState readStencil(JsonValue value) {
        return StencilFaceState.of(
                enumValue(CompareFunction.class,
                        value.requireString("compare")),
                enumValue(StencilOperation.class,
                        value.requireString("fail")),
                enumValue(StencilOperation.class,
                        value.requireString("depthFail")),
                enumValue(StencilOperation.class,
                        value.requireString("pass")));
    }

    private static JsonValue layout(VertexLayout value) {
        JsonValue attributes = JsonValue.array();
        for (VertexAttribute attribute : value.attributes()) {
            attributes.add(JsonValue.object()
                    .put("location", attribute.location())
                    .put("format", name(attribute.format()))
                    .put("offset", attribute.offset()));
        }
        return JsonValue.object()
                .put("stride", value.arrayStride())
                .put("stepMode", name(value.stepMode()))
                .put("attributes", attributes);
    }

    private static VertexLayout readLayout(JsonValue value) {
        JsonValue attributes = value.require("attributes");
        requireArray(attributes, "vertex attributes");
        VertexAttribute[] values =
                new VertexAttribute[attributes.size()];
        for (int i = 0; i < values.length; i++) {
            JsonValue attribute = attributes.require(i);
            values[i] = VertexAttribute.of(
                    attribute.require("location").intValue(),
                    enumValue(VertexFormat.class,
                            attribute.requireString("format")),
                    attribute.require("offset").intValue());
        }
        return VertexLayout.of(
                value.require("stride").intValue(),
                enumValue(VertexStepMode.class,
                        value.requireString("stepMode")),
                values);
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> type, String value) {
        try {
            return Enum.valueOf(type,
                    value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new FdxException(
                    "Unknown " + type.getSimpleName()
                            + " value: " + value,
                    exception);
        }
    }

    private static void requireArray(JsonValue value,
            String label) {
        if (!value.isArray()) {
            throw new FdxException(
                    "Shader graph " + label
                            + " must be an array");
        }
    }
}
