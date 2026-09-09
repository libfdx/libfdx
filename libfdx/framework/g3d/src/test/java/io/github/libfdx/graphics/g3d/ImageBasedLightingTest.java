package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.*;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.math.ClipDepthRange;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

final class ImageBasedLightingTest {
    @Test void halfConversionMatchesJdkForEveryFinitePositiveHalfAndRoundingBoundaries() {
        for (int bits = 0; bits < 0x7c00; bits++) {
            float value = Float.float16ToFloat((short) bits);
            assertEquals(value, ImageBasedLightingData.fromHalf(bits));
            assertEquals(bits, ImageBasedLightingData.half(value) & 0xffff);
            if (bits < 0x7bff) {
                float midpoint = (value + Float.float16ToFloat((short) (bits+1))) * .5f;
                for (float x : new float[] {Math.nextDown(midpoint), midpoint, Math.nextUp(midpoint)}) {
                    if (x >= 0 && x <= 65504) assertEquals(Float.floatToFloat16(x), ImageBasedLightingData.half(x));
                }
            }
        }
        Random random = new Random(3947);
        for (int i=0;i<10000;i++) {
            float x = Float.intBitsToFloat(random.nextInt(0x477fe001));
            assertEquals(Float.floatToFloat16(x), ImageBasedLightingData.half(x));
        }
    }
    @Test void fileRoundTripIsDeterministicAndRejectsMalformedPayloadBeforeUpload() {
        ImageBasedLightingData data = data();
        byte[] encoded = data.encode();
        assertArrayEquals(encoded, ImageBasedLightingData.decode(encoded).encode());
        assertEquals('F', encoded[0]);
        assertEquals(8, data.specularWidth());
        assertEquals(4, data.specularLevelCount());
        for (int offset : new int[] {0,4,8,12,16,20,24,28,32}) {
            byte[] malformed = encoded.clone();
            ByteBuffer.wrap(malformed).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, -1);
            assertThrows(FdxException.class, () -> ImageBasedLightingData.decode(malformed), "offset " + offset);
        }
        assertThrows(FdxException.class, () -> ImageBasedLightingData.decode(Arrays.copyOf(encoded, encoded.length-1)));
        assertThrows(FdxException.class, () -> ImageBasedLightingData.decode(Arrays.copyOf(encoded, encoded.length+1)));
        ByteBuffer view = data.diffusePixels();
        assertTrue(view.isReadOnly());
        view.position(8);
        assertEquals(0, data.diffusePixels().position());
        float[][] spec = specular();
        float[] diffuse = rgb(8, 2), brdf = new float[32];
        ImageBasedLightingData copied = ImageBasedLightingData.of(8, spec, 4, diffuse, 4, brdf);
        byte[] before = copied.encode();
        spec[0][0] = 7; diffuse[0] = 8; brdf[0] = 1;
        assertArrayEquals(before, copied.encode());
        spec[0][0] = Float.NaN;
        assertThrows(FdxException.class, () -> ImageBasedLightingData.of(8, spec, 4, diffuse, 4, brdf));
    }
    @Test void uploadOwnsExactlyThreeTexturesAndRollsBackEveryFailurePosition() {
        for (int failAt=1; failAt<=3; failAt++) {
            Device device = new Device(failAt);
            assertEquals("Injected upload failure", assertThrows(FdxException.class,
                    () -> ImageBasedLighting3D.create(device.context(), data())).getMessage());
            assertEquals(failAt, device.textures.size());
            for (MemoryTexture texture : device.textures) assertEquals(1, texture.disposals);
        }
        Device device = new Device(0);
        ImageBasedLighting3D light = ImageBasedLighting3D.create(device.context(), data());
        assertEquals(4, light.specularTexture().mipLevelCount());
        assertEquals(3, device.uploads);
        Environment3D environment = new Environment3D().imageBasedLighting(light).imageBasedLightingTransform(2, .5f);
        assertSame(light, environment.imageBasedLighting());
        environment.imageBasedLighting(null);
        assertFalse(light.isDisposed());
        assertThrows(FdxException.class, () -> environment.imageBasedLightingTransform(Float.NaN, 0));
        light.dispose(); light.dispose();
        for (MemoryTexture texture : device.textures) assertEquals(1, texture.disposals);
        assertThrows(FdxException.class, light::diffuseTexture);
        assertThrows(FdxException.class, () -> environment.imageBasedLighting(light));
        assertFalse(ImageBasedLighting3D.isSupported(null));
    }
    static ImageBasedLightingData data() { return ImageBasedLightingData.of(8, specular(), 4, rgb(8, 2), 4, new float[32]); }
    private static float[][] specular() { return new float[][] {rgb(32, 3), rgb(8, 2), rgb(2, 1), rgb(1, .5f)}; }
    private static float[] rgb(int pixels, float value) { float[] result = new float[pixels*3]; Arrays.fill(result, value); return result; }

    private static final class Device {
        final ArrayList<MemoryTexture> textures = new ArrayList<>();
        final int failAt;
        int uploads;
        Device(int failAt) { this.failAt = failAt; }
        GraphicsContext context() {
            GraphicsDevice device = (GraphicsDevice) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {GraphicsDevice.class}, (p,m,a) -> {
                switch (m.getName()) {
                    case "capabilities": return GraphicsCapabilities.builder().profile(ShaderProfile.PORTABLE_WEBGL2)
                            .feature(GraphicsFeature.TEXTURE_MIP_LEVELS).colorFormats(TextureFormat.RGBA16_FLOAT)
                            .filterableColorFormats(TextureFormat.RGBA16_FLOAT)
                            .clipDepthRange(ClipDepthRange.NEGATIVE_ONE_TO_ONE).sampleCounts(1)
                            .limits(GraphicsLimits.builder().maxSampledTexturesPerStage(16).maxSamplersPerStage(16).build()).build();
                    case "createTexture": MemoryTexture texture = new MemoryTexture((TextureDescriptor) a[0]); textures.add(texture); return texture;
                    case "writeTexture", "writeTextureMipLevels":
                        if (++uploads == failAt) throw new FdxException("Injected upload failure");
                        MemoryTexture destination = (MemoryTexture) a[0];
                        ByteBuffer[] levels = a[1] instanceof ByteBuffer[] multiple ? multiple : new ByteBuffer[] {(ByteBuffer) a[1]};
                        assertEquals(destination.mipLevelCount(), levels.length);
                        for (int i=0;i<levels.length;i++) assertEquals(destination.mipWidth(i)*destination.mipHeight(i)*8, levels[i].remaining());
                        return null;
                    default: throw new AssertionError(m);
                }
            });
            return (GraphicsContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {GraphicsContext.class}, (p,m,a) -> switch(m.getName()) {
                case "device" -> device;
                case "providerId" -> ProviderId.of("gl");
                default -> throw new AssertionError(m);
            });
        }
    }
    private static final class MemoryTexture implements Texture {
        final TextureDescriptor descriptor;
        int disposals;
        MemoryTexture(TextureDescriptor descriptor) { this.descriptor = descriptor; }
        @Override public int width() { return descriptor.width(); }
        @Override public int height() { return descriptor.height(); }
        @Override public int mipLevelCount() { return descriptor.mipLevelCount(); }
        @Override public TextureFormat format() { return descriptor.format(); }
        @Override public TextureUsage usage() { return descriptor.usage(); }
        @Override public ProviderId providerId() { return ProviderId.of("gl"); }
        @Override public <T> T as() { throw new FdxException("Unavailable"); }
        @Override public void dispose() { disposals++; }
        @Override public boolean isDisposed() { return disposals != 0; }
    }
}
