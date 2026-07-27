package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.BuiltInPbrShaderManifest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class PbrShaderManifestTest {
    @Test
    void canonicalSourcesMatchTheirCheckedInManifests() {
        assertSame(BuiltInPbrShaderManifest.staticReflection(),
                BuiltInPbrShaderManifest.requireStaticSource(
                        PbrShaderProvider.PBR_RENDERER_TEMPLATE));
        assertSame(BuiltInPbrShaderManifest.skinnedReflection(),
                BuiltInPbrShaderManifest.requireSkinnedSource(
                        PbrShaderProvider.skinnedPbrRendererTemplate()));
    }

    @Test
    void sourceDriftFailsBeforePipelineCreation() {
        assertThrows(FdxException.class, () -> BuiltInPbrShaderManifest.requireStaticSource(
                PbrShaderProvider.PBR_RENDERER_TEMPLATE + "\n// drift"));
        assertThrows(FdxException.class, () -> BuiltInPbrShaderManifest.requireSkinnedSource(
                PbrShaderProvider.skinnedPbrRendererTemplate() + "\n// drift"));
    }
}
