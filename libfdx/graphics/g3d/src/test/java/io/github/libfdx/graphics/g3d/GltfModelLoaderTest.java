package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.math.Matrix4;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class GltfModelLoaderTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void loadsHierarchySkinWeightsAndLinearAnimation() {
        Model model = new GltfModelLoader(new FakeGraphicsContext())
                .loadModelBytes("embedded.gltf", skinnedGltf().getBytes(StandardCharsets.UTF_8));

        assertEquals(1, model.nodes().size());
        ModelNode root = model.nodes().get(0);
        assertEquals("meshNode", root.id());
        assertTranslation(root.localTransform(), 0.0f, 0.0f, 0.0f);
        assertEquals(1, root.children().size());
        assertEquals("joint", root.children().get(0).id());
        assertEquals(1, root.parts().size());

        ModelNodePart part = root.parts().get(0);
        assertNotNull(part.skin());
        assertEquals(12, part.joints().length);
        assertEquals(12, part.weights().length);
        assertEquals(0, part.joints()[0]);
        assertEquals(1.0f, part.weights()[0], EPSILON);
        assertEquals(Mesh.PBR_SKINNED_LAYOUT, part.meshPart().mesh().vertexLayout());
        assertNotNull(part.meshPart().mesh().sourcePositions());
        assertEquals(12, part.meshPart().mesh().sourceJoints().length);
        assertEquals(12, part.meshPart().mesh().sourceWeights().length);
        assertEquals(9, part.meshPart().mesh().sourcePositions().length);

        assertEquals(1, model.skins().size());
        Skin skin = model.skins().get(0);
        assertEquals("skin0", skin.id());
        assertEquals(1, skin.skeleton().bones().size());
        Bone bone = skin.skeleton().bones().get(0);
        assertEquals("joint", bone.id());
        assertEquals(-1, bone.parentIndex());
        assertTranslation(bone.inverseBindTransform(), 0.0f, -1.0f, 0.0f);

        assertEquals(1, model.animations().size());
        DefaultModelInstance instance = new DefaultModelInstance(model);
        new AnimationController(instance).play(model.animations().get(0), false).time(0.5f);
        assertTranslation(instance.nodeTransform("joint"), 0.0f, 2.0f, 0.0f);

        SkinningPalette palette = new SkinningPalette(skin).update(instance);
        assertTranslation(palette.boneMatrix(0), 0.0f, 1.0f, 0.0f);
    }

    private static String skinnedGltf() {
        BinaryGltfData data = binaryGltfData();
        return "{"
                + "\"asset\":{\"version\":\"2.0\"},"
                + "\"buffers\":[{\"uri\":\"data:application/octet-stream;base64," + data.base64 + "\",\"byteLength\":"
                + data.byteLength + "}],"
                + "\"bufferViews\":["
                + "{\"buffer\":0,\"byteOffset\":0,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":36,\"byteLength\":36},"
                + "{\"buffer\":0,\"byteOffset\":72,\"byteLength\":24},"
                + "{\"buffer\":0,\"byteOffset\":96,\"byteLength\":24},"
                + "{\"buffer\":0,\"byteOffset\":120,\"byteLength\":48},"
                + "{\"buffer\":0,\"byteOffset\":168,\"byteLength\":64},"
                + "{\"buffer\":0,\"byteOffset\":232,\"byteLength\":8},"
                + "{\"buffer\":0,\"byteOffset\":240,\"byteLength\":24}"
                + "],"
                + "\"accessors\":["
                + "{\"bufferView\":0,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":1,\"componentType\":5126,\"count\":3,\"type\":\"VEC3\"},"
                + "{\"bufferView\":2,\"componentType\":5126,\"count\":3,\"type\":\"VEC2\"},"
                + "{\"bufferView\":3,\"componentType\":5123,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":4,\"componentType\":5126,\"count\":3,\"type\":\"VEC4\"},"
                + "{\"bufferView\":5,\"componentType\":5126,\"count\":1,\"type\":\"MAT4\"},"
                + "{\"bufferView\":6,\"componentType\":5126,\"count\":2,\"type\":\"SCALAR\"},"
                + "{\"bufferView\":7,\"componentType\":5126,\"count\":2,\"type\":\"VEC3\"}"
                + "],"
                + "\"meshes\":[{\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,"
                + "\"TEXCOORD_0\":2,\"JOINTS_0\":3,\"WEIGHTS_0\":4}}]}],"
                + "\"nodes\":["
                + "{\"name\":\"meshNode\",\"mesh\":0,\"skin\":0,\"children\":[1]},"
                + "{\"name\":\"joint\",\"translation\":[0,1,0]}"
                + "],"
                + "\"skins\":[{\"name\":\"skin0\",\"joints\":[1],\"inverseBindMatrices\":5}],"
                + "\"animations\":[{\"name\":\"moveJoint\",\"samplers\":[{\"input\":6,\"output\":7,"
                + "\"interpolation\":\"LINEAR\"}],\"channels\":[{\"sampler\":0,"
                + "\"target\":{\"node\":1,\"path\":\"translation\"}}]}],"
                + "\"scenes\":[{\"nodes\":[0]}],\"scene\":0"
                + "}";
    }

    private static BinaryGltfData binaryGltfData() {
        ByteBuffer buffer = ByteBuffer.allocate(264).order(ByteOrder.LITTLE_ENDIAN);
        putFloats(buffer,
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f);
        putFloats(buffer,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 1.0f);
        putFloats(buffer,
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f);
        putShorts(buffer,
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0);
        putFloats(buffer,
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f, 0.0f);
        putFloats(buffer, Matrix4.translation(0.0f, -1.0f, 0.0f).values());
        putFloats(buffer, 0.0f, 1.0f);
        putFloats(buffer,
                0.0f, 1.0f, 0.0f,
                0.0f, 3.0f, 0.0f);
        byte[] bytes = new byte[buffer.position()];
        buffer.flip();
        buffer.get(bytes);
        return new BinaryGltfData(Base64.getEncoder().encodeToString(bytes), bytes.length);
    }

    private static void putFloats(ByteBuffer buffer, float... values) {
        for (int i = 0; i < values.length; i++) {
            buffer.putFloat(values[i]);
        }
    }

    private static void putShorts(ByteBuffer buffer, int... values) {
        for (int i = 0; i < values.length; i++) {
            buffer.putShort((short)values[i]);
        }
    }

    private static void assertTranslation(Matrix4 matrix, float x, float y, float z) {
        float[] values = matrix.values();
        assertEquals(x, values[12], EPSILON);
        assertEquals(y, values[13], EPSILON);
        assertEquals(z, values[14], EPSILON);
    }

    private static final class BinaryGltfData {
        private final String base64;
        private final int byteLength;

        BinaryGltfData(String base64, int byteLength) {
            this.base64 = base64;
            this.byteLength = byteLength;
        }
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test");
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();

        @Override
        public FakeGraphicsDevice device() {
            return device;
        }

        @Override
        public TextureFormat surfaceFormat() {
            return TextureFormat.RGBA8_UNORM;
        }

        @Override
        public GraphicsFrame currentFrame() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear(float red, float green, float blue, float alpha) {
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeGraphicsDevice implements GraphicsDevice {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-device");

        @Override
        public Buffer createBuffer(BufferDescriptor descriptor) {
            return new FakeBuffer(descriptor.size(), descriptor.usage());
        }

        @Override
        public void writeBuffer(Buffer buffer, ByteBuffer data) {
        }

        @Override
        public Texture createTexture(TextureDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTexture(Texture texture, ByteBuffer data) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ShaderModule createShaderModule(ShaderModuleDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPipeline createRenderPipeline(RenderPipelineDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }

    private static final class FakeBuffer implements Buffer {
        private static final ProviderId PROVIDER_ID = ProviderId.of("test-buffer");
        private final int size;
        private final BufferUsage usage;
        private boolean disposed;

        FakeBuffer(int size, BufferUsage usage) {
            this.size = size;
            this.usage = usage;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public BufferUsage usage() {
            return usage;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        public <T> T as() {
            return null;
        }
    }
}
