package io.github.libfdx.graphics.g3d;

import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.*;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.json.JsonValue;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class GltfTexturesTest {
    @Test void textureTransformsSelectOverrideUvAndApplyScaleThenRotationThenOffset() {
        JsonValue info = new JsonReader().parse("""
            {"index":0,"texCoord":0,"extensions":{"KHR_texture_transform":{
              "texCoord":1,"offset":[0.3,-0.2],"rotation":1.5707963267948966,"scale":[-2,3]}}}
            """);
        TextureCoordinates uv = GltfTextures.coordinates(info);
        assertEquals(1, uv.set());
        assertEquals(-1.2f, uv.u(.25f, .5f), .000001f);
        assertEquals(-.7f, uv.v(.25f, .5f), .000001f);
        assertSame(TextureCoordinates.UV0, GltfTextures.coordinates(JsonValue.object()));
        for (String invalid : new String[] {"{\"texCoord\":2}", "{\"texCoord\":-1}",
                "{\"extensions\":{\"KHR_texture_transform\":{\"scale\":[1]}}}",
                "{\"extensions\":{\"KHR_texture_transform\":{\"rotation\":1e100}}}"})
            assertThrows(RuntimeException.class, () -> GltfTextures.coordinates(new JsonReader().parse(invalid)));
    }

    @Test void vertexBakingHonorsUvSelectionAddressingAndLinearColorInterpolation() {
        JsonValue root = root();
        root.put("samplers", JsonValue.array().add(JsonValue.object().put("magFilter", 9728).put("wrapS", 33648)));
        root.require("textures").require(0).put("sampler", 0);
        GltfTextures prepared = new GltfTextures(root);
        prepared.prepare(new ImageData[] {image(255,0,0,255, 0,255,0,255)});
        var binding = prepared.binding(JsonValue.object().put("index", 0).put("texCoord", 1));
        assertEquals(1, binding.sample(.8f,0, -.2f,0, false).red());
        assertEquals(1, binding.sample(.1f,0, 1.2f,0, false).green());
        prepared = new GltfTextures(root());
        prepared.prepare(new ImageData[] {image(0,0,0,255, 255,255,255,255)});
        binding = prepared.binding(JsonValue.object().put("index", 0));
        assertEquals(.5f, binding.sample(.5f,0,0,0,true).red(), .000001f);
        // Repeat taps straddle the seam, using texel centers rather than width-1 interpolation.
        assertEquals(.5f, binding.sample(0,0,0,0,true).red(), .000001f);
    }
    @Test void importsEveryMinMagCombinationAndAddressMode() {
        int[] filters = {9728, 9729, 9984, 9985, 9986, 9987};
        for (int mag : new int[] {9728, 9729}) for (int min : filters) {
            JsonValue root = root();
            root.put("samplers", JsonValue.array().add(JsonValue.object().put("minFilter", min)
                    .put("magFilter", mag).put("wrapS", 33648).put("wrapT", 33071)));
            root.require("textures").require(0).put("sampler", 0);
            Capture capture = upload(root, new ImageData(19, 11, ByteBuffer.allocateDirect(19*11*4)));
            assertEquals(1, capture.descriptors.size());
            TextureDescriptor d = capture.descriptors.get(0);
            assertEquals(min == 9728 || min == 9984 || min == 9986 ? TextureFilter.NEAREST : TextureFilter.LINEAR, d.minFilter());
            assertEquals(mag == 9728 ? TextureFilter.NEAREST : TextureFilter.LINEAR, d.magFilter());
            assertEquals(min < 9984 ? TextureMipmapFilter.NONE : min < 9986 ? TextureMipmapFilter.NEAREST : TextureMipmapFilter.LINEAR, d.mipmapFilter());
            assertEquals(min < 9984 ? 1 : 5, d.mipLevelCount());
            assertEquals(d.mipLevelCount(), capture.uploads.get(0).length);
            assertEquals(TextureWrap.MIRRORED_REPEAT, d.wrapS());
            assertEquals(TextureWrap.CLAMP_TO_EDGE, d.wrapT());
        }
        TextureDescriptor defaults = upload(root(), image(0,0,0,255, 255,255,255,255)).descriptors.get(0);
        assertEquals(TextureFilter.LINEAR, defaults.minFilter());
        assertEquals(TextureFilter.LINEAR, defaults.magFilter());
        assertEquals(TextureMipmapFilter.NONE, defaults.mipmapFilter());
        assertEquals(TextureWrap.REPEAT, defaults.wrapS());
    }

    @Test void sharedImagesHaveDistinctColorDataAndAlphaMipsAndUnusedTexturesHaveNoUpload() {
        JsonValue root = root();
        root.put("samplers", JsonValue.array().add(JsonValue.object().put("minFilter", 9987)));
        root.require("textures").require(0).put("sampler", 0);
        root.require("textures").add(JsonValue.object().put("source", 0)); // Unused.
        JsonValue material = root.require("materials").require(0);
        material.put("normalTexture", JsonValue.object().put("index", 0));
        material.put("emissiveTexture", JsonValue.object().put("index", 0));
        root.require("materials").add(JsonValue.object().put("alphaMode", "BLEND")
                .put("pbrMetallicRoughness", JsonValue.object()
                        .put("baseColorTexture", JsonValue.object().put("index", 0))));
        Capture capture = upload(root, image(255,0,0,255, 0,0,255,0));
        assertEquals(3, capture.uploads.size());
        assertEquals(TextureFormat.RGBA8_UNORM, capture.descriptors.get(0).format());
        assertEquals(TextureFormat.RGBA8_UNORM_SRGB, capture.descriptors.get(1).format());
        assertEquals(TextureFormat.RGBA8_UNORM_SRGB, capture.descriptors.get(2).format());
        assertArrayEquals(new int[] {128,0,128,128}, rgba(capture.uploads.get(0)[1]));
        assertArrayEquals(new int[] {188,0,188,128}, rgba(capture.uploads.get(1)[1]));
        assertArrayEquals(new int[] {255,0,0,128}, rgba(capture.uploads.get(2)[1]));
    }

    @Test void invalidSamplerAndReferencesFailDuringDocumentPreparation() {
        for (String invalid : new String[] {
                "{\"minFilter\":7}", "{\"magFilter\":9987}", "{\"wrapS\":4}",
                "{\"wrapT\":-1}", "{\"minFilter\":9728.5}"}) {
            JsonValue root = root();
            root.put("samplers", JsonValue.array().add(new JsonReader().parse(invalid)));
            assertThrows(FdxException.class, () -> new GltfTextures(root));
        }
        for (String field : new String[] {"source", "sampler"}) for (int invalid : new int[] {-2, 4}) {
            JsonValue root = root();
            root.require("textures").require(0).put(field, invalid);
            assertThrows(FdxException.class, () -> new GltfTextures(root));
        }
        JsonValue root = root();
        root.require("materials").require(0).require("pbrMetallicRoughness")
                .require("baseColorTexture").put("index", 3);
        assertThrows(FdxException.class, () -> new GltfTextures(root));
    }

    private static JsonValue root() {
        return new JsonReader().parse("""
            {"images":[{}], "textures":[{"source":0}], "materials":[{
              "pbrMetallicRoughness":{"baseColorTexture":{"index":0}}}]}
            """);
    }
    private static ImageData image(int... bytes) {
        ByteBuffer data = ByteBuffer.allocateDirect(bytes.length);
        for (int value : bytes) data.put((byte)value);
        data.flip();
        return new ImageData(bytes.length/4, 1, data);
    }
    private static int[] rgba(ByteBuffer value) {
        return new int[] {value.get(0)&255, value.get(1)&255, value.get(2)&255, value.get(3)&255};
    }
    private static Capture upload(JsonValue root, ImageData image) {
        GltfTextures prepared = new GltfTextures(root);
        prepared.prepare(new ImageData[] {image});
        Capture capture = new Capture();
        GraphicsDevice device = (GraphicsDevice)Proxy.newProxyInstance(GraphicsDevice.class.getClassLoader(),
                new Class<?>[] {GraphicsDevice.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "capabilities" -> GraphicsCapabilities.conservativeRender();
                    case "createTexture" -> {
                        capture.descriptors.add((TextureDescriptor)args[0]);
                        yield Proxy.newProxyInstance(Texture.class.getClassLoader(), new Class<?>[] {Texture.class},
                                (p, m, a) -> { throw new UnsupportedOperationException(m.getName()); });
                    }
                    case "writeTextureMipLevels" -> { capture.uploads.add((ByteBuffer[])args[1]); yield null; }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        prepared.upload(device, "test", prepared.allocateHandles());
        return capture;
    }
    private static final class Capture {
        final List<TextureDescriptor> descriptors = new ArrayList<>();
        final List<ByteBuffer[]> uploads = new ArrayList<>();
    }
}
