package io.github.libfdx.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TextureDescriptorTest {
    @Test
    void sampledTextureFilterDefaultsToLinear() {
        TextureDescriptor descriptor = TextureDescriptor.rgba8("texture", 8, 8);

        assertEquals(TextureFilter.LINEAR, descriptor.filter());
    }

    @Test
    void nullTextureFilterFallsBackToLinear() {
        TextureDescriptor descriptor = TextureDescriptor.rgba8("texture", 8, 8)
                .filter(TextureFilter.NEAREST)
                .filter(null);

        assertEquals(TextureFilter.LINEAR, descriptor.filter());
    }

    @Test
    void nearestTextureFilterCanBeRequested() {
        TextureDescriptor descriptor = TextureDescriptor.rgba8("texture", 8, 8)
                .filter(TextureFilter.NEAREST);

        assertEquals(TextureFilter.NEAREST, descriptor.filter());
    }
}
