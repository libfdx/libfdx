package io.github.libfdx.physics.box3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.DirectionalShadowMap3D;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** GPU-instanced solid and shadow renderer for repeated Box3D debug geometry. */
final class InstancedSolidRenderer implements Disposable {
    private static final int VERTEX_FLOATS = 6;
    private static final int INSTANCE_FLOATS = 20;
    private static final int INSTANCE_BYTES = INSTANCE_FLOATS * Float.BYTES;
    private static final int INITIAL_INSTANCE_CAPACITY = 128;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(VERTEX_FLOATS * Float.BYTES,
            VertexAttribute.of(0, VertexFormat.FLOAT32X3, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X3, 12));
    private static final VertexLayout INSTANCE_LAYOUT = VertexLayout.instance(INSTANCE_BYTES,
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(3, VertexFormat.FLOAT32X4, 16),
            VertexAttribute.of(4, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(5, VertexFormat.FLOAT32X4, 48),
            VertexAttribute.of(6, VertexFormat.FLOAT32X4, 64));
    private static final ShaderValueType MATRIX4 = ShaderValueType
            .matrix(ShaderScalarType.F32, 4, 4, 16)
            .named("mat4x4<f32>");
    private static final ShaderValueType FLOAT4 = ShaderValueType
            .vector(ShaderScalarType.F32, 4)
            .named("vec4<f32>");
    private static final ShaderParameterLayout MAIN_UNIFORM_LAYOUT = ShaderParameterLayout.of(192, 16,
            ShaderParameter.of("viewProjection", MATRIX4, 0, 64, 16),
            ShaderParameter.of("lightViewProjection", MATRIX4, 64, 64, 16),
            ShaderParameter.of("lightDirection", FLOAT4, 128, 16, 16),
            ShaderParameter.of("ambientColor", FLOAT4, 144, 16, 16),
            ShaderParameter.of("lightColorIntensity", FLOAT4, 160, 16, 16),
            ShaderParameter.of("shadowParams", FLOAT4, 176, 16, 16));
    private static final ShaderParameterLayout SHADOW_UNIFORM_LAYOUT = ShaderParameterLayout.of(64, 16,
            ShaderParameter.of("viewProjection", MATRIX4, 0, 64, 16));
    private static final ShaderParameterHandle MAIN_VIEW_PROJECTION =
            MAIN_UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderParameterHandle LIGHT_VIEW_PROJECTION =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightViewProjection");
    private static final ShaderParameterHandle LIGHT_DIRECTION =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightDirection");
    private static final ShaderParameterHandle AMBIENT_COLOR =
            MAIN_UNIFORM_LAYOUT.requireHandle("ambientColor");
    private static final ShaderParameterHandle LIGHT_COLOR_INTENSITY =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightColorIntensity");
    private static final ShaderParameterHandle SHADOW_PARAMS =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowParams");
    private static final ShaderParameterHandle SHADOW_VIEW_PROJECTION =
            SHADOW_UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final String MAIN_SHADER = """
            struct Uniforms {
                viewProjection : mat4x4<f32>,
                lightViewProjection : mat4x4<f32>,
                lightDirection : vec4f,
                ambientColor : vec4f,
                lightColorIntensity : vec4f,
                shadowParams : vec4f,
            };
            @group(0) @binding(0) var shadowTexture : texture_2d<f32>;
            @group(0) @binding(1) var shadowSampler : sampler;
            @group(1) @binding(0) var<uniform> uniforms : Uniforms;
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
                @location(2) model0 : vec4f,
                @location(3) model1 : vec4f,
                @location(4) model2 : vec4f,
                @location(5) model3 : vec4f,
                @location(6) color : vec4f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) worldPosition : vec3f,
                @location(1) normal : vec3f,
                @location(2) color : vec4f,
            };
            @vertex fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                let model = mat4x4<f32>(input.model0, input.model1, input.model2, input.model3);
                let world = model * vec4f(input.position, 1.0);
                let normalMatrix = mat3x3<f32>(input.model0.xyz, input.model1.xyz, input.model2.xyz);
                output.position = uniforms.viewProjection * world;
                output.worldPosition = world.xyz;
                output.normal = normalize(normalMatrix * input.normal);
                output.color = input.color;
                return output;
            }
            fn unpackShadowDepth(encodedDepth : vec4f) -> f32 {
                return encodedDepth.r + encodedDepth.g / 255.0;
            }
            fn sampleVisibility(uv : vec2f, currentDepth : f32, offset : vec2f) -> f32 {
                let sampleUv = uv + offset;
                if (sampleUv.x < 0.0 || sampleUv.x > 1.0
                        || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
                    return 1.0;
                }
                let closest = unpackShadowDepth(
                        textureSampleLevel(shadowTexture, shadowSampler, sampleUv, 0.0));
                if (currentDepth - uniforms.shadowParams.y > closest) {
                    return 1.0 - uniforms.shadowParams.z;
                }
                return 1.0;
            }
            fn shadowVisibility(worldPosition : vec3f) -> f32 {
                if (uniforms.shadowParams.x < 0.5) {
                    return 1.0;
                }
                let clip = uniforms.lightViewProjection * vec4f(worldPosition, 1.0);
                if (abs(clip.w) <= 0.000001) {
                    return 1.0;
                }
                let ndc = clip.xyz / clip.w;
                if (ndc.z < -1.0 || ndc.z > 1.0) {
                    return 1.0;
                }
                let uv = vec2f(ndc.x * 0.5 + 0.5,
                        0.5 + ndc.y * 0.5 * uniforms.shadowParams.w);
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    return 1.0;
                }
                let currentDepth = ndc.z * 0.5 + 0.5;
                let dimensions = vec2f(textureDimensions(shadowTexture, 0));
                let texel = vec2f(1.0) / max(dimensions, vec2f(1.0));
                var visibility = sampleVisibility(uv, currentDepth, vec2f(0.0)) * 0.4;
                visibility += sampleVisibility(uv, currentDepth, vec2f(texel.x, 0.0)) * 0.15;
                visibility += sampleVisibility(uv, currentDepth, vec2f(-texel.x, 0.0)) * 0.15;
                visibility += sampleVisibility(uv, currentDepth, vec2f(0.0, texel.y)) * 0.15;
                visibility += sampleVisibility(uv, currentDepth, vec2f(0.0, -texel.y)) * 0.15;
                return visibility;
            }
            fn linearToSrgb(value : vec3f) -> vec3f {
                return pow(max(value, vec3f(0.0)), vec3f(1.0 / 2.2));
            }
            @fragment fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let normal = normalize(input.normal);
                let lightDirection = normalize(-uniforms.lightDirection.xyz);
                let diffuse = max(dot(normal, lightDirection), 0.0);
                let shadow = shadowVisibility(input.worldPosition);
                let direct = uniforms.lightColorIntensity.rgb
                        * uniforms.lightColorIntensity.a * diffuse * shadow;
                let linearColor = input.color.rgb * (uniforms.ambientColor.rgb + direct);
                return vec4f(linearToSrgb(min(linearColor, vec3f(1.0))), input.color.a);
            }
            """;

    private static final String SHADOW_SHADER = """
            struct Uniforms {
                viewProjection : mat4x4<f32>,
            };
            @group(0) @binding(0) var<uniform> uniforms : Uniforms;
            struct VertexInput {
                @location(0) position : vec3f,
                @location(2) model0 : vec4f,
                @location(3) model1 : vec4f,
                @location(4) model2 : vec4f,
                @location(5) model3 : vec4f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) depth : f32,
            };
            @vertex fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                let model = mat4x4<f32>(input.model0, input.model1, input.model2, input.model3);
                let clip = uniforms.viewProjection * model * vec4f(input.position, 1.0);
                output.position = clip;
                output.position.z = clip.z * 0.5 + clip.w * 0.5;
                output.depth = (clip.z / clip.w) * 0.5 + 0.5;
                return output;
            }
            @fragment fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let depth = clamp(input.depth, 0.0, 0.999999);
                let raw = fract(depth * vec2f(1.0, 255.0));
                return vec4f(raw.x - raw.y / 255.0, raw.y, 0.0, 1.0);
            }
            """;

    private final GraphicsContext graphics;
    private final Array<Geometry> geometries = new Array<Geometry>();
    private final RenderPassDescriptor mainPassDescriptor = new RenderPassDescriptor()
            .label("box3d instanced solid pass")
            .colorLoadOp(LoadOp.load())
            .colorStoreOp(StoreOp.store())
            .depthClear(1.0f);
    private final ShaderParameterBlock mainUniformBlock =
            ShaderParameterBlock.allocate(MAIN_UNIFORM_LAYOUT);
    private final ShaderParameterBlock shadowUniformBlock =
            ShaderParameterBlock.allocate(SHADOW_UNIFORM_LAYOUT);
    private final float[] lightMatrix = new float[Matrix4.VALUE_COUNT];
    private ShaderModule mainShader;
    private ShaderModule shadowShader;
    private RenderPipeline mainPipeline;
    private RenderPipeline shadowPipeline;
    private ByteBuffer uploadBuffer;
    private int mainDrawCallCount;
    private int shadowDrawCallCount;
    private boolean disposed;

    InstancedSolidRenderer(GraphicsContext graphics) {
        if(graphics == null) {
            throw new FdxException("InstancedSolidRenderer graphics cannot be null");
        }
        this.graphics = graphics;
        if(!graphics.device().capabilities().supports(GraphicsFeature.INSTANCED_DRAW)) {
            return;
        }
        mainShader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "box3d instanced solids", MAIN_SHADER));
        shadowShader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "box3d instanced shadows", SHADOW_SHADER));
        mainPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(mainShader, graphics.surfaceFormat())
                .label("box3d instanced solids")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexLayouts(VERTEX_LAYOUT, INSTANCE_LAYOUT)
                .depthTestEnabled(true)
                .depthWriteEnabled(true));
        shadowPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shadowShader, TextureFormat.RGBA8_UNORM)
                .label("box3d instanced shadows")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexLayouts(VERTEX_LAYOUT, INSTANCE_LAYOUT)
                .depthTestEnabled(true)
                .depthWriteEnabled(true));
    }

    boolean supported() {
        return mainPipeline != null && shadowPipeline != null;
    }

    Geometry createGeometry(String id, float[] positions, float[] normals) {
        ensureNotDisposed();
        if(!supported() || positions == null || positions.length == 0) {
            return null;
        }
        Geometry geometry = new Geometry(id, positions, normals);
        geometries.add(geometry);
        return geometry;
    }

    void beginFrame() {
        mainDrawCallCount = 0;
        shadowDrawCallCount = 0;
        for(int i = 0; i < geometries.size(); i++) {
            geometries.get(i).beginFrame();
        }
    }

    boolean hasInstances() {
        for(int i = 0; i < geometries.size(); i++) {
            if(geometries.get(i).instanceCount > 0) {
                return true;
            }
        }
        return false;
    }

    void renderShadow(DirectionalShadowMap3D shadowMap, DirectionalLight light) {
        ensureNotDisposed();
        if(!supported() || !hasInstances()) {
            return;
        }
        shadowMap.render(light, (pass, viewProjection) -> {
            viewProjection.copyValues(lightMatrix, 0);
            shadowUniformBlock.setFloatMatrix(SHADOW_VIEW_PROJECTION, lightMatrix, 0);
            pass.setPipeline(shadowPipeline);
            pass.setParameterBlock(0, 0, shadowUniformBlock);
            for(int i = 0; i < geometries.size(); i++) {
                Geometry geometry = geometries.get(i);
                if(geometry.render(pass)) {
                    shadowDrawCallCount++;
                }
            }
        });
    }

    void render(float[] viewProjection, DirectionalLight light, Color ambient,
            DirectionalShadowMap3D shadowMap, boolean shadowsEnabled) {
        ensureNotDisposed();
        if(!supported() || !hasInstances()) {
            return;
        }
        if(viewProjection == null || viewProjection.length < Matrix4.VALUE_COUNT) {
            throw new FdxException("Instanced solid rendering requires a 4x4 view-projection matrix");
        }
        Vector3 lightDirection = light.direction();
        Color lightColor = light.color();
        Color ambientColor = ambient != null ? ambient : Color.BLACK;
        shadowMap.lightViewProjection().copyValues(lightMatrix, 0);
        mainUniformBlock.setFloatMatrix(MAIN_VIEW_PROJECTION, viewProjection, 0);
        mainUniformBlock.setFloatMatrix(LIGHT_VIEW_PROJECTION, lightMatrix, 0);
        mainUniformBlock.setFloat4(LIGHT_DIRECTION, lightDirection.x(), lightDirection.y(),
                lightDirection.z(), 0.0f);
        mainUniformBlock.setFloat4(AMBIENT_COLOR, ambientColor.red(), ambientColor.green(),
                ambientColor.blue(), ambientColor.alpha());
        mainUniformBlock.setFloat4(LIGHT_COLOR_INTENSITY, lightColor.red(), lightColor.green(),
                lightColor.blue(), light.intensity());
        mainUniformBlock.setFloat4(SHADOW_PARAMS, shadowsEnabled ? 1.0f : 0.0f,
                shadowMap.bias(), shadowMap.strength(), shadowYSign());

        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(mainPassDescriptor
                .colorAttachment(frame.colorAttachment()));
        try {
            pass.setPipeline(mainPipeline);
            pass.setParameterBlock(1, 0, mainUniformBlock);
            pass.setTextureBinding(0, 0, shadowMap.texture());
            pass.setTextureSamplerBinding(0, 1, shadowMap.texture());
            for(int i = 0; i < geometries.size(); i++) {
                Geometry geometry = geometries.get(i);
                if(geometry.render(pass)) {
                    mainDrawCallCount++;
                }
            }
        }
        finally {
            pass.end();
        }
    }

    int mainDrawCallCount() {
        return mainDrawCallCount;
    }

    int shadowDrawCallCount() {
        return shadowDrawCallCount;
    }

    private float shadowYSign() {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "opengl".equals(provider)
                || "webgl".equals(provider) || "gles".equals(provider) ? 1.0f : -1.0f;
    }

    private ByteBuffer uploadBuffer(int byteCount) {
        if(uploadBuffer == null || uploadBuffer.capacity() < byteCount) {
            uploadBuffer = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
        }
        uploadBuffer.clear();
        return uploadBuffer;
    }

    @Override
    public void dispose() {
        if(disposed) {
            return;
        }
        disposed = true;
        while(!geometries.isEmpty()) {
            geometries.get(geometries.size() - 1).dispose();
        }
        if(mainPipeline != null) {
            mainPipeline.dispose();
            mainPipeline = null;
        }
        if(shadowPipeline != null) {
            shadowPipeline.dispose();
            shadowPipeline = null;
        }
        if(mainShader != null) {
            mainShader.dispose();
            mainShader = null;
        }
        if(shadowShader != null) {
            shadowShader.dispose();
            shadowShader = null;
        }
        uploadBuffer = null;
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private void ensureNotDisposed() {
        if(disposed) {
            throw new FdxException("InstancedSolidRenderer has been disposed");
        }
    }

    final class Geometry implements Disposable {
        private final String id;
        private Buffer vertexBuffer;
        private Buffer instanceBuffer;
        private final int vertexCount;
        private float[] instances = new float[INITIAL_INSTANCE_CAPACITY * INSTANCE_FLOATS];
        private int instanceFloatCount;
        private int instanceCount;
        private boolean prepared;
        private boolean geometryDisposed;

        private Geometry(String id, float[] positions, float[] normals) {
            this.id = id != null ? id : "box3d-solid";
            if(positions.length % 9 != 0) {
                throw new FdxException("Instanced solid positions must contain complete triangles");
            }
            if(normals == null || normals.length != positions.length) {
                throw new FdxException("Instanced solid normals must match the position count");
            }
            vertexCount = positions.length / 3;
            int floatCount = vertexCount * VERTEX_FLOATS;
            int byteCount = floatCount * Float.BYTES;
            float[] interleaved = new float[floatCount];
            int target = 0;
            for(int i = 0; i < positions.length; i += 3) {
                interleaved[target++] = positions[i];
                interleaved[target++] = positions[i + 1];
                interleaved[target++] = positions[i + 2];
                interleaved[target++] = normals[i];
                interleaved[target++] = normals[i + 1];
                interleaved[target++] = normals[i + 2];
            }
            vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex(
                    this.id + " vertices", byteCount));
            ByteBuffer vertices = ByteBuffer.allocateDirect(byteCount).order(ByteOrder.nativeOrder());
            vertices.asFloatBuffer().put(interleaved);
            vertices.limit(byteCount);
            vertices.position(0);
            graphics.device().writeBuffer(vertexBuffer, vertices);
            instanceBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                    this.id + " instances", instances.length * Float.BYTES));
        }

        void append(Matrix4 transform, int color, float[] multiplier) {
            if(geometryDisposed) {
                return;
            }
            ensureInstanceCapacity(instanceFloatCount + INSTANCE_FLOATS);
            transform.copyValues(instances, instanceFloatCount);
            instanceFloatCount += Matrix4.VALUE_COUNT;
            instances[instanceFloatCount++] = srgbToLinear(((color >>> 16) & 0xFF) / 255.0f)
                    * multiplier[0];
            instances[instanceFloatCount++] = srgbToLinear(((color >>> 8) & 0xFF) / 255.0f)
                    * multiplier[1];
            instances[instanceFloatCount++] = srgbToLinear((color & 0xFF) / 255.0f)
                    * multiplier[2];
            instances[instanceFloatCount++] = multiplier[3];
            instanceCount++;
        }

        private void beginFrame() {
            instanceFloatCount = 0;
            instanceCount = 0;
            prepared = false;
        }

        private boolean render(RenderPass pass) {
            if(geometryDisposed || instanceCount == 0) {
                return false;
            }
            prepare();
            pass.setVertexBuffer(0, vertexBuffer);
            pass.setVertexBuffer(1, instanceBuffer);
            pass.draw(vertexCount, instanceCount, 0, 0);
            return true;
        }

        private void prepare() {
            if(prepared) {
                return;
            }
            int byteCount = instanceFloatCount * Float.BYTES;
            ensureInstanceBuffer(byteCount);
            ByteBuffer upload = uploadBuffer(byteCount);
            upload.asFloatBuffer().put(instances, 0, instanceFloatCount);
            upload.limit(byteCount);
            upload.position(0);
            graphics.device().writeBuffer(instanceBuffer, upload);
            prepared = true;
        }

        private void ensureInstanceCapacity(int requiredFloats) {
            if(requiredFloats <= instances.length) {
                return;
            }
            int newCapacity = instances.length;
            while(newCapacity < requiredFloats) {
                newCapacity *= 2;
            }
            float[] grown = new float[newCapacity];
            System.arraycopy(instances, 0, grown, 0, instanceFloatCount);
            instances = grown;
        }

        private void ensureInstanceBuffer(int byteCount) {
            if(instanceBuffer != null && instanceBuffer.size() >= byteCount) {
                return;
            }
            if(instanceBuffer != null) {
                instanceBuffer.dispose();
            }
            instanceBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(
                    id + " instances", byteCount));
        }

        @Override
        public void dispose() {
            if(geometryDisposed) {
                return;
            }
            geometryDisposed = true;
            if(instanceBuffer != null) {
                instanceBuffer.dispose();
                instanceBuffer = null;
            }
            if(vertexBuffer != null) {
                vertexBuffer.dispose();
                vertexBuffer = null;
            }
            instances = new float[0];
            instanceFloatCount = 0;
            instanceCount = 0;
            prepared = false;
            geometries.removeValue(this, true);
        }

        @Override
        public boolean isDisposed() {
            return geometryDisposed;
        }
    }

    private static float srgbToLinear(float value) {
        return value <= 0.04045f ? value / 12.92f
                : (float)Math.pow((value + 0.055f) / 1.055f, 2.4f);
    }

}
