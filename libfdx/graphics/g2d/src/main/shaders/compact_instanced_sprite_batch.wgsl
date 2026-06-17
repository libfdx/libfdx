struct VertexInput {
    @location(0) localPosition : vec2f,
    @location(1) texCoord : vec2f,
    @location(2) color : vec4f,
    @location(3) center : vec2f,
};
struct VertexOutput {
    @builtin(position) position : vec4f,
    @location(0) texCoord : vec2f,
    @location(1) color : vec4f,
};
@group(0) @binding(0) var u_texture : texture_2d<f32>;
@group(0) @binding(1) var u_sampler : sampler;
@vertex
fn vertexMain(input : VertexInput) -> VertexOutput {
    var output : VertexOutput;
    output.position = vec4f(input.center + input.localPosition, 0.0, 1.0);
    output.texCoord = input.texCoord;
    output.color = input.color;
    return output;
}
@fragment
fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
    return textureSample(u_texture, u_sampler, input.texCoord) * input.color;
}
