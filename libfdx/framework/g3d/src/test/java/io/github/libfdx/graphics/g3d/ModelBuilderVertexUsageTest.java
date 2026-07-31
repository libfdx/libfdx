package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;
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
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ModelBuilderVertexUsageTest {
    private static final float EPSILON = 0.00001f;
    private static final ProviderId PROVIDER_ID =
            ProviderId.of("model-builder-vertex-usage-test");

    @Test
    void defaultMethodsPreservePositionColorOutput() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        Model cube = new ModelBuilder(graphics).cube(2.0f);
        Model box = new ModelBuilder(graphics).box(2.0f, 4.0f, 6.0f);
        Model sphere = new ModelBuilder(graphics).sphere(2.0f, 8);
        try {
            assertPositionColor(mesh(cube));
            assertPositionColor(mesh(box));
            assertPositionColor(mesh(sphere));
            assertEquals(36, mesh(cube).vertexCount());
            assertEquals(36, mesh(box).vertexCount());
        }
        finally {
            cube.dispose();
            box.dispose();
            sphere.dispose();
        }
    }

    @Test
    void usageSelectsEachSupportedLayoutAndWhiteFallback() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        float[] positions = {
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        };
        float[] colors = {
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f
        };
        Model position = new ModelBuilder(graphics).triangles("position",
                positions, null, colors, ModelVertexUsage.POSITION);
        Model positionColor = new ModelBuilder(graphics).triangles(
                "position-color", positions, null, colors,
                ModelVertexUsage.DEFAULT);
        Model positionNormal = new ModelBuilder(graphics).triangles(
                "position-normal", positions, null, colors,
                ModelVertexUsage.POSITION | ModelVertexUsage.NORMAL);
        Model positionNormalColor = new ModelBuilder(graphics).triangles(
                "position-normal-color", positions, null, colors,
                ModelVertexUsage.ALL);
        try {
            assertSame(Mesh.POSITION_LAYOUT, mesh(position).vertexLayout());
            assertSame(Mesh.POSITION_COLOR_LAYOUT,
                    mesh(positionColor).vertexLayout());
            assertSame(Mesh.POSITION_NORMAL_LAYOUT,
                    mesh(positionNormal).vertexLayout());
            assertSame(Mesh.POSITION_NORMAL_COLOR_LAYOUT,
                    mesh(positionNormalColor).vertexLayout());
            assertWhite(mesh(position).sourceColors());
            assertWhite(mesh(positionNormal).sourceColors());
            assertArrayEquals(new float[] {
                    1.0f, 0.0f, 0.0f, 1.0f,
                    0.0f, 1.0f, 0.0f, 1.0f,
                    0.0f, 0.0f, 1.0f, 1.0f
            }, mesh(positionColor).sourceColors(), EPSILON);
            assertArrayEquals(mesh(positionColor).sourceColors(),
                    mesh(positionNormalColor).sourceColors(), EPSILON);
            assertNull(mesh(position).sourceNormals());
            assertNull(mesh(positionColor).sourceNormals());
            assertPositiveZ(mesh(positionNormal).sourceNormals());
            assertPositiveZ(mesh(positionNormalColor).sourceNormals());
        }
        finally {
            position.dispose();
            positionColor.dispose();
            positionNormal.dispose();
            positionNormalColor.dispose();
        }
    }

    @Test
    void boxAndCubeUseFlatOutwardNormals() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        long usage = ModelVertexUsage.POSITION | ModelVertexUsage.NORMAL;
        Model box = new ModelBuilder(graphics).box(2.0f, 4.0f, 6.0f,
                usage);
        Model cube = new ModelBuilder(graphics).cube(2.0f, usage);
        try {
            assertFlatOutward(mesh(box));
            assertFlatOutward(mesh(cube));
            assertWhite(mesh(box).sourceColors());
            assertWhite(mesh(cube).sourceColors());
        }
        finally {
            box.dispose();
            cube.dispose();
        }
    }

    @Test
    void sphereUsesSmoothRadialNormals() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        float radius = 2.0f;
        Model sphere = new ModelBuilder(graphics).sphere("smooth-sphere",
                radius, 8, 4,
                ModelVertexUsage.POSITION | ModelVertexUsage.NORMAL);
        try {
            Mesh mesh = mesh(sphere);
            assertSame(Mesh.POSITION_NORMAL_LAYOUT, mesh.vertexLayout());
            float[] positions = mesh.sourcePositions();
            float[] normals = mesh.sourceNormals();
            assertEquals(positions.length, normals.length);
            for (int i = 0; i < positions.length; i += 3) {
                assertEquals(positions[i] / radius, normals[i], EPSILON);
                assertEquals(positions[i + 1] / radius, normals[i + 1],
                        EPSILON);
                assertEquals(positions[i + 2] / radius, normals[i + 2],
                        EPSILON);
                assertEquals(1.0f, length(normals, i), EPSILON);
            }
        }
        finally {
            sphere.dispose();
        }
    }

    @Test
    void trianglesGenerateFlatNormalsOrExpandExplicitNormals() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        float[] positions = {
                0.0f, 0.0f, 0.0f,
                1.0f, 0.0f, 0.0f,
                1.0f, 1.0f, 0.0f,
                0.0f, 1.0f, 0.0f
        };
        int[] indices = { 0, 1, 2, 0, 2, 3 };
        float[] normals = {
                1.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 1.0f,
                -1.0f, 0.0f, 0.0f
        };
        long usage = ModelVertexUsage.POSITION | ModelVertexUsage.NORMAL;
        Model generated = new ModelBuilder(graphics).triangles("generated",
                positions, indices, null, usage);
        Model explicit = new ModelBuilder(graphics).triangles("explicit",
                positions, indices, null, normals, usage);
        try {
            assertPositiveZ(mesh(generated).sourceNormals());
            assertArrayEquals(new float[] {
                    1.0f, 0.0f, 0.0f,
                    0.0f, 1.0f, 0.0f,
                    0.0f, 0.0f, 1.0f,
                    1.0f, 0.0f, 0.0f,
                    0.0f, 0.0f, 1.0f,
                    -1.0f, 0.0f, 0.0f
            }, mesh(explicit).sourceNormals(), EPSILON);
        }
        finally {
            generated.dispose();
            explicit.dispose();
        }
    }

    @Test
    void degenerateTrianglesGenerateFiniteZeroNormals() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        Model model = new ModelBuilder(graphics).triangles("degenerate",
                new float[] {
                        1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f,
                        1.0f, 1.0f, 1.0f
                }, null, null,
                ModelVertexUsage.POSITION | ModelVertexUsage.NORMAL);
        try {
            assertArrayEquals(new float[9], mesh(model).sourceNormals(),
                    EPSILON);
        }
        finally {
            model.dispose();
        }
    }

    @Test
    void usageRequiresPositionAndRejectsUnknownBits() {
        FakeGraphicsContext graphics = new FakeGraphicsContext();
        ModelBuilder builder = new ModelBuilder(graphics);
        FdxException missingPosition = assertThrows(FdxException.class,
                () -> builder.box(1.0f, 1.0f, 1.0f,
                        ModelVertexUsage.COLOR));
        assertEquals("Model vertex usage must include POSITION",
                missingPosition.getMessage());

        long unknownUsage = ModelVertexUsage.DEFAULT | (1L << 16);
        FdxException unknown = assertThrows(FdxException.class,
                () -> builder.sphere(1.0f, 8, unknownUsage));
        assertEquals("Unsupported model vertex usage bits: 65536",
                unknown.getMessage());
    }

    private static void assertPositionColor(Mesh mesh) {
        assertSame(Mesh.POSITION_COLOR_LAYOUT, mesh.vertexLayout());
        assertNull(mesh.sourceNormals());
    }

    private static void assertFlatOutward(Mesh mesh) {
        assertSame(Mesh.POSITION_NORMAL_LAYOUT, mesh.vertexLayout());
        assertEquals(36, mesh.vertexCount());
        float[] positions = mesh.sourcePositions();
        float[] normals = mesh.sourceNormals();
        for (int i = 0; i < positions.length; i += 3) {
            assertEquals(1.0f, length(normals, i), EPSILON);
            float outward = positions[i] * normals[i]
                    + positions[i + 1] * normals[i + 1]
                    + positions[i + 2] * normals[i + 2];
            assertTrue(outward > 0.0f);
        }
    }

    private static void assertPositiveZ(float[] normals) {
        for (int i = 0; i < normals.length; i += 3) {
            assertEquals(0.0f, normals[i], EPSILON);
            assertEquals(0.0f, normals[i + 1], EPSILON);
            assertEquals(1.0f, normals[i + 2], EPSILON);
        }
    }

    private static void assertWhite(float[] colors) {
        for (int i = 0; i < colors.length; i += 4) {
            assertEquals(1.0f, colors[i], EPSILON);
            assertEquals(1.0f, colors[i + 1], EPSILON);
            assertEquals(1.0f, colors[i + 2], EPSILON);
            assertEquals(1.0f, colors[i + 3], EPSILON);
        }
    }

    private static float length(float[] values, int offset) {
        return (float)Math.sqrt(values[offset] * values[offset]
                + values[offset + 1] * values[offset + 1]
                + values[offset + 2] * values[offset + 2]);
    }

    private static Mesh mesh(Model model) {
        return model.nodes().get(0).parts().get(0).meshPart().mesh();
    }

    private static final class FakeGraphicsContext implements GraphicsContext {
        private final FakeGraphicsDevice device = new FakeGraphicsDevice();

        @Override
        public GraphicsDevice device() {
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
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }

    private static final class FakeGraphicsDevice implements GraphicsDevice {
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
        public ShaderModule createShaderModule(
                ShaderModuleDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RenderPipeline createRenderPipeline(
                RenderPipelineDescriptor descriptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }
    }

    private static final class FakeBuffer implements Buffer {
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
        public ProviderId providerId() {
            return PROVIDER_ID;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T as() {
            return (T)this;
        }

        @Override
        public void dispose() {
            disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}
