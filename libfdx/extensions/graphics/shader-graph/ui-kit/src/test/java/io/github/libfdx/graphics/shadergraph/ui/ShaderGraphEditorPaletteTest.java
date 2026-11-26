package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorPaletteTest {
    @Test
    void displaysOperandRolesForSavedPositionalPorts() {
        var builder = new ShaderGraphBuilder("example", ShaderGraphKind.FUNCTION);
        var scalar = builder.floatValue(1);
        var xy = builder.construct("xy", ShaderGraphType.vector(ShaderScalarType.F32, 2), scalar, scalar);
        builder.construct("xyz", ShaderGraphType.vector(ShaderScalarType.F32, 3), xy, scalar);
        builder.output("result", builder.lerp("blend", scalar, scalar, scalar));
        var graph = builder.build();
        var document = ShaderGraphEditorDocument.of(graph);
        var blend = graph.node(ShaderGraphId.of("blend"));

        assertEquals(List.of("in000000", "in000001", "in000002"),
                Arrays.stream(blend.inputs()).map(port -> port.id().value()).toList());
        assertEquals(List.of("Start", "End", "Weight"), List.of(ShaderGraphEditorInputLabels.forNode(blend, document)));
        assertEquals(List.of("X", "Y"), List.of(ShaderGraphEditorInputLabels.forNode(
                graph.node(ShaderGraphId.of("xy")), document)));
        assertEquals(List.of("XY", "Z"), List.of(ShaderGraphEditorInputLabels.forNode(
                graph.node(ShaderGraphId.of("xyz")), document)));
        var fresnel = ShaderGraphEditorPalette.standard().templates(ShaderGraphKind.SURFACE, "Fresnel")[0].create("rim");
        assertEquals(List.of("Normal", "Power", "View direction"),
                List.of(ShaderGraphEditorInputLabels.forNode(fresnel, document)));
    }

    @Test
    void preservesAuthoredPortNamesAndNumbersUnknownPositionalInputs() {
        var type = ShaderGraphEditorFixtures.F32;
        var node = ShaderNode.of("material", "custom.material", 1, new ShaderGraphPort[] {
                ShaderGraphPort.required("roughness", type), ShaderGraphPort.required("in000000", type)
        }, new ShaderGraphPort[] {ShaderGraphPort.required("value", type)});
        var document = ShaderGraphEditorDocument.of(ShaderGraphEditorFixtures.editableFunction("example"));
        assertEquals(List.of("Input 1", "roughness"), List.of(ShaderGraphEditorInputLabels.forNode(node, document)));
    }

    @Test
    void surfacePaletteExposesTheEffectNodeInputs() {
        var palette = ShaderGraphEditorPalette.standard();
        assertEquals(List.of("edge0", "edge1", "value"), inputs(palette, "Smoothstep"));
        assertEquals(List.of("uv"), inputs(palette, "Noise 2D"));
        assertEquals(List.of("normal", "power", "view"), inputs(palette, "Fresnel"));
    }

    private static List<String> inputs(ShaderGraphEditorPalette palette, String label) {
        var matches = palette.templates(ShaderGraphKind.SURFACE, label);
        assertEquals(1, matches.length);
        return Arrays.stream(matches[0].create("effect").inputs()).map(port -> port.id().value()).toList();
    }

    @Test
    void filtersTypedTemplatesWithoutEmbeddingUiInSemantics() {
        ShaderGraphEditorPalette palette =
                ShaderGraphEditorPalette.standard();
        ShaderGraphEditorNodeTemplate[] compute =
                palette.templates(ShaderGraphKind.COMPUTE, "math");
        ShaderGraphEditorNodeTemplate[] fragment =
                palette.templates(ShaderGraphKind.FRAGMENT, "custom");

        assertFalse(compute.length == 0);
        assertEquals(1, fragment.length);
        ShaderNode node = compute[0].create("created");
        assertEquals("created", node.id().value());
        assertTrue(node.outputs().length > 0);
        assertTrue(Arrays.stream(palette.templates())
                .noneMatch(template -> template.getClass().getName()
                        .startsWith("io.github.libfdx.ui")));
    }
}
