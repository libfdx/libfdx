package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.internal.TextureUploads;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TextureMipmapsTest {
    @Test void oddDimensionsIncludeTheFinalRowAndColumnAndCopyOnlyTheActiveSourceRange() {
        ByteBuffer source = ByteBuffer.allocate(44);
        source.position(4);
        for (int i = 0; i < 9; i++) source.put((byte)(i == 8 ? 255 : 0)).put((byte)0).put((byte)0).put((byte)255);
        source.limit(40).position(4);
        ByteBuffer[] chain = TextureMipmaps.rgba8(source, 3, 3, false, false);
        assertEquals(2, chain.length);
        assertEquals(36, chain[0].remaining());
        assertEquals(28, chain[1].get(0)&255); // One bright texel out of all nine, not an ignored edge.
        assertEquals(255, chain[1].get(3)&255);
        assertEquals(4, source.position());
        assertEquals(40, source.limit());
        source.put(4, (byte)99);
        assertEquals(0, chain[0].get(0));
        assertEquals(5, TextureMipmaps.levelCount(19, 11));
    }

    @Test void srgbAndStraightAlphaUseLinearColorAndCoverageWeights() {
        ByteBuffer pixels = ByteBuffer.wrap(new byte[] {0,0,0,(byte)255, (byte)255,(byte)255,(byte)255,(byte)255});
        var srgb = TextureMipmaps.rgba8(pixels, 2, 1, true, false);
        assertEquals(188, srgb[1].get(0)&255);
        var data = TextureMipmaps.rgba8(pixels, 2, 1, false, false);
        assertEquals(128, data[1].get(0)&255);
        pixels = ByteBuffer.wrap(new byte[] {(byte)255,0,0,(byte)255, 0,0,(byte)255,0});
        var straight = TextureMipmaps.rgba8(pixels, 2, 1, true, true);
        assertEquals(255, straight[1].get(0)&255);
        assertEquals(0, straight[1].get(2)&255);
        assertEquals(128, straight[1].get(3)&255);
        var ignoredAlpha = TextureMipmaps.rgba8(pixels, 2, 1, true, false);
        assertEquals(188, ignoredAlpha[1].get(2)&255);
    }

    @Test void descriptorsGateExactLevelsAndIndependentFiltersBeforeProviderAllocation() {
        var basic = GraphicsCapabilities.conservativeRender();
        var capable = GraphicsCapabilities.builder().colorFormats(TextureFormat.RGBA8_UNORM)
                .clipDepthRange(io.github.libfdx.math.ClipDepthRange.ZERO_TO_ONE)
                .profile(io.github.libfdx.graphics.shader.ShaderProfile.PORTABLE_WEBGPU)
                .feature(GraphicsFeature.TEXTURE_MIP_LEVELS).feature(GraphicsFeature.TEXTURE_MIN_MAG_FILTERS).build();
        var descriptor = TextureDescriptor.rgba8("mips", 19, 11).filter(TextureFilter.NEAREST).mipLevelCount(5);
        assertThrows(FdxException.class, () -> descriptor.validate(basic));
        assertDoesNotThrow(() -> descriptor.validate(capable));
        descriptor.mipLevelCount(6);
        assertThrows(FdxException.class, () -> descriptor.validate(capable));
        descriptor.mipLevelCount(1).filters(TextureFilter.NEAREST, TextureFilter.LINEAR, TextureMipmapFilter.NONE);
        assertThrows(FdxException.class, () -> descriptor.validate(basic));
        descriptor.filter(TextureFilter.NEAREST);
        assertEquals(TextureFilter.NEAREST, descriptor.magFilter());
        assertEquals(TextureMipmapFilter.NONE, descriptor.mipmapFilter());
        assertDoesNotThrow(() -> descriptor.validate(basic));
    }

    @Test void fullUploadsRejectMissingLevelsWrongSizesAndIntegerOverflowWithoutMovingBuffers() {
        Texture texture = new TestTexture(19, 11, 5);
        ByteBuffer[] chain = TextureMipmaps.rgba8(ByteBuffer.allocate(19*11*4), 19, 11, false, false);
        assertDoesNotThrow(() -> TextureUploads.validate(texture, chain));
        assertEquals(9, texture.mipWidth(1));
        assertEquals(5, texture.mipHeight(1));
        assertEquals(1, texture.mipWidth(4));
        assertEquals(1, texture.mipHeight(4));
        assertThrows(FdxException.class, () -> TextureUploads.validateSingle(texture, chain[0]));
        assertThrows(FdxException.class, () -> TextureUploads.validate(texture, new ByteBuffer[] {chain[0]}));
        chain[4].limit(3);
        assertThrows(FdxException.class, () -> TextureUploads.validate(texture, chain));
        for (var level : chain) assertEquals(0, level.position());
        assertThrows(FdxException.class, () -> texture.mipWidth(-1));
        assertThrows(FdxException.class, () -> texture.mipHeight(5));
        assertThrows(FdxException.class, () -> TextureUploads.byteCount(new TestTexture(Integer.MAX_VALUE, 2, 1), 0));
        assertThrows(FdxException.class, () -> TextureMipmaps.rgba8(ByteBuffer.allocate(4), Integer.MAX_VALUE, 2, false, false));
    }

    private record TestTexture(int width, int height, int mipLevelCount) implements Texture {
        public TextureFormat format() { return TextureFormat.RGBA8_UNORM; }
        public TextureUsage usage() { return TextureUsage.SAMPLED; }
        public ProviderId providerId() { return ProviderId.of("test"); }
        @SuppressWarnings("unchecked") public <T> T as() { return (T)this; }
        public void dispose() { }
        public boolean isDisposed() { return false; }
    }
}
