package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.KeyComparison;
import io.github.libfdx.collections.ObjectIterator;
import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceBinding;
import io.github.libfdx.graphics.camera.Camera;
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
import io.github.libfdx.graphics.RenderPassCompatibility;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.runtime.ResolvedShaderPass;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.shader.runtime.ShaderRequest;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Provides pbr shader services.
 *
 * @author xpenatan
 */
public final class PbrShaderProvider implements ShaderProvider3D, Disposable {
    private static final int PRIMITIVE_TOPOLOGY_COUNT = PrimitiveTopology.values().length;
    private static final float[] IDENTITY_MATRIX_VALUES = Matrix4.IDENTITY.values();
    private static final int MAX_POINT_LIGHTS = PbrShaderParameters.manifestMaxPointLights();
    private static final int MAX_SHADOW_CASCADES = PbrShaderParameters.manifestMaxShadowCascades();
    private static final int SHADOW_TEXTURE_SLOT_OFFSET = 5;
    private static final int MAX_SHADER_BONES = PbrShaderParameters.manifestMaxBones();
    private static final int MAX_SPOT_LIGHTS = PbrShaderParameters.manifestMaxSpotLights();
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
    static final String PBR_RENDERER_TEMPLATE = """
            struct VertexInput {
                @location(0) position : vec3f,
                @location(1) normal : vec3f,
                @location(2) uv : vec2f,
                @location(3) color : vec4f,
                @location(4) pbr : vec3f,
                @location(5) emissive : vec3f,
                //__PBR_SKINNED_INPUTS__
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
                cameraDirection : vec4f,
                ambientColor : vec4f,
                lightDirection : vec4f,
                lightColorIntensity : vec4f,
                fillLightDirection : vec4f,
                fillLightColorIntensity : vec4f,
                postProcessing : vec4f,
                textureFlags : vec4f,
                emissiveFlags : vec4f,
                fogColor : vec4f,
                fogParams : vec4f,
                skyZenithColor : vec4f,
                skyHorizonColor : vec4f,
                skyNadirColor : vec4f,
                skySunColor : vec4f,
                skySunDirection : vec4f,
                skyParams : vec4f,
                pointLightCount : vec4f,
                pointLightPositions : array<vec4f, 4>,
                pointLightColors : array<vec4f, 4>,
                spotLightCount : vec4f,
                spotLightPositions : array<vec4f, 4>,
                spotLightDirections : array<vec4f, 4>,
                spotLightColors : array<vec4f, 4>,
                spotLightCones : array<vec4f, 4>,
                shadowViewProjection0 : mat4x4<f32>,
                shadowViewProjection1 : mat4x4<f32>,
                shadowViewProjection2 : mat4x4<f32>,
                shadowViewProjection3 : mat4x4<f32>,
                shadowParams : vec4f,
                shadowCascadeSplits : vec4f,
                shadowBiases : vec4f,
                shadowCameraPosition : vec4f,
                shadowCameraDirection : vec4f,
                shadowCameraUp : vec4f,
                shadowCameraParams : vec4f,
                shadowFilterParams : vec4f,
                //__PBR_SKINNED_UNIFORMS__
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
            @group(0) @binding(10) var shadowTexture0 : texture_2d<f32>;
            @group(0) @binding(11) var shadowSampler0 : sampler;
            @group(0) @binding(12) var shadowTexture1 : texture_2d<f32>;
            @group(0) @binding(13) var shadowSampler1 : sampler;
            @group(0) @binding(14) var shadowTexture2 : texture_2d<f32>;
            @group(0) @binding(15) var shadowSampler2 : sampler;
            @group(0) @binding(16) var shadowTexture3 : texture_2d<f32>;
            @group(0) @binding(17) var shadowSampler3 : sampler;
            @group(1) @binding(0) var<uniform> uniforms : PbrUniforms;
            const PI : f32 = 3.14159265359;
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                var localPosition = vec4f(input.position, 1.0);
                var localNormal = vec4f(input.normal, 0.0);
                //__PBR_SKINNING_TRANSFORM__
                //__PBR_VERTEX_GRAPH_EVALUATION__
                let worldPosition = uniforms.model * localPosition;
                output.worldPosition = worldPosition.xyz;
                output.normal = (uniforms.model * localNormal).xyz;
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
            fn neutralToneMapping(colorIn : vec3f) -> vec3f {
                const startCompression = 0.76;
                const desaturation = 0.15;
                let darkest = min(colorIn.r, min(colorIn.g, colorIn.b));
                var offset = 0.04;
                if (darkest < 0.08) {
                    offset = darkest - 6.25 * darkest * darkest;
                }
                var color = max(colorIn - vec3f(offset), vec3f(0.0));
                let peak = max(color.r, max(color.g, color.b));
                if (peak < startCompression) {
                    return color;
                }
                let distanceToWhite = 1.0 - startCompression;
                let mappedPeak = 1.0 - distanceToWhite * distanceToWhite
                        / (peak + distanceToWhite - startCompression);
                color *= mappedPeak / max(peak, 0.000001);
                let desaturationAmount = 1.0
                        - 1.0 / (desaturation * (peak - mappedPeak) + 1.0);
                return mix(color, vec3f(mappedPeak), vec3f(desaturationAmount));
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
            fn pbrLightContribution(n : vec3f, v : vec3f, l : vec3f, albedo : vec3f,
                    metallic : f32, roughness : f32, radiance : vec3f) -> vec3f {
                let h = normalize(v + l);
                let f0 = mix(vec3f(0.04), albedo, vec3f(metallic));
                let ndl = max(dot(n, l), 0.0);
                let ndv = max(dot(n, v), 0.0);
                let f = fresnelSchlick(max(dot(h, v), 0.0), f0);
                let d = distributionGGX(n, h, roughness);
                let g = geometrySmith(n, v, l, roughness);
                let specular = (d * g * f) / max(4.0 * ndv * ndl, 0.000001);
                let kd = (vec3f(1.0) - f) * (1.0 - metallic);
                return (kd * albedo / PI + specular) * radiance * ndl;
            }
            fn skyEnvironmentColor(rayIn : vec3f) -> vec3f {
                let ray = normalize(rayIn);
                let below = smoothstep(-0.58, 0.02, ray.y);
                let above = smoothstep(0.02, 0.82, ray.y);
                let horizonHaze = 1.0 - smoothstep(0.0, 0.38, abs(ray.y));
                var color = mix(uniforms.skyNadirColor.rgb, uniforms.skyHorizonColor.rgb, below);
                color = mix(color, uniforms.skyZenithColor.rgb, above);
                color = mix(color, uniforms.skyHorizonColor.rgb,
                        horizonHaze * clamp(uniforms.skyParams.w, 0.0, 1.0));
                return color;
            }
            fn skyEnvironmentContribution(n : vec3f, v : vec3f, albedo : vec3f,
                    metallic : f32, roughness : f32, ao : f32) -> vec3f {
                if (uniforms.skyParams.x < 0.5) {
                    return vec3f(0.0);
                }
                let ndv = max(dot(n, v), 0.0);
                let f0 = mix(vec3f(0.04), albedo, vec3f(metallic));
                let f = fresnelSchlick(ndv, f0);
                let kd = (vec3f(1.0) - f) * (1.0 - metallic);
                let irradiance = skyEnvironmentColor(n) * uniforms.skyParams.y;
                let diffuse = irradiance * albedo / PI;
                let reflection = reflect(-v, n);
                let glossy = skyEnvironmentColor(reflection);
                let blurred = skyEnvironmentColor(mix(reflection, n, clamp(roughness * 0.72, 0.0, 1.0)));
                let prefiltered = mix(glossy, blurred, clamp(roughness, 0.0, 1.0));
                let envBrdf = f * (1.0 - roughness * 0.62) + vec3f(0.035) * (1.0 - metallic) * roughness;
                let sunDirection = normalize(uniforms.skySunDirection.xyz);
                let sunDot = dot(normalize(reflection), sunDirection);
                let sunWidth = mix(0.018, 0.18, roughness);
                let sunSpec = smoothstep(1.0 - sunWidth, 1.0, sunDot)
                        * uniforms.skySunColor.rgb * uniforms.skySunColor.a
                        * pow(1.0 - roughness, 2.0);
                let specular = (prefiltered * envBrdf + sunSpec) * uniforms.skyParams.z;
                return (kd * diffuse + specular) * ao;
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
            fn unpackShadowDepth(encodedDepth : vec4f) -> f32 {
                return encodedDepth.r + encodedDepth.g / 255.0;
            }
            fn shadowDepth(cascadeIndex : i32, uv : vec2f) -> f32 {
                if (cascadeIndex == 1) {
                    return unpackShadowDepth(
                            textureSampleLevel(shadowTexture1, shadowSampler1, uv, 0.0));
                }
                if (cascadeIndex == 2) {
                    return unpackShadowDepth(
                            textureSampleLevel(shadowTexture2, shadowSampler2, uv, 0.0));
                }
                if (cascadeIndex == 3) {
                    return unpackShadowDepth(
                            textureSampleLevel(shadowTexture3, shadowSampler3, uv, 0.0));
                }
                return unpackShadowDepth(
                        textureSampleLevel(shadowTexture0, shadowSampler0, uv, 0.0));
            }
            fn shadowViewDistance(worldPosition : vec3f) -> f32 {
                return dot(worldPosition - uniforms.shadowCameraPosition.xyz,
                        normalize(uniforms.shadowCameraDirection.xyz));
            }
            fn insideShadowCameraFrustum(worldPosition : vec3f, viewDistance : f32) -> bool {
                if (uniforms.shadowCameraParams.y <= uniforms.shadowCameraParams.x
                        || uniforms.shadowCameraParams.z <= 0.0) {
                    return true;
                }
                if (viewDistance < uniforms.shadowCameraParams.x || viewDistance > uniforms.shadowCameraParams.y) {
                    return false;
                }
                let forward = normalize(uniforms.shadowCameraDirection.xyz);
                let sourceUp = normalize(uniforms.shadowCameraUp.xyz);
                let right = normalize(cross(forward, sourceUp));
                let up = normalize(cross(right, forward));
                let relative = worldPosition - uniforms.shadowCameraPosition.xyz;
                let halfHeight = viewDistance * uniforms.shadowCameraParams.z;
                let halfWidth = halfHeight * uniforms.shadowCameraParams.w;
                return abs(dot(relative, right)) <= halfWidth && abs(dot(relative, up)) <= halfHeight;
            }
            fn shadowCascadeIndex(worldPosition : vec3f) -> i32 {
                let cascadeCount = i32(clamp(uniforms.shadowParams.x, 0.0, 4.0));
                if (cascadeCount <= 0) {
                    return -1;
                }
                if (cascadeCount == 1) {
                    return 0;
                }
                let viewDistance = shadowViewDistance(worldPosition);
                if (!insideShadowCameraFrustum(worldPosition, viewDistance)) {
                    return -1;
                }
                var cascadeIndex = 0;
                if (cascadeCount > 1 && viewDistance > uniforms.shadowCascadeSplits.x) {
                    cascadeIndex = 1;
                }
                if (cascadeCount > 2 && viewDistance > uniforms.shadowCascadeSplits.y) {
                    cascadeIndex = 2;
                }
                if (cascadeCount > 3 && viewDistance > uniforms.shadowCascadeSplits.z) {
                    cascadeIndex = 3;
                }
                return min(cascadeIndex, cascadeCount - 1);
            }
            fn shadowLightClip(cascadeIndex : i32, worldPosition : vec3f) -> vec4f {
                if (cascadeIndex == 1) {
                    return uniforms.shadowViewProjection1 * vec4f(worldPosition, 1.0);
                }
                if (cascadeIndex == 2) {
                    return uniforms.shadowViewProjection2 * vec4f(worldPosition, 1.0);
                }
                if (cascadeIndex == 3) {
                    return uniforms.shadowViewProjection3 * vec4f(worldPosition, 1.0);
                }
                return uniforms.shadowViewProjection0 * vec4f(worldPosition, 1.0);
            }
            fn shadowBias(cascadeIndex : i32) -> f32 {
                if (cascadeIndex == 1) {
                    return uniforms.shadowBiases.y;
                }
                if (cascadeIndex == 2) {
                    return uniforms.shadowBiases.z;
                }
                if (cascadeIndex == 3) {
                    return uniforms.shadowBiases.w;
                }
                return uniforms.shadowBiases.x;
            }
            fn shadowVisibility(cascadeIndex : i32, uv : vec2f, currentDepth : f32,
                    bias : f32, offset : vec2f) -> f32 {
                let closestDepth = shadowDepth(cascadeIndex, uv + offset);
                if (currentDepth - bias > closestDepth) {
                    return 1.0 - uniforms.shadowParams.z;
                }
                return 1.0;
            }
            fn directionalShadow(worldPosition : vec3f) -> f32 {
                let cascadeIndex = shadowCascadeIndex(worldPosition);
                if (cascadeIndex < 0) {
                    return 1.0;
                }
                let lightClip = shadowLightClip(cascadeIndex, worldPosition);
                if (abs(lightClip.w) <= 0.000001) {
                    return 1.0;
                }
                let ndc = lightClip.xyz / lightClip.w;
                let uv = vec2f(ndc.x * 0.5 + 0.5,
                        0.5 + ndc.y * 0.5 * uniforms.shadowParams.w);
                if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
                    return 1.0;
                }
                let currentDepth = clamp(ndc.z * 0.5 + 0.5, 0.0, 1.0);
                let bias = shadowBias(cascadeIndex);
                let radiusX = max(uniforms.shadowFilterParams.x, 0.000001)
                        * uniforms.shadowFilterParams.z;
                let radiusY = max(uniforms.shadowFilterParams.y, 0.000001)
                        * uniforms.shadowFilterParams.z;
                let x0 = -2.0 * radiusX;
                let x1 = -radiusX;
                let x2 = 0.0;
                let x3 = radiusX;
                let x4 = 2.0 * radiusX;
                let y0 = -2.0 * radiusY;
                let y1 = -radiusY;
                let y2 = 0.0;
                let y3 = radiusY;
                let y4 = 2.0 * radiusY;
                var visibility = shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x0, y0));
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x1, y0)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x2, y0)) * 6.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x3, y0)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x4, y0));
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x0, y1)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x1, y1)) * 16.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x2, y1)) * 24.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x3, y1)) * 16.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x4, y1)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x0, y2)) * 6.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x1, y2)) * 24.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x2, y2)) * 36.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x3, y2)) * 24.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x4, y2)) * 6.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x0, y3)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x1, y3)) * 16.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x2, y3)) * 24.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x3, y3)) * 16.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x4, y3)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x0, y4));
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x1, y4)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x2, y4)) * 6.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x3, y4)) * 4.0;
                visibility += shadowVisibility(cascadeIndex, uv, currentDepth, bias, vec2f(x4, y4));
                return visibility / 256.0;
            }
            //__PBR_SURFACE_GRAPH_DECLARATIONS__
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
                var n = mappedNormal(input.normal, input.worldPosition, uv);
                //__PBR_SURFACE_GRAPH_EVALUATION__
                let v = normalize(uniforms.cameraPosition.xyz - input.worldPosition);
                let l = normalize(-uniforms.lightDirection.xyz);
                let albedo = base.rgb;
                let radiance = uniforms.lightColorIntensity.rgb * uniforms.lightColorIntensity.a;
                let shadow = directionalShadow(input.worldPosition);
                var color = pbrLightContribution(n, v, l, albedo, metallic, roughness, radiance * shadow);
                let fillRadiance = uniforms.fillLightColorIntensity.rgb
                        * uniforms.fillLightColorIntensity.a;
                if (uniforms.fillLightColorIntensity.a > 0.0) {
                    color += pbrLightContribution(n, v,
                            normalize(-uniforms.fillLightDirection.xyz),
                            albedo, metallic, roughness, fillRadiance);
                }
                let pointLightCount = i32(uniforms.pointLightCount.x);
                for (var i : i32 = 0; i < 4; i = i + 1) {
                    if (i < pointLightCount) {
                        let positionRange = uniforms.pointLightPositions[i];
                        let toLight = positionRange.xyz - input.worldPosition;
                        let lightDistance = length(toLight);
                        if (lightDistance > 0.0001) {
                            let range = max(positionRange.w, 0.0001);
                            let attenuation = pow(clamp(1.0 - lightDistance / range, 0.0, 1.0), 2.0);
                            let pointColor = uniforms.pointLightColors[i];
                            let pointRadiance = pointColor.rgb * pointColor.a * attenuation;
                            color += pbrLightContribution(n, v, toLight / lightDistance, albedo, metallic,
                                    roughness, pointRadiance);
                        }
                    }
                }
                let spotLightCount = i32(uniforms.spotLightCount.x);
                for (var i : i32 = 0; i < 4; i = i + 1) {
                    if (i < spotLightCount) {
                        let positionRange = uniforms.spotLightPositions[i];
                        let toLight = positionRange.xyz - input.worldPosition;
                        let lightDistance = length(toLight);
                        if (lightDistance > 0.0001) {
                            let range = max(positionRange.w, 0.0001);
                            let rangeAttenuation = pow(clamp(1.0 - lightDistance / range, 0.0, 1.0), 2.0);
                            let directionInner = uniforms.spotLightDirections[i];
                            let lightDirection = toLight / lightDistance;
                            let cone = uniforms.spotLightCones[i];
                            let spotCos = dot(-lightDirection, normalize(directionInner.xyz));
                            let coneAttenuation = clamp((spotCos - cone.x)
                                    / max(directionInner.w - cone.x, 0.0001), 0.0, 1.0);
                            let attenuation = rangeAttenuation * coneAttenuation;
                            let spotColor = uniforms.spotLightColors[i];
                            let spotRadiance = spotColor.rgb * spotColor.a * attenuation;
                            color += pbrLightContribution(n, v, lightDirection, albedo, metallic,
                                    roughness, spotRadiance);
                        }
                    }
                }
                color += skyEnvironmentContribution(n, v, albedo, metallic, roughness, ao);
                color += uniforms.ambientColor.rgb * albedo * ao;
                color += emissive;
                //__PBR_LIGHTING_GRAPH_EVALUATION__
                let viewDistance = distance(uniforms.cameraPosition.xyz, input.worldPosition);
                let fogRange = max(uniforms.fogParams.y - uniforms.fogParams.x, 0.0001);
                let fogAmount = clamp((viewDistance - uniforms.fogParams.x) / fogRange,
                        0.0, uniforms.fogParams.z);
                color = mix(color, srgbToLinear(uniforms.fogColor.rgb), fogAmount);
                color *= max(uniforms.postProcessing.y, 0.0001);
                if (uniforms.postProcessing.x > 0.5) {
                    color = neutralToneMapping(color);
                }
                return vec4f(linearToSrgb(color), base.a);
            }
            """;
    static String skinnedPbrRendererTemplate() {
        return pbrRendererTemplate(true, "");
    }

    static String pbrRendererTemplate(boolean skinned,
            String graphMaterialFields) {
        String materialFields = graphMaterialFields != null
                ? graphMaterialFields : "";
        if (!skinned) {
            return PBR_RENDERER_TEMPLATE.replace(
                    "//__PBR_SKINNED_UNIFORMS__",
                    materialFields);
        }
        String source = PBR_RENDERER_TEMPLATE.replace(
                "//__PBR_SKINNED_INPUTS__",
                "@location(6) joints : vec4f,\n"
                        + "    @location(7) weights : vec4f,");
        source = source.replace(
                "//__PBR_SKINNED_UNIFORMS__",
                "skinningParams : vec4f,\n"
                        + "    boneMatrices : array<mat4x4<f32>, 64>,"
                        + (materialFields.isEmpty() ? ""
                                : "\n" + materialFields));
        return source.replace(
                "//__PBR_SKINNING_TRANSFORM__",
                "if (uniforms.skinningParams.x > 0.0) {\n"
                        + "        let joint0 = clamp(i32(input.joints.x), 0, 63);\n"
                        + "        let joint1 = clamp(i32(input.joints.y), 0, 63);\n"
                        + "        let joint2 = clamp(i32(input.joints.z), 0, 63);\n"
                        + "        let joint3 = clamp(i32(input.joints.w), 0, 63);\n"
                        + "        localPosition = (uniforms.boneMatrices[joint0] * vec4f(input.position, 1.0))\n"
                        + "                * input.weights.x\n"
                        + "                + (uniforms.boneMatrices[joint1] * vec4f(input.position, 1.0))\n"
                        + "                * input.weights.y\n"
                        + "                + (uniforms.boneMatrices[joint2] * vec4f(input.position, 1.0))\n"
                        + "                * input.weights.z\n"
                        + "                + (uniforms.boneMatrices[joint3] * vec4f(input.position, 1.0))\n"
                        + "                * input.weights.w;\n"
                        + "        localNormal = (uniforms.boneMatrices[joint0] * vec4f(input.normal, 0.0))\n"
                        + "                * input.weights.x\n"
                        + "                + (uniforms.boneMatrices[joint1] * vec4f(input.normal, 0.0))\n"
                        + "                * input.weights.y\n"
                        + "                + (uniforms.boneMatrices[joint2] * vec4f(input.normal, 0.0))\n"
                        + "                * input.weights.z\n"
                        + "                + (uniforms.boneMatrices[joint3] * vec4f(input.normal, 0.0))\n"
                        + "                * input.weights.w;\n"
                        + "    }");
    }

    private final PositionColorShader shader;
    private final GpuPbrShader gpuShader;
    private final ShaderGraphProvider ownedGraphProvider;
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
        this(graphics, config, standardGraphProvider(graphics));
    }

    static PbrShaderProvider common(GraphicsContext graphics,
            PbrShaderConfig config, ShaderProvider commonProvider) {
        if (commonProvider == null
                || !commonProvider.supportsPassResolution()) {
            throw new FdxException(
                    "ModelBatch common shader provider must support pass resolution");
        }
        return new PbrShaderProvider(graphics, config,
                commonProvider, null, false);
    }

    private PbrShaderProvider(GraphicsContext graphics,
            PbrShaderConfig config,
            ShaderGraphProvider graphProvider) {
        this(graphics, config, graphProvider, graphProvider,
                true);
    }

    private PbrShaderProvider(GraphicsContext graphics,
            PbrShaderConfig config,
            ShaderProvider commonProvider,
            ShaderGraphProvider graphProvider,
            boolean positionColorFallback) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        PbrShaderConfig safeConfig = config != null
                ? config : new PbrShaderConfig();
        String providerId = graphics.providerId().value();
        PositionColorShader createdShader = null;
        GpuPbrShader createdGpuShader = null;
        try {
            createdShader = positionColorFallback
                    ? new PositionColorShader(graphics) : null;
            if (usesGpuPbrShader(providerId)) {
                if (commonProvider == null) {
                    throw new FdxException(
                            "GPU PBR rendering requires the standard graph provider");
                }
                createdGpuShader = new GpuPbrShader(graphics,
                        providerId, safeConfig.maxBones(),
                        commonProvider);
            }
        } catch (RuntimeException failure) {
            if (createdGpuShader != null) {
                createdGpuShader.dispose();
            }
            if (createdShader != null) {
                createdShader.dispose();
            }
            if (graphProvider != null) {
                graphProvider.dispose();
            }
            throw failure;
        }
        shader = createdShader;
        gpuShader = createdGpuShader;
        ownedGraphProvider = graphProvider;
    }

    private static ShaderGraphProvider standardGraphProvider(
            GraphicsContext graphics) {
        if (graphics == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (!usesGpuPbrShader(graphics.providerId().value())) {
            return null;
        }
        return new ShaderGraphProvider(graphics,
                StandardPbrTechnique.create(graphics).technique());
    }

    static boolean usesGpuPbrShader(String providerId) {
        return "gl".equals(providerId) || "gles".equals(providerId) || "webgl".equals(providerId)
                || "wgpu".equals(providerId) || "vulkan".equals(providerId) || "metal".equals(providerId)
                || "d3d12".equals(providerId);
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
        if (shader == null) {
            throw new FdxException(
                    "Common ModelBatch shader provider does not support this mesh layout");
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
        if (shader != null) {
            shader.dispose();
        }
        if (ownedGraphProvider != null) {
            ownedGraphProvider.dispose();
        }
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
        private static final float MIN_VISIBLE_CLIP_W = 0.000001f;
        private final GraphicsContext graphics;
        private final ShaderModule shaderModule;
        private final ObjectMap<VertexLayout, RenderPipeline[]> pipelines =
                new ObjectMap<VertexLayout, RenderPipeline[]>(KeyComparison.IDENTITY);
        private ScratchBuffer[] scratchBuffers = new ScratchBuffer[4];
        private WorldVertex[] worldVertexPool = new WorldVertex[64];
        private ColorVertex[] colorVertexPool = new ColorVertex[64];
        private ProjectedVertex[] projectedVertexPool = new ProjectedVertex[64];
        private ProjectedTriangle[] projectedTriangles = new ProjectedTriangle[16];
        private ProjectedTriangle[] projectedTriangleSortScratch = new ProjectedTriangle[16];
        private float[] projectedVertexValues = new float[0];
        private final float[] worldMatrixValues = new float[Matrix4.VALUE_COUNT];
        private final float[] viewProjectionMatrixValues = new float[Matrix4.VALUE_COUNT];
        private final ProjectedMesh projectedMesh = new ProjectedMesh();
        private ByteBuffer projectedUpload;
        private int worldVertexCursor;
        private int colorVertexCursor;
        private int projectedVertexCursor;
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
                if (projectedMesh.vertexCount == 0) {
                    return;
                }
                int projectedByteCount = projectedMesh.floatCount * 4;
                vertexBuffer = scratchBuffer(projectedByteCount);
                graphics.device().writeBuffer(vertexBuffer,
                        projectedUpload(projectedMesh.vertices, projectedMesh.floatCount));
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
            PrimitiveTopology actualTopology = topology != null ? topology : PrimitiveTopology.TRIANGLE_LIST;
            RenderPipeline[] variants = pipelines.get(vertexLayout);
            if (variants == null) {
                variants = new RenderPipeline[PRIMITIVE_TOPOLOGY_COUNT];
                pipelines.put(vertexLayout, variants);
            }
            int slot = actualTopology.ordinal();
            RenderPipeline pipeline = variants[slot];
            if (pipeline == null) {
                pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                        .shader(shaderModule, graphics.surfaceFormat())
                        .label("model batch position color")
                        .primitiveTopology(actualTopology)
                        .depthTestEnabled(true)
                        .depthWriteEnabled(true)
                        .vertexLayout(vertexLayout));
                variants[slot] = pipeline;
            }
            return pipeline;
        }

        private boolean isPositionColorLayout(VertexLayout layout) {
            if (layout.attributeCount() < 2) {
                return false;
            }
            VertexAttribute position = layout.attribute(0);
            VertexAttribute color = layout.attribute(1);
            return position.location() == 0
                    && position.format() == VertexFormat.FLOAT32X3
                    && color.location() == 1
                    && color.format() == VertexFormat.FLOAT32X4;
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
            ObjectIterator<RenderPipeline[]> iterator = pipelines.values().iterator();
            while (iterator.hasNext()) {
                RenderPipeline[] variants = iterator.next();
                for (int i = 0; i < variants.length; i++) {
                    if (variants[i] != null) {
                        variants[i].dispose();
                    }
                }
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
            resetProjectionPools();
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
            int maximumTriangleCount = vertexCount / 3;
            ensureProjectedTriangleCapacity(maximumTriangleCount);
            worldTransform.copyValues(worldMatrixValues, 0);
            camera.combined().copyValues(viewProjectionMatrixValues, 0);
            float[] world = worldMatrixValues;
            float[] viewProjection = viewProjectionMatrixValues;
            float minClipW = Math.max(camera.near(), MIN_VISIBLE_CLIP_W);
            int triangleCount = 0;
            for (int i = 0; i < maximumTriangleCount; i++) {
                int vertex = firstVertex + i * 3;
                WorldVertex w0 = worldVertex(sourcePositions, vertex, world);
                WorldVertex w1 = worldVertex(sourcePositions, vertex + 1, world);
                WorldVertex w2 = worldVertex(sourcePositions, vertex + 2, world);
                if (material.doubleSided() || facesCamera(w0, w1, w2, camera.position())) {
                    WorldVertex faceNormal = faceNormal(w0, w1, w2);
                    ProjectedVertex p0 = projectVertex(w0, sourceColors, sourceNormals, sourcePbr, sourceEmissive,
                            vertex, faceNormal, world, viewProjection, context);
                    ProjectedVertex p1 = projectVertex(w1, sourceColors, sourceNormals, sourcePbr, sourceEmissive,
                            vertex + 1, faceNormal, world, viewProjection, context);
                    ProjectedVertex p2 = projectVertex(w2, sourceColors, sourceNormals, sourcePbr, sourceEmissive,
                            vertex + 2, faceNormal, world, viewProjection, context);
                    if (projectedTriangleVisible(p0, p1, p2, minClipW)) {
                        projectedTriangles[triangleCount] = projectedTriangle(triangleCount, p0, p1, p2);
                        triangleCount++;
                    }
                }
            }
            if (triangleCount == 0) {
                for (int i = 0; i < maximumTriangleCount; i++) {
                    int vertex = firstVertex + i * 3;
                    WorldVertex w0 = worldVertex(sourcePositions, vertex, world);
                    WorldVertex w1 = worldVertex(sourcePositions, vertex + 1, world);
                    WorldVertex w2 = worldVertex(sourcePositions, vertex + 2, world);
                    WorldVertex faceNormal = faceNormal(w0, w1, w2);
                    ProjectedVertex p0 = projectVertex(w0, sourceColors, sourceNormals, sourcePbr, sourceEmissive,
                            vertex, faceNormal, world, viewProjection, context);
                    ProjectedVertex p1 = projectVertex(w1, sourceColors, sourceNormals, sourcePbr, sourceEmissive,
                            vertex + 1, faceNormal, world, viewProjection, context);
                    ProjectedVertex p2 = projectVertex(w2, sourceColors, sourceNormals, sourcePbr, sourceEmissive,
                            vertex + 2, faceNormal, world, viewProjection, context);
                    if (projectedTriangleVisible(p0, p1, p2, minClipW)) {
                        projectedTriangles[triangleCount] = projectedTriangle(triangleCount, p0, p1, p2);
                        triangleCount++;
                    }
                }
            }
            sortProjectedTriangles(triangleCount);
            int floatCount = triangleCount * 3 * Mesh.POSITION_COLOR_FLOATS_PER_VERTEX;
            ensureProjectedVertexValueCapacity(floatCount);
            int out = 0;
            for (int i = 0; i < triangleCount; i++) {
                out = appendProjectedVertex(projectedVertexValues, out, projectedTriangles[i].v0);
                out = appendProjectedVertex(projectedVertexValues, out, projectedTriangles[i].v1);
                out = appendProjectedVertex(projectedVertexValues, out, projectedTriangles[i].v2);
            }
            return projectedMesh.set(projectedVertexValues, floatCount, triangleCount * 3);
        }

        private WorldVertex worldVertex(float[] positions, int vertex, float[] matrix) {
            int positionOffset = vertex * 3;
            float x = positions[positionOffset];
            float y = positions[positionOffset + 1];
            float z = positions[positionOffset + 2];
            return obtainWorldVertex(
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
            return obtainProjectedVertex(clipX * invW, clipY * invW, clipZ * invW,
                    clipW, shaded.red, shaded.green, shaded.blue, shaded.alpha);
        }

        private boolean projectedTriangleVisible(ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2,
                float minClipW) {
            return projectedVertexVisible(v0, minClipW)
                    && projectedVertexVisible(v1, minClipW)
                    && projectedVertexVisible(v2, minClipW);
        }

        private boolean projectedVertexVisible(ProjectedVertex vertex, float minClipW) {
            return vertex.clipW >= minClipW
                    && Float.isFinite(vertex.x)
                    && Float.isFinite(vertex.y)
                    && Float.isFinite(vertex.z);
        }

        private ColorVertex shade(WorldVertex worldVertex, float[] colors, float[] normals, float[] pbr,
                float[] emissive, int vertex, int colorOffset, WorldVertex faceNormal, float[] worldMatrix,
                RenderContext3D context) {
            float red = colors[colorOffset];
            float green = colors[colorOffset + 1];
            float blue = colors[colorOffset + 2];
            float alpha = colors[colorOffset + 3];
            if (normals == null) {
                return obtainColorVertex(red, green, blue, alpha);
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
            int pointLightCount = 0;
            int spotLightCount = 0;
            for (int i = 0; i < context.environment().lights().size(); i++) {
                Light light = context.environment().lights().get(i);
                if (light instanceof DirectionalLight) {
                    DirectionalLight directional = (DirectionalLight)light;
                    Vector3 direction = directional.direction();
                    WorldVertex lightDirection = normalize(-direction.x(), -direction.y(), -direction.z());
                    ColorVertex contribution = pbrLightContribution(normal, view, lightDirection, red, green, blue,
                            metallic, roughness, directional.color(), directional.intensity());
                    outRed += contribution.red;
                    outGreen += contribution.green;
                    outBlue += contribution.blue;
                }
                else if (light instanceof PointLight) {
                    if (pointLightCount >= MAX_POINT_LIGHTS) {
                        continue;
                    }
                    pointLightCount++;
                    PointLight point = (PointLight)light;
                    Vector3 position = point.position();
                    float toLightX = position.x() - worldVertex.x;
                    float toLightY = position.y() - worldVertex.y;
                    float toLightZ = position.z() - worldVertex.z;
                    float distance = (float)Math.sqrt(toLightX * toLightX + toLightY * toLightY
                            + toLightZ * toLightZ);
                    if (distance <= 0.0001f) {
                        continue;
                    }
                    float range = Math.max(point.range(), 0.0001f);
                    float attenuation = clamp(1.0f - distance / range, 0.0f, 1.0f);
                    attenuation *= attenuation;
                    if (attenuation <= 0.0f) {
                        continue;
                    }
                    WorldVertex lightDirection = normalize(toLightX, toLightY, toLightZ);
                    ColorVertex contribution = pbrLightContribution(normal, view, lightDirection, red, green, blue,
                            metallic, roughness, point.color(), point.intensity() * attenuation);
                    outRed += contribution.red;
                    outGreen += contribution.green;
                    outBlue += contribution.blue;
                }
                else if (light instanceof SpotLight) {
                    if (spotLightCount >= MAX_SPOT_LIGHTS) {
                        continue;
                    }
                    spotLightCount++;
                    SpotLight spot = (SpotLight)light;
                    Vector3 position = spot.position();
                    float toLightX = position.x() - worldVertex.x;
                    float toLightY = position.y() - worldVertex.y;
                    float toLightZ = position.z() - worldVertex.z;
                    float distance = (float)Math.sqrt(toLightX * toLightX + toLightY * toLightY
                            + toLightZ * toLightZ);
                    if (distance <= 0.0001f) {
                        continue;
                    }
                    float range = Math.max(spot.range(), 0.0001f);
                    float rangeAttenuation = clamp(1.0f - distance / range, 0.0f, 1.0f);
                    rangeAttenuation *= rangeAttenuation;
                    if (rangeAttenuation <= 0.0f) {
                        continue;
                    }
                    WorldVertex lightDirection = normalize(toLightX, toLightY, toLightZ);
                    Vector3 direction = spot.direction();
                    float directionX = direction.x();
                    float directionY = direction.y();
                    float directionZ = direction.z();
                    if (directionX * directionX + directionY * directionY + directionZ * directionZ
                            <= 0.000001f) {
                        directionX = 0.0f;
                        directionY = -1.0f;
                        directionZ = 0.0f;
                    }
                    WorldVertex spotDirection = normalize(directionX, directionY, directionZ);
                    float spotCos = -(lightDirection.x * spotDirection.x
                            + lightDirection.y * spotDirection.y
                            + lightDirection.z * spotDirection.z);
                    float innerCos = coneCos(spot.innerConeDegrees());
                    float outerCos = coneCos(Math.max(spot.innerConeDegrees(), spot.outerConeDegrees()));
                    float coneAttenuation = clamp((spotCos - outerCos)
                            / Math.max(innerCos - outerCos, 0.0001f), 0.0f, 1.0f);
                    float attenuation = rangeAttenuation * coneAttenuation;
                    if (attenuation <= 0.0f) {
                        continue;
                    }
                    ColorVertex contribution = pbrLightContribution(normal, view, lightDirection, red, green, blue,
                            metallic, roughness, spot.color(), spot.intensity() * attenuation);
                    outRed += contribution.red;
                    outGreen += contribution.green;
                    outBlue += contribution.blue;
                }
            }

            ColorVertex skyContribution = skyEnvironmentContribution(context.environment(), normal, view,
                    red, green, blue, metallic, roughness, ao);
            outRed += skyContribution.red;
            outGreen += skyContribution.green;
            outBlue += skyContribution.blue;

            float emissiveRed = 0.0f;
            float emissiveGreen = 0.0f;
            float emissiveBlue = 0.0f;
            if (emissive != null) {
                int emissiveOffset = vertex * 3;
                emissiveRed = emissive[emissiveOffset];
                emissiveGreen = emissive[emissiveOffset + 1];
                emissiveBlue = emissive[emissiveOffset + 2];
            }

            return applyFog(obtainColorVertex(
                    linearToSrgb(outRed + emissiveRed),
                    linearToSrgb(outGreen + emissiveGreen),
                    linearToSrgb(outBlue + emissiveBlue),
                    alpha), worldVertex, context);
        }

        private ColorVertex applyFog(ColorVertex color, WorldVertex worldVertex, RenderContext3D context) {
            Environment3D environment = context.environment();
            if (!environment.fogEnabled()) {
                return color;
            }
            float factor = fogFactor(environment, worldVertex, context.camera().position());
            if (factor <= 0.0f) {
                return color;
            }
            Color fog = environment.fogColor();
            return obtainColorVertex(
                    color.red + (fog.red() - color.red) * factor,
                    color.green + (fog.green() - color.green) * factor,
                    color.blue + (fog.blue() - color.blue) * factor,
                    color.alpha);
        }

        private ColorVertex pbrLightContribution(WorldVertex normal, WorldVertex view, WorldVertex lightDirection,
                float red, float green, float blue, float metallic, float roughness, Color lightColor,
                float intensity) {
            float ndl = Math.max(0.0f, dot(normal, lightDirection));
            if (ndl <= 0.0f || intensity <= 0.0f) {
                return obtainColorVertex(0.0f, 0.0f, 0.0f, 0.0f);
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
            float radianceRed = lightColor.red() * intensity;
            float radianceGreen = lightColor.green() * intensity;
            float radianceBlue = lightColor.blue() * intensity;
            return obtainColorVertex(
                    (kdRed * red / PI + baseSpecular * fRed) * radianceRed * ndl,
                    (kdGreen * green / PI + baseSpecular * fGreen) * radianceGreen * ndl,
                    (kdBlue * blue / PI + baseSpecular * fBlue) * radianceBlue * ndl,
                    0.0f);
        }

        private ColorVertex skyEnvironmentContribution(Environment3D environment, WorldVertex normal,
                WorldVertex view, float red, float green, float blue, float metallic, float roughness, float ao) {
            SkyEnvironment3D sky = environment.skyEnvironment();
            if (sky == null) {
                return obtainColorVertex(0.0f, 0.0f, 0.0f, 0.0f);
            }
            float ndv = Math.max(dot(normal, view), 0.0f);
            float fRed = fresnelSchlick(ndv, 0.04f + (red - 0.04f) * metallic);
            float fGreen = fresnelSchlick(ndv, 0.04f + (green - 0.04f) * metallic);
            float fBlue = fresnelSchlick(ndv, 0.04f + (blue - 0.04f) * metallic);
            float kdRed = (1.0f - fRed) * (1.0f - metallic);
            float kdGreen = (1.0f - fGreen) * (1.0f - metallic);
            float kdBlue = (1.0f - fBlue) * (1.0f - metallic);
            ColorVertex irradiance = skyEnvironmentColor(sky, normal);
            float reflectionDot = dot(view, normal);
            WorldVertex reflection = normalize(
                    2.0f * reflectionDot * normal.x - view.x,
                    2.0f * reflectionDot * normal.y - view.y,
                    2.0f * reflectionDot * normal.z - view.z);
            WorldVertex blurredDirection = normalize(
                    reflection.x + (normal.x - reflection.x) * clamp(roughness * 0.72f, 0.0f, 1.0f),
                    reflection.y + (normal.y - reflection.y) * clamp(roughness * 0.72f, 0.0f, 1.0f),
                    reflection.z + (normal.z - reflection.z) * clamp(roughness * 0.72f, 0.0f, 1.0f));
            ColorVertex glossy = skyEnvironmentColor(sky, reflection);
            ColorVertex blurred = skyEnvironmentColor(sky, blurredDirection);
            float rough = clamp(roughness, 0.0f, 1.0f);
            float prefilteredRed = glossy.red + (blurred.red - glossy.red) * rough;
            float prefilteredGreen = glossy.green + (blurred.green - glossy.green) * rough;
            float prefilteredBlue = glossy.blue + (blurred.blue - glossy.blue) * rough;
            float envBrdfRed = fRed * (1.0f - roughness * 0.62f) + 0.035f * (1.0f - metallic) * roughness;
            float envBrdfGreen = fGreen * (1.0f - roughness * 0.62f) + 0.035f * (1.0f - metallic) * roughness;
            float envBrdfBlue = fBlue * (1.0f - roughness * 0.62f) + 0.035f * (1.0f - metallic) * roughness;
            WorldVertex sunDirection = normalize(sky.sunDirectionX(), sky.sunDirectionY(), sky.sunDirectionZ());
            float sunDot = dot(reflection, sunDirection);
            float sunWidth = 0.018f + (0.18f - 0.018f) * roughness;
            float sunSpec = smoothstep(1.0f - sunWidth, 1.0f, sunDot)
                    * sky.sunIntensity() * (float)Math.pow(1.0f - roughness, 2.0f);
            Color sun = sky.sunColor();
            float diffuseIntensity = sky.diffuseIntensity();
            float specularIntensity = sky.specularIntensity();
            return obtainColorVertex(
                    (kdRed * irradiance.red * diffuseIntensity * red / PI
                            + (prefilteredRed * envBrdfRed + sun.red() * sunSpec) * specularIntensity) * ao,
                    (kdGreen * irradiance.green * diffuseIntensity * green / PI
                            + (prefilteredGreen * envBrdfGreen + sun.green() * sunSpec) * specularIntensity) * ao,
                    (kdBlue * irradiance.blue * diffuseIntensity * blue / PI
                            + (prefilteredBlue * envBrdfBlue + sun.blue() * sunSpec) * specularIntensity) * ao,
                    0.0f);
        }

        private ColorVertex skyEnvironmentColor(SkyEnvironment3D sky, WorldVertex rayIn) {
            WorldVertex ray = normalize(rayIn.x, rayIn.y, rayIn.z);
            float below = smoothstep(-0.58f, 0.02f, ray.y);
            float above = smoothstep(0.02f, 0.82f, ray.y);
            float horizonHaze = 1.0f - smoothstep(0.0f, 0.38f, Math.abs(ray.y));
            Color nadir = sky.nadirColor();
            Color horizon = sky.horizonColor();
            Color zenith = sky.zenithColor();
            float red = nadir.red() + (horizon.red() - nadir.red()) * below;
            float green = nadir.green() + (horizon.green() - nadir.green()) * below;
            float blue = nadir.blue() + (horizon.blue() - nadir.blue()) * below;
            red += (zenith.red() - red) * above;
            green += (zenith.green() - green) * above;
            blue += (zenith.blue() - blue) * above;
            float haze = horizonHaze * sky.horizonBlend();
            red += (horizon.red() - red) * haze;
            green += (horizon.green() - green) * haze;
            blue += (horizon.blue() - blue) * haze;
            return obtainColorVertex(red, green, blue, 0.0f);
        }

        private float fogFactor(Environment3D environment, WorldVertex worldVertex, Vector3 cameraPosition) {
            float dx = cameraPosition.x() - worldVertex.x;
            float dy = cameraPosition.y() - worldVertex.y;
            float dz = cameraPosition.z() - worldVertex.z;
            float distance = (float)Math.sqrt(dx * dx + dy * dy + dz * dz);
            float start = environment.fogStartDistance();
            float range = Math.max(environment.fogEndDistance() - start, 0.0001f);
            return clamp((distance - start) / range, 0.0f,
                    clamp(environment.fogColor().alpha(), 0.0f, 1.0f));
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

        private float smoothstep(float edge0, float edge1, float x) {
            float t = clamp((x - edge0) / Math.max(edge1 - edge0, 0.000001f), 0.0f, 1.0f);
            return t * t * (3.0f - 2.0f * t);
        }

        private float coneCos(float degrees) {
            return (float)Math.cos(Math.toRadians(clamp(degrees, 0.0f, 180.0f)));
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
                return obtainWorldVertex(0.0f, 0.0f, 0.0f);
            }
            float invLen = 1.0f / len;
            return obtainWorldVertex(x * invLen, y * invLen, z * invLen);
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

        private void resetProjectionPools() {
            worldVertexCursor = 0;
            colorVertexCursor = 0;
            projectedVertexCursor = 0;
        }

        private WorldVertex obtainWorldVertex(float x, float y, float z) {
            if (worldVertexCursor >= worldVertexPool.length) {
                worldVertexPool = Arrays.copyOf(worldVertexPool, worldVertexPool.length * 2);
            }
            WorldVertex vertex = worldVertexPool[worldVertexCursor];
            if (vertex == null) {
                vertex = new WorldVertex();
                worldVertexPool[worldVertexCursor] = vertex;
            }
            worldVertexCursor++;
            return vertex.set(x, y, z);
        }

        private ColorVertex obtainColorVertex(float red, float green, float blue, float alpha) {
            if (colorVertexCursor >= colorVertexPool.length) {
                colorVertexPool = Arrays.copyOf(colorVertexPool, colorVertexPool.length * 2);
            }
            ColorVertex vertex = colorVertexPool[colorVertexCursor];
            if (vertex == null) {
                vertex = new ColorVertex();
                colorVertexPool[colorVertexCursor] = vertex;
            }
            colorVertexCursor++;
            return vertex.set(red, green, blue, alpha);
        }

        private ProjectedVertex obtainProjectedVertex(float x, float y, float z, float clipW,
                float red, float green, float blue, float alpha) {
            if (projectedVertexCursor >= projectedVertexPool.length) {
                projectedVertexPool = Arrays.copyOf(projectedVertexPool, projectedVertexPool.length * 2);
            }
            ProjectedVertex vertex = projectedVertexPool[projectedVertexCursor];
            if (vertex == null) {
                vertex = new ProjectedVertex();
                projectedVertexPool[projectedVertexCursor] = vertex;
            }
            projectedVertexCursor++;
            return vertex.set(x, y, z, clipW, red, green, blue, alpha);
        }

        private ProjectedTriangle projectedTriangle(int index, ProjectedVertex v0, ProjectedVertex v1,
                ProjectedVertex v2) {
            ProjectedTriangle triangle = projectedTriangles[index];
            if (triangle == null) {
                triangle = new ProjectedTriangle();
            }
            return triangle.set(v0, v1, v2);
        }

        private void ensureProjectedTriangleCapacity(int count) {
            if (projectedTriangles.length >= count) {
                return;
            }
            int capacity = projectedTriangles.length;
            while (capacity < count) {
                capacity *= 2;
            }
            projectedTriangles = Arrays.copyOf(projectedTriangles, capacity);
            projectedTriangleSortScratch = Arrays.copyOf(projectedTriangleSortScratch, capacity);
        }

        private void sortProjectedTriangles(int count) {
            int width = 1;
            while (width < count) {
                for (int left = 0; left < count; left += width * 2) {
                    int middle = Math.min(left + width, count);
                    int right = Math.min(left + width * 2, count);
                    mergeProjectedTriangles(left, middle, right);
                }
                for (int i = 0; i < count; i++) {
                    projectedTriangles[i] = projectedTriangleSortScratch[i];
                }
                if (width > count / 2) {
                    break;
                }
                width *= 2;
            }
            for (int i = 0; i < count; i++) {
                projectedTriangleSortScratch[i] = null;
            }
        }

        private void mergeProjectedTriangles(int left, int middle, int right) {
            int leftIndex = left;
            int rightIndex = middle;
            int output = left;
            while (leftIndex < middle && rightIndex < right) {
                ProjectedTriangle leftValue = projectedTriangles[leftIndex];
                ProjectedTriangle rightValue = projectedTriangles[rightIndex];
                if (Float.compare(rightValue.depth, leftValue.depth) <= 0) {
                    projectedTriangleSortScratch[output++] = leftValue;
                    leftIndex++;
                }
                else {
                    projectedTriangleSortScratch[output++] = rightValue;
                    rightIndex++;
                }
            }
            while (leftIndex < middle) {
                projectedTriangleSortScratch[output++] = projectedTriangles[leftIndex++];
            }
            while (rightIndex < right) {
                projectedTriangleSortScratch[output++] = projectedTriangles[rightIndex++];
            }
        }

        private void ensureProjectedVertexValueCapacity(int count) {
            if (projectedVertexValues.length >= count) {
                return;
            }
            int capacity = Math.max(256, projectedVertexValues.length);
            while (capacity < count) {
                capacity *= 2;
            }
            projectedVertexValues = Arrays.copyOf(projectedVertexValues, capacity);
        }

        private ByteBuffer projectedUpload(float[] values, int floatCount) {
            int byteCount = floatCount * 4;
            if (projectedUpload == null || projectedUpload.capacity() < byteCount) {
                int capacity = projectedUpload != null ? projectedUpload.capacity() : 1024;
                while (capacity < byteCount) {
                    capacity *= 2;
                }
                projectedUpload = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder());
            }
            projectedUpload.clear();
            for (int i = 0; i < floatCount; i++) {
                projectedUpload.putFloat(values[i]);
            }
            projectedUpload.flip();
            return projectedUpload;
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
            private float x;
            private float y;
            private float z;

            WorldVertex set(float x, float y, float z) {
                this.x = x;
                this.y = y;
                this.z = z;
                return this;
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
            private float red;
            private float green;
            private float blue;
            private float alpha;

            ColorVertex set(float red, float green, float blue, float alpha) {
                this.red = red;
                this.green = green;
                this.blue = blue;
                this.alpha = alpha;
                return this;
            }
        }

        /**
         * Represents a projected mesh.
         *
         * @author xpenatan
         */
        private static final class ProjectedMesh {
            private float[] vertices;
            private int floatCount;
            private int vertexCount;

            ProjectedMesh set(float[] vertices, int floatCount, int vertexCount) {
                this.vertices = vertices;
                this.floatCount = floatCount;
                this.vertexCount = vertexCount;
                return this;
            }
        }

        /**
         * Represents a projected triangle.
         *
         * @author xpenatan
         */
        private static final class ProjectedTriangle {
            private ProjectedVertex v0;
            private ProjectedVertex v1;
            private ProjectedVertex v2;
            private float depth;

            ProjectedTriangle set(ProjectedVertex v0, ProjectedVertex v1, ProjectedVertex v2) {
                this.v0 = v0;
                this.v1 = v1;
                this.v2 = v2;
                depth = (v0.z + v1.z + v2.z) / 3.0f;
                return this;
            }
        }

        /**
         * Represents a projected vertex.
         *
         * @author xpenatan
         */
        private static final class ProjectedVertex {
            private float x;
            private float y;
            private float z;
            private float clipW;
            private float red;
            private float green;
            private float blue;
            private float alpha;

            ProjectedVertex set(float x, float y, float z, float clipW, float red, float green, float blue,
                    float alpha) {
                this.x = x;
                this.y = y;
                this.z = z;
                this.clipW = clipW;
                this.red = red;
                this.green = green;
                this.blue = blue;
                this.alpha = alpha;
                return this;
            }
        }
    }

    /**
     * Represents a gpu pbr shader.
     *
     * @author xpenatan
     */
    private static final class GpuPbrShader implements Shader3D {
        private static final int RESOLVED_PASS_CACHE_CAPACITY = 16;

        private final GraphicsContext graphics;
        private final Texture whiteTexture;
        private final Texture blackTexture;
        private final Texture normalTexture;
        private final String providerId;
        private final int maxBones;
        private final ShaderProvider commonProvider;
        private PbrShaderParameters staticParameters;
        private PbrShaderParameters skinnedParameters;
        private long staticLayoutIdentity = -1;
        private long skinnedLayoutIdentity = -1;
        private long commonRevision = -1;
        private final ResolvedPassEntry[] resolvedPassCache =
                new ResolvedPassEntry[RESOLVED_PASS_CACHE_CAPACITY];
        private long resolvedPassClock;
        private final float[] boneValues = new float[MAX_SHADER_BONES * Matrix4.VALUE_COUNT];
        private final float[] boneMatrix = new float[Matrix4.VALUE_COUNT];
        private final float[] modelMatrix = new float[Matrix4.VALUE_COUNT];
        private final float[] viewProjectionMatrix = new float[Matrix4.VALUE_COUNT];
        private final float[] shadowMatrix = new float[Matrix4.VALUE_COUNT];
        private PbrShaderParameters uniforms;
        private RenderContext3D context;
        private boolean disposed;

        GpuPbrShader(GraphicsContext graphics, String providerId,
                int maxBones,
                ShaderProvider commonProvider) {
            this.graphics = graphics;
            this.providerId = providerId != null ? providerId : "";
            if (commonProvider == null
                    || !commonProvider.supportsPassResolution()) {
                throw new FdxException(
                        "GPU PBR shader requires a graph-compatible ShaderProvider");
            }
            this.commonProvider = commonProvider;
            if (maxBones <= 0 || maxBones > MAX_SHADER_BONES) {
                throw new FdxException("PBR GPU skinning supports 1.." + MAX_SHADER_BONES + " bones");
            }
            this.maxBones = maxBones;
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
                    && (renderable.meshPart().mesh().vertexLayout() == Mesh.PBR_LAYOUT
                    || renderable.meshPart().mesh().vertexLayout() == Mesh.PBR_SKINNED_LAYOUT);
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
            long revision = commonProvider.revision();
            if (revision != commonRevision) {
                Arrays.fill(resolvedPassCache, null);
                staticParameters = null;
                skinnedParameters = null;
                staticLayoutIdentity = -1;
                skinnedLayoutIdentity = -1;
            }
            commonRevision = revision;
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
            boolean skinned =
                    mesh.vertexLayout() == Mesh.PBR_SKINNED_LAYOUT;
            RenderPass pass = context.pass();
            ResolvedShaderPass resolved = resolveCommon(
                    mesh.vertexLayout(),
                    meshPart.primitiveTopology(), skinned);
            uniforms = commonParameters(resolved, skinned);
            pass.setPipeline(resolved.pipeline());
            pass.setVertexBuffer(mesh.vertexBuffer());
            renderable.worldTransform().copyValues(modelMatrix, 0);
            context.camera().combined().copyValues(viewProjectionMatrix, 0);
            uniforms.setUniformMatrix4(uniforms.MODEL, modelMatrix);
            uniforms.setUniformMatrix4(uniforms.VIEW_PROJECTION, viewProjectionMatrix);
            Vector3 cameraPosition = context.camera().position();
            uniforms.setUniform3f(uniforms.CAMERA_POSITION,
                    cameraPosition.x(), cameraPosition.y(), cameraPosition.z());
            Vector3 cameraDirection = context.camera().direction();
            uniforms.setUniform3f(uniforms.CAMERA_DIRECTION,
                    cameraDirection.x(), cameraDirection.y(), cameraDirection.z());
            applyEnvironment(pass);
            applySkinning(pass, renderable);
            applyMaterial(pass, renderable.material());
            ShaderMaterialBinding materialBinding =
                    renderable.material().shaderBinding();
            io.github.libfdx.graphics.shader.runtime.ShaderResourceBinding
                    selectedBinding = materialBinding != null
                            ? materialBinding
                            : resolved.defaultResources();
            if (selectedBinding instanceof GraphPbrMaterial graphMaterial) {
                graphMaterial.writeParameters(
                        resolved.resourceLayout(),
                        uniforms.block());
            }
            uniforms.bind(pass);
            if (selectedBinding != null) {
                selectedBinding.bind(pass,
                        resolved.resourceLayout());
            }
            int indexCount = meshPart.indexCount() > 0 ? meshPart.indexCount() : mesh.indexCount();
            if (indexCount > 0) {
                pass.setIndexBuffer(mesh.indexBuffer());
                pass.drawIndexed(indexCount, 1, meshPart.firstIndex(), 0, 0);
                return;
            }
            int vertexCount = meshPart.vertexCount() > 0 ? meshPart.vertexCount() : mesh.vertexCount();
            pass.draw(vertexCount, 1, meshPart.firstVertex(), 0);
        }

        private ResolvedShaderPass resolveCommon(
                VertexLayout vertexLayout,
                PrimitiveTopology topology, boolean skinned) {
            if (commonProvider.revision() != commonRevision) {
                throw new FdxException(
                        "ModelBatch shader provider changed during an active batch");
            }
            ShaderProfile profile = graphics.device().capabilities()
                    .supports(ShaderProfile.PORTABLE_WEBGPU)
                            ? ShaderProfile.PORTABLE_WEBGPU
                            : graphics.device().capabilities().supports(
                                    ShaderProfile.PORTABLE_WEBGL2)
                                             ? ShaderProfile.PORTABLE_WEBGL2
                                             : ShaderProfile.NATIVE;
            PrimitiveTopology actualTopology = topology != null
                    ? topology : PrimitiveTopology.TRIANGLE_LIST;
            RenderPassCompatibility compatibility =
                    context.renderPassCompatibility();
            for (ResolvedPassEntry entry : resolvedPassCache) {
                if (entry != null && entry.matches(
                        context.shaderPassId(), profile,
                        compatibility, actualTopology, vertexLayout,
                        skinned, commonRevision)) {
                    entry.lastUse = ++resolvedPassClock;
                    return entry.resolved;
                }
            }
            ShaderRequest request = ShaderRequest.builder(
                             context.shaderPassId())
                    .profile(profile)
                    .renderPass(compatibility)
                    .topology(actualTopology)
                    .vertexLayouts(vertexLayout)
                    .variantKey(skinned ? "skinned" : "")
                    .build();
            if (!commonProvider.supports(request)) {
                throw new FdxException("ModelBatch shader provider does not "
                        + "support pass " + context.shaderPassId()
                        + ", variant " + (skinned ? "skinned" : "static")
                        + ", and the renderable vertex layout");
            }
            ResolvedShaderPass resolved =
                    commonProvider.resolve(request);
            if (!context.shaderPassId().equals(resolved.passId())
                    || resolved.providerRevision()
                            != commonRevision) {
                throw new FdxException(
                        "ModelBatch shader provider changed or returned the wrong pass");
            }
            int slot = emptyOrOldestResolvedPass();
            resolvedPassCache[slot] = new ResolvedPassEntry(
                    context.shaderPassId(), profile, compatibility,
                    actualTopology, vertexLayout, skinned,
                    commonRevision, resolved, ++resolvedPassClock);
            return resolved;
        }

        private int emptyOrOldestResolvedPass() {
            int oldest = 0;
            long oldestUse = Long.MAX_VALUE;
            for (int i = 0; i < resolvedPassCache.length; i++) {
                ResolvedPassEntry entry = resolvedPassCache[i];
                if (entry == null) {
                    return i;
                }
                if (entry.lastUse < oldestUse) {
                    oldest = i;
                    oldestUse = entry.lastUse;
                }
            }
            return oldest;
        }

        private PbrShaderParameters commonParameters(
                ResolvedShaderPass resolved, boolean skinned) {
            long identity = resolved.resourceLayout().identity();
            if (skinned) {
                if (skinnedParameters == null
                        || skinnedLayoutIdentity != identity) {
                    skinnedParameters = new PbrShaderParameters(
                            resolved.resourceLayout().reflection());
                    skinnedLayoutIdentity = identity;
                }
                return skinnedParameters;
            }
            if (staticParameters == null
                    || staticLayoutIdentity != identity) {
                staticParameters = new PbrShaderParameters(
                        resolved.resourceLayout().reflection());
                staticLayoutIdentity = identity;
            }
            return staticParameters;
        }

        /**
         * Ends the operation.
         */
        @Override
        public void end() {
            context = null;
        }

        private void applyEnvironment(RenderPass pass) {
            Environment3D environment = context.environment();
            Color ambient = environment.ambientColor();
            uniforms.setUniform3f(uniforms.AMBIENT_COLOR,
                    ambient.red(), ambient.green(), ambient.blue());
            DirectionalLight directional = null;
            DirectionalLight fillDirectional = null;
            for (int i = 0; i < environment.lights().size(); i++) {
                Light light = environment.lights().get(i);
                if (light instanceof DirectionalLight) {
                    if (directional == null) {
                        directional = (DirectionalLight)light;
                    }
                    else {
                        fillDirectional = (DirectionalLight)light;
                        break;
                    }
                }
            }
            uniforms.setUniform4f(uniforms.POST_PROCESSING,
                    environment.neutralToneMappingEnabled() ? 1.0f : 0.0f,
                    environment.exposure(), 0.0f, 0.0f);
            if (fillDirectional == null) {
                uniforms.setUniform3f(uniforms.FILL_LIGHT_DIRECTION, 0.4f, -0.5f, 0.7f);
                uniforms.setUniform3f(uniforms.FILL_LIGHT_COLOR_INTENSITY,
                        1.0f, 1.0f, 1.0f);
                uniforms.setUniform1f(uniforms.FILL_LIGHT_INTENSITY, 0.0f);
            }
            else {
                Vector3 fillDirection = fillDirectional.direction();
                Color fillColor = fillDirectional.color();
                uniforms.setUniform3f(uniforms.FILL_LIGHT_DIRECTION,
                        fillDirection.x(), fillDirection.y(), fillDirection.z());
                uniforms.setUniform3f(uniforms.FILL_LIGHT_COLOR_INTENSITY,
                        fillColor.red(), fillColor.green(), fillColor.blue());
                uniforms.setUniform1f(uniforms.FILL_LIGHT_INTENSITY,
                        fillDirectional.intensity());
            }
            applyFog(pass, environment);
            applySkyEnvironment(pass, environment);
            applyPointLights(pass, environment);
            applySpotLights(pass, environment);
            applyDirectionalShadow(pass, environment);
            if (directional == null) {
                uniforms.setUniform3f(uniforms.LIGHT_DIRECTION, -0.4f, -0.8f, -0.3f);
                uniforms.setUniform3f(uniforms.LIGHT_COLOR_INTENSITY,
                        1.0f, 1.0f, 1.0f);
                uniforms.setUniform1f(uniforms.LIGHT_INTENSITY, 0.0f);
                return;
            }
            Vector3 direction = directional.direction();
            Color color = directional.color();
            uniforms.setUniform3f(uniforms.LIGHT_DIRECTION,
                    direction.x(), direction.y(), direction.z());
            uniforms.setUniform3f(uniforms.LIGHT_COLOR_INTENSITY,
                    color.red(), color.green(), color.blue());
            uniforms.setUniform1f(uniforms.LIGHT_INTENSITY, directional.intensity());
        }

        private void applySkinning(RenderPass pass, Renderable3D renderable) {
            if (renderable.meshPart().mesh().vertexLayout() != Mesh.PBR_SKINNED_LAYOUT) {
                return;
            }
            SkinningPalette palette = renderable.skinningPalette();
            if (palette == null || palette.size() == 0) {
                uniforms.setUniform4f(uniforms.SKINNING_PARAMS,
                        0.0f, 0.0f, 0.0f, 0.0f);
                return;
            }
            if (palette.size() > maxBones) {
                throw new FdxException("Renderable skin uses " + palette.size()
                        + " bones, but this PBR shader is configured for " + maxBones);
            }
            uniforms.setUniform4f(uniforms.SKINNING_PARAMS,
                    palette.size(), 0.0f, 0.0f, 0.0f);
            palette.copyValues(boneValues);
            for (int i = 0; i < palette.size(); i++) {
                int offset = i * Matrix4.VALUE_COUNT;
                System.arraycopy(boneValues, offset, boneMatrix, 0, Matrix4.VALUE_COUNT);
                uniforms.setUniformMatrix4(uniforms.boneMatrix(i), boneMatrix);
            }
        }

        private void applyFog(RenderPass pass, Environment3D environment) {
            if (!environment.fogEnabled()) {
                uniforms.setUniform4f(uniforms.FOG_COLOR,
                        0.0f, 0.0f, 0.0f, 0.0f);
                uniforms.setUniform4f(uniforms.FOG_PARAMS,
                        0.0f, 1.0f, 0.0f, 0.0f);
                return;
            }
            Color fog = environment.fogColor();
            uniforms.setUniform4f(uniforms.FOG_COLOR,
                    fog.red(), fog.green(), fog.blue(), fog.alpha());
            uniforms.setUniform4f(uniforms.FOG_PARAMS,
                    environment.fogStartDistance(), environment.fogEndDistance(),
                    Math.max(0.0f, Math.min(1.0f, fog.alpha())), 0.0f);
        }

        private void applySkyEnvironment(RenderPass pass, Environment3D environment) {
            SkyEnvironment3D sky = environment.skyEnvironment();
            if (sky == null) {
                uniforms.setUniform4f(uniforms.SKY_ZENITH_COLOR,
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.SKY_HORIZON_COLOR,
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.SKY_NADIR_COLOR,
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.SKY_SUN_COLOR,
                        1.0f, 1.0f, 1.0f, 0.0f);
                uniforms.setUniform4f(uniforms.SKY_SUN_DIRECTION,
                        0.0f, 1.0f, 0.0f, 0.0f);
                uniforms.setUniform4f(uniforms.SKY_PARAMS,
                        0.0f, 0.0f, 0.0f, 0.0f);
                return;
            }
            Color zenith = sky.zenithColor();
            Color horizon = sky.horizonColor();
            Color nadir = sky.nadirColor();
            Color sun = sky.sunColor();
            uniforms.setUniform4f(uniforms.SKY_ZENITH_COLOR,
                    zenith.red(), zenith.green(), zenith.blue(), zenith.alpha());
            uniforms.setUniform4f(uniforms.SKY_HORIZON_COLOR,
                    horizon.red(), horizon.green(), horizon.blue(), horizon.alpha());
            uniforms.setUniform4f(uniforms.SKY_NADIR_COLOR,
                    nadir.red(), nadir.green(), nadir.blue(), nadir.alpha());
            uniforms.setUniform4f(uniforms.SKY_SUN_COLOR,
                    sun.red(), sun.green(), sun.blue(), sky.sunIntensity());
            uniforms.setUniform4f(uniforms.SKY_SUN_DIRECTION,
                    sky.sunDirectionX(), sky.sunDirectionY(), sky.sunDirectionZ(), 0.0f);
            uniforms.setUniform4f(uniforms.SKY_PARAMS,
                    1.0f, sky.diffuseIntensity(), sky.specularIntensity(),
                    sky.horizonBlend());
        }

        private void applyPointLights(RenderPass pass, Environment3D environment) {
            int count = 0;
            for (int i = 0; i < environment.lights().size() && count < MAX_POINT_LIGHTS; i++) {
                Light light = environment.lights().get(i);
                if (!(light instanceof PointLight)) {
                    continue;
                }
                PointLight point = (PointLight)light;
                Vector3 position = point.position();
                Color color = point.color();
                uniforms.setUniform4f(uniforms.pointLightPosition(count),
                        position.x(), position.y(), position.z(), Math.max(point.range(), 0.0001f));
                uniforms.setUniform4f(uniforms.pointLightColor(count),
                        color.red(), color.green(), color.blue(), Math.max(point.intensity(), 0.0f));
                count++;
            }
            uniforms.setUniform1f(uniforms.POINT_LIGHT_COUNT_VALUE, count);
            for (int i = count; i < MAX_POINT_LIGHTS; i++) {
                uniforms.setUniform4f(uniforms.pointLightPosition(i),
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.pointLightColor(i),
                        0.0f, 0.0f, 0.0f, 0.0f);
            }
        }

        private void applySpotLights(RenderPass pass, Environment3D environment) {
            int count = 0;
            for (int i = 0; i < environment.lights().size() && count < MAX_SPOT_LIGHTS; i++) {
                Light light = environment.lights().get(i);
                if (!(light instanceof SpotLight)) {
                    continue;
                }
                SpotLight spot = (SpotLight)light;
                Vector3 position = spot.position();
                Vector3 direction = spot.direction();
                Color color = spot.color();
                float directionX = direction.x();
                float directionY = direction.y();
                float directionZ = direction.z();
                float directionLen2 = directionX * directionX + directionY * directionY + directionZ * directionZ;
                if (directionLen2 <= 0.000001f) {
                    directionX = 0.0f;
                    directionY = -1.0f;
                    directionZ = 0.0f;
                }
                float innerCone = Math.max(0.0f, Math.min(180.0f, spot.innerConeDegrees()));
                float outerCone = Math.max(innerCone, Math.min(180.0f, spot.outerConeDegrees()));
                float innerCos = (float)Math.cos(Math.toRadians(innerCone));
                float outerCos = (float)Math.cos(Math.toRadians(outerCone));
                uniforms.setUniform4f(uniforms.spotLightPosition(count),
                        position.x(), position.y(), position.z(), Math.max(spot.range(), 0.0001f));
                uniforms.setUniform4f(uniforms.spotLightDirection(count),
                        directionX, directionY, directionZ, innerCos);
                uniforms.setUniform4f(uniforms.spotLightColor(count),
                        color.red(), color.green(), color.blue(), Math.max(spot.intensity(), 0.0f));
                uniforms.setUniform4f(uniforms.spotLightCone(count),
                        outerCos, 0.0f, 0.0f, 0.0f);
                count++;
            }
            uniforms.setUniform1f(uniforms.SPOT_LIGHT_COUNT_VALUE, count);
            for (int i = count; i < MAX_SPOT_LIGHTS; i++) {
                uniforms.setUniform4f(uniforms.spotLightPosition(i),
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.spotLightDirection(i),
                        0.0f, -1.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.spotLightColor(i),
                        0.0f, 0.0f, 0.0f, 0.0f);
                uniforms.setUniform4f(uniforms.spotLightCone(i),
                        -1.0f, 0.0f, 0.0f, 0.0f);
            }
        }

        private void applyDirectionalShadow(RenderPass pass, Environment3D environment) {
            CascadedShadowMap3D cascaded = activeCascadedShadowMap(environment);
            if (cascaded != null) {
                applyCascadedDirectionalShadow(pass, cascaded);
                return;
            }
            DirectionalShadowMap3D shadowMap = activeDirectionalShadowMap(environment);
            if (shadowMap == null) {
                for (int i = 0; i < MAX_SHADOW_CASCADES; i++) {
                    uniforms.setUniformMatrix4(uniforms.shadowViewProjection(i),
                            IDENTITY_MATRIX_VALUES);
                }
                uniforms.setUniform4f(uniforms.SHADOW_PARAMS,
                        0.0f, 0.0f, 0.0f, shadowYSign());
                uniforms.setUniform4f(uniforms.SHADOW_CASCADE_SPLITS,
                        0.0f, 0.0f, 0.0f, 0.0f);
                uniforms.setUniform4f(uniforms.SHADOW_BIASES,
                        0.0f, 0.0f, 0.0f, 0.0f);
                uniforms.setUniform4f(uniforms.SHADOW_CAMERA_POSITION,
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.SHADOW_CAMERA_DIRECTION,
                        0.0f, 0.0f, -1.0f, 0.0f);
                uniforms.setUniform4f(uniforms.SHADOW_CAMERA_UP,
                        0.0f, 1.0f, 0.0f, 0.0f);
                uniforms.setUniform4f(uniforms.SHADOW_CAMERA_PARAMS,
                        0.0f, 0.0f, 0.0f, 1.0f);
                uniforms.setUniform4f(uniforms.SHADOW_FILTER_PARAMS,
                        0.0f, 0.0f, 0.0f, 0.0f);
                return;
            }
            shadowMap.lightViewProjection().copyValues(shadowMatrix, 0);
            uniforms.setUniformMatrix4(uniforms.shadowViewProjection(0), shadowMatrix);
            for (int i = 1; i < MAX_SHADOW_CASCADES; i++) {
                uniforms.setUniformMatrix4(uniforms.shadowViewProjection(i),
                        IDENTITY_MATRIX_VALUES);
            }
            uniforms.setUniform4f(uniforms.SHADOW_PARAMS,
                    1.0f, shadowMap.bias(), shadowMap.strength(), shadowYSign());
            uniforms.setUniform4f(uniforms.SHADOW_CASCADE_SPLITS,
                    0.0f, 0.0f, 0.0f, 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_BIASES,
                    shadowMap.bias(), 0.0f, 0.0f, 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_POSITION,
                    0.0f, 0.0f, 0.0f, 1.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_DIRECTION,
                    0.0f, 0.0f, -1.0f, 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_UP,
                    0.0f, 1.0f, 0.0f, 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_PARAMS,
                    0.0f, 0.0f, 0.0f, 1.0f);
            uniforms.setUniform4f(uniforms.SHADOW_FILTER_PARAMS,
                    1.0f / Math.max(1, shadowMap.texture().width()),
                    1.0f / Math.max(1, shadowMap.texture().height()),
                    1.35f, 0.0f);
        }

        private void applyCascadedDirectionalShadow(RenderPass pass, CascadedShadowMap3D cascaded) {
            int cascadeCount = Math.min(cascaded.cascadeCount(), MAX_SHADOW_CASCADES);
            float strength = 0.0f;
            float bias0 = 0.0f;
            float bias1 = 0.0f;
            float bias2 = 0.0f;
            float bias3 = 0.0f;
            for (int i = 0; i < MAX_SHADOW_CASCADES; i++) {
                if (i < cascadeCount) {
                    DirectionalShadowMap3D cascade = cascaded.cascade(i);
                    cascade.lightViewProjection().copyValues(shadowMatrix, 0);
                    uniforms.setUniformMatrix4(uniforms.shadowViewProjection(i), shadowMatrix);
                    float cascadeBias = cascaded.cascadeBias(i);
                    if (i == 0) {
                        bias0 = cascadeBias;
                    }
                    else if (i == 1) {
                        bias1 = cascadeBias;
                    }
                    else if (i == 2) {
                        bias2 = cascadeBias;
                    }
                    else {
                        bias3 = cascadeBias;
                    }
                    if (i == 0) {
                        strength = cascade.strength();
                    }
                }
                else {
                    uniforms.setUniformMatrix4(uniforms.shadowViewProjection(i),
                            IDENTITY_MATRIX_VALUES);
                }
            }
            uniforms.setUniform4f(uniforms.SHADOW_PARAMS,
                    cascadeCount, bias0, strength, shadowYSign());
            uniforms.setUniform4f(uniforms.SHADOW_CASCADE_SPLITS,
                    cascadeCount > 0 ? cascaded.splitDistance(0) : 0.0f,
                    cascadeCount > 1 ? cascaded.splitDistance(1) : 0.0f,
                    cascadeCount > 2 ? cascaded.splitDistance(2) : 0.0f,
                    cascadeCount > 3 ? cascaded.splitDistance(3) : 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_BIASES, bias0, bias1, bias2, bias3);
            Vector3 shadowCameraPosition = cascaded.viewCameraPosition();
            Vector3 shadowCameraDirection = cascaded.viewCameraDirection();
            Vector3 shadowCameraUp = cascaded.viewCameraUp();
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_POSITION,
                    shadowCameraPosition.x(), shadowCameraPosition.y(), shadowCameraPosition.z(), 1.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_DIRECTION,
                    shadowCameraDirection.x(), shadowCameraDirection.y(), shadowCameraDirection.z(), 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_UP,
                    shadowCameraUp.x(), shadowCameraUp.y(), shadowCameraUp.z(), 0.0f);
            uniforms.setUniform4f(uniforms.SHADOW_CAMERA_PARAMS,
                    cascaded.viewCameraNear(), cascaded.viewCameraFar(),
                    cascaded.viewCameraTanHalfFov(), cascaded.viewCameraAspect());
            DirectionalShadowMap3D firstCascade = cascadeCount > 0 ? cascaded.cascade(0) : null;
            uniforms.setUniform4f(uniforms.SHADOW_FILTER_PARAMS,
                    firstCascade != null ? 1.0f / Math.max(1, firstCascade.texture().width()) : 0.0f,
                    firstCascade != null ? 1.0f / Math.max(1, firstCascade.texture().height()) : 0.0f,
                    1.35f, 0.0f);
        }

        private float shadowYSign() {
            return "gl".equals(providerId) || "opengl".equals(providerId)
                    || "webgl".equals(providerId) || "gles".equals(providerId) ? 1.0f : -1.0f;
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
            uniforms.bindTexture(pass, 0, baseColor);
            uniforms.bindTexture(pass, 1, metallicRoughness);
            uniforms.bindTexture(pass, 2, normal);
            uniforms.bindTexture(pass, 3, occlusion);
            uniforms.bindTexture(pass, 4, emissive);
            applyShadowTextures(pass, context.environment());
            uniforms.setUniform1i(uniforms.HAS_BASE_COLOR_TEXTURE,
                    pbr != null && pbr.baseColorTexture() != null ? 1 : 0);
            uniforms.setUniform1i(uniforms.HAS_METALLIC_ROUGHNESS_TEXTURE,
                    pbr != null && pbr.metallicRoughnessTexture() != null ? 1 : 0);
            uniforms.setUniform1i(uniforms.HAS_NORMAL_TEXTURE,
                    pbr != null && pbr.normalTexture() != null ? 1 : 0);
            uniforms.setUniform1i(uniforms.HAS_OCCLUSION_TEXTURE,
                    pbr != null && pbr.occlusionTexture() != null ? 1 : 0);
            uniforms.setUniform1i(uniforms.HAS_EMISSIVE_TEXTURE,
                    pbr != null && pbr.emissiveTexture() != null ? 1 : 0);
        }

        private void applyShadowTextures(RenderPass pass, Environment3D environment) {
            CascadedShadowMap3D cascaded = activeCascadedShadowMap(environment);
            DirectionalShadowMap3D singleShadowMap = cascaded == null ? activeDirectionalShadowMap(environment) : null;
            int cascadeCount = cascaded != null ? Math.min(cascaded.cascadeCount(), MAX_SHADOW_CASCADES) : 0;
            for (int i = 0; i < MAX_SHADOW_CASCADES; i++) {
                Texture shadow = whiteTexture;
                if (cascaded != null && i < cascadeCount) {
                    shadow = cascaded.cascade(i).texture();
                }
                else if (singleShadowMap != null && i == 0) {
                    shadow = singleShadowMap.texture();
                }
                int slot = SHADOW_TEXTURE_SLOT_OFFSET + i;
                uniforms.bindTexture(pass, slot, shadow);
            }
        }

        private CascadedShadowMap3D activeCascadedShadowMap(Environment3D environment) {
            CascadedShadowMap3D cascaded = environment.cascadedShadowMap();
            if (cascaded != null && !cascaded.isDisposed()) {
                return cascaded;
            }
            return null;
        }

        private DirectionalShadowMap3D activeDirectionalShadowMap(Environment3D environment) {
            DirectionalShadowMap3D shadowMap = environment.directionalShadowMap();
            return shadowMap != null && !shadowMap.isDisposed() ? shadowMap : null;
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
            whiteTexture.dispose();
            blackTexture.dispose();
            normalTexture.dispose();
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

        private static final class ResolvedPassEntry {
            final io.github.libfdx.graphics.shader.runtime.ShaderPassId passId;
            final ShaderProfile profile;
            final RenderPassCompatibility compatibility;
            final PrimitiveTopology topology;
            final VertexLayout vertexLayout;
            final boolean skinned;
            final long providerRevision;
            final ResolvedShaderPass resolved;
            long lastUse;

            ResolvedPassEntry(
                    io.github.libfdx.graphics.shader.runtime.ShaderPassId passId,
                    ShaderProfile profile,
                    RenderPassCompatibility compatibility,
                    PrimitiveTopology topology,
                    VertexLayout vertexLayout, boolean skinned,
                    long providerRevision,
                    ResolvedShaderPass resolved, long lastUse) {
                this.passId = passId;
                this.profile = profile;
                this.compatibility = compatibility;
                this.topology = topology;
                this.vertexLayout = vertexLayout;
                this.skinned = skinned;
                this.providerRevision = providerRevision;
                this.resolved = resolved;
                this.lastUse = lastUse;
            }

            boolean matches(
                    io.github.libfdx.graphics.shader.runtime.ShaderPassId requestedPass,
                    ShaderProfile requestedProfile,
                    RenderPassCompatibility requestedCompatibility,
                    PrimitiveTopology requestedTopology,
                    VertexLayout requestedLayout,
                    boolean requestedSkinned, long revision) {
                return passId.equals(requestedPass)
                        && profile == requestedProfile
                        && compatibility.equals(
                                requestedCompatibility)
                        && topology == requestedTopology
                        && vertexLayout.equals(requestedLayout)
                        && skinned == requestedSkinned
                        && providerRevision == revision;
            }
        }

    }

}
