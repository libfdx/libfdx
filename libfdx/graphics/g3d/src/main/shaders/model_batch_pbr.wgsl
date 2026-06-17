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
