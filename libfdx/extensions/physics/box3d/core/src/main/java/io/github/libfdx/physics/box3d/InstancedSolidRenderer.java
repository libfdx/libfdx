package io.github.libfdx.physics.box3d;

import io.github.libfdx.collections.Array;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.ColorTargetState;
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
import io.github.libfdx.graphics.g3d.CascadedShadowMap3D;
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
    private static final int MAX_SHADOW_CASCADES = 3;
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
    private static final ShaderParameterLayout MAIN_UNIFORM_LAYOUT = ShaderParameterLayout.of(432, 16,
            ShaderParameter.of("viewProjection", MATRIX4, 0, 64, 16),
            ShaderParameter.of("lightViewProjection0", MATRIX4, 64, 64, 16),
            ShaderParameter.of("lightViewProjection1", MATRIX4, 128, 64, 16),
            ShaderParameter.of("lightViewProjection2", MATRIX4, 192, 64, 16),
            ShaderParameter.of("lightDirection", FLOAT4, 256, 16, 16),
            ShaderParameter.of("ambientColor", FLOAT4, 272, 16, 16),
            ShaderParameter.of("lightColorIntensity", FLOAT4, 288, 16, 16),
            ShaderParameter.of("shadowParams", FLOAT4, 304, 16, 16),
            ShaderParameter.of("shadowCascadeSplits", FLOAT4, 320, 16, 16),
            ShaderParameter.of("shadowBiases", FLOAT4, 336, 16, 16),
            ShaderParameter.of("shadowCameraPosition", FLOAT4, 352, 16, 16),
            ShaderParameter.of("shadowCameraDirection", FLOAT4, 368, 16, 16),
            ShaderParameter.of("shadowFilterParams", FLOAT4, 384, 16, 16),
            ShaderParameter.of("shadowFilterScales", FLOAT4, 400, 16, 16),
            ShaderParameter.of("shadowFadeParams", FLOAT4, 416, 16, 16));
    private static final ShaderParameterLayout SHADOW_UNIFORM_LAYOUT = ShaderParameterLayout.of(112, 16,
            ShaderParameter.of("viewProjection", MATRIX4, 0, 64, 16),
            ShaderParameter.of("fadeCameraPosition", FLOAT4, 64, 16, 16),
            ShaderParameter.of("fadeCameraDirection", FLOAT4, 80, 16, 16),
            ShaderParameter.of("fadeParams", FLOAT4, 96, 16, 16));
    private static final ShaderParameterHandle MAIN_VIEW_PROJECTION =
            MAIN_UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderParameterHandle LIGHT_VIEW_PROJECTION_0 =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightViewProjection0");
    private static final ShaderParameterHandle LIGHT_VIEW_PROJECTION_1 =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightViewProjection1");
    private static final ShaderParameterHandle LIGHT_VIEW_PROJECTION_2 =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightViewProjection2");
    private static final ShaderParameterHandle LIGHT_DIRECTION =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightDirection");
    private static final ShaderParameterHandle AMBIENT_COLOR =
            MAIN_UNIFORM_LAYOUT.requireHandle("ambientColor");
    private static final ShaderParameterHandle LIGHT_COLOR_INTENSITY =
            MAIN_UNIFORM_LAYOUT.requireHandle("lightColorIntensity");
    private static final ShaderParameterHandle SHADOW_PARAMS =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowParams");
    private static final ShaderParameterHandle SHADOW_CASCADE_SPLITS =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowCascadeSplits");
    private static final ShaderParameterHandle SHADOW_BIASES =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowBiases");
    private static final ShaderParameterHandle SHADOW_CAMERA_POSITION =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowCameraPosition");
    private static final ShaderParameterHandle SHADOW_CAMERA_DIRECTION =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowCameraDirection");
    private static final ShaderParameterHandle SHADOW_FILTER_PARAMS =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowFilterParams");
    private static final ShaderParameterHandle SHADOW_FILTER_SCALES =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowFilterScales");
    private static final ShaderParameterHandle SHADOW_FADE_PARAMS_MAIN =
            MAIN_UNIFORM_LAYOUT.requireHandle("shadowFadeParams");
    private static final ShaderParameterHandle SHADOW_VIEW_PROJECTION =
            SHADOW_UNIFORM_LAYOUT.requireHandle("viewProjection");
    private static final ShaderParameterHandle SHADOW_FADE_CAMERA_POSITION =
            SHADOW_UNIFORM_LAYOUT.requireHandle("fadeCameraPosition");
    private static final ShaderParameterHandle SHADOW_FADE_CAMERA_DIRECTION =
            SHADOW_UNIFORM_LAYOUT.requireHandle("fadeCameraDirection");
    private static final ShaderParameterHandle SHADOW_FADE_PARAMS =
            SHADOW_UNIFORM_LAYOUT.requireHandle("fadeParams");
    private static final String MAIN_SHADER = """
            struct Uniforms {
                viewProjection : mat4x4<f32>,
                lightViewProjection0 : mat4x4<f32>,
                lightViewProjection1 : mat4x4<f32>,
                lightViewProjection2 : mat4x4<f32>,
                lightDirection : vec4f,
                ambientColor : vec4f,
                lightColorIntensity : vec4f,
                shadowParams : vec4f,
                shadowCascadeSplits : vec4f,
                shadowBiases : vec4f,
                shadowCameraPosition : vec4f,
                shadowCameraDirection : vec4f,
                shadowFilterParams : vec4f,
                shadowFilterScales : vec4f,
                shadowFadeParams : vec4f,
            };
            @group(0) @binding(0) var shadowTexture0 : texture_2d<f32>;
            @group(0) @binding(1) var shadowSampler0 : sampler;
            @group(0) @binding(2) var shadowTexture1 : texture_2d<f32>;
            @group(0) @binding(3) var shadowSampler1 : sampler;
            @group(0) @binding(4) var shadowTexture2 : texture_2d<f32>;
            @group(0) @binding(5) var shadowSampler2 : sampler;
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
            fn shadowMatrix(cascadeIndex : i32) -> mat4x4<f32> {
                if (cascadeIndex == 1) {
                    return uniforms.lightViewProjection1;
                }
                if (cascadeIndex == 2) {
                    return uniforms.lightViewProjection2;
                }
                return uniforms.lightViewProjection0;
            }
            fn shadowSplit(cascadeIndex : i32) -> f32 {
                if (cascadeIndex == 1) {
                    return uniforms.shadowCascadeSplits.y;
                }
                if (cascadeIndex == 2) {
                    return uniforms.shadowCascadeSplits.z;
                }
                return uniforms.shadowCascadeSplits.x;
            }
            fn shadowBias(cascadeIndex : i32) -> f32 {
                if (cascadeIndex == 1) {
                    return uniforms.shadowBiases.y;
                }
                if (cascadeIndex == 2) {
                    return uniforms.shadowBiases.z;
                }
                return uniforms.shadowBiases.x;
            }
            fn shadowFilterScale(cascadeIndex : i32) -> f32 {
                if (cascadeIndex == 1) {
                    return uniforms.shadowFilterScales.y;
                }
                if (cascadeIndex == 2) {
                    return uniforms.shadowFilterScales.z;
                }
                return uniforms.shadowFilterScales.x;
            }
            fn shadowCascadeNear(cascadeIndex : i32) -> f32 {
                if (cascadeIndex == 1) {
                    return uniforms.shadowCascadeSplits.x;
                }
                if (cascadeIndex == 2) {
                    return uniforms.shadowCascadeSplits.y;
                }
                return 0.0;
            }
            fn sampleShadow(cascadeIndex : i32, uv : vec2f) -> vec4f {
                if (cascadeIndex == 1) {
                    return textureSampleLevel(shadowTexture1,
                            shadowSampler1, uv, 0.0);
                }
                if (cascadeIndex == 2) {
                    return textureSampleLevel(shadowTexture2,
                            shadowSampler2, uv, 0.0);
                }
                return textureSampleLevel(shadowTexture0,
                        shadowSampler0, uv, 0.0);
            }
            fn sampleVisibility(cascadeIndex : i32, uv : vec2f,
                    currentDepth : f32, receiverBias : f32, offset : vec2f) -> f32 {
                let sampleUv = uv + offset;
                if (sampleUv.x < 0.0 || sampleUv.x > 1.0
                        || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
                    return 1.0;
                }
                let casterSample = sampleShadow(cascadeIndex, sampleUv);
                let closest = unpackShadowDepth(casterSample);
                if (currentDepth - receiverBias > closest) {
                    return 1.0 - uniforms.shadowParams.y
                            * casterSample.a;
                }
                return 1.0;
            }
            fn cascadeVisibility(cascadeIndex : i32, worldPosition : vec3f,
                    normalDotLight : f32) -> f32 {
                let clip = shadowMatrix(cascadeIndex) * vec4f(worldPosition, 1.0);
                if (abs(clip.w) <= 0.000001) {
                    return 1.0;
                }
                let ndc = clip.xyz / clip.w;
                if (ndc.z < -1.0 || ndc.z > 1.0) {
                    return 1.0;
                }
                let uv = vec2f(ndc.x * 0.5 + 0.5,
                        0.5 + ndc.y * 0.5 * uniforms.shadowParams.z);
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    return 1.0;
                }
                let currentDepth = ndc.z * 0.5 + 0.5;
                // Match Box3D's receiver-side slope bias. The larger bias at
                // grazing angles prevents self-shadow bands without moving
                // head-on contact shadows away from their casters.
                let biasResolutionScale = max(
                        uniforms.shadowFilterParams.x * 2048.0, 1.0);
                let receiverBias = max(shadowBias(cascadeIndex),
                        mix(0.0040, 0.0008, normalDotLight) * biasResolutionScale);
                let filterOffset = uniforms.shadowFilterParams.xy
                        * uniforms.shadowFilterParams.z * shadowFilterScale(cascadeIndex);
                var visibility = 0.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(-filterOffset.x, -filterOffset.y)) * 25.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(0.0, -filterOffset.y)) * 30.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(filterOffset.x, -filterOffset.y)) * 25.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(-filterOffset.x, 0.0)) * 30.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(0.0)) * 36.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(filterOffset.x, 0.0)) * 30.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(-filterOffset.x, filterOffset.y)) * 25.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(0.0, filterOffset.y)) * 30.0;
                visibility += sampleVisibility(cascadeIndex, uv, currentDepth, receiverBias,
                        vec2f(filterOffset.x, filterOffset.y)) * 25.0;
                return visibility / 256.0;
            }
            fn shadowVisibility(worldPosition : vec3f, normal : vec3f) -> f32 {
                let cascadeCount = i32(uniforms.shadowParams.x);
                if (cascadeCount <= 0) {
                    return 1.0;
                }
                let cameraDirection = normalize(uniforms.shadowCameraDirection.xyz);
                let viewDistance = dot(worldPosition - uniforms.shadowCameraPosition.xyz,
                        cameraDirection);
                var cascadeIndex = 0;
                if (cascadeCount > 1 && viewDistance > uniforms.shadowCascadeSplits.x) {
                    cascadeIndex = 1;
                }
                if (cascadeCount > 2 && viewDistance > uniforms.shadowCascadeSplits.y) {
                    cascadeIndex = 2;
                }
                if (viewDistance > uniforms.shadowFadeParams.z) {
                    return 1.0;
                }
                let normalDotLight = max(dot(normalize(normal),
                        normalize(-uniforms.lightDirection.xyz)), 0.0);
                var visibility = cascadeVisibility(cascadeIndex, worldPosition, normalDotLight);
                if (cascadeIndex + 1 < cascadeCount) {
                    let splitNear = shadowCascadeNear(cascadeIndex);
                    let splitFar = shadowSplit(cascadeIndex);
                    let blendWidth = max((splitFar - splitNear)
                            * uniforms.shadowFilterParams.w, 0.0001);
                    let blendStart = splitFar - blendWidth;
                    if (viewDistance > blendStart) {
                        let blend = smoothstep(blendStart, splitFar,
                                viewDistance);
                        visibility = mix(visibility,
                                cascadeVisibility(cascadeIndex + 1, worldPosition,
                                        normalDotLight), blend);
                    }
                }
                return visibility;
            }
            fn linearToSrgb(value : vec3f) -> vec3f {
                return pow(max(value, vec3f(0.0)), vec3f(1.0 / 2.2));
            }
            @fragment fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let normal = normalize(input.normal);
                let lightDirection = normalize(-uniforms.lightDirection.xyz);
                let diffuse = max(dot(normal, lightDirection), 0.0);
                let shadow = shadowVisibility(input.worldPosition, normal);
                let direct = uniforms.lightColorIntensity.rgb
                        * uniforms.lightColorIntensity.a * diffuse * shadow;
                let linearColor = input.color.rgb * (uniforms.ambientColor.rgb + direct);
                return vec4f(linearToSrgb(min(linearColor, vec3f(1.0))), input.color.a);
            }
            """;

    private static final String SHADOW_SHADER = """
            struct Uniforms {
                viewProjection : mat4x4<f32>,
                fadeCameraPosition : vec4f,
                fadeCameraDirection : vec4f,
                fadeParams : vec4f,
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
                @location(1) @interpolate(flat) casterOpacity : f32,
            };
            @vertex fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                let model = mat4x4<f32>(input.model0, input.model1, input.model2, input.model3);
                let clip = uniforms.viewProjection * model * vec4f(input.position, 1.0);
                output.position = clip;
                output.position.z = clip.z * 0.5 + clip.w * 0.5;
                output.depth = (clip.z / clip.w) * 0.5 + 0.5;
                output.casterOpacity = 1.0;
                if (uniforms.fadeParams.z > 0.5) {
                    let cameraDirection = normalize(
                            uniforms.fadeCameraDirection.xyz);
                    let instanceCenter = input.model3.xyz;
                    let viewDistance = dot(instanceCenter
                                    - uniforms.fadeCameraPosition.xyz,
                            cameraDirection);
                    output.casterOpacity = 1.0 - smoothstep(
                            uniforms.fadeParams.x, uniforms.fadeParams.y,
                            viewDistance);
                }
                return output;
            }
            @fragment fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let depth = clamp(input.depth, 0.0, 0.999999);
                let raw = fract(depth * vec2f(1.0, 255.0));
                return vec4f(raw.x - raw.y / 255.0, raw.y, 0.0,
                        input.casterOpacity);
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
    private final float[][] lightMatrices = new float[MAX_SHADOW_CASCADES][Matrix4.VALUE_COUNT];
    private final float[] shadowPassMatrix = new float[Matrix4.VALUE_COUNT];
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
                .colorTargets(ColorTargetState.opaque(
                        TextureFormat.RGBA8_UNORM))
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

    void renderShadow(CascadedShadowMap3D shadowMap, DirectionalLight light) {
        ensureNotDisposed();
        if(!supported() || !hasInstances()) {
            return;
        }
        Vector3 fadeCameraPosition = shadowMap.viewCameraPosition();
        Vector3 fadeCameraDirection = shadowMap.viewCameraDirection();
        float fadeEnd = shadowMap.viewCameraFar();
        float fadeWidth = (fadeEnd - shadowMap.viewCameraNear())
                * shadowMap.shadowFadeFraction();
        for(int cascadeIndex = 0; cascadeIndex < shadowMap.cascadeCount(); cascadeIndex++) {
            boolean fadeCascade = cascadeIndex == shadowMap.cascadeCount() - 1
                    && fadeWidth > 0.0f;
            DirectionalShadowMap3D cascade = shadowMap.cascade(cascadeIndex);
            cascade.render(light, (pass, viewProjection) -> {
                viewProjection.copyValues(shadowPassMatrix, 0);
                shadowUniformBlock.setFloatMatrix(SHADOW_VIEW_PROJECTION, shadowPassMatrix, 0);
                shadowUniformBlock.setFloat4(SHADOW_FADE_CAMERA_POSITION,
                        fadeCameraPosition.x(), fadeCameraPosition.y(),
                        fadeCameraPosition.z(), 0.0f);
                shadowUniformBlock.setFloat4(SHADOW_FADE_CAMERA_DIRECTION,
                        fadeCameraDirection.x(), fadeCameraDirection.y(),
                        fadeCameraDirection.z(), 0.0f);
                shadowUniformBlock.setFloat4(SHADOW_FADE_PARAMS,
                        fadeEnd - fadeWidth, fadeEnd,
                        fadeCascade ? 1.0f : 0.0f, 0.0f);
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
    }

    void render(float[] viewProjection, DirectionalLight light, Color ambient,
            CascadedShadowMap3D shadowMap, boolean shadowsEnabled) {
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
        DirectionalShadowMap3D cascade0 = cascade(shadowMap, 0);
        DirectionalShadowMap3D cascade1 = cascade(shadowMap, 1);
        DirectionalShadowMap3D cascade2 = cascade(shadowMap, 2);
        cascade0.lightViewProjection().copyValues(lightMatrices[0], 0);
        cascade1.lightViewProjection().copyValues(lightMatrices[1], 0);
        cascade2.lightViewProjection().copyValues(lightMatrices[2], 0);
        mainUniformBlock.setFloatMatrix(MAIN_VIEW_PROJECTION, viewProjection, 0);
        mainUniformBlock.setFloatMatrix(LIGHT_VIEW_PROJECTION_0, lightMatrices[0], 0);
        mainUniformBlock.setFloatMatrix(LIGHT_VIEW_PROJECTION_1, lightMatrices[1], 0);
        mainUniformBlock.setFloatMatrix(LIGHT_VIEW_PROJECTION_2, lightMatrices[2], 0);
        mainUniformBlock.setFloat4(LIGHT_DIRECTION, lightDirection.x(), lightDirection.y(),
                lightDirection.z(), 0.0f);
        mainUniformBlock.setFloat4(AMBIENT_COLOR, ambientColor.red(), ambientColor.green(),
                ambientColor.blue(), ambientColor.alpha());
        mainUniformBlock.setFloat4(LIGHT_COLOR_INTENSITY, lightColor.red(), lightColor.green(),
                lightColor.blue(), light.intensity());
        int cascadeCount = Math.min(shadowMap.cascadeCount(), MAX_SHADOW_CASCADES);
        mainUniformBlock.setFloat4(SHADOW_PARAMS, shadowsEnabled ? cascadeCount : 0.0f,
                cascade0.strength(), shadowYSign(), 0.0f);
        mainUniformBlock.setFloat4(SHADOW_CASCADE_SPLITS,
                split(shadowMap, 0), split(shadowMap, 1), split(shadowMap, 2), 0.0f);
        mainUniformBlock.setFloat4(SHADOW_BIASES,
                bias(shadowMap, 0), bias(shadowMap, 1), bias(shadowMap, 2), 0.0f);
        Vector3 cameraPosition = shadowMap.viewCameraPosition();
        Vector3 cameraDirection = shadowMap.viewCameraDirection();
        mainUniformBlock.setFloat4(SHADOW_CAMERA_POSITION, cameraPosition.x(), cameraPosition.y(),
                cameraPosition.z(), 0.0f);
        mainUniformBlock.setFloat4(SHADOW_CAMERA_DIRECTION, cameraDirection.x(), cameraDirection.y(),
                cameraDirection.z(), 0.0f);
        mainUniformBlock.setFloat4(SHADOW_FILTER_PARAMS,
                1.0f / Math.max(1, cascade0.texture().width()),
                1.0f / Math.max(1, cascade0.texture().height()),
                1.2f, shadowMap.shadowFadeFraction());
        float firstHalfSize = shadowMap.cascadeHalfSize(0);
        mainUniformBlock.setFloat4(SHADOW_FILTER_SCALES,
                1.0f,
                filterScale(firstHalfSize, halfSize(shadowMap, 1)),
                filterScale(firstHalfSize, halfSize(shadowMap, 2)),
                0.0f);
        float fadeEnd = shadowMap.viewCameraFar();
        float fadeWidth = (fadeEnd - shadowMap.viewCameraNear())
                * shadowMap.shadowFadeFraction();
        mainUniformBlock.setFloat4(SHADOW_FADE_PARAMS_MAIN,
                fadeEnd - fadeWidth, fadeEnd,
                shadowMap.viewCameraCoverageFar(), 0.0f);

        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(mainPassDescriptor
                .colorAttachment(frame.colorAttachment()));
        try {
            pass.setPipeline(mainPipeline);
            pass.setParameterBlock(1, 0, mainUniformBlock);
            pass.setTextureBinding(0, 0, cascade0.texture());
            pass.setTextureSamplerBinding(0, 1, cascade0.texture());
            pass.setTextureBinding(0, 2, cascade1.texture());
            pass.setTextureSamplerBinding(0, 3, cascade1.texture());
            pass.setTextureBinding(0, 4, cascade2.texture());
            pass.setTextureSamplerBinding(0, 5, cascade2.texture());
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

    private static DirectionalShadowMap3D cascade(CascadedShadowMap3D shadowMap, int index) {
        return shadowMap.cascade(Math.min(index, shadowMap.cascadeCount() - 1));
    }

    private static float split(CascadedShadowMap3D shadowMap, int index) {
        return shadowMap.splitDistance(Math.min(index, shadowMap.cascadeCount() - 1));
    }

    private static float bias(CascadedShadowMap3D shadowMap, int index) {
        return shadowMap.cascadeBias(Math.min(index, shadowMap.cascadeCount() - 1));
    }

    private static float halfSize(CascadedShadowMap3D shadowMap, int index) {
        return shadowMap.cascadeHalfSize(Math.min(index, shadowMap.cascadeCount() - 1));
    }

    private static float filterScale(float firstHalfSize, float cascadeHalfSize) {
        if(cascadeHalfSize <= 0.0f) {
            return 1.0f;
        }
        return Math.max(0.25f, Math.min(1.0f, firstHalfSize / cascadeHalfSize));
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
