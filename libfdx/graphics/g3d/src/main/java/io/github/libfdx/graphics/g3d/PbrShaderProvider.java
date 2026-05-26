package io.github.libfdx.graphics.g3d;

import io.github.libfdx.graphics.Camera;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class PbrShaderProvider implements ShaderProvider3D, Disposable {
    private final PositionColorShader shader;
    private final GpuPbrShader gpuShader;
    private boolean disposed;

    public PbrShaderProvider(GraphicsContext graphics) {
        this(graphics, new PbrShaderConfig());
    }

    public PbrShaderProvider(GraphicsContext graphics, PbrShaderConfig config) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        shader = new PositionColorShader(graphics);
        String providerId = graphics.providerId().value();
        gpuShader = usesGpuPbrShader(providerId)
                ? new GpuPbrShader(graphics, providerId)
                : null;
    }

    private static boolean usesGpuPbrShader(String providerId) {
        return "gl".equals(providerId) || "gles".equals(providerId) || "webgl".equals(providerId)
                || "wgpu".equals(providerId) || "vulkan".equals(providerId);
    }

    @Override
    public Shader3D shader(Renderable3D renderable, RenderContext3D context) {
        if (disposed) {
            throw new FdxException("PbrShaderProvider has been disposed");
        }
        if (gpuShader != null && gpuShader.canRender(renderable)) {
            return gpuShader;
        }
        if (!shader.canRender(renderable)) {
            throw new FdxException("Default ModelBatch shader currently supports position/color meshes");
        }
        return shader;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (gpuShader != null) {
            gpuShader.dispose();
        }
        shader.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static final class PositionColorShader implements Shader3D {
        private static final String WGSL =
                "struct VertexInput {\n" +
                "    @location(0) position : vec3f,\n" +
                "    @location(1) color : vec4f,\n" +
                "};\n" +
                "struct VertexOutput {\n" +
                "    @builtin(position) position : vec4f,\n" +
                "    @location(0) color : vec4f,\n" +
                "};\n" +
                "@vertex\n" +
                "fn vertexMain(input : VertexInput) -> VertexOutput {\n" +
                "    var output : VertexOutput;\n" +
                "    output.position = vec4f(input.position, 1.0);\n" +
                "    output.color = input.color;\n" +
                "    return output;\n" +
                "}\n" +
                "@fragment\n" +
                "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
                "    return input.color;\n" +
                "}\n";
        private static final String VERTEX_GLSL =
                "#version 330 core\n" +
                "layout(location = 0) in vec3 a_position;\n" +
                "layout(location = 1) in vec4 a_color;\n" +
                "out vec4 v_color;\n" +
                "void main() {\n" +
                "    v_color = a_color;\n" +
                "    gl_Position = vec4(a_position, 1.0);\n" +
                "}\n";
        private static final String FRAGMENT_GLSL =
                "#version 330 core\n" +
                "in vec4 v_color;\n" +
                "out vec4 fragColor;\n" +
                "void main() {\n" +
                "    fragColor = v_color;\n" +
                "}\n";
        private static final float PI = 3.14159265359f;
        private static final int[] VERTEX_SPIRV = {
                0x07230203, 0x00010600, 0x00070000, 0x00000020, 0x00000000, 0x00020011, 0x00000001,
                0x0006000b, 0x00000001, 0x4c534c47, 0x6474732e, 0x3035342e, 0x00000000, 0x0003000e,
                0x00000000, 0x00000001, 0x000a000f, 0x00000000, 0x00000002, 0x74726576, 0x614d7865,
                0x00006e69, 0x00000003, 0x00000004, 0x00000005, 0x00000006, 0x00030003, 0x00000002,
                0x000001c2, 0x000a0004, 0x475f4c47, 0x4c474f4f, 0x70635f45, 0x74735f70, 0x5f656c79,
                0x656e696c, 0x7269645f, 0x69746365, 0x00006576, 0x00080004, 0x475f4c47, 0x4c474f4f,
                0x6e695f45, 0x64756c63, 0x69645f65, 0x74636572, 0x00657669, 0x00050005, 0x00000002,
                0x74726576, 0x614d7865, 0x00006e69, 0x00040005, 0x00000003, 0x6f635f76, 0x00726f6c,
                0x00040005, 0x00000004, 0x6f635f61, 0x00726f6c, 0x00060005, 0x00000007, 0x505f6c67,
                0x65567265, 0x78657472, 0x00000000, 0x00060006, 0x00000007, 0x00000000, 0x505f6c67,
                0x7469736f, 0x006e6f69, 0x00070006, 0x00000007, 0x00000001, 0x505f6c67, 0x746e696f,
                0x657a6953, 0x00000000, 0x00070006, 0x00000007, 0x00000002, 0x435f6c67, 0x4470696c,
                0x61747369, 0x0065636e, 0x00070006, 0x00000007, 0x00000003, 0x435f6c67, 0x446c6c75,
                0x61747369, 0x0065636e, 0x00030005, 0x00000005, 0x00000000, 0x00050005, 0x00000006,
                0x6f705f61, 0x69746973, 0x00006e6f, 0x00040047, 0x00000003, 0x0000001e, 0x00000000,
                0x00040047, 0x00000004, 0x0000001e, 0x00000001, 0x00050048, 0x00000007, 0x00000000,
                0x0000000b, 0x00000000, 0x00050048, 0x00000007, 0x00000001, 0x0000000b, 0x00000001,
                0x00050048, 0x00000007, 0x00000002, 0x0000000b, 0x00000003, 0x00050048, 0x00000007,
                0x00000003, 0x0000000b, 0x00000004, 0x00030047, 0x00000007, 0x00000002, 0x00040047,
                0x00000006, 0x0000001e, 0x00000000, 0x00020013, 0x00000008, 0x00030021, 0x00000009,
                0x00000008, 0x00030016, 0x0000000a, 0x00000020, 0x00040017, 0x0000000b, 0x0000000a,
                0x00000004, 0x00040020, 0x0000000c, 0x00000003, 0x0000000b, 0x0004003b, 0x0000000c,
                0x00000003, 0x00000003, 0x00040020, 0x0000000d, 0x00000001, 0x0000000b, 0x0004003b,
                0x0000000d, 0x00000004, 0x00000001, 0x00040015, 0x0000000e, 0x00000020, 0x00000000,
                0x0004002b, 0x0000000e, 0x0000000f, 0x00000001, 0x0004001c, 0x00000010, 0x0000000a,
                0x0000000f, 0x0006001e, 0x00000007, 0x0000000b, 0x0000000a, 0x00000010, 0x00000010,
                0x00040020, 0x00000011, 0x00000003, 0x00000007, 0x0004003b, 0x00000011, 0x00000005,
                0x00000003, 0x00040015, 0x00000012, 0x00000020, 0x00000001, 0x0004002b, 0x00000012,
                0x00000013, 0x00000000, 0x00040017, 0x00000014, 0x0000000a, 0x00000002, 0x00040020,
                0x00000015, 0x00000001, 0x00000014, 0x0004003b, 0x0000000d, 0x00000006, 0x00000001,
                0x0004002b, 0x0000000a, 0x00000016, 0x00000000, 0x0004002b, 0x0000000a, 0x00000017,
                0x3f800000, 0x00050036, 0x00000008, 0x00000002, 0x00000000, 0x00000009, 0x000200f8,
                0x00000018, 0x0004003d, 0x0000000b, 0x00000019, 0x00000004, 0x0003003e, 0x00000003,
                0x00000019, 0x0004003d, 0x0000000b, 0x0000001a, 0x00000006, 0x00050051, 0x0000000a,
                0x0000001b, 0x0000001a, 0x00000000, 0x00050051, 0x0000000a, 0x0000001c, 0x0000001a,
                0x00000001, 0x00050051, 0x0000000a, 0x0000001f, 0x0000001a, 0x00000002, 0x00070050,
                0x0000000b, 0x0000001d, 0x0000001b, 0x0000001c, 0x0000001f,
                0x00000017, 0x00050041, 0x0000000c, 0x0000001e, 0x00000005, 0x00000013, 0x0003003e,
                0x0000001e, 0x0000001d, 0x000100fd, 0x00010038
        };
        private static final int[] FRAGMENT_SPIRV = {
                0x07230203, 0x00010600, 0x00070000, 0x0000000d, 0x00000000, 0x00020011, 0x00000001,
                0x0006000b, 0x00000001, 0x4c534c47, 0x6474732e, 0x3035342e, 0x00000000, 0x0003000e,
                0x00000000, 0x00000001, 0x0009000f, 0x00000004, 0x00000002, 0x67617266, 0x746e656d,
                0x6e69614d, 0x00000000, 0x00000003, 0x00000004, 0x00030010, 0x00000002, 0x00000007,
                0x00030003, 0x00000002, 0x000001c2, 0x000a0004, 0x475f4c47, 0x4c474f4f, 0x70635f45,
                0x74735f70, 0x5f656c79, 0x656e696c, 0x7269645f, 0x69746365, 0x00006576, 0x00080004,
                0x475f4c47, 0x4c474f4f, 0x6e695f45, 0x64756c63, 0x69645f65, 0x74636572, 0x00657669,
                0x00060005, 0x00000002, 0x67617266, 0x746e656d, 0x6e69614d, 0x00000000, 0x00050005,
                0x00000003, 0x67617266, 0x6f6c6f43, 0x00000072, 0x00040005, 0x00000004, 0x6f635f76,
                0x00726f6c, 0x00040047, 0x00000003, 0x0000001e, 0x00000000, 0x00040047, 0x00000004,
                0x0000001e, 0x00000000, 0x00020013, 0x00000005, 0x00030021, 0x00000006, 0x00000005,
                0x00030016, 0x00000007, 0x00000020, 0x00040017, 0x00000008, 0x00000007, 0x00000004,
                0x00040020, 0x00000009, 0x00000003, 0x00000008, 0x0004003b, 0x00000009, 0x00000003,
                0x00000003, 0x00040020, 0x0000000a, 0x00000001, 0x00000008, 0x0004003b, 0x0000000a,
                0x00000004, 0x00000001, 0x00050036, 0x00000005, 0x00000002, 0x00000000, 0x00000006,
                0x000200f8, 0x0000000b, 0x0004003d, 0x00000008, 0x0000000c, 0x00000004, 0x0003003e,
                0x00000003, 0x0000000c, 0x000100fd, 0x00010038
        };

        private final GraphicsContext graphics;
        private final ShaderModule shaderModule;
        private final Map<PipelineKey, RenderPipeline> pipelines = new HashMap<PipelineKey, RenderPipeline>();
        private ScratchBuffer[] scratchBuffers = new ScratchBuffer[4];
        private int scratchCursor;
        private RenderContext3D context;
        private boolean disposed;

        PositionColorShader(GraphicsContext graphics) {
            this.graphics = graphics;
            shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                    .wgsl("model batch position color", WGSL)
                    .glsl(VERTEX_GLSL, FRAGMENT_GLSL)
                    .spirv(VERTEX_SPIRV, FRAGMENT_SPIRV));
        }

        @Override
        public boolean canRender(Renderable3D renderable) {
            if (renderable == null || renderable.meshPart() == null) {
                return false;
            }
            Mesh mesh = renderable.meshPart().mesh();
            return isPositionColorLayout(mesh.vertexLayout())
                    || mesh.hasPositionColor3DSource();
        }

        @Override
        public void begin(RenderContext3D context) {
            if (disposed) {
                throw new FdxException("ModelBatch shader has been disposed");
            }
            this.context = context;
            scratchCursor = 0;
        }

        @Override
        public void render(Renderable3D renderable) {
            if (context == null) {
                throw new FdxException("Shader3D.begin() must be called before render");
            }
            MeshPart meshPart = renderable.meshPart();
            Mesh mesh = meshPart.mesh();
            RenderPass pass = context.pass();
            Buffer vertexBuffer = mesh.vertexBuffer();
            VertexLayout vertexLayout = mesh.vertexLayout();
            int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : mesh.vertexCount();
            int firstVertex = meshPart.firstVertex();
            if (mesh.hasPositionColor3DSource()) {
                ProjectedMesh projectedMesh = project(mesh, meshPart, renderable.worldTransform(),
                        renderable.material(), context);
                vertexBuffer = scratchBuffer(projectedMesh.vertices.length * 4);
                graphics.device().writeBuffer(vertexBuffer, floats(projectedMesh.vertices));
                vertexLayout = Mesh.POSITION_COLOR_LAYOUT;
                vertexCount = projectedMesh.vertexCount;
                firstVertex = 0;
            }
            pass.setPipeline(pipeline(vertexLayout, meshPart.primitiveTopology()));
            pass.setVertexBuffer(vertexBuffer);
            int indexCount = meshPart.indexCount() > 0 ? meshPart.indexCount() : mesh.indexCount();
            if (indexCount > 0 && vertexBuffer == mesh.vertexBuffer()) {
                pass.setIndexBuffer(mesh.indexBuffer());
                pass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
            }
            else {
                pass.draw(vertexCount, 1, firstVertex, 0);
            }
        }

        @Override
        public void end() {
            context = null;
        }

        private RenderPipeline pipeline(VertexLayout vertexLayout, PrimitiveTopology topology) {
            PipelineKey key = new PipelineKey(vertexLayout, topology);
            RenderPipeline pipeline = pipelines.get(key);
            if (pipeline == null) {
                pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                        .shader(shaderModule, graphics.surfaceFormat())
                        .label("model batch position color")
                        .primitiveTopology(topology)
                        .depthTestEnabled(true)
                        .depthWriteEnabled(true)
                        .vertexLayout(vertexLayout));
                pipelines.put(key, pipeline);
            }
            return pipeline;
        }

        private boolean isPositionColorLayout(VertexLayout layout) {
            VertexAttribute[] attributes = layout.attributes();
            if (attributes.length < 2) {
                return false;
            }
            return attributes[0].location() == 0
                    && attributes[0].format() == VertexFormat.FLOAT32X3
                    && attributes[1].location() == 1
                    && attributes[1].format() == VertexFormat.FLOAT32X4;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            for (int i = 0; i < scratchBuffers.length; i++) {
                if (scratchBuffers[i] != null) {
                    scratchBuffers[i].buffer.dispose();
                    scratchBuffers[i] = null;
                }
            }
            Iterator<RenderPipeline> iterator = pipelines.values().iterator();
            while (iterator.hasNext()) {
                iterator.next().dispose();
            }
            pipelines.clear();
            shaderModule.dispose();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        private Buffer scratchBuffer(int byteCount) {
            if (scratchCursor >= scratchBuffers.length) {
                scratchBuffers = Arrays.copyOf(scratchBuffers, scratchBuffers.length * 2);
            }
            ScratchBuffer scratchBuffer = scratchBuffers[scratchCursor];
            if (scratchBuffer == null || scratchBuffer.byteCount < byteCount) {
                if (scratchBuffer != null) {
                    scratchBuffer.buffer.dispose();
                }
                scratchBuffer = new ScratchBuffer(graphics.device().createBuffer(BufferDescriptor
                        .vertex("model batch projected vertices", byteCount)), byteCount);
                scratchBuffers[scratchCursor] = scratchBuffer;
            }
            scratchCursor++;
            return scratchBuffer.buffer;
        }

        private ProjectedMesh project(Mesh mesh, MeshPart meshPart, Matrix4 worldTransform, Material material,
                RenderContext3D context) {
            float[] sourcePositions = mesh.sourcePositions();
            float[] sourceColors = mesh.sourceBakedColors() != null ? mesh.sourceBakedColors() : mesh.sourceColors();
            float[] sourceNormals = mesh.sourceNormals();
            float[] sourcePbr = mesh.sourceBakedPbr() != null ? mesh.sourceBakedPbr() : mesh.sourcePbr();
            float[] sourceEmissive = mesh.sourceBakedEmissive() != null ? mesh.sourceBakedEmissive()
                    : mesh.sourceEmissive();
            Camera camera = context.camera();
            int firstVertex = meshPart.firstVertex();
            int availableVertices = sourcePositions.length / 3;
            int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : availableVertices - firstVertex;
            if (firstVertex < 0 || firstVertex + vertexCount > availableVertices || vertexCount % 3 != 0) {
                throw new FdxException("Position/color 3D mesh parts must address complete triangles");
            }
            ProjectedTriangle[] triangles = new ProjectedTriangle[vertexCount / 3];
            float[] world = worldTransform.values();
            float[] viewProjection = camera.combined().values();
            int triangleCount = 0;
            for (int i = 0; i < triangles.length; i++) {
                int vertex = firstVertex + i * 3;
                WorldVertex w0 = worldVertex(sourcePositions, vertex, world);
                WorldVertex w1 = worldVertex(sourcePositions, vertex + 1, world);
                WorldVertex w2 = worldVertex(sourcePositions, vertex + 2, world);
                if (material.doubleSided() || facesCamera(w0, w1, w2, camera.position())) {
                    WorldVertex faceNormal = faceNormal(w0, w1, w2);
                    triangles[triangleCount++] = new ProjectedTriangle(
                            projectVertex(w0, sourceColors, sourceNormals, sourcePbr, sourceEmissive, vertex,
                                    faceNormal, world, viewProjection, context),
                            projectVertex(w1, sourceColors, sourceNormals, sourcePbr, sourceEmissive, vertex + 1,
                                    faceNormal, world, viewProjection, context),
                            projectVertex(w2, sourceColors, sourceNormals, sourcePbr, sourceEmissive, vertex + 2,
                                    faceNormal, world, viewProjection, context));
                }
            }
            if (triangleCount == 0) {
                for (int i = 0; i < triangles.length; i++) {
                    int vertex = firstVertex + i * 3;
                    WorldVertex w0 = worldVertex(sourcePositions, vertex, world);
                    WorldVertex w1 = worldVertex(sourcePositions, vertex + 1, world);
                    WorldVertex w2 = worldVertex(sourcePositions, vertex + 2, world);
                    WorldVertex faceNormal = faceNormal(w0, w1, w2);
                    triangles[triangleCount++] = new ProjectedTriangle(
                            projectVertex(w0, sourceColors, sourceNormals, sourcePbr, sourceEmissive, vertex,
                                    faceNormal, world, viewProjection, context),
                            projectVertex(w1, sourceColors, sourceNormals, sourcePbr, sourceEmissive, vertex + 1,
                                    faceNormal, world, viewProjection, context),
                            projectVertex(w2, sourceColors, sourceNormals, sourcePbr, sourceEmissive, vertex + 2,
                                    faceNormal, world, viewProjection, context));
                }
            }
            triangles = Arrays.copyOf(triangles, triangleCount);
            Arrays.sort(triangles, new Comparator<ProjectedTriangle>() {
                @Override
                public int compare(ProjectedTriangle left, ProjectedTriangle right) {
                    return Float.compare(right.depth, left.depth);
                }
            });
            float[] vertices = new float[triangleCount * 3 * Mesh.POSITION_COLOR_FLOATS_PER_VERTEX];
            int out = 0;
            for (int i = 0; i < triangles.length; i++) {
                out = appendProjectedVertex(vertices, out, triangles[i].v0);
                out = appendProjectedVertex(vertices, out, triangles[i].v1);
                out = appendProjectedVertex(vertices, out, triangles[i].v2);
            }
            return new ProjectedMesh(vertices, triangleCount * 3);
        }

        private WorldVertex worldVertex(float[] positions, int vertex, float[] matrix) {
            int positionOffset = vertex * 3;
            float x = positions[positionOffset];
            float y = positions[positionOffset + 1];
            float z = positions[positionOffset + 2];
            return new WorldVertex(
                    matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                    matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                    matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]);
        }

        private boolean facesCamera(WorldVertex v0, WorldVertex v1, WorldVertex v2, Vector3 cameraPosition) {
            WorldVertex normal = faceNormal(v0, v1, v2);
            float centerX = (v0.x + v1.x + v2.x) / 3.0f;
            float centerY = (v0.y + v1.y + v2.y) / 3.0f;
            float centerZ = (v0.z + v1.z + v2.z) / 3.0f;
            float viewX = cameraPosition.x() - centerX;
            float viewY = cameraPosition.y() - centerY;
            float viewZ = cameraPosition.z() - centerZ;
            return normal.x * viewX + normal.y * viewY + normal.z * viewZ > 0.0f;
        }

        private WorldVertex faceNormal(WorldVertex v0, WorldVertex v1, WorldVertex v2) {
            float ax = v1.x - v0.x;
            float ay = v1.y - v0.y;
            float az = v1.z - v0.z;
            float bx = v2.x - v0.x;
            float by = v2.y - v0.y;
            float bz = v2.z - v0.z;
            float nx = ay * bz - az * by;
            float ny = az * bx - ax * bz;
            float nz = ax * by - ay * bx;
            return normalize(nx, ny, nz);
        }

        private ProjectedVertex projectVertex(WorldVertex worldVertex, float[] colors, float[] normals, float[] pbr,
                float[] emissive, int vertex, WorldVertex faceNormal, float[] worldMatrix, float[] matrix,
                RenderContext3D context) {
            float x = worldVertex.x;
            float y = worldVertex.y;
            float z = worldVertex.z;
            float clipX = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
            float clipY = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
            float clipZ = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
            float clipW = matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15];
            float invW = Math.abs(clipW) > 0.000001f ? 1.0f / clipW : 1.0f;
            int colorOffset = vertex * 4;
            ColorVertex shaded = shade(worldVertex, colors, normals, pbr, emissive, vertex, colorOffset, faceNormal,
                    worldMatrix, context);
            return new ProjectedVertex(clipX * invW, clipY * invW, clipZ * invW,
                    shaded.red, shaded.green, shaded.blue, shaded.alpha);
        }

        private ColorVertex shade(WorldVertex worldVertex, float[] colors, float[] normals, float[] pbr,
                float[] emissive, int vertex, int colorOffset, WorldVertex faceNormal, float[] worldMatrix,
                RenderContext3D context) {
            float red = colors[colorOffset];
            float green = colors[colorOffset + 1];
            float blue = colors[colorOffset + 2];
            float alpha = colors[colorOffset + 3];
            if (normals == null) {
                return new ColorVertex(red, green, blue, alpha);
            }

            WorldVertex normal = worldNormal(normals, vertex, worldMatrix);
            if (normal.lengthSquared() == 0.0f) {
                normal = faceNormal;
            }
            float ao = 1.0f;
            float metallic = 0.0f;
            float roughness = 1.0f;
            if (pbr != null) {
                int pbrOffset = vertex * 3;
                ao = clamp(pbr[pbrOffset], 0.0f, 1.0f);
                metallic = clamp(pbr[pbrOffset + 1], 0.0f, 1.0f);
                roughness = clamp(pbr[pbrOffset + 2], 0.04f, 1.0f);
            }

            Color ambient = context.environment().ambientColor();
            float outRed = ambient.red() * red * ao;
            float outGreen = ambient.green() * green * ao;
            float outBlue = ambient.blue() * blue * ao;
            float viewX = context.camera().position().x() - worldVertex.x;
            float viewY = context.camera().position().y() - worldVertex.y;
            float viewZ = context.camera().position().z() - worldVertex.z;
            WorldVertex view = normalize(viewX, viewY, viewZ);
            for (int i = 0; i < context.environment().lights().size(); i++) {
                Light light = context.environment().lights().get(i);
                if (light instanceof DirectionalLight) {
                    DirectionalLight directional = (DirectionalLight)light;
                    Vector3 direction = directional.direction();
                    WorldVertex lightDirection = normalize(-direction.x(), -direction.y(), -direction.z());
                    float ndl = Math.max(0.0f, dot(normal, lightDirection));
                    if (ndl <= 0.0f) {
                        continue;
                    }
                    WorldVertex halfVector = normalize(view.x + lightDirection.x, view.y + lightDirection.y,
                            view.z + lightDirection.z);
                    float ndv = Math.max(0.0f, dot(normal, view));
                    float hv = Math.max(0.0f, dot(halfVector, view));
                    float distribution = distributionGGX(normal, halfVector, roughness);
                    float geometry = geometrySmith(normal, view, lightDirection, roughness);
                    float baseSpecular = distribution * geometry / Math.max(4.0f * ndv * ndl, 0.000001f);
                    float fRed = fresnelSchlick(hv, 0.04f + (red - 0.04f) * metallic);
                    float fGreen = fresnelSchlick(hv, 0.04f + (green - 0.04f) * metallic);
                    float fBlue = fresnelSchlick(hv, 0.04f + (blue - 0.04f) * metallic);
                    float kdRed = (1.0f - fRed) * (1.0f - metallic);
                    float kdGreen = (1.0f - fGreen) * (1.0f - metallic);
                    float kdBlue = (1.0f - fBlue) * (1.0f - metallic);
                    float radianceRed = directional.color().red() * directional.intensity();
                    float radianceGreen = directional.color().green() * directional.intensity();
                    float radianceBlue = directional.color().blue() * directional.intensity();
                    outRed += (kdRed * red / PI + baseSpecular * fRed) * radianceRed * ndl;
                    outGreen += (kdGreen * green / PI + baseSpecular * fGreen) * radianceGreen * ndl;
                    outBlue += (kdBlue * blue / PI + baseSpecular * fBlue) * radianceBlue * ndl;
                }
            }

            float emissiveRed = 0.0f;
            float emissiveGreen = 0.0f;
            float emissiveBlue = 0.0f;
            if (emissive != null) {
                int emissiveOffset = vertex * 3;
                emissiveRed = emissive[emissiveOffset];
                emissiveGreen = emissive[emissiveOffset + 1];
                emissiveBlue = emissive[emissiveOffset + 2];
            }

            return new ColorVertex(
                    linearToSrgb(outRed + emissiveRed),
                    linearToSrgb(outGreen + emissiveGreen),
                    linearToSrgb(outBlue + emissiveBlue),
                    alpha);
        }

        private WorldVertex worldNormal(float[] normals, int vertex, float[] matrix) {
            int normalOffset = vertex * 3;
            float x = normals[normalOffset];
            float y = normals[normalOffset + 1];
            float z = normals[normalOffset + 2];
            return normalize(
                    matrix[0] * x + matrix[4] * y + matrix[8] * z,
                    matrix[1] * x + matrix[5] * y + matrix[9] * z,
                    matrix[2] * x + matrix[6] * y + matrix[10] * z);
        }

        private float distributionGGX(WorldVertex normal, WorldVertex halfVector, float roughness) {
            float a = roughness * roughness;
            float a2 = a * a;
            float ndh = Math.max(dot(normal, halfVector), 0.0f);
            float denom = ndh * ndh * (a2 - 1.0f) + 1.0f;
            return a2 / Math.max(PI * denom * denom, 0.000001f);
        }

        private float geometrySchlickGGX(float ndv, float roughness) {
            float r = roughness + 1.0f;
            float k = (r * r) / 8.0f;
            return ndv / Math.max(ndv * (1.0f - k) + k, 0.000001f);
        }

        private float geometrySmith(WorldVertex normal, WorldVertex view, WorldVertex light, float roughness) {
            return geometrySchlickGGX(Math.max(dot(normal, view), 0.0f), roughness)
                    * geometrySchlickGGX(Math.max(dot(normal, light), 0.0f), roughness);
        }

        private float fresnelSchlick(float cosTheta, float f0) {
            return f0 + (1.0f - f0) * (float)Math.pow(clamp(1.0f - cosTheta, 0.0f, 1.0f), 5.0f);
        }

        private float dot(WorldVertex left, WorldVertex right) {
            return left.x * right.x + left.y * right.y + left.z * right.z;
        }

        private float linearToSrgb(float value) {
            return clamp((float)Math.pow(Math.max(value, 0.0f), 1.0f / 2.2f), 0.0f, 1.0f);
        }

        private WorldVertex normalize(float x, float y, float z) {
            float len = (float)Math.sqrt(x * x + y * y + z * z);
            if (len == 0.0f) {
                return new WorldVertex(0.0f, 0.0f, 0.0f);
            }
            float invLen = 1.0f / len;
            return new WorldVertex(x * invLen, y * invLen, z * invLen);
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private int appendProjectedVertex(float[] vertices, int out, ProjectedVertex vertex) {
            vertices[out++] = vertex.x;
            vertices[out++] = vertex.y;
            vertices[out++] = vertex.z;
            vertices[out++] = vertex.red;
            vertices[out++] = vertex.green;
            vertices[out++] = vertex.blue;
            vertices[out++] = vertex.alpha;
            return out;
        }

        private ByteBuffer floats(float[] values) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder());
            buffer.asFloatBuffer().put(values);
            buffer.limit(values.length * 4);
            buffer.position(0);
            return buffer;
        }

        private static final class ScratchBuffer {
            private final Buffer buffer;
            private final int byteCount;

            ScratchBuffer(Buffer buffer, int byteCount) {
                this.buffer = buffer;
                this.byteCount = byteCount;
            }
        }

        private static final class WorldVertex {
            private final float x;
            private final float y;
            private final float z;

            WorldVertex(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }

            float lengthSquared() {
                return x * x + y * y + z * z;
            }
        }

        private static final class ColorVertex {
            private final float red;
            private final float green;
            private final float blue;
            private final float alpha;

            ColorVertex(float red, float green, float blue, float alpha) {
                this.red = red;
                this.green = green;
                this.blue = blue;
                this.alpha = alpha;
            }
        }

        private static final class ProjectedMesh {
            private final float[] vertices;
            private final int vertexCount;

            ProjectedMesh(float[] vertices, int vertexCount) {
                this.vertices = vertices;
                this.vertexCount = vertexCount;
            }
        }

        private static final class ProjectedTriangle {
            private final ProjectedVertex v0;
            private final ProjectedVertex v1;
            private final ProjectedVertex v2;
            private final float depth;

            ProjectedTriangle(ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2) {
                this.v0 = v0;
                this.v1 = v1;
                this.v2 = v2;
                depth = (v0.z + v1.z + v2.z) / 3.0f;
            }
        }

        private static final class ProjectedVertex {
            private final float x;
            private final float y;
            private final float z;
            private final float red;
            private final float green;
            private final float blue;
            private final float alpha;

            ProjectedVertex(float x, float y, float z, float red, float green, float blue, float alpha) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.red = red;
                this.green = green;
                this.blue = blue;
                this.alpha = alpha;
            }
        }
    }

    private static final class GpuPbrShader implements Shader3D {
        private static final String VERTEX_GLSL =
                "#version 330 core\n" +
                "layout(location = 0) in vec3 a_position;\n" +
                "layout(location = 1) in vec3 a_normal;\n" +
                "layout(location = 2) in vec2 a_uv;\n" +
                "layout(location = 3) in vec4 a_color;\n" +
                "layout(location = 4) in vec3 a_pbr;\n" +
                "layout(location = 5) in vec3 a_emissive;\n" +
                "uniform mat4 u_model;\n" +
                "uniform mat4 u_viewProjection;\n" +
                "out vec3 v_worldPosition;\n" +
                "out vec3 v_normal;\n" +
                "out vec2 v_uv;\n" +
                "out vec4 v_color;\n" +
                "out vec3 v_pbr;\n" +
                "out vec3 v_emissive;\n" +
                "void main() {\n" +
                "    vec4 worldPosition = u_model * vec4(a_position, 1.0);\n" +
                "    v_worldPosition = worldPosition.xyz;\n" +
                "    v_normal = mat3(u_model) * a_normal;\n" +
                "    v_uv = a_uv;\n" +
                "    v_color = a_color;\n" +
                "    v_pbr = a_pbr;\n" +
                "    v_emissive = a_emissive;\n" +
                "    gl_Position = u_viewProjection * worldPosition;\n" +
                "}\n";
        private static final String FRAGMENT_GLSL =
                "#version 330 core\n" +
                "in vec3 v_worldPosition;\n" +
                "in vec3 v_normal;\n" +
                "in vec2 v_uv;\n" +
                "in vec4 v_color;\n" +
                "in vec3 v_pbr;\n" +
                "in vec3 v_emissive;\n" +
                "uniform vec3 u_cameraPosition;\n" +
                "uniform vec3 u_ambientColor;\n" +
                "uniform vec3 u_lightDirection;\n" +
                "uniform vec3 u_lightColor;\n" +
                "uniform float u_lightIntensity;\n" +
                "uniform sampler2D u_baseColorTexture;\n" +
                "uniform sampler2D u_metallicRoughnessTexture;\n" +
                "uniform sampler2D u_normalTexture;\n" +
                "uniform sampler2D u_occlusionTexture;\n" +
                "uniform sampler2D u_emissiveTexture;\n" +
                "uniform int u_hasBaseColorTexture;\n" +
                "uniform int u_hasMetallicRoughnessTexture;\n" +
                "uniform int u_hasNormalTexture;\n" +
                "uniform int u_hasOcclusionTexture;\n" +
                "uniform int u_hasEmissiveTexture;\n" +
                "out vec4 fragColor;\n" +
                "const float PI = 3.14159265359;\n" +
                "vec3 srgbToLinear(vec3 value) {\n" +
                "    return pow(max(value, vec3(0.0)), vec3(2.2));\n" +
                "}\n" +
                "vec3 linearToSrgb(vec3 value) {\n" +
                "    return pow(max(value, vec3(0.0)), vec3(1.0 / 2.2));\n" +
                "}\n" +
                "float distributionGGX(vec3 n, vec3 h, float roughness) {\n" +
                "    float a = roughness * roughness;\n" +
                "    float a2 = a * a;\n" +
                "    float ndh = max(dot(n, h), 0.0);\n" +
                "    float denom = ndh * ndh * (a2 - 1.0) + 1.0;\n" +
                "    return a2 / max(PI * denom * denom, 0.000001);\n" +
                "}\n" +
                "float geometrySchlickGGX(float ndv, float roughness) {\n" +
                "    float r = roughness + 1.0;\n" +
                "    float k = (r * r) / 8.0;\n" +
                "    return ndv / max(ndv * (1.0 - k) + k, 0.000001);\n" +
                "}\n" +
                "float geometrySmith(vec3 n, vec3 v, vec3 l, float roughness) {\n" +
                "    return geometrySchlickGGX(max(dot(n, v), 0.0), roughness)\n" +
                "            * geometrySchlickGGX(max(dot(n, l), 0.0), roughness);\n" +
                "}\n" +
                "vec3 fresnelSchlick(float cosTheta, vec3 f0) {\n" +
                "    return f0 + (1.0 - f0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);\n" +
                "}\n" +
                "vec3 mappedNormal(vec3 n) {\n" +
                "    if (u_hasNormalTexture == 0) {\n" +
                "        return normalize(n);\n" +
                "    }\n" +
                "    vec3 q1 = dFdx(v_worldPosition);\n" +
                "    vec3 q2 = dFdy(v_worldPosition);\n" +
                "    vec2 st1 = dFdx(v_uv);\n" +
                "    vec2 st2 = dFdy(v_uv);\n" +
                "    vec3 tangent = q1 * st2.t - q2 * st1.t;\n" +
                "    if (dot(tangent, tangent) < 0.000001) {\n" +
                "        return normalize(n);\n" +
                "    }\n" +
                "    vec3 t = normalize(tangent);\n" +
                "    vec3 b = normalize(cross(n, t));\n" +
                "    vec3 sampleNormal = texture(u_normalTexture, v_uv).xyz * 2.0 - 1.0;\n" +
                "    return normalize(mat3(t, b, n) * sampleNormal);\n" +
                "}\n" +
                "void main() {\n" +
                "    vec2 uv = v_uv;\n" +
                "    vec4 base = v_color;\n" +
                "    if (u_hasBaseColorTexture != 0) {\n" +
                "        vec4 texel = texture(u_baseColorTexture, uv);\n" +
                "        base.rgb *= srgbToLinear(texel.rgb);\n" +
                "        base.a *= texel.a;\n" +
                "    }\n" +
                "    if (base.a <= 0.001) {\n" +
                "        discard;\n" +
                "    }\n" +
                "    float ao = clamp(v_pbr.x, 0.0, 1.0);\n" +
                "    float metallic = clamp(v_pbr.y, 0.0, 1.0);\n" +
                "    float roughness = clamp(v_pbr.z, 0.04, 1.0);\n" +
                "    if (u_hasMetallicRoughnessTexture != 0) {\n" +
                "        vec4 mr = texture(u_metallicRoughnessTexture, uv);\n" +
                "        roughness = clamp(roughness * mr.g, 0.04, 1.0);\n" +
                "        metallic = clamp(metallic * mr.b, 0.0, 1.0);\n" +
                "    }\n" +
                "    if (u_hasOcclusionTexture != 0) {\n" +
                "        ao *= texture(u_occlusionTexture, uv).r;\n" +
                "    }\n" +
                "    vec3 emissive = v_emissive;\n" +
                "    if (u_hasEmissiveTexture != 0) {\n" +
                "        emissive *= srgbToLinear(texture(u_emissiveTexture, uv).rgb);\n" +
                "    }\n" +
                "    vec3 n = mappedNormal(normalize(v_normal));\n" +
                "    vec3 v = normalize(u_cameraPosition - v_worldPosition);\n" +
                "    vec3 l = normalize(-u_lightDirection);\n" +
                "    vec3 h = normalize(v + l);\n" +
                "    vec3 albedo = max(base.rgb, vec3(0.0));\n" +
                "    vec3 f0 = mix(vec3(0.04), albedo, metallic);\n" +
                "    float ndl = max(dot(n, l), 0.0);\n" +
                "    float ndv = max(dot(n, v), 0.0);\n" +
                "    vec3 f = fresnelSchlick(max(dot(h, v), 0.0), f0);\n" +
                "    float d = distributionGGX(n, h, roughness);\n" +
                "    float g = geometrySmith(n, v, l, roughness);\n" +
                "    vec3 specular = (d * g * f) / max(4.0 * ndv * ndl, 0.000001);\n" +
                "    vec3 kd = (vec3(1.0) - f) * (1.0 - metallic);\n" +
                "    vec3 radiance = u_lightColor * u_lightIntensity;\n" +
                "    vec3 color = (kd * albedo / PI + specular) * radiance * ndl;\n" +
                "    color += u_ambientColor * albedo * ao;\n" +
                "    color += emissive;\n" +
                "    fragColor = vec4(linearToSrgb(color), base.a);\n" +
                "}\n";
        private static final String WGSL =
                "struct VertexInput {\n" +
                "    @location(0) position : vec3f,\n" +
                "    @location(1) normal : vec3f,\n" +
                "    @location(2) uv : vec2f,\n" +
                "    @location(3) color : vec4f,\n" +
                "    @location(4) pbr : vec3f,\n" +
                "    @location(5) emissive : vec3f,\n" +
                "};\n" +
                "struct VertexOutput {\n" +
                "    @builtin(position) position : vec4f,\n" +
                "    @location(0) worldPosition : vec3f,\n" +
                "    @location(1) normal : vec3f,\n" +
                "    @location(2) uv : vec2f,\n" +
                "    @location(3) color : vec4f,\n" +
                "    @location(4) pbr : vec3f,\n" +
                "    @location(5) emissive : vec3f,\n" +
                "};\n" +
                "struct PbrUniforms {\n" +
                "    model : mat4x4<f32>,\n" +
                "    viewProjection : mat4x4<f32>,\n" +
                "    cameraPosition : vec4f,\n" +
                "    ambientColor : vec4f,\n" +
                "    lightDirection : vec4f,\n" +
                "    lightColorIntensity : vec4f,\n" +
                "    textureFlags : vec4f,\n" +
                "    emissiveFlags : vec4f,\n" +
                "};\n" +
                "@group(0) @binding(0) var baseColorTexture : texture_2d<f32>;\n" +
                "@group(0) @binding(1) var baseColorSampler : sampler;\n" +
                "@group(0) @binding(2) var metallicRoughnessTexture : texture_2d<f32>;\n" +
                "@group(0) @binding(3) var metallicRoughnessSampler : sampler;\n" +
                "@group(0) @binding(4) var normalTexture : texture_2d<f32>;\n" +
                "@group(0) @binding(5) var normalSampler : sampler;\n" +
                "@group(0) @binding(6) var occlusionTexture : texture_2d<f32>;\n" +
                "@group(0) @binding(7) var occlusionSampler : sampler;\n" +
                "@group(0) @binding(8) var emissiveTexture : texture_2d<f32>;\n" +
                "@group(0) @binding(9) var emissiveSampler : sampler;\n" +
                "@group(1) @binding(0) var<uniform> uniforms : PbrUniforms;\n" +
                "const PI : f32 = 3.14159265359;\n" +
                "@vertex\n" +
                "fn vertexMain(input : VertexInput) -> VertexOutput {\n" +
                "    var output : VertexOutput;\n" +
                "    let worldPosition = uniforms.model * vec4f(input.position, 1.0);\n" +
                "    output.worldPosition = worldPosition.xyz;\n" +
                "    output.normal = (uniforms.model * vec4f(input.normal, 0.0)).xyz;\n" +
                "    output.uv = input.uv;\n" +
                "    output.color = input.color;\n" +
                "    output.pbr = input.pbr;\n" +
                "    output.emissive = input.emissive;\n" +
                "    output.position = uniforms.viewProjection * worldPosition;\n" +
                "    return output;\n" +
                "}\n" +
                "fn srgbToLinear(value : vec3f) -> vec3f {\n" +
                "    return pow(max(value, vec3f(0.0)), vec3f(2.2));\n" +
                "}\n" +
                "fn linearToSrgb(value : vec3f) -> vec3f {\n" +
                "    return pow(max(value, vec3f(0.0)), vec3f(1.0 / 2.2));\n" +
                "}\n" +
                "fn distributionGGX(n : vec3f, h : vec3f, roughness : f32) -> f32 {\n" +
                "    let a = roughness * roughness;\n" +
                "    let a2 = a * a;\n" +
                "    let ndh = max(dot(n, h), 0.0);\n" +
                "    let denom = ndh * ndh * (a2 - 1.0) + 1.0;\n" +
                "    return a2 / max(PI * denom * denom, 0.000001);\n" +
                "}\n" +
                "fn geometrySchlickGGX(ndv : f32, roughness : f32) -> f32 {\n" +
                "    let r = roughness + 1.0;\n" +
                "    let k = (r * r) / 8.0;\n" +
                "    return ndv / max(ndv * (1.0 - k) + k, 0.000001);\n" +
                "}\n" +
                "fn geometrySmith(n : vec3f, v : vec3f, l : vec3f, roughness : f32) -> f32 {\n" +
                "    return geometrySchlickGGX(max(dot(n, v), 0.0), roughness)\n" +
                "            * geometrySchlickGGX(max(dot(n, l), 0.0), roughness);\n" +
                "}\n" +
                "fn fresnelSchlick(cosTheta : f32, f0 : vec3f) -> vec3f {\n" +
                "    return f0 + (vec3f(1.0) - f0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);\n" +
                "}\n" +
                "fn mappedNormal(nIn : vec3f, worldPosition : vec3f, uv : vec2f) -> vec3f {\n" +
                "    let n = normalize(nIn);\n" +
                "    if (uniforms.textureFlags.z < 0.5) {\n" +
                "        return n;\n" +
                "    }\n" +
                "    let sampleNormal = textureSample(normalTexture, normalSampler, uv).xyz * 2.0 - vec3f(1.0);\n" +
                "    let q1 = dpdx(worldPosition);\n" +
                "    let q2 = dpdy(worldPosition);\n" +
                "    let st1 = dpdx(uv);\n" +
                "    let st2 = dpdy(uv);\n" +
                "    let tangent = q1 * st2.y - q2 * st1.y;\n" +
                "    if (dot(tangent, tangent) < 0.000001) {\n" +
                "        return n;\n" +
                "    }\n" +
                "    let t = normalize(tangent);\n" +
                "    let b = normalize(cross(n, t));\n" +
                "    return normalize(mat3x3<f32>(t, b, n) * sampleNormal);\n" +
                "}\n" +
                "@fragment\n" +
                "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
                "    let uv = input.uv;\n" +
                "    var base = input.color;\n" +
                "    if (uniforms.textureFlags.x > 0.5) {\n" +
                "        let texel = textureSample(baseColorTexture, baseColorSampler, uv);\n" +
                "        base = vec4f(base.rgb * srgbToLinear(texel.rgb), base.a * texel.a);\n" +
                "    }\n" +
                "    if (base.a <= 0.001) {\n" +
                "        discard;\n" +
                "    }\n" +
                "    var ao = clamp(input.pbr.x, 0.0, 1.0);\n" +
                "    var metallic = clamp(input.pbr.y, 0.0, 1.0);\n" +
                "    var roughness = clamp(input.pbr.z, 0.04, 1.0);\n" +
                "    if (uniforms.textureFlags.y > 0.5) {\n" +
                "        let mr = textureSample(metallicRoughnessTexture, metallicRoughnessSampler, uv);\n" +
                "        roughness = clamp(roughness * mr.g, 0.04, 1.0);\n" +
                "        metallic = clamp(metallic * mr.b, 0.0, 1.0);\n" +
                "    }\n" +
                "    if (uniforms.textureFlags.w > 0.5) {\n" +
                "        ao *= textureSample(occlusionTexture, occlusionSampler, uv).r;\n" +
                "    }\n" +
                "    var emissive = input.emissive;\n" +
                "    if (uniforms.emissiveFlags.x > 0.5) {\n" +
                "        emissive *= srgbToLinear(textureSample(emissiveTexture, emissiveSampler, uv).rgb);\n" +
                "    }\n" +
                "    let n = mappedNormal(input.normal, input.worldPosition, uv);\n" +
                "    let v = normalize(uniforms.cameraPosition.xyz - input.worldPosition);\n" +
                "    let l = normalize(-uniforms.lightDirection.xyz);\n" +
                "    let h = normalize(v + l);\n" +
                "    let albedo = max(base.rgb, vec3f(0.0));\n" +
                "    let f0 = mix(vec3f(0.04), albedo, vec3f(metallic));\n" +
                "    let ndl = max(dot(n, l), 0.0);\n" +
                "    let ndv = max(dot(n, v), 0.0);\n" +
                "    let f = fresnelSchlick(max(dot(h, v), 0.0), f0);\n" +
                "    let d = distributionGGX(n, h, roughness);\n" +
                "    let g = geometrySmith(n, v, l, roughness);\n" +
                "    let specular = (d * g * f) / max(4.0 * ndv * ndl, 0.000001);\n" +
                "    let kd = (vec3f(1.0) - f) * (1.0 - metallic);\n" +
                "    let radiance = uniforms.lightColorIntensity.rgb * uniforms.lightColorIntensity.a;\n" +
                "    var color = (kd * albedo / PI + specular) * radiance * ndl;\n" +
                "    color += uniforms.ambientColor.rgb * albedo * ao;\n" +
                "    color += emissive;\n" +
                "    return vec4f(linearToSrgb(color), base.a);\n" +
                "}\n";

        private final GraphicsContext graphics;
        private final ShaderModule shaderModule;
        private final Map<PipelineKey, RenderPipeline> pipelines = new HashMap<PipelineKey, RenderPipeline>();
        private final Texture whiteTexture;
        private final Texture blackTexture;
        private final Texture normalTexture;
        private final String providerId;
        private RenderContext3D context;
        private boolean disposed;

        GpuPbrShader(GraphicsContext graphics, String providerId) {
            this.graphics = graphics;
            this.providerId = providerId != null ? providerId : "";
            shaderModule = graphics.device().createShaderModule(shaderModuleDescriptor());
            whiteTexture = solidTexture("model batch white", 255, 255, 255, 255);
            blackTexture = solidTexture("model batch black", 0, 0, 0, 255);
            normalTexture = solidTexture("model batch normal", 128, 128, 255, 255);
        }

        @Override
        public boolean canRender(Renderable3D renderable) {
            return renderable != null
                    && renderable.meshPart() != null
                    && renderable.meshPart().mesh().vertexLayout() == Mesh.PBR_LAYOUT;
        }

        @Override
        public void begin(RenderContext3D context) {
            if (disposed) {
                throw new FdxException("ModelBatch PBR shader has been disposed");
            }
            this.context = context;
        }

        @Override
        public void render(Renderable3D renderable) {
            if (context == null) {
                throw new FdxException("Shader3D.begin() must be called before render");
            }
            MeshPart meshPart = renderable.meshPart();
            Mesh mesh = meshPart.mesh();
            RenderPass pass = context.pass();
            pass.setPipeline(pipeline(mesh.vertexLayout(), meshPart.primitiveTopology()));
            pass.setVertexBuffer(mesh.vertexBuffer());
            pass.setUniformMatrix4("u_model", renderable.worldTransform().values());
            pass.setUniformMatrix4("u_viewProjection", context.camera().combined().values());
            Vector3 cameraPosition = context.camera().position();
            pass.setUniform3f("u_cameraPosition", cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
            applyEnvironment(pass);
            applyMaterial(pass, renderable.material());
            int indexCount = meshPart.indexCount() > 0 ? meshPart.indexCount() : mesh.indexCount();
            if (indexCount > 0) {
                pass.setIndexBuffer(mesh.indexBuffer());
                pass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
                return;
            }
            int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : mesh.vertexCount();
            pass.draw(vertexCount, 1, meshPart.firstVertex(), 0);
        }

        @Override
        public void end() {
            context = null;
        }

        private RenderPipeline pipeline(VertexLayout vertexLayout, PrimitiveTopology topology) {
            PipelineKey key = new PipelineKey(vertexLayout, topology);
            RenderPipeline pipeline = pipelines.get(key);
            if (pipeline == null) {
                pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                        .shader(shaderModule, graphics.surfaceFormat())
                        .label(pipelineLabel())
                        .primitiveTopology(topology)
                        .sampledTextureCount(5)
                        .depthTestEnabled(true)
                        .depthWriteEnabled(true)
                        .vertexLayout(vertexLayout));
                pipelines.put(key, pipeline);
            }
            return pipeline;
        }

        private ShaderModuleDescriptor shaderModuleDescriptor() {
            if ("wgpu".equals(providerId)) {
                return ShaderModuleDescriptor.wgsl("model batch wgpu pbr", WGSL);
            }
            if ("vulkan".equals(providerId)) {
                return ShaderModuleDescriptor.spirv("model batch vulkan pbr",
                        VulkanPbrShaderSpirv.VERTEX, VulkanPbrShaderSpirv.FRAGMENT);
            }
            return ShaderModuleDescriptor.glsl("model batch gl pbr", VERTEX_GLSL, FRAGMENT_GLSL);
        }

        private String pipelineLabel() {
            if ("wgpu".equals(providerId)) {
                return "model batch wgpu pbr";
            }
            if ("vulkan".equals(providerId)) {
                return "model batch vulkan pbr";
            }
            return "model batch gl pbr";
        }

        private void applyEnvironment(RenderPass pass) {
            Color ambient = context.environment().ambientColor();
            pass.setUniform3f("u_ambientColor", ambient.red(), ambient.green(), ambient.blue());
            DirectionalLight directional = null;
            for (int i = 0; i < context.environment().lights().size(); i++) {
                Light light = context.environment().lights().get(i);
                if (light instanceof DirectionalLight) {
                    directional = (DirectionalLight)light;
                    break;
                }
            }
            if (directional == null) {
                pass.setUniform3f("u_lightDirection", -0.4f, -0.8f, -0.3f);
                pass.setUniform3f("u_lightColor", 1.0f, 1.0f, 1.0f);
                pass.setUniform1f("u_lightIntensity", 0.0f);
                return;
            }
            Vector3 direction = directional.direction();
            Color color = directional.color();
            pass.setUniform3f("u_lightDirection", direction.x(), direction.y(), direction.z());
            pass.setUniform3f("u_lightColor", color.red(), color.green(), color.blue());
            pass.setUniform1f("u_lightIntensity", directional.intensity());
        }

        private void applyMaterial(RenderPass pass, Material material) {
            PbrMaterial pbr = material instanceof PbrMaterial ? (PbrMaterial)material : null;
            Texture baseColor = pbr != null && pbr.baseColorTexture() != null ? pbr.baseColorTexture() : whiteTexture;
            Texture metallicRoughness = pbr != null && pbr.metallicRoughnessTexture() != null
                    ? pbr.metallicRoughnessTexture()
                    : whiteTexture;
            Texture normal = pbr != null && pbr.normalTexture() != null ? pbr.normalTexture() : normalTexture;
            Texture occlusion = pbr != null && pbr.occlusionTexture() != null ? pbr.occlusionTexture() : whiteTexture;
            Texture emissive = pbr != null && pbr.emissiveTexture() != null ? pbr.emissiveTexture() : blackTexture;
            pass.setTexture(0, baseColor);
            pass.setTexture(1, metallicRoughness);
            pass.setTexture(2, normal);
            pass.setTexture(3, occlusion);
            pass.setTexture(4, emissive);
            pass.setUniform1i("u_baseColorTexture", 0);
            pass.setUniform1i("u_metallicRoughnessTexture", 1);
            pass.setUniform1i("u_normalTexture", 2);
            pass.setUniform1i("u_occlusionTexture", 3);
            pass.setUniform1i("u_emissiveTexture", 4);
            pass.setUniform1i("u_hasBaseColorTexture", pbr != null && pbr.baseColorTexture() != null ? 1 : 0);
            pass.setUniform1i("u_hasMetallicRoughnessTexture", pbr != null && pbr.metallicRoughnessTexture() != null
                    ? 1 : 0);
            pass.setUniform1i("u_hasNormalTexture", pbr != null && pbr.normalTexture() != null ? 1 : 0);
            pass.setUniform1i("u_hasOcclusionTexture", pbr != null && pbr.occlusionTexture() != null ? 1 : 0);
            pass.setUniform1i("u_hasEmissiveTexture", pbr != null && pbr.emissiveTexture() != null ? 1 : 0);
        }

        private Texture solidTexture(String label, int red, int green, int blue, int alpha) {
            Texture texture = graphics.device().createTexture(TextureDescriptor.rgba8(label, 1, 1));
            ByteBuffer buffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
            buffer.put((byte)red);
            buffer.put((byte)green);
            buffer.put((byte)blue);
            buffer.put((byte)alpha);
            buffer.flip();
            graphics.device().writeTexture(texture, buffer);
            return texture;
        }

        @Override
        public void dispose() {
            if (disposed) {
                return;
            }
            disposed = true;
            for (Iterator<RenderPipeline> iterator = pipelines.values().iterator(); iterator.hasNext();) {
                iterator.next().dispose();
            }
            pipelines.clear();
            whiteTexture.dispose();
            blackTexture.dispose();
            normalTexture.dispose();
            shaderModule.dispose();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    private static final class PipelineKey {
        private final VertexLayout vertexLayout;
        private final PrimitiveTopology topology;

        PipelineKey(VertexLayout vertexLayout, PrimitiveTopology topology) {
            this.vertexLayout = vertexLayout;
            this.topology = topology != null ? topology : PrimitiveTopology.TRIANGLE_LIST;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PipelineKey)) {
                return false;
            }
            PipelineKey key = (PipelineKey)other;
            return vertexLayout == key.vertexLayout && topology == key.topology;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(vertexLayout) * 31 + topology.hashCode();
        }
    }
}
