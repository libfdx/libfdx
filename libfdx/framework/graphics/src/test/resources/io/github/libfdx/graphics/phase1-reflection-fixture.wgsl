struct Uniforms {
    transform: mat4x4<f32>,
    tint: vec3<f32>,
    weights: array<vec4<f32>, 2>,
    nested: array<array<mat2x3<f32>, 3>, 2>,
}

struct Storage {
    values: array<f32>,
}

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) uv: vec2<f32>,
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var<storage, read_write> storage_data: Storage;
@group(1) @binding(0) var color_texture: texture_2d<f32>;
@group(1) @binding(1) var color_sampler: sampler;

@vertex
fn vs_main(@location(0) position: vec3<f32>, @builtin(instance_index) instance: u32) -> VertexOutput {
    var output: VertexOutput;
    output.position = uniforms.transform * vec4<f32>(position, 1.0);
    output.uv = vec2<f32>(f32(instance), uniforms.tint.x);
    return output;
}

@fragment
fn fs_main(input: VertexOutput) -> @location(0) vec4<f32> {
    return textureSample(color_texture, color_sampler, input.uv);
}

@compute @workgroup_size(8, 4, 2)
fn cs_main(@builtin(global_invocation_id) id: vec3<u32>) {
    storage_data.values[id.x] = f32(id.y);
}
