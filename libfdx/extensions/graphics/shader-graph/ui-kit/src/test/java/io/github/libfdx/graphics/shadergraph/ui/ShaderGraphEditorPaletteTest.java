package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphEditorPaletteTest {
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
