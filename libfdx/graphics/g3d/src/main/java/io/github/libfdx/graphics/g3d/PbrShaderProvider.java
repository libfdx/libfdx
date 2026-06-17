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
import io.github.libfdx.graphics.ShaderAttribute;
import io.github.libfdx.graphics.ShaderBinding;
import io.github.libfdx.graphics.ShaderBindingType;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.ShaderReflection;
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

/**
 * Provides pbr shader services.
 *
 * @author xpenatan
 */
public final class PbrShaderProvider implements ShaderProvider3D, Disposable {
    private static final String POSITION_COLOR_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) color : vec4f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) color : vec4f,
            };
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 1.0);
                output.color = input.color;
                return output;
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return input.color;
            }
            """;
    private static final String PBR_SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
                @location(2) uv : vec2f,
                @location(3) color : vec4f,
                @location(4) pbr : vec3f,
                @location(5) emissive : vec3f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) worldPosition : vec3f,
                @location(1) normal : vec3f,
                @location(2) uv : vec2f,
                @location(3) color : vec4f,
                @location(4) pbr : vec3f,
                @location(5) emissive : vec3f,
            };
            struct PbrUniforms {
                model : mat4x4<f32>,
                viewProjection : mat4x4<f32>,
                cameraPosition : vec4f,
                ambientColor : vec4f,
                lightDirection : vec4f,
                lightColorIntensity : vec4f,
                textureFlags : vec4f,
                emissiveFlags : vec4f,
            };
            @group(0) @binding(0) var baseColorTexture : texture_2d<f32>;
            @group(0) @binding(1) var baseColorSampler : sampler;
            @group(0) @binding(2) var metallicRoughnessTexture : texture_2d<f32>;
            @group(0) @binding(3) var metallicRoughnessSampler : sampler;
            @group(0) @binding(4) var normalTexture : texture_2d<f32>;
            @group(0) @binding(5) var normalSampler : sampler;
            @group(0) @binding(6) var occlusionTexture : texture_2d<f32>;
            @group(0) @binding(7) var occlusionSampler : sampler;
            @group(0) @binding(8) var emissiveTexture : texture_2d<f32>;
            @group(0) @binding(9) var emissiveSampler : sampler;
            @group(1) @binding(0) var<uniform> uniforms : PbrUniforms;
            const PI : f32 = 3.14159265359;
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                let worldPosition = uniforms.model * vec4f(input.position, 1.0);
                output.worldPosition = worldPosition.xyz;
                output.normal = (uniforms.model * vec4f(input.normal, 0.0)).xyz;
                output.uv = input.uv;
                output.color = input.color;
                output.pbr = input.pbr;
                output.emissive = input.emissive;
                output.position = uniforms.viewProjection * worldPosition;
                return output;
            }
            fn srgbToLinear(value : vec3f) -> vec3f {
                return pow(max(value, vec3f(0.0)), vec3f(2.2));
            }
            fn linearToSrgb(value : vec3f) -> vec3f {
                return pow(max(value, vec3f(0.0)), vec3f(1.0 / 2.2));
            }
            fn distributionGGX(n : vec3f, h : vec3f, roughness : f32) -> f32 {
                let a = roughness * roughness;
                let a2 = a * a;
                let ndh = max(dot(n, h), 0.0);
                let denom = ndh * ndh * (a2 - 1.0) + 1.0;
                return a2 / max(PI * denom * denom, 0.000001);
            }
            fn geometrySchlickGGX(ndv : f32, roughness : f32) -> f32 {
                let r = roughness + 1.0;
                let k = (r * r) / 8.0;
                return ndv / max(ndv * (1.0 - k) + k, 0.000001);
            }
            fn geometrySmith(n : vec3f, v : vec3f, l : vec3f, roughness : f32) -> f32 {
                return geometrySchlickGGX(max(dot(n, v), 0.0), roughness)
                        * geometrySchlickGGX(max(dot(n, l), 0.0), roughness);
            }
            fn fresnelSchlick(cosTheta : f32, f0 : vec3f) -> vec3f {
                return f0 + (vec3f(1.0) - f0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
            }
            fn mappedNormal(nIn : vec3f, worldPosition : vec3f, uv : vec2f) -> vec3f {
                let n = normalize(nIn);
                if (uniforms.textureFlags.z < 0.5) {
                    return n;
                }
                let sampleNormal = textureSample(normalTexture, normalSampler, uv).xyz * 2.0 - vec3f(1.0);
                let q1 = dpdx(worldPosition);
                let q2 = dpdy(worldPosition);
                let st1 = dpdx(uv);
                let st2 = dpdy(uv);
                let tangent = q1 * st2.y - q2 * st1.y;
                if (dot(tangent, tangent) < 0.000001) {
                    return n;
                }
                let t = normalize(tangent);
                let b = normalize(cross(n, t));
                return normalize(mat3x3<f32>(t, b, n) * sampleNormal);
            }
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let uv = input.uv;
                var base = input.color;
                if (uniforms.textureFlags.x > 0.5) {
                    let texel = textureSample(baseColorTexture, baseColorSampler, uv);
                    base = vec4f(base.rgb * srgbToLinear(texel.rgb), base.a * texel.a);
                }
                if (base.a <= 0.001) {
                    discard;
                }
                var ao = clamp(input.pbr.x, 0.0, 1.0);
                var metallic = clamp(input.pbr.y, 0.0, 1.0);
                var roughness = clamp(input.pbr.z, 0.04, 1.0);
                if (uniforms.textureFlags.y > 0.5) {
                    let mr = textureSample(metallicRoughnessTexture, metallicRoughnessSampler, uv);
                    roughness = clamp(roughness * mr.g, 0.04, 1.0);
                    metallic = clamp(metallic * mr.b, 0.0, 1.0);
                }
                if (uniforms.textureFlags.w > 0.5) {
                    ao *= textureSample(occlusionTexture, occlusionSampler, uv).r;
                }
                var emissive = input.emissive;
                if (uniforms.emissiveFlags.x > 0.5) {
                    emissive *= srgbToLinear(textureSample(emissiveTexture, emissiveSampler, uv).rgb);
                }
                let n = mappedNormal(input.normal, input.worldPosition, uv);
                let v = normalize(uniforms.cameraPosition.xyz - input.worldPosition);
                let l = normalize(-uniforms.lightDirection.xyz);
                let h = normalize(v + l);
                let albedo = max(base.rgb, vec3f(0.0));
                let f0 = mix(vec3f(0.04), albedo, vec3f(metallic));
                let ndl = max(dot(n, l), 0.0);
                let ndv = max(dot(n, v), 0.0);
                let f = fresnelSchlick(max(dot(h, v), 0.0), f0);
                let d = distributionGGX(n, h, roughness);
                let g = geometrySmith(n, v, l, roughness);
                let specular = (d * g * f) / max(4.0 * ndv * ndl, 0.000001);
                let kd = (vec3f(1.0) - f) * (1.0 - metallic);
                let radiance = uniforms.lightColorIntensity.rgb * uniforms.lightColorIntensity.a;
                var color = (kd * albedo / PI + specular) * radiance * ndl;
                color += uniforms.ambientColor.rgb * albedo * ao;
                color += emissive;
                return vec4f(linearToSrgb(color), base.a);
            }
            """;
    private static final ShaderReflection PBR_REFLECTION = ShaderReflection.of(new ShaderBinding[] {
            ShaderBinding.of(0, 0, "baseColorTexture", ShaderBindingType.TEXTURE),
            ShaderBinding.of(0, 1, "baseColorSampler", ShaderBindingType.SAMPLER),
            ShaderBinding.of(0, 2, "metallicRoughnessTexture", ShaderBindingType.TEXTURE),
            ShaderBinding.of(0, 3, "metallicRoughnessSampler", ShaderBindingType.SAMPLER),
            ShaderBinding.of(0, 4, "normalTexture", ShaderBindingType.TEXTURE),
            ShaderBinding.of(0, 5, "normalSampler", ShaderBindingType.SAMPLER),
            ShaderBinding.of(0, 6, "occlusionTexture", ShaderBindingType.TEXTURE),
            ShaderBinding.of(0, 7, "occlusionSampler", ShaderBindingType.SAMPLER),
            ShaderBinding.of(0, 8, "emissiveTexture", ShaderBindingType.TEXTURE),
            ShaderBinding.of(0, 9, "emissiveSampler", ShaderBindingType.SAMPLER),
            ShaderBinding.of(1, 0, "uniforms", ShaderBindingType.UNIFORM_BUFFER)
    }, new ShaderAttribute[] {
            ShaderAttribute.of(0, "position", VertexFormat.FLOAT32X3),
            ShaderAttribute.of(1, "normal", VertexFormat.FLOAT32X3),
            ShaderAttribute.of(2, "uv", VertexFormat.FLOAT32X2),
            ShaderAttribute.of(3, "color", VertexFormat.FLOAT32X4),
            ShaderAttribute.of(4, "pbr", VertexFormat.FLOAT32X3),
            ShaderAttribute.of(5, "emissive", VertexFormat.FLOAT32X3)
    });

    private final PositionColorShader shader;
    private final GpuPbrShader gpuShader;
    private boolean disposed;

    /**
     * Creates a PBR shader provider.
     *
     * @param graphics the graphics context
     */
    public PbrShaderProvider(GraphicsContext graphics) {
        this(graphics, new PbrShaderConfig());
    }

    /**
     * Creates a PBR shader provider.
     *
     * @param graphics the graphics context
     * @param config the configuration
     */
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
                || "wgpu".equals(providerId) || "vulkan".equals(providerId) || "metal".equals(providerId);
    }

    /**
     * Runs the shader step.
     *
     * @param renderable the renderable
     * @param context the context
     * @return the shader
     */
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

    /**
     * Releases resources held by this instance.
     */
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

    /**
     * Returns whether this instance has already been disposed.
     *
     * @return true if disposed is enabled or true; false otherwise
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Represents a position color shader.
     *
     * @author xpenatan
     */
    private static final class PositionColorShader implements Shader3D {
        private static final float PI = 3.14159265359f;
        private final GraphicsContext graphics;
        private final ShaderModule shaderModule;
        private final Map<PipelineKey, RenderPipeline> pipelines = new HashMap<PipelineKey, RenderPipeline>();
        private ScratchBuffer[] scratchBuffers = new ScratchBuffer[4];
        private int scratchCursor;
        private RenderContext3D context;
        private boolean disposed;

        PositionColorShader(GraphicsContext graphics) {
            this.graphics = graphics;
            shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                    "model batch position color", POSITION_COLOR_SHADER_SOURCE));
        }

        /**
         * Returns whether this instance can render.
         *
         * @param renderable the renderable
         * @return true if can render succeeds or is active; false otherwise
         */
        @Override
        public boolean canRender(Renderable3D renderable) {
            if (renderable == null || renderable.meshPart() == null) {
                return false;
            }
            Mesh mesh = renderable.meshPart().mesh();
            return isPositionColorLayout(mesh.vertexLayout())
                    || mesh.hasPositionColor3DSource();
        }

        /**
         * Begins the operation.
         *
         * @param context the context
         */
        @Override
        public void begin(RenderContext3D context) {
            if (disposed) {
                throw new FdxException("ModelBatch shader has been disposed");
            }
            this.context = context;
            scratchCursor = 0;
        }

        /**
         * Renders the current content.
         *
         * @param renderable the renderable
         */
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

        /**
         * Ends the operation.
         */
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

        /**
         * Releases resources held by this instance.
         */
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

        /**
         * Returns whether this instance has already been disposed.
         *
         * @return true if disposed is enabled or true; false otherwise
         */
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

        /**
         * Represents a scratch buffer.
         *
         * @author xpenatan
         */
        private static final class ScratchBuffer {
            private final Buffer buffer;
            private final int byteCount;

            ScratchBuffer(Buffer buffer, int byteCount) {
                this.buffer = buffer;
                this.byteCount = byteCount;
            }
        }

        /**
         * Represents a world vertex.
         *
         * @author xpenatan
         */
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

        /**
         * Represents a color vertex.
         *
         * @author xpenatan
         */
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

        /**
         * Represents a projected mesh.
         *
         * @author xpenatan
         */
        private static final class ProjectedMesh {
            private final float[] vertices;
            private final int vertexCount;

            ProjectedMesh(float[] vertices, int vertexCount) {
                this.vertices = vertices;
                this.vertexCount = vertexCount;
            }
        }

        /**
         * Represents a projected triangle.
         *
         * @author xpenatan
         */
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

        /**
         * Represents a projected vertex.
         *
         * @author xpenatan
         */
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

    /**
     * Represents a gpu pbr shader.
     *
     * @author xpenatan
     */
    private static final class GpuPbrShader implements Shader3D {
        private static final ShaderReflection REFLECTION = PBR_REFLECTION;
        private static final int SAMPLED_TEXTURE_COUNT = sampledTextureCount(REFLECTION);
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

        /**
         * Returns whether this instance can render.
         *
         * @param renderable the renderable
         * @return true if can render succeeds or is active; false otherwise
         */
        @Override
        public boolean canRender(Renderable3D renderable) {
            return renderable != null
                    && renderable.meshPart() != null
                    && renderable.meshPart().mesh().vertexLayout() == Mesh.PBR_LAYOUT;
        }

        /**
         * Begins the operation.
         *
         * @param context the context
         */
        @Override
        public void begin(RenderContext3D context) {
            if (disposed) {
                throw new FdxException("ModelBatch PBR shader has been disposed");
            }
            this.context = context;
        }

        /**
         * Renders the current content.
         *
         * @param renderable the renderable
         */
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

        /**
         * Ends the operation.
         */
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
                        .shaderReflection(REFLECTION)
                        .primitiveTopology(topology)
                        .sampledTextureCount(SAMPLED_TEXTURE_COUNT)
                        .depthTestEnabled(true)
                        .depthWriteEnabled(true)
                        .vertexLayout(vertexLayout));
                pipelines.put(key, pipeline);
            }
            return pipeline;
        }

        private ShaderModuleDescriptor shaderModuleDescriptor() {
            return ShaderModuleDescriptor.wgsl("model batch pbr", PBR_SHADER_SOURCE);
        }

        private String pipelineLabel() {
            if ("wgpu".equals(providerId)) {
                return "model batch wgpu pbr";
            }
            if ("vulkan".equals(providerId)) {
                return "model batch vulkan pbr";
            }
            if ("metal".equals(providerId)) {
                return "model batch metal pbr";
            }
            return "model batch gl pbr";
        }

        private static int sampledTextureCount(ShaderReflection reflection) {
            int count = 0;
            ShaderBinding[] bindings = reflection.bindings();
            for (int i = 0; i < bindings.length; i++) {
                if (bindings[i].type() == ShaderBindingType.TEXTURE) {
                    count++;
                }
            }
            return count;
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
            pass.setUniform1i("f_baseColorTexture_baseColorSampler", 0);
            pass.setUniform1i("f_metallicRoughnessTexture_metallicRoughnessSampler", 1);
            pass.setUniform1i("f_normalTexture_normalSampler", 2);
            pass.setUniform1i("f_occlusionTexture_occlusionSampler", 3);
            pass.setUniform1i("f_emissiveTexture_emissiveSampler", 4);
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

        /**
         * Releases resources held by this instance.
         */
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

        /**
         * Returns whether this instance has already been disposed.
         *
         * @return true if disposed is enabled or true; false otherwise
         */
        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }

    /**
     * Represents a pipeline key.
     *
     * @author xpenatan
     */
    private static final class PipelineKey {
        private final VertexLayout vertexLayout;
        private final PrimitiveTopology topology;

        PipelineKey(VertexLayout vertexLayout, PrimitiveTopology topology) {
            this.vertexLayout = vertexLayout;
            this.topology = topology != null ? topology : PrimitiveTopology.TRIANGLE_LIST;
        }

        /**
         * Compares this instance with another object for equality.
         *
         * @param other the other
         * @return true if equals succeeds or is active; false otherwise
         */
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

        /**
         * Returns the hash code for this instance.
         *
         * @return the hash code
         */
        @Override
        public int hashCode() {
            return System.identityHashCode(vertexLayout) * 31 + topology.hashCode();
        }
    }

}
