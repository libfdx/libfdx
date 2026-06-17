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
