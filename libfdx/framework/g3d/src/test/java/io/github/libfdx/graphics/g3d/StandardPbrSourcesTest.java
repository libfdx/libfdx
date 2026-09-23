package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.math.ClipDepthRange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandardPbrSourcesTest {
    @Test void workerRecipeMatchesCanonicalSourcesReflectionAndDefaultValuesForEveryVariantAndProfile() {
        for (ShaderProfile profile : ShaderProfile.values()) {
            var capabilities = GraphicsCapabilities.builder().profile(profile)
                    .clipDepthRange(ClipDepthRange.NEGATIVE_ONE_TO_ONE).build();
            var canonical = StandardPbrTechnique.compileCustomization(profile, capabilities,
                    StandardPbrSurfaceGraph.create(), StandardPbrVertexGraph.create(), StandardPbrLightingGraph.create(), null);
            var packet = StandardPbrSources.compile(profile);
            var restored = packet.restore();
            assertEquals(canonical.definition().graph().semanticHash(), restored.definition().graph().semanticHash());
            for (int i = 0; i < 8; i++) {
                var expected = canonical.shader((i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
                var actual = restored.shader((i & 1) != 0, (i & 2) != 0, (i & 4) != 0);
                assertEquals(expected.wgslSource(), actual.wgslSource());
                assertEquals(expected.reflection().fullHash(), actual.reflection().fullHash());
                assertEquals(expected.reflection().physicalHash(), actual.reflection().physicalHash());
            }
            var expected = canonical.defaultMaterial().graphMaterial();
            var actual = restored.defaultMaterial().graphMaterial();
            for (int i = 0; i < expected.definition().parameterCount(); i++) assertEquals(expected.value(i), actual.value(i));
            actual.set("emissive_gain", io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral.f32(5));
            assertTrue(java.util.stream.IntStream.range(0, expected.definition().parameterCount())
                    .anyMatch(i -> !expected.value(i).equals(actual.value(i))), "Restored materials must own independent defaults");
        }
    }

    @Test void sourcePacketRejectsIncompleteVariantsAndSnapshotsTheArray() {
        String[] variants = {"a","b","c","d","e","f","g","h"};
        var packet = new StandardPbrSources(ShaderProfile.PORTABLE_WEBGL2,"surface","library",variants);
        variants[0] = "changed";
        assertEquals("a",packet.variant(0));
        assertThrows(IllegalArgumentException.class, () -> new StandardPbrSources(ShaderProfile.PORTABLE_WEBGL2,"surface","library",new String[8]));
    }
}
