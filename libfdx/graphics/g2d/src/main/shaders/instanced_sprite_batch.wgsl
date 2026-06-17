struct VertexInput {
    @location(0) baseAndEdgeX : vec4f,
    @location(1) edgeYAndUvBase : vec4f,
    @location(2) uvSizeAndColorRG : vec4f,
    @location(3) colorBA : vec2f,
};
struct VertexOutput {
    @builtin(position) position : vec4f,
    @location(0) texCoord : vec2f,
    @location(1) color : vec4f,
};
@group(0) @binding(0) var u_texture : texture_2d<f32>;
@group(0) @binding(1) var u_sampler : sampler;
@vertex
fn vertexMain(@builtin(vertex_index) vertexIndex : u32, input : VertexInput) -> VertexOutput {
    var output : VertexOutput;
    let cornerIndex = vertexIndex;
    let corner = vec2f(
        select(0.0, 1.0, cornerIndex == 2u || cornerIndex == 4u || cornerIndex == 5u),
        select(0.0, 1.0, cornerIndex == 1u || cornerIndex == 2u || cornerIndex == 4u));
    let basePosition = vec2f(input.baseAndEdgeX.x, input.baseAndEdgeX.y);
    let edgeX = vec2f(input.baseAndEdgeX.z, input.baseAndEdgeX.w);
    let edgeY = vec2f(input.edgeYAndUvBase.x, input.edgeYAndUvBase.y);
    let uvBase = vec2f(input.edgeYAndUvBase.z, input.edgeYAndUvBase.w);
    let uvSize = vec2f(input.uvSizeAndColorRG.x, input.uvSizeAndColorRG.y);
    let color = vec4f(input.uvSizeAndColorRG.z, input.uvSizeAndColorRG.w,
        input.colorBA.x, input.colorBA.y);
    let position = basePosition + edgeX * corner.x + edgeY * corner.y;
    output.position = vec4f(position, 0.0, 1.0);
    output.texCoord = uvBase + uvSize * corner;
    output.color = color;
    return output;
}
@fragment
fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
    return textureSample(u_texture, u_sampler, input.texCoord) * input.color;
}
