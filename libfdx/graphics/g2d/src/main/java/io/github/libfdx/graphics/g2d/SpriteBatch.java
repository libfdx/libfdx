package io.github.libfdx.graphics.g2d;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.VertexStepMode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

public final class SpriteBatch implements Batch2D {
    private static final int FLOATS_PER_VERTEX = 8;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTICES_PER_SPRITE = 6;
    private static final int INDEXED_VERTICES_PER_SPRITE = 4;
    private static final int INDICES_PER_SPRITE = 6;
    private static final int BYTES_PER_INDEX = 2;
    private static final int MAX_INDEXED_SPRITES = 65535 / INDEXED_VERTICES_PER_SPRITE;
    private static final int DEFAULT_MAX_SPRITES = 1024;
    private static final VertexLayout SPRITE_VERTEX_LAYOUT = VertexLayout.of(
            BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    private static final int WHITE_FLOATS_PER_VERTEX = 4;
    private static final int WHITE_BYTES_PER_VERTEX = WHITE_FLOATS_PER_VERTEX * 4;
    private static final VertexLayout WHITE_SPRITE_VERTEX_LAYOUT = VertexLayout.of(
            WHITE_BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final int INSTANCED_INDEXED_QUAD_VERTICES = 4;
    private static final int INSTANCED_NON_INDEXED_QUAD_VERTICES = 6;
    private static final int INSTANCED_INDICES = 6;
    private static final int INSTANCED_QUAD_FLOATS_PER_VERTEX = 8;
    private static final int INSTANCED_QUAD_BYTES_PER_VERTEX = INSTANCED_QUAD_FLOATS_PER_VERTEX * 4;
    private static final int INSTANCED_CENTER_FLOATS_PER_SPRITE = 2;
    private static final int INSTANCED_CENTER_BYTES_PER_SPRITE = INSTANCED_CENTER_FLOATS_PER_SPRITE * 4;
    private static final int INSTANCE_FLOATS_PER_SPRITE = 14;
    private static final int INSTANCE_BYTES_PER_SPRITE = INSTANCE_FLOATS_PER_SPRITE * 4;
    private static final VertexLayout INSTANCED_QUAD_VERTEX_LAYOUT = VertexLayout.of(
            INSTANCED_QUAD_BYTES_PER_VERTEX,
            VertexStepMode.VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 16));
    private static final VertexLayout INSTANCED_CENTER_VERTEX_LAYOUT = VertexLayout.instance(
            INSTANCED_CENTER_BYTES_PER_SPRITE,
            VertexAttribute.of(3, VertexFormat.FLOAT32X2, 0));
    private static final VertexLayout INSTANCED_SPRITE_VERTEX_LAYOUT = VertexLayout.instance(
            INSTANCE_BYTES_PER_SPRITE,
            VertexAttribute.of(0, VertexFormat.FLOAT32X4, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 16),
            VertexAttribute.of(2, VertexFormat.FLOAT32X4, 32),
            VertexAttribute.of(3, VertexFormat.FLOAT32X2, 48));
    private static final String SPRITE_WGSL =
            "struct VertexInput {\n" +
            "    @location(0) position : vec2f,\n" +
            "    @location(1) texCoord : vec2f,\n" +
            "    @location(2) color : vec4f,\n" +
            "};\n" +
            "struct VertexOutput {\n" +
            "    @builtin(position) position : vec4f,\n" +
            "    @location(0) texCoord : vec2f,\n" +
            "    @location(1) color : vec4f,\n" +
            "};\n" +
            "@group(0) @binding(0) var u_texture : texture_2d<f32>;\n" +
            "@group(0) @binding(1) var u_sampler : sampler;\n" +
            "@vertex\n" +
            "fn vertexMain(input : VertexInput) -> VertexOutput {\n" +
            "    var output : VertexOutput;\n" +
            "    output.position = vec4f(input.position, 0.0, 1.0);\n" +
            "    output.texCoord = input.texCoord;\n" +
            "    output.color = input.color;\n" +
            "    return output;\n" +
            "}\n" +
            "@fragment\n" +
            "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
            "    return textureSample(u_texture, u_sampler, input.texCoord) * input.color;\n" +
            "}\n";
    private static final String SPRITE_VERTEX_GLSL =
            "#version 330 core\n" +
            "layout(location = 0) in vec2 a_position;\n" +
            "layout(location = 1) in vec2 a_texCoord;\n" +
            "layout(location = 2) in vec4 a_color;\n" +
            "out vec2 v_texCoord;\n" +
            "out vec4 v_color;\n" +
            "void main() {\n" +
            "    v_texCoord = a_texCoord;\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "}\n";
    private static final String SPRITE_FRAGMENT_GLSL =
            "#version 330 core\n" +
            "in vec2 v_texCoord;\n" +
            "in vec4 v_color;\n" +
            "uniform sampler2D u_texture;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(u_texture, v_texCoord) * v_color;\n" +
            "}\n";
    private static final String WHITE_SPRITE_WGSL =
            "struct VertexInput {\n" +
            "    @location(0) position : vec2f,\n" +
            "    @location(1) texCoord : vec2f,\n" +
            "};\n" +
            "struct VertexOutput {\n" +
            "    @builtin(position) position : vec4f,\n" +
            "    @location(0) texCoord : vec2f,\n" +
            "};\n" +
            "@group(0) @binding(0) var u_texture : texture_2d<f32>;\n" +
            "@group(0) @binding(1) var u_sampler : sampler;\n" +
            "@vertex\n" +
            "fn vertexMain(input : VertexInput) -> VertexOutput {\n" +
            "    var output : VertexOutput;\n" +
            "    output.position = vec4f(input.position, 0.0, 1.0);\n" +
            "    output.texCoord = input.texCoord;\n" +
            "    return output;\n" +
            "}\n" +
            "@fragment\n" +
            "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
            "    return textureSample(u_texture, u_sampler, input.texCoord);\n" +
            "}\n";
    private static final String WHITE_SPRITE_VERTEX_GLSL =
            "#version 330 core\n" +
            "layout(location = 0) in vec2 a_position;\n" +
            "layout(location = 1) in vec2 a_texCoord;\n" +
            "out vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    v_texCoord = a_texCoord;\n" +
            "    gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "}\n";
    private static final String WHITE_SPRITE_FRAGMENT_GLSL =
            "#version 330 core\n" +
            "in vec2 v_texCoord;\n" +
            "uniform sampler2D u_texture;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(u_texture, v_texCoord);\n" +
            "}\n";
    private static final String INSTANCED_SPRITE_WGSL =
            "struct VertexInput {\n" +
            "    @location(0) baseAndEdgeX : vec4f,\n" +
            "    @location(1) edgeYAndUvBase : vec4f,\n" +
            "    @location(2) uvSizeAndColorRG : vec4f,\n" +
            "    @location(3) colorBA : vec2f,\n" +
            "};\n" +
            "struct VertexOutput {\n" +
            "    @builtin(position) position : vec4f,\n" +
            "    @location(0) texCoord : vec2f,\n" +
            "    @location(1) color : vec4f,\n" +
            "};\n" +
            "@group(0) @binding(0) var u_texture : texture_2d<f32>;\n" +
            "@group(0) @binding(1) var u_sampler : sampler;\n" +
            "@vertex\n" +
            "fn vertexMain(@builtin(vertex_index) vertexIndex : u32, input : VertexInput) -> VertexOutput {\n" +
            "    var output : VertexOutput;\n" +
            "    let cornerIndex = vertexIndex % 6u;\n" +
            "    let corner = vec2f(\n" +
            "        select(0.0, 1.0, cornerIndex == 2u || cornerIndex == 4u || cornerIndex == 5u),\n" +
            "        select(0.0, 1.0, cornerIndex == 1u || cornerIndex == 2u || cornerIndex == 4u));\n" +
            "    let basePosition = vec2f(input.baseAndEdgeX.x, input.baseAndEdgeX.y);\n" +
            "    let edgeX = vec2f(input.baseAndEdgeX.z, input.baseAndEdgeX.w);\n" +
            "    let edgeY = vec2f(input.edgeYAndUvBase.x, input.edgeYAndUvBase.y);\n" +
            "    let uvBase = vec2f(input.edgeYAndUvBase.z, input.edgeYAndUvBase.w);\n" +
            "    let uvSize = vec2f(input.uvSizeAndColorRG.x, input.uvSizeAndColorRG.y);\n" +
            "    let color = vec4f(input.uvSizeAndColorRG.z, input.uvSizeAndColorRG.w,\n" +
            "        input.colorBA.x, input.colorBA.y);\n" +
            "    let position = basePosition + edgeX * corner.x + edgeY * corner.y;\n" +
            "    output.position = vec4f(position, 0.0, 1.0);\n" +
            "    output.texCoord = uvBase + uvSize * corner;\n" +
            "    output.color = color;\n" +
            "    return output;\n" +
            "}\n" +
            "@fragment\n" +
            "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
            "    return textureSample(u_texture, u_sampler, input.texCoord) * input.color;\n" +
            "}\n";
    private static final String INSTANCED_SPRITE_VERTEX_GLSL =
            "#version 330 core\n" +
            "layout(location = 0) in vec4 a_baseAndEdgeX;\n" +
            "layout(location = 1) in vec4 a_edgeYAndUvBase;\n" +
            "layout(location = 2) in vec4 a_uvSizeAndColorRG;\n" +
            "layout(location = 3) in vec2 a_colorBA;\n" +
            "out vec2 v_texCoord;\n" +
            "out vec4 v_color;\n" +
            "void main() {\n" +
            "    int cornerIndex = gl_VertexID % 6;\n" +
            "    vec2 corner = vec2(\n" +
            "        (cornerIndex == 2 || cornerIndex == 4 || cornerIndex == 5) ? 1.0 : 0.0,\n" +
            "        (cornerIndex == 1 || cornerIndex == 2 || cornerIndex == 4) ? 1.0 : 0.0);\n" +
            "    vec2 basePosition = a_baseAndEdgeX.xy;\n" +
            "    vec2 edgeX = a_baseAndEdgeX.zw;\n" +
            "    vec2 edgeY = a_edgeYAndUvBase.xy;\n" +
            "    vec2 uvBase = a_edgeYAndUvBase.zw;\n" +
            "    vec2 uvSize = a_uvSizeAndColorRG.xy;\n" +
            "    vec4 color = vec4(a_uvSizeAndColorRG.zw, a_colorBA);\n" +
            "    vec2 position = basePosition + edgeX * corner.x + edgeY * corner.y;\n" +
            "    v_texCoord = uvBase + uvSize * corner;\n" +
            "    v_color = color;\n" +
            "    gl_Position = vec4(position, 0.0, 1.0);\n" +
            "}\n";
    private static final String INSTANCED_SPRITE_FRAGMENT_GLSL =
            "#version 330 core\n" +
            "in vec2 v_texCoord;\n" +
            "in vec4 v_color;\n" +
            "uniform sampler2D u_texture;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(u_texture, v_texCoord) * v_color;\n" +
            "}\n";
    private static final String COMPACT_INSTANCED_SPRITE_WGSL =
            "struct VertexInput {\n" +
            "    @location(0) localPosition : vec2f,\n" +
            "    @location(1) texCoord : vec2f,\n" +
            "    @location(2) color : vec4f,\n" +
            "    @location(3) center : vec2f,\n" +
            "};\n" +
            "struct VertexOutput {\n" +
            "    @builtin(position) position : vec4f,\n" +
            "    @location(0) texCoord : vec2f,\n" +
            "    @location(1) color : vec4f,\n" +
            "};\n" +
            "@group(0) @binding(0) var u_texture : texture_2d<f32>;\n" +
            "@group(0) @binding(1) var u_sampler : sampler;\n" +
            "@vertex\n" +
            "fn vertexMain(input : VertexInput) -> VertexOutput {\n" +
            "    var output : VertexOutput;\n" +
            "    output.position = vec4f(input.center + input.localPosition, 0.0, 1.0);\n" +
            "    output.texCoord = input.texCoord;\n" +
            "    output.color = input.color;\n" +
            "    return output;\n" +
            "}\n" +
            "@fragment\n" +
            "fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {\n" +
            "    return textureSample(u_texture, u_sampler, input.texCoord) * input.color;\n" +
            "}\n";
    private static final String COMPACT_INSTANCED_SPRITE_VERTEX_GLSL =
            "#version 330 core\n" +
            "layout(location = 0) in vec2 a_localPosition;\n" +
            "layout(location = 1) in vec2 a_texCoord;\n" +
            "layout(location = 2) in vec4 a_color;\n" +
            "layout(location = 3) in vec2 a_center;\n" +
            "out vec2 v_texCoord;\n" +
            "out vec4 v_color;\n" +
            "void main() {\n" +
            "    v_texCoord = a_texCoord;\n" +
            "    v_color = a_color;\n" +
            "    gl_Position = vec4(a_center + a_localPosition, 0.0, 1.0);\n" +
            "}\n";
    private static final String COMPACT_INSTANCED_SPRITE_FRAGMENT_GLSL =
            "#version 330 core\n" +
            "in vec2 v_texCoord;\n" +
            "in vec4 v_color;\n" +
            "uniform sampler2D u_texture;\n" +
            "out vec4 fragColor;\n" +
            "void main() {\n" +
            "    fragColor = texture(u_texture, v_texCoord) * v_color;\n" +
            "}\n";
    private static final int[] SPRITE_VERTEX_SPIRV = {
            0x07230203,0x00010600,0x00070000,0x00000023,0x00000000,0x00020011,0x00000001,0x0006000b,
            0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
            0x000c000f,0x00000000,0x00000002,0x74726576,0x614d7865,0x00006e69,0x00000003,0x00000004,
            0x00000005,0x00000006,0x00000007,0x00000008,0x00030003,0x00000002,0x000001c2,0x000a0004,
            0x475f4c47,0x4c474f4f,0x70635f45,0x74735f70,0x5f656c79,0x656e696c,0x7269645f,0x69746365,
            0x00006576,0x00080004,0x475f4c47,0x4c474f4f,0x6e695f45,0x64756c63,0x69645f65,0x74636572,
            0x00657669,0x00050005,0x00000002,0x74726576,0x614d7865,0x00006e69,0x00050005,0x00000003,
            0x65745f76,0x6f6f4378,0x00006472,0x00050005,0x00000004,0x65745f61,0x6f6f4378,0x00006472,
            0x00040005,0x00000005,0x6f635f76,0x00726f6c,0x00040005,0x00000006,0x6f635f61,0x00726f6c,
            0x00060005,0x00000009,0x505f6c67,0x65567265,0x78657472,0x00000000,0x00060006,0x00000009,
            0x00000000,0x505f6c67,0x7469736f,0x006e6f69,0x00070006,0x00000009,0x00000001,0x505f6c67,
            0x746e696f,0x657a6953,0x00000000,0x00070006,0x00000009,0x00000002,0x435f6c67,0x4470696c,
            0x61747369,0x0065636e,0x00070006,0x00000009,0x00000003,0x435f6c67,0x446c6c75,0x61747369,
            0x0065636e,0x00030005,0x00000007,0x00000000,0x00050005,0x00000008,0x6f705f61,0x69746973,
            0x00006e6f,0x00040047,0x00000003,0x0000001e,0x00000000,0x00040047,0x00000004,0x0000001e,
            0x00000001,0x00040047,0x00000005,0x0000001e,0x00000001,0x00040047,0x00000006,0x0000001e,
            0x00000002,0x00050048,0x00000009,0x00000000,0x0000000b,0x00000000,0x00050048,0x00000009,
            0x00000001,0x0000000b,0x00000001,0x00050048,0x00000009,0x00000002,0x0000000b,0x00000003,
            0x00050048,0x00000009,0x00000003,0x0000000b,0x00000004,0x00030047,0x00000009,0x00000002,
            0x00040047,0x00000008,0x0000001e,0x00000000,0x00020013,0x0000000a,0x00030021,0x0000000b,
            0x0000000a,0x00030016,0x0000000c,0x00000020,0x00040017,0x0000000d,0x0000000c,0x00000002,
            0x00040020,0x0000000e,0x00000003,0x0000000d,0x0004003b,0x0000000e,0x00000003,0x00000003,
            0x00040020,0x0000000f,0x00000001,0x0000000d,0x0004003b,0x0000000f,0x00000004,0x00000001,
            0x00040017,0x00000010,0x0000000c,0x00000004,0x00040020,0x00000011,0x00000003,0x00000010,
            0x0004003b,0x00000011,0x00000005,0x00000003,0x00040020,0x00000012,0x00000001,0x00000010,
            0x0004003b,0x00000012,0x00000006,0x00000001,0x00040015,0x00000013,0x00000020,0x00000000,
            0x0004002b,0x00000013,0x00000014,0x00000001,0x0004001c,0x00000015,0x0000000c,0x00000014,
            0x0006001e,0x00000009,0x00000010,0x0000000c,0x00000015,0x00000015,0x00040020,0x00000016,
            0x00000003,0x00000009,0x0004003b,0x00000016,0x00000007,0x00000003,0x00040015,0x00000017,
            0x00000020,0x00000001,0x0004002b,0x00000017,0x00000018,0x00000000,0x0004003b,0x0000000f,
            0x00000008,0x00000001,0x0004002b,0x0000000c,0x00000019,0x00000000,0x0004002b,0x0000000c,
            0x0000001a,0x3f800000,0x00050036,0x0000000a,0x00000002,0x00000000,0x0000000b,0x000200f8,
            0x0000001b,0x0004003d,0x0000000d,0x0000001c,0x00000004,0x0003003e,0x00000003,0x0000001c,
            0x0004003d,0x00000010,0x0000001d,0x00000006,0x0003003e,0x00000005,0x0000001d,0x0004003d,
            0x0000000d,0x0000001e,0x00000008,0x00050051,0x0000000c,0x0000001f,0x0000001e,0x00000000,
            0x00050051,0x0000000c,0x00000020,0x0000001e,0x00000001,0x00070050,0x00000010,0x00000021,
            0x0000001f,0x00000020,0x00000019,0x0000001a,0x00050041,0x00000011,0x00000022,0x00000007,
            0x00000018,0x0003003e,0x00000022,0x00000021,0x000100fd,0x00010038
    };
    private static final int[] SPRITE_FRAGMENT_SPIRV = {
            0x07230203,0x00010600,0x00070000,0x00000018,0x00000000,0x00020011,0x00000001,0x0006000b,
            0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
            0x000a000f,0x00000004,0x00000002,0x67617266,0x746e656d,0x6e69614d,0x00000000,0x00000003,
            0x00000004,0x00000005,0x00030010,0x00000002,0x00000007,0x00030003,0x00000002,0x000001c2,
            0x000a0004,0x475f4c47,0x4c474f4f,0x70635f45,0x74735f70,0x5f656c79,0x656e696c,0x7269645f,
            0x69746365,0x00006576,0x00080004,0x475f4c47,0x4c474f4f,0x6e695f45,0x64756c63,0x69645f65,
            0x74636572,0x00657669,0x00060005,0x00000002,0x67617266,0x746e656d,0x6e69614d,0x00000000,
            0x00050005,0x00000003,0x67617266,0x6f6c6f43,0x00000072,0x00050005,0x00000006,0x65745f75,
            0x72757478,0x00000065,0x00050005,0x00000004,0x65745f76,0x6f6f4378,0x00006472,0x00040005,
            0x00000005,0x6f635f76,0x00726f6c,0x00040047,0x00000003,0x0000001e,0x00000000,0x00040047,
            0x00000006,0x00000022,0x00000000,0x00040047,0x00000006,0x00000021,0x00000000,0x00040047,
            0x00000004,0x0000001e,0x00000000,0x00040047,0x00000005,0x0000001e,0x00000001,0x00020013,
            0x00000007,0x00030021,0x00000008,0x00000007,0x00030016,0x00000009,0x00000020,0x00040017,
            0x0000000a,0x00000009,0x00000004,0x00040020,0x0000000b,0x00000003,0x0000000a,0x0004003b,
            0x0000000b,0x00000003,0x00000003,0x00090019,0x0000000c,0x00000009,0x00000001,0x00000000,
            0x00000000,0x00000000,0x00000001,0x00000000,0x0003001b,0x0000000d,0x0000000c,0x00040020,
            0x0000000e,0x00000000,0x0000000d,0x0004003b,0x0000000e,0x00000006,0x00000000,0x00040017,
            0x0000000f,0x00000009,0x00000002,0x00040020,0x00000010,0x00000001,0x0000000f,0x0004003b,
            0x00000010,0x00000004,0x00000001,0x00040020,0x00000011,0x00000001,0x0000000a,0x0004003b,
            0x00000011,0x00000005,0x00000001,0x00050036,0x00000007,0x00000002,0x00000000,0x00000008,
            0x000200f8,0x00000012,0x0004003d,0x0000000d,0x00000013,0x00000006,0x0004003d,0x0000000f,
            0x00000014,0x00000004,0x00050057,0x0000000a,0x00000015,0x00000013,0x00000014,0x0004003d,
            0x0000000a,0x00000016,0x00000005,0x00050085,0x0000000a,0x00000017,0x00000015,0x00000016,
            0x0003003e,0x00000003,0x00000017,0x000100fd,0x00010038
    };
    private static final int[] INSTANCED_SPRITE_VERTEX_SPIRV = {
            0x07230203,0x00010000,0x000d000b,0x00000070,0x00000000,0x00020011,0x00000001,0x0006000b,
            0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
            0x000e000f,0x00000000,0x00000004,0x74726576,0x614d7865,0x00006e69,0x0000000a,0x0000002f,
            0x00000036,0x0000003d,0x00000045,0x0000005d,0x00000064,0x00000069,0x00040047,0x0000000a,
            0x0000000b,0x0000002a,0x00040047,0x0000002f,0x0000001e,0x00000000,0x00040047,0x00000036,
            0x0000001e,0x00000001,0x00040047,0x0000003d,0x0000001e,0x00000002,0x00040047,0x00000045,
            0x0000001e,0x00000003,0x00040047,0x0000005d,0x0000001e,0x00000000,0x00040047,0x00000064,
            0x0000001e,0x00000001,0x00030047,0x00000067,0x00000002,0x00050048,0x00000067,0x00000000,
            0x0000000b,0x00000000,0x00050048,0x00000067,0x00000001,0x0000000b,0x00000001,0x00050048,
            0x00000067,0x00000002,0x0000000b,0x00000003,0x00050048,0x00000067,0x00000003,0x0000000b,
            0x00000004,0x00020013,0x00000002,0x00030021,0x00000003,0x00000002,0x00040015,0x00000006,
            0x00000020,0x00000001,0x00040020,0x00000009,0x00000001,0x00000006,0x0004003b,0x00000009,
            0x0000000a,0x00000001,0x0004002b,0x00000006,0x0000000c,0x00000006,0x00030016,0x0000000e,
            0x00000020,0x00040017,0x0000000f,0x0000000e,0x00000002,0x0004002b,0x00000006,0x00000013,
            0x00000002,0x00020014,0x00000014,0x0004002b,0x00000006,0x00000017,0x00000004,0x0004002b,
            0x00000006,0x0000001b,0x00000005,0x0004002b,0x0000000e,0x0000001e,0x3f800000,0x0004002b,
            0x0000000e,0x0000001f,0x00000000,0x0004002b,0x00000006,0x00000022,0x00000001,0x00040017,
            0x0000002d,0x0000000e,0x00000004,0x00040020,0x0000002e,0x00000001,0x0000002d,0x0004003b,
            0x0000002e,0x0000002f,0x00000001,0x0004003b,0x0000002e,0x00000036,0x00000001,0x0004003b,
            0x0000002e,0x0000003d,0x00000001,0x00040020,0x00000044,0x00000001,0x0000000f,0x0004003b,
            0x00000044,0x00000045,0x00000001,0x00040015,0x0000004f,0x00000020,0x00000000,0x0004002b,
            0x0000004f,0x00000057,0x00000001,0x00040020,0x0000005c,0x00000003,0x0000000f,0x0004003b,
            0x0000005c,0x0000005d,0x00000003,0x00040020,0x00000063,0x00000003,0x0000002d,0x0004003b,
            0x00000063,0x00000064,0x00000003,0x0004001c,0x00000066,0x0000000e,0x00000057,0x0006001e,
            0x00000067,0x0000002d,0x0000000e,0x00000066,0x00000066,0x00040020,0x00000068,0x00000003,
            0x00000067,0x0004003b,0x00000068,0x00000069,0x00000003,0x0004002b,0x00000006,0x0000006a,
            0x00000000,0x00050036,0x00000002,0x00000004,0x00000000,0x00000003,0x000200f8,0x00000005,
            0x0004003d,0x00000006,0x0000000b,0x0000000a,0x0005008b,0x00000006,0x0000000d,0x0000000b,
            0x0000000c,0x000500aa,0x00000014,0x00000015,0x0000000d,0x00000013,0x000500aa,0x00000014,
            0x00000018,0x0000000d,0x00000017,0x000500a6,0x00000014,0x00000019,0x00000015,0x00000018,
            0x000500aa,0x00000014,0x0000001c,0x0000000d,0x0000001b,0x000500a6,0x00000014,0x0000001d,
            0x00000019,0x0000001c,0x000600a9,0x0000000e,0x00000020,0x0000001d,0x0000001e,0x0000001f,
            0x000500aa,0x00000014,0x00000023,0x0000000d,0x00000022,0x000500a6,0x00000014,0x00000026,
            0x00000023,0x00000015,0x000500a6,0x00000014,0x00000029,0x00000026,0x00000018,0x000600a9,
            0x0000000e,0x0000002a,0x00000029,0x0000001e,0x0000001f,0x00050050,0x0000000f,0x0000002b,
            0x00000020,0x0000002a,0x0004003d,0x0000002d,0x00000030,0x0000002f,0x0007004f,0x0000000f,
            0x00000031,0x00000030,0x00000030,0x00000000,0x00000001,0x0007004f,0x0000000f,0x00000034,
            0x00000030,0x00000030,0x00000002,0x00000003,0x0004003d,0x0000002d,0x00000037,0x00000036,
            0x0007004f,0x0000000f,0x00000038,0x00000037,0x00000037,0x00000000,0x00000001,0x0007004f,
            0x0000000f,0x0000003b,0x00000037,0x00000037,0x00000002,0x00000003,0x0004003d,0x0000002d,
            0x0000003e,0x0000003d,0x0007004f,0x0000000f,0x0000003f,0x0000003e,0x0000003e,0x00000000,
            0x00000001,0x0004003d,0x0000000f,0x00000046,0x00000045,0x00050051,0x0000000e,0x00000047,
            0x0000003e,0x00000002,0x00050051,0x0000000e,0x00000048,0x0000003e,0x00000003,0x00050051,
            0x0000000e,0x00000049,0x00000046,0x00000000,0x00050051,0x0000000e,0x0000004a,0x00000046,
            0x00000001,0x00070050,0x0000002d,0x0000004b,0x00000047,0x00000048,0x00000049,0x0000004a,
            0x0005008e,0x0000000f,0x00000054,0x00000034,0x00000020,0x00050081,0x0000000f,0x00000055,
            0x00000031,0x00000054,0x0005008e,0x0000000f,0x0000005a,0x00000038,0x0000002a,0x00050081,
            0x0000000f,0x0000005b,0x00000055,0x0000005a,0x00050085,0x0000000f,0x00000061,0x0000003f,
            0x0000002b,0x00050081,0x0000000f,0x00000062,0x0000003b,0x00000061,0x0003003e,0x0000005d,
            0x00000062,0x0003003e,0x00000064,0x0000004b,0x00050051,0x0000000e,0x0000006c,0x0000005b,
            0x00000000,0x00050051,0x0000000e,0x0000006d,0x0000005b,0x00000001,0x00070050,0x0000002d,
            0x0000006e,0x0000006c,0x0000006d,0x0000001f,0x0000001e,0x00050041,0x00000063,0x0000006f,
            0x00000069,0x0000006a,0x0003003e,0x0000006f,0x0000006e,0x000100fd,0x00010038
    };
    private static final int[] INSTANCED_SPRITE_FRAGMENT_SPIRV = {
            0x07230203,0x00010000,0x000d000b,0x00000018,0x00000000,0x00020011,0x00000001,0x0006000b,
            0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
            0x000a000f,0x00000004,0x00000004,0x67617266,0x746e656d,0x6e69614d,0x00000000,0x00000009,
            0x00000011,0x00000015,0x00030010,0x00000004,0x00000007,0x00040047,0x00000009,0x0000001e,
            0x00000000,0x00040047,0x0000000d,0x00000021,0x00000000,0x00040047,0x0000000d,0x00000022,
            0x00000000,0x00040047,0x00000011,0x0000001e,0x00000000,0x00040047,0x00000015,0x0000001e,
            0x00000001,0x00020013,0x00000002,0x00030021,0x00000003,0x00000002,0x00030016,0x00000006,
            0x00000020,0x00040017,0x00000007,0x00000006,0x00000004,0x00040020,0x00000008,0x00000003,
            0x00000007,0x0004003b,0x00000008,0x00000009,0x00000003,0x00090019,0x0000000a,0x00000006,
            0x00000001,0x00000000,0x00000000,0x00000000,0x00000001,0x00000000,0x0003001b,0x0000000b,
            0x0000000a,0x00040020,0x0000000c,0x00000000,0x0000000b,0x0004003b,0x0000000c,0x0000000d,
            0x00000000,0x00040017,0x0000000f,0x00000006,0x00000002,0x00040020,0x00000010,0x00000001,
            0x0000000f,0x0004003b,0x00000010,0x00000011,0x00000001,0x00040020,0x00000014,0x00000001,
            0x00000007,0x0004003b,0x00000014,0x00000015,0x00000001,0x00050036,0x00000002,0x00000004,
            0x00000000,0x00000003,0x000200f8,0x00000005,0x0004003d,0x0000000b,0x0000000e,0x0000000d,
            0x0004003d,0x0000000f,0x00000012,0x00000011,0x00050057,0x00000007,0x00000013,0x0000000e,
            0x00000012,0x0004003d,0x00000007,0x00000016,0x00000015,0x00050085,0x00000007,0x00000017,
            0x00000013,0x00000016,0x0003003e,0x00000009,0x00000017,0x000100fd,0x00010038
    };
    private static final int[] COMPACT_INSTANCED_SPRITE_VERTEX_SPIRV = {
            0x07230203,0x00010000,0x000d000b,0x00000029,0x00000000,0x00020011,0x00000001,0x0006000b,
            0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
            0x000d000f,0x00000000,0x00000004,0x74726576,0x614d7865,0x00006e69,0x0000000b,0x0000000d,
            0x00000011,0x00000012,0x00000016,0x00000018,0x0000001f,0x00040047,0x0000000b,0x0000001e,
            0x00000003,0x00040047,0x0000000d,0x0000001e,0x00000000,0x00040047,0x00000011,0x0000001e,
            0x00000000,0x00040047,0x00000012,0x0000001e,0x00000001,0x00040047,0x00000016,0x0000001e,
            0x00000001,0x00040047,0x00000018,0x0000001e,0x00000002,0x00030047,0x0000001d,0x00000002,
            0x00050048,0x0000001d,0x00000000,0x0000000b,0x00000000,0x00050048,0x0000001d,0x00000001,
            0x0000000b,0x00000001,0x00050048,0x0000001d,0x00000002,0x0000000b,0x00000003,0x00050048,
            0x0000001d,0x00000003,0x0000000b,0x00000004,0x00020013,0x00000002,0x00030021,0x00000003,
            0x00000002,0x00030016,0x00000006,0x00000020,0x00040017,0x00000007,0x00000006,0x00000002,
            0x00040020,0x0000000a,0x00000001,0x00000007,0x0004003b,0x0000000a,0x0000000b,0x00000001,
            0x0004003b,0x0000000a,0x0000000d,0x00000001,0x00040020,0x00000010,0x00000003,0x00000007,
            0x0004003b,0x00000010,0x00000011,0x00000003,0x0004003b,0x0000000a,0x00000012,0x00000001,
            0x00040017,0x00000014,0x00000006,0x00000004,0x00040020,0x00000015,0x00000003,0x00000014,
            0x0004003b,0x00000015,0x00000016,0x00000003,0x00040020,0x00000017,0x00000001,0x00000014,
            0x0004003b,0x00000017,0x00000018,0x00000001,0x00040015,0x0000001a,0x00000020,0x00000000,
            0x0004002b,0x0000001a,0x0000001b,0x00000001,0x0004001c,0x0000001c,0x00000006,0x0000001b,
            0x0006001e,0x0000001d,0x00000014,0x00000006,0x0000001c,0x0000001c,0x00040020,0x0000001e,
            0x00000003,0x0000001d,0x0004003b,0x0000001e,0x0000001f,0x00000003,0x00040015,0x00000020,
            0x00000020,0x00000001,0x0004002b,0x00000020,0x00000021,0x00000000,0x0004002b,0x00000006,
            0x00000023,0x00000000,0x0004002b,0x00000006,0x00000024,0x3f800000,0x00050036,0x00000002,
            0x00000004,0x00000000,0x00000003,0x000200f8,0x00000005,0x0004003d,0x00000007,0x0000000c,
            0x0000000b,0x0004003d,0x00000007,0x0000000e,0x0000000d,0x00050081,0x00000007,0x0000000f,
            0x0000000c,0x0000000e,0x0004003d,0x00000007,0x00000013,0x00000012,0x0003003e,0x00000011,
            0x00000013,0x0004003d,0x00000014,0x00000019,0x00000018,0x0003003e,0x00000016,0x00000019,
            0x00050051,0x00000006,0x00000025,0x0000000f,0x00000000,0x00050051,0x00000006,0x00000026,
            0x0000000f,0x00000001,0x00070050,0x00000014,0x00000027,0x00000025,0x00000026,0x00000023,
            0x00000024,0x00050041,0x00000015,0x00000028,0x0000001f,0x00000021,0x0003003e,0x00000028,
            0x00000027,0x000100fd,0x00010038
    };
    private static final int[] COMPACT_INSTANCED_SPRITE_FRAGMENT_SPIRV = {
            0x07230203,0x00010000,0x000d000b,0x00000018,0x00000000,0x00020011,0x00000001,0x0006000b,
            0x00000001,0x4c534c47,0x6474732e,0x3035342e,0x00000000,0x0003000e,0x00000000,0x00000001,
            0x000a000f,0x00000004,0x00000004,0x67617266,0x746e656d,0x6e69614d,0x00000000,0x00000009,
            0x00000011,0x00000015,0x00030010,0x00000004,0x00000007,0x00040047,0x00000009,0x0000001e,
            0x00000000,0x00040047,0x0000000d,0x00000021,0x00000000,0x00040047,0x0000000d,0x00000022,
            0x00000000,0x00040047,0x00000011,0x0000001e,0x00000000,0x00040047,0x00000015,0x0000001e,
            0x00000001,0x00020013,0x00000002,0x00030021,0x00000003,0x00000002,0x00030016,0x00000006,
            0x00000020,0x00040017,0x00000007,0x00000006,0x00000004,0x00040020,0x00000008,0x00000003,
            0x00000007,0x0004003b,0x00000008,0x00000009,0x00000003,0x00090019,0x0000000a,0x00000006,
            0x00000001,0x00000000,0x00000000,0x00000000,0x00000001,0x00000000,0x0003001b,0x0000000b,
            0x0000000a,0x00040020,0x0000000c,0x00000000,0x0000000b,0x0004003b,0x0000000c,0x0000000d,
            0x00000000,0x00040017,0x0000000f,0x00000006,0x00000002,0x00040020,0x00000010,0x00000001,
            0x0000000f,0x0004003b,0x00000010,0x00000011,0x00000001,0x00040020,0x00000014,0x00000001,
            0x00000007,0x0004003b,0x00000014,0x00000015,0x00000001,0x00050036,0x00000002,0x00000004,
            0x00000000,0x00000003,0x000200f8,0x00000005,0x0004003d,0x0000000b,0x0000000e,0x0000000d,
            0x0004003d,0x0000000f,0x00000012,0x00000011,0x00050057,0x00000007,0x00000013,0x0000000e,
            0x00000012,0x0004003d,0x00000007,0x00000016,0x00000015,0x00050085,0x00000007,0x00000017,
            0x00000013,0x00000016,0x0003003e,0x00000009,0x00000017,0x000100fd,0x00010038
    };

    private final GraphicsContext graphics;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final ShaderModule whiteShader;
    private final RenderPipeline whitePipeline;
    private final boolean heapUploadBuffers;
    private final boolean indexed;
    private final ShaderModule instancedShader;
    private final RenderPipeline instancedPipeline;
    private final ShaderModule compactInstancedShader;
    private final RenderPipeline compactInstancedPipeline;
    private final boolean instanced;
    private final boolean instancedIndexed;
    private final int instancedQuadVertexCount;
    private final RenderPassDescriptor renderPassDescriptor =
            new RenderPassDescriptor().label("sprite batch pass");
    private float[] vertices;
    private float[] instances;
    private float[] compactInstances;
    private int floatCount;
    private int instanceFloatCount;
    private int compactInstanceFloatCount;
    private Buffer[] vertexBuffers;
    private Buffer indexBuffer;
    private Buffer[] instancedQuadBuffers;
    private Buffer[] instanceBuffers;
    private Buffer[] compactInstanceBuffers;
    private Buffer[] retiredBuffers;
    private Buffer instancedIndexBuffer;
    private ByteBuffer uploadBuffer;
    private ByteBuffer indexUploadBuffer;
    private ByteBuffer instancedQuadUploadBuffer;
    private ByteBuffer instanceUploadBuffer;
    private ByteBuffer compactInstanceUploadBuffer;
    private ByteBuffer instancedIndexUploadBuffer;
    private FloatBuffer uploadFloats;
    private FloatBuffer instancedQuadUploadFloats;
    private FloatBuffer instanceUploadFloats;
    private FloatBuffer compactInstanceUploadFloats;
    private int vertexCount;
    private int indexCount;
    private int spriteCount;
    private int instanceCount;
    private int compactInstanceCount;
    private int indexBufferSpriteCapacity;
    private int vertexBufferSlot;
    private int instancedQuadBufferSlot;
    private int instanceBufferSlot;
    private int compactInstanceBufferSlot;
    private int retiredBufferCount;
    private RenderPass pass;
    private RenderPass flushSlotPass;
    private Texture currentTexture;
    private boolean ownsPass;
    private boolean drawing;
    private boolean disposed;
    private boolean batchUsesColor = true;
    private float red = 1.0f;
    private float green = 1.0f;
    private float blue = 1.0f;
    private float alpha = 1.0f;
    private int viewportWidth;
    private int viewportHeight;
    private boolean hasTransformCache;
    private float cachedWidth;
    private float cachedHeight;
    private float cachedOriginX;
    private float cachedOriginY;
    private float cachedRotationDegrees;
    private float cachedViewportScaleX;
    private float cachedViewportScaleY;
    private float cachedX1;
    private float cachedY1;
    private float cachedX2;
    private float cachedY2;
    private float cachedX3;
    private float cachedY3;
    private float cachedX4;
    private float cachedY4;
    private boolean compactBatchStateSet;
    private float compactX1;
    private float compactY1;
    private float compactX2;
    private float compactY2;
    private float compactX3;
    private float compactY3;
    private float compactX4;
    private float compactY4;
    private float compactU;
    private float compactV;
    private float compactU2;
    private float compactV2;
    private float compactRed;
    private float compactGreen;
    private float compactBlue;
    private float compactAlpha;

    public SpriteBatch(GraphicsContext graphicsSystem) {
        this(graphicsSystem, DEFAULT_MAX_SPRITES);
    }

    public SpriteBatch(GraphicsContext graphicsSystem, int initialMaxSprites) {
        if (graphicsSystem == null) {
            throw new FdxException("GraphicsContext cannot be null");
        }
        if (initialMaxSprites <= 0) {
            throw new FdxException("SpriteBatch initial sprite count must be greater than zero");
        }
        graphics = graphicsSystem;
        heapUploadBuffers = usesHeapUploadBuffers(graphicsSystem);
        indexed = supportsIndexedSprites(graphics);
        instanced = supportsInstancedSprites(graphics);
        instancedIndexed = instanced && supportsIndexedInstancedSprites(graphics);
        instancedQuadVertexCount = instancedIndexed ? INSTANCED_INDEXED_QUAD_VERTICES : INSTANCED_NON_INDEXED_QUAD_VERTICES;
        vertices = new float[initialMaxSprites * VERTICES_PER_SPRITE * FLOATS_PER_VERTEX];
        instances = new float[initialMaxSprites * INSTANCE_FLOATS_PER_SPRITE];
        compactInstances = new float[initialMaxSprites * INSTANCED_CENTER_FLOATS_PER_SPRITE];
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("sprite batch", SPRITE_WGSL)
                .glsl(SPRITE_VERTEX_GLSL, SPRITE_FRAGMENT_GLSL)
                .spirv(SPRITE_VERTEX_SPIRV, SPRITE_FRAGMENT_SPIRV));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label("sprite batch")
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexEntryPoint("vertexMain")
                .fragmentEntryPoint("fragmentMain")
                .vertexLayout(SPRITE_VERTEX_LAYOUT)
                .sampledTextureCount(1));
        if (supportsWhitePipeline(graphics)) {
            whiteShader = graphics.device().createShaderModule(ShaderModuleDescriptor
                    .wgsl("white sprite batch", WHITE_SPRITE_WGSL)
                    .glsl(WHITE_SPRITE_VERTEX_GLSL, WHITE_SPRITE_FRAGMENT_GLSL));
            whitePipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(whiteShader, graphics.surfaceFormat())
                    .label("white sprite batch")
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                    .vertexEntryPoint("vertexMain")
                    .fragmentEntryPoint("fragmentMain")
                    .vertexLayout(WHITE_SPRITE_VERTEX_LAYOUT)
                    .sampledTextureCount(1));
        } else {
            whiteShader = null;
            whitePipeline = null;
        }
        if (instanced) {
            instancedShader = graphics.device().createShaderModule(ShaderModuleDescriptor
                    .wgsl("instanced sprite batch", INSTANCED_SPRITE_WGSL)
                    .glsl(INSTANCED_SPRITE_VERTEX_GLSL, INSTANCED_SPRITE_FRAGMENT_GLSL)
                    .spirv(INSTANCED_SPRITE_VERTEX_SPIRV, INSTANCED_SPRITE_FRAGMENT_SPIRV));
            instancedPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(instancedShader, graphics.surfaceFormat())
                    .label("instanced sprite batch")
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                    .vertexEntryPoint("vertexMain")
                    .fragmentEntryPoint("fragmentMain")
                    .vertexLayout(INSTANCED_SPRITE_VERTEX_LAYOUT)
                    .sampledTextureCount(1));
            instanceBuffers = ensureBufferSlots(instanceBuffers, 0);
            instanceBuffers[0] = ensureBuffer(instanceBuffers[0],
                    initialMaxSprites * INSTANCE_BYTES_PER_SPRITE, "sprite batch instances");
            compactInstancedShader = graphics.device().createShaderModule(ShaderModuleDescriptor
                    .wgsl("compact instanced sprite batch", COMPACT_INSTANCED_SPRITE_WGSL)
                    .glsl(COMPACT_INSTANCED_SPRITE_VERTEX_GLSL, COMPACT_INSTANCED_SPRITE_FRAGMENT_GLSL)
                    .spirv(COMPACT_INSTANCED_SPRITE_VERTEX_SPIRV, COMPACT_INSTANCED_SPRITE_FRAGMENT_SPIRV));
            compactInstancedPipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                    .shader(compactInstancedShader, graphics.surfaceFormat())
                    .label("compact instanced sprite batch")
                    .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                    .vertexEntryPoint("vertexMain")
                    .fragmentEntryPoint("fragmentMain")
                    .vertexLayouts(INSTANCED_QUAD_VERTEX_LAYOUT, INSTANCED_CENTER_VERTEX_LAYOUT)
                    .sampledTextureCount(1));
            compactInstanceBuffers = ensureBufferSlots(compactInstanceBuffers, 0);
            compactInstanceBuffers[0] = ensureBuffer(compactInstanceBuffers[0],
                    initialMaxSprites * INSTANCED_CENTER_BYTES_PER_SPRITE, "sprite batch compact instances");
            if (instancedIndexed) {
                ensureInstancedIndexBuffer();
            }
        } else {
            instancedShader = null;
            instancedPipeline = null;
            compactInstancedShader = null;
            compactInstancedPipeline = null;
        }
        int initialByteCount = initialMaxSprites * VERTICES_PER_SPRITE * BYTES_PER_VERTEX;
        ensureVertexBuffer(0, initialByteCount);
        ensureUploadBuffer(initialByteCount);
        if (indexed) {
            ensureIndexBuffer(Math.min(initialMaxSprites, MAX_INDEXED_SPRITES));
        }
    }

    @Override
    public void begin() {
        begin(LoadOp.load());
    }

    @Override
    public void begin(LoadOp loadOp) {
        ensureNotDisposed();
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(loadOp != null ? loadOp : LoadOp.load())
                .colorStoreOp(StoreOp.store()));
        ownsPass = true;
        drawing = true;
        resetFlushBufferSlots();
        flushSlotPass = pass;
    }

    @Override
    public void begin(RenderPass pass) {
        ensureNotDisposed();
        if (pass == null) {
            throw new FdxException("RenderPass cannot be null");
        }
        this.pass = pass;
        if (flushSlotPass != pass) {
            resetFlushBufferSlots();
            flushSlotPass = pass;
        }
        ownsPass = false;
        drawing = true;
    }

    @Override
    public SpriteBatch color(float red, float green, float blue, float alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        return this;
    }

    @Override
    public SpriteBatch viewport(int width, int height) {
        viewportWidth = Math.max(0, width);
        viewportHeight = Math.max(0, height);
        return this;
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height) {
        draw(new TextureRegion(texture), x, y, width, height);
    }

    @Override
    public void draw(Texture texture, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees) {
        draw(new TextureRegion(texture), x, y, width, height, originX, originY, rotationDegrees);
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height) {
        draw(region, x, y, width, height, 0.0f, 0.0f, 0.0f);
    }

    @Override
    public void draw(TextureRegion region, float x, float y, float width, float height,
            float originX, float originY, float rotationDegrees) {
        ensureDrawing();
        if (region == null) {
            throw new FdxException("TextureRegion cannot be null");
        }
        if (currentTexture != null && currentTexture != region.texture()) {
            flush();
        }
        currentTexture = region.texture();
        float worldOriginX = x + originX;
        float worldOriginY = y + originY;
        float scaleX = viewportWidth > 0 ? viewportWidth * 0.5f : 1.0f;
        float scaleY = viewportHeight > 0 ? viewportHeight * 0.5f : 1.0f;
        updateTransformCache(width, height, originX, originY, rotationDegrees, scaleX, scaleY);

        float u = region.u();
        float v = region.v();
        float u2 = region.u2();
        float v2 = region.v2();
        float x1 = cachedX1 + worldOriginX;
        float y1 = cachedY1 + worldOriginY;
        float x2 = cachedX2 + worldOriginX;
        float y2 = cachedY2 + worldOriginY;
        float x3 = cachedX3 + worldOriginX;
        float y3 = cachedY3 + worldOriginY;
        float x4 = cachedX4 + worldOriginX;
        float y4 = cachedY4 + worldOriginY;

        if (instanced) {
            if (vertexCount > 0 || compactInstanceCount > 0) {
                flush();
            }
            appendInstance(x1, y1, x2, y2, x4, y4, u, v, u2, v2);
            return;
        }

        prepareBatchForCurrentColor();
        if (instanceCount > 0 || compactInstanceCount > 0) {
            flush();
        }

        appendQuad(x1, y1, x2, y2, x3, y3, x4, y4, u, v, u2, v2);
    }

    @Override
    public void draw(TextureRegion region, float[] centerX, float[] centerY, int count, float width, float height,
            float originX, float originY, float rotationDegrees) {
        ensureDrawing();
        if (region == null) {
            throw new FdxException("TextureRegion cannot be null");
        }
        if (centerX == null || centerY == null) {
            throw new FdxException("Sprite center arrays cannot be null");
        }
        if (count < 0 || count > centerX.length || count > centerY.length) {
            throw new FdxException("Sprite center count is outside the provided arrays");
        }
        if (count == 0) {
            return;
        }
        if (!instanced) {
            for (int i = 0; i < count; i++) {
                draw(region, centerX[i] - originX, centerY[i] - originY, width, height, originX, originY,
                        rotationDegrees);
            }
            return;
        }
        if (currentTexture != null && currentTexture != region.texture()) {
            flush();
        }
        currentTexture = region.texture();
        if (vertexCount > 0 || instanceCount > 0) {
            flush();
        }
        float scaleX = viewportWidth > 0 ? viewportWidth * 0.5f : 1.0f;
        float scaleY = viewportHeight > 0 ? viewportHeight * 0.5f : 1.0f;
        updateTransformCache(width, height, originX, originY, rotationDegrees, scaleX, scaleY);
        prepareCompactBatch(region.u(), region.v(), region.u2(), region.v2());
        appendCompactInstances(centerX, centerY, count);
    }

    @Override
    public void end() {
        ensureDrawing();
        flush();
        drawing = false;
        if (ownsPass) {
            pass.end();
            flushSlotPass = null;
        }
        ownsPass = false;
        pass = null;
        currentTexture = null;
    }

    private void appendQuad(float x1, float y1, float x2, float y2, float x3, float y3,
            float x4, float y4, float u, float v, float u2, float v2) {
        if (indexed && spriteCount == MAX_INDEXED_SPRITES) {
            flush();
        }
        int floatsPerVertex = batchUsesColor ? FLOATS_PER_VERTEX : WHITE_FLOATS_PER_VERTEX;
        int verticesPerSprite = indexed ? INDEXED_VERTICES_PER_SPRITE : VERTICES_PER_SPRITE;
        vertices = ensureFloatCapacity(vertices, floatCount + verticesPerSprite * floatsPerVertex);
        float r = red;
        float g = green;
        float b = blue;
        float a = alpha;
        float[] values = vertices;
        int index = floatCount;

        values[index++] = x1;
        values[index++] = y1;
        values[index++] = u;
        values[index++] = v2;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        values[index++] = x2;
        values[index++] = y2;
        values[index++] = u;
        values[index++] = v;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        values[index++] = x3;
        values[index++] = y3;
        values[index++] = u2;
        values[index++] = v;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        if (!indexed) {
            values[index++] = x1;
            values[index++] = y1;
            values[index++] = u;
            values[index++] = v2;
            if (batchUsesColor) {
                values[index++] = r;
                values[index++] = g;
                values[index++] = b;
                values[index++] = a;
            }

            values[index++] = x3;
            values[index++] = y3;
            values[index++] = u2;
            values[index++] = v;
            if (batchUsesColor) {
                values[index++] = r;
                values[index++] = g;
                values[index++] = b;
                values[index++] = a;
            }
        }

        values[index++] = x4;
        values[index++] = y4;
        values[index++] = u2;
        values[index++] = v2;
        if (batchUsesColor) {
            values[index++] = r;
            values[index++] = g;
            values[index++] = b;
            values[index++] = a;
        }

        floatCount = index;
        vertexCount += verticesPerSprite;
        if (indexed) {
            indexCount += INDICES_PER_SPRITE;
        }
        spriteCount++;
    }

    private void appendInstance(float x1, float y1, float x2, float y2, float x4, float y4,
            float u, float v, float u2, float v2) {
        instances = ensureFloatCapacity(instances, instanceFloatCount + INSTANCE_FLOATS_PER_SPRITE);
        float[] values = instances;
        int index = instanceFloatCount;
        values[index++] = x1;
        values[index++] = y1;
        values[index++] = x4 - x1;
        values[index++] = y4 - y1;
        values[index++] = x2 - x1;
        values[index++] = y2 - y1;
        values[index++] = u;
        values[index++] = v2;
        values[index++] = u2 - u;
        values[index++] = v - v2;
        values[index++] = red;
        values[index++] = green;
        values[index++] = blue;
        values[index++] = alpha;
        instanceFloatCount = index;
        instanceCount++;
    }

    private void prepareCompactBatch(float u, float v, float u2, float v2) {
        if (compactBatchStateSet && compactInstanceCount > 0 && !matchesCompactBatch(u, v, u2, v2)) {
            flush();
        }
        if (!compactBatchStateSet || !matchesCompactBatch(u, v, u2, v2)) {
            compactBatchStateSet = true;
            compactX1 = cachedX1;
            compactY1 = cachedY1;
            compactX2 = cachedX2;
            compactY2 = cachedY2;
            compactX3 = cachedX3;
            compactY3 = cachedY3;
            compactX4 = cachedX4;
            compactY4 = cachedY4;
            compactU = u;
            compactV = v;
            compactU2 = u2;
            compactV2 = v2;
            compactRed = red;
            compactGreen = green;
            compactBlue = blue;
            compactAlpha = alpha;
        }
    }

    private boolean matchesCompactBatch(float u, float v, float u2, float v2) {
        return compactX1 == cachedX1
                && compactY1 == cachedY1
                && compactX2 == cachedX2
                && compactY2 == cachedY2
                && compactX3 == cachedX3
                && compactY3 == cachedY3
                && compactX4 == cachedX4
                && compactY4 == cachedY4
                && compactU == u
                && compactV == v
                && compactU2 == u2
                && compactV2 == v2
                && compactRed == red
                && compactGreen == green
                && compactBlue == blue
                && compactAlpha == alpha;
    }

    private void appendCompactInstances(float[] centerX, float[] centerY, int count) {
        compactInstances = ensureFloatCapacity(compactInstances,
                compactInstanceFloatCount + count * INSTANCED_CENTER_FLOATS_PER_SPRITE);
        float[] values = compactInstances;
        int index = compactInstanceFloatCount;
        for (int i = 0; i < count; i++) {
            values[index++] = centerX[i];
            values[index++] = centerY[i];
        }
        compactInstanceFloatCount = index;
        compactInstanceCount += count;
    }

    private void prepareBatchForCurrentColor() {
        boolean usesColor = whitePipeline == null || !isWhite();
        if (vertexCount > 0 && batchUsesColor != usesColor) {
            flush();
        }
        batchUsesColor = usesColor;
    }

    private boolean isWhite() {
        return red == 1.0f && green == 1.0f && blue == 1.0f && alpha == 1.0f;
    }

    private void updateTransformCache(float width, float height, float originX, float originY,
            float rotationDegrees, float scaleX, float scaleY) {
        if (hasTransformCache
                && cachedWidth == width
                && cachedHeight == height
                && cachedOriginX == originX
                && cachedOriginY == originY
                && cachedRotationDegrees == rotationDegrees
                && cachedViewportScaleX == scaleX
                && cachedViewportScaleY == scaleY) {
            return;
        }
        hasTransformCache = true;
        cachedWidth = width;
        cachedHeight = height;
        cachedOriginX = originX;
        cachedOriginY = originY;
        cachedRotationDegrees = rotationDegrees;
        cachedViewportScaleX = scaleX;
        cachedViewportScaleY = scaleY;

        float localX = -originX;
        float localY = -originY;
        float localX2 = width - originX;
        float localY2 = height - originY;
        float radians = (float) Math.toRadians(rotationDegrees);
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);

        cachedX1 = rotateX(localX, localY, cos, sin, scaleX, scaleY);
        cachedY1 = rotateY(localX, localY, cos, sin, scaleX, scaleY);
        cachedX2 = rotateX(localX, localY2, cos, sin, scaleX, scaleY);
        cachedY2 = rotateY(localX, localY2, cos, sin, scaleX, scaleY);
        cachedX3 = rotateX(localX2, localY2, cos, sin, scaleX, scaleY);
        cachedY3 = rotateY(localX2, localY2, cos, sin, scaleX, scaleY);
        cachedX4 = rotateX(localX2, localY, cos, sin, scaleX, scaleY);
        cachedY4 = rotateY(localX2, localY, cos, sin, scaleX, scaleY);
    }

    private float rotateX(float x, float y, float cos, float sin, float scaleX, float scaleY) {
        float pixelX = x * scaleX;
        float pixelY = y * scaleY;
        return (pixelX * cos - pixelY * sin) / scaleX;
    }

    private float rotateY(float x, float y, float cos, float sin, float scaleX, float scaleY) {
        float pixelX = x * scaleX;
        float pixelY = y * scaleY;
        return (pixelX * sin + pixelY * cos) / scaleY;
    }

    private void flush() {
        if (vertexCount == 0 && instanceCount == 0 && compactInstanceCount == 0) {
            return;
        }
        if (compactInstanceCount > 0) {
            flushCompactInstances();
        }
        if (instanceCount > 0) {
            flushInstances();
        }
        if (vertexCount == 0) {
            return;
        }
        int byteCount = floatCount * 4;
        Buffer activeVertexBuffer = nextVertexBuffer(byteCount);
        ensureUploadBuffer(byteCount);
        uploadBuffer.clear();
        uploadFloats.clear();
        uploadFloats.put(vertices, 0, floatCount);
        uploadBuffer.limit(byteCount);
        uploadBuffer.position(0);
        graphics.device().writeBuffer(activeVertexBuffer, uploadBuffer);
        uploadBuffer.clear();
        pass.setPipeline(batchUsesColor ? pipeline : whitePipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(activeVertexBuffer);
        if (indexed) {
            ensureIndexBuffer(spriteCount);
            pass.setIndexBuffer(indexBuffer);
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        } else {
            pass.draw(vertexCount, 1, 0, 0);
        }
        floatCount = 0;
        vertexCount = 0;
        indexCount = 0;
        spriteCount = 0;
    }

    private void flushInstances() {
        int instanceByteCount = instanceFloatCount * 4;
        Buffer activeInstanceBuffer = nextInstanceBuffer(instanceCount);
        ensureInstanceUploadBuffer(instanceByteCount);
        instanceUploadBuffer.clear();
        instanceUploadFloats.clear();
        instanceUploadFloats.put(instances, 0, instanceFloatCount);
        instanceUploadBuffer.limit(instanceByteCount);
        instanceUploadBuffer.position(0);
        graphics.device().writeBuffer(activeInstanceBuffer, instanceUploadBuffer);
        instanceUploadBuffer.clear();

        pass.setPipeline(instancedPipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(activeInstanceBuffer);
        pass.draw(VERTICES_PER_SPRITE, instanceCount, 0, 0);
        instanceFloatCount = 0;
        instanceCount = 0;
    }

    private void flushCompactInstances() {
        int instanceByteCount = compactInstanceFloatCount * 4;
        Buffer activeInstancedQuadBuffer = nextInstancedQuadBuffer();
        Buffer activeCompactInstanceBuffer = nextCompactInstanceBuffer(compactInstanceCount);
        ensureCompactInstanceUploadBuffer(instanceByteCount);
        uploadInstancedQuadBuffer(activeInstancedQuadBuffer);
        compactInstanceUploadBuffer.clear();
        compactInstanceUploadFloats.clear();
        compactInstanceUploadFloats.put(compactInstances, 0, compactInstanceFloatCount);
        compactInstanceUploadBuffer.limit(instanceByteCount);
        compactInstanceUploadBuffer.position(0);
        graphics.device().writeBuffer(activeCompactInstanceBuffer, compactInstanceUploadBuffer);
        compactInstanceUploadBuffer.clear();

        pass.setPipeline(compactInstancedPipeline);
        pass.setTexture(0, currentTexture);
        pass.setVertexBuffer(0, activeInstancedQuadBuffer);
        pass.setVertexBuffer(1, activeCompactInstanceBuffer);
        if (instancedIndexed) {
            ensureInstancedIndexBuffer();
            pass.setIndexBuffer(instancedIndexBuffer);
            pass.drawIndexed(INSTANCED_INDICES, compactInstanceCount, 0, 0, 0);
        } else {
            pass.draw(instancedQuadVertexCount, compactInstanceCount, 0, 0);
        }
        compactInstanceFloatCount = 0;
        compactInstanceCount = 0;
        compactBatchStateSet = false;
    }

    private Buffer nextVertexBuffer(int byteCount) {
        Buffer buffer = ensureVertexBuffer(vertexBufferSlot, byteCount);
        vertexBufferSlot++;
        return buffer;
    }

    private Buffer ensureVertexBuffer(int slot, int byteCount) {
        vertexBuffers = ensureBufferSlots(vertexBuffers, slot);
        vertexBuffers[slot] = ensureBuffer(vertexBuffers[slot], byteCount, "sprite batch vertices");
        return vertexBuffers[slot];
    }

    private void ensureIndexBuffer(int sprites) {
        if (sprites <= 0 || indexBufferSpriteCapacity >= sprites) {
            return;
        }
        int actualSprites = Math.min(sprites, MAX_INDEXED_SPRITES);
        int byteCount = actualSprites * INDICES_PER_SPRITE * BYTES_PER_INDEX;
        if (indexBuffer != null) {
            retireBuffer(indexBuffer);
        }
        indexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex("sprite batch indices", byteCount));
        if (indexUploadBuffer == null || indexUploadBuffer.capacity() < byteCount) {
            indexUploadBuffer = newUploadBuffer(byteCount);
        }
        indexUploadBuffer.clear();
        for (int i = 0; i < actualSprites; i++) {
            int vertex = i * INDEXED_VERTICES_PER_SPRITE;
            indexUploadBuffer.putShort((short) vertex);
            indexUploadBuffer.putShort((short) (vertex + 1));
            indexUploadBuffer.putShort((short) (vertex + 2));
            indexUploadBuffer.putShort((short) vertex);
            indexUploadBuffer.putShort((short) (vertex + 2));
            indexUploadBuffer.putShort((short) (vertex + 3));
        }
        indexUploadBuffer.flip();
        graphics.device().writeBuffer(indexBuffer, indexUploadBuffer);
        indexBufferSpriteCapacity = actualSprites;
    }

    private void uploadInstancedQuadBuffer(Buffer activeInstancedQuadBuffer) {
        int byteCount = instancedQuadVertexCount * INSTANCED_QUAD_BYTES_PER_VERTEX;
        ensureInstancedQuadUploadBuffer(byteCount);
        instancedQuadUploadBuffer.clear();
        instancedQuadUploadFloats.clear();
        putInstancedQuadVertex(compactX1, compactY1, compactU, compactV2);
        putInstancedQuadVertex(compactX2, compactY2, compactU, compactV);
        putInstancedQuadVertex(compactX3, compactY3, compactU2, compactV);
        if (!instancedIndexed) {
            putInstancedQuadVertex(compactX1, compactY1, compactU, compactV2);
            putInstancedQuadVertex(compactX3, compactY3, compactU2, compactV);
        }
        putInstancedQuadVertex(compactX4, compactY4, compactU2, compactV2);
        instancedQuadUploadBuffer.limit(byteCount);
        instancedQuadUploadBuffer.position(0);
        graphics.device().writeBuffer(activeInstancedQuadBuffer, instancedQuadUploadBuffer);
        instancedQuadUploadBuffer.clear();
    }

    private void putInstancedQuadVertex(float x, float y, float u, float v) {
        instancedQuadUploadFloats.put(x).put(y);
        instancedQuadUploadFloats.put(u).put(v);
        instancedQuadUploadFloats.put(compactRed).put(compactGreen).put(compactBlue).put(compactAlpha);
    }

    private Buffer nextInstancedQuadBuffer() {
        int byteCount = instancedQuadVertexCount * INSTANCED_QUAD_BYTES_PER_VERTEX;
        instancedQuadBuffers = ensureBufferSlots(instancedQuadBuffers, instancedQuadBufferSlot);
        instancedQuadBuffers[instancedQuadBufferSlot] = ensureBuffer(instancedQuadBuffers[instancedQuadBufferSlot],
                byteCount, "sprite batch instanced quad");
        Buffer buffer = instancedQuadBuffers[instancedQuadBufferSlot];
        instancedQuadBufferSlot++;
        return buffer;
    }

    private Buffer nextInstanceBuffer(int sprites) {
        int byteCount = sprites * INSTANCE_BYTES_PER_SPRITE;
        instanceBuffers = ensureBufferSlots(instanceBuffers, instanceBufferSlot);
        instanceBuffers[instanceBufferSlot] = ensureBuffer(instanceBuffers[instanceBufferSlot], byteCount,
                "sprite batch instances");
        Buffer buffer = instanceBuffers[instanceBufferSlot];
        instanceBufferSlot++;
        return buffer;
    }

    private Buffer nextCompactInstanceBuffer(int sprites) {
        int byteCount = sprites * INSTANCED_CENTER_BYTES_PER_SPRITE;
        compactInstanceBuffers = ensureBufferSlots(compactInstanceBuffers, compactInstanceBufferSlot);
        compactInstanceBuffers[compactInstanceBufferSlot] = ensureBuffer(
                compactInstanceBuffers[compactInstanceBufferSlot], byteCount, "sprite batch compact instances");
        Buffer buffer = compactInstanceBuffers[compactInstanceBufferSlot];
        compactInstanceBufferSlot++;
        return buffer;
    }

    private Buffer[] ensureBufferSlots(Buffer[] buffers, int slot) {
        if (buffers != null && slot < buffers.length) {
            return buffers;
        }
        int next = buffers != null ? buffers.length : 4;
        while (slot >= next) {
            next *= 2;
        }
        Buffer[] larger = new Buffer[next];
        if (buffers != null) {
            System.arraycopy(buffers, 0, larger, 0, buffers.length);
        }
        return larger;
    }

    private Buffer ensureBuffer(Buffer buffer, int byteCount, String label) {
        if (buffer != null && buffer.size() >= byteCount) {
            return buffer;
        }
        if (buffer != null) {
            retireBuffer(buffer);
        }
        return graphics.device().createBuffer(BufferDescriptor.vertex(label, byteCount));
    }

    private void retireBuffer(Buffer buffer) {
        if (buffer == null) {
            return;
        }
        if (retiredBuffers == null) {
            retiredBuffers = new Buffer[4];
        } else if (retiredBufferCount == retiredBuffers.length) {
            Buffer[] larger = new Buffer[retiredBuffers.length * 2];
            System.arraycopy(retiredBuffers, 0, larger, 0, retiredBuffers.length);
            retiredBuffers = larger;
        }
        retiredBuffers[retiredBufferCount++] = buffer;
    }

    private void resetFlushBufferSlots() {
        vertexBufferSlot = 0;
        instancedQuadBufferSlot = 0;
        instanceBufferSlot = 0;
        compactInstanceBufferSlot = 0;
    }

    private void disposeBuffers(Buffer[] buffers) {
        if (buffers == null) {
            return;
        }
        for (int i = 0; i < buffers.length; i++) {
            if (buffers[i] != null) {
                buffers[i].dispose();
                buffers[i] = null;
            }
        }
    }

    private void ensureInstancedIndexBuffer() {
        if (instancedIndexBuffer != null) {
            return;
        }
        instancedIndexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex("sprite batch instanced indices",
                INSTANCED_INDICES * BYTES_PER_INDEX));
        if (instancedIndexUploadBuffer == null) {
            instancedIndexUploadBuffer = newUploadBuffer(INSTANCED_INDICES * BYTES_PER_INDEX);
        }
        instancedIndexUploadBuffer.clear();
        instancedIndexUploadBuffer.putShort((short) 0);
        instancedIndexUploadBuffer.putShort((short) 1);
        instancedIndexUploadBuffer.putShort((short) 2);
        instancedIndexUploadBuffer.putShort((short) 0);
        instancedIndexUploadBuffer.putShort((short) 2);
        instancedIndexUploadBuffer.putShort((short) 3);
        instancedIndexUploadBuffer.flip();
        graphics.device().writeBuffer(instancedIndexBuffer, instancedIndexUploadBuffer);
    }

    private boolean supportsIndexedSprites(GraphicsContext graphics) {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "vulkan".equals(provider) || "wgpu".equals(provider)
                || "webgl".equals(provider);
    }

    private boolean supportsInstancedSprites(GraphicsContext graphics) {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "vulkan".equals(provider) || "wgpu".equals(provider);
    }

    private boolean supportsIndexedInstancedSprites(GraphicsContext graphics) {
        String provider = graphics.providerId().value();
        return "gl".equals(provider) || "vulkan".equals(provider) || "wgpu".equals(provider);
    }

    private boolean supportsWhitePipeline(GraphicsContext graphics) {
        return !"vulkan".equals(graphics.providerId().value());
    }

    private void ensureUploadBuffer(int byteCount) {
        if (uploadBuffer != null && uploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = uploadBuffer != null ? uploadBuffer.capacity() : BYTES_PER_VERTEX;
        while (next < byteCount) {
            next *= 2;
        }
        uploadBuffer = newUploadBuffer(next);
        uploadFloats = uploadBuffer.asFloatBuffer();
    }

    private void ensureInstancedQuadUploadBuffer(int byteCount) {
        if (instancedQuadUploadBuffer != null && instancedQuadUploadBuffer.capacity() >= byteCount) {
            return;
        }
        instancedQuadUploadBuffer = newUploadBuffer(byteCount);
        instancedQuadUploadFloats = instancedQuadUploadBuffer.asFloatBuffer();
    }

    private void ensureInstanceUploadBuffer(int byteCount) {
        if (instanceUploadBuffer != null && instanceUploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = instanceUploadBuffer != null
                ? instanceUploadBuffer.capacity()
                : INSTANCE_BYTES_PER_SPRITE;
        while (next < byteCount) {
            next *= 2;
        }
        instanceUploadBuffer = newUploadBuffer(next);
        instanceUploadFloats = instanceUploadBuffer.asFloatBuffer();
    }

    private void ensureCompactInstanceUploadBuffer(int byteCount) {
        if (compactInstanceUploadBuffer != null && compactInstanceUploadBuffer.capacity() >= byteCount) {
            return;
        }
        int next = compactInstanceUploadBuffer != null
                ? compactInstanceUploadBuffer.capacity()
                : INSTANCED_CENTER_BYTES_PER_SPRITE;
        while (next < byteCount) {
            next *= 2;
        }
        compactInstanceUploadBuffer = newUploadBuffer(next);
        compactInstanceUploadFloats = compactInstanceUploadBuffer.asFloatBuffer();
    }

    private ByteBuffer newUploadBuffer(int byteCount) {
        ByteBuffer buffer = heapUploadBuffers ? ByteBuffer.allocate(byteCount) : ByteBuffer.allocateDirect(byteCount);
        return buffer.order(ByteOrder.nativeOrder());
    }

    private static boolean usesHeapUploadBuffers(GraphicsContext graphics) {
        return "psp".equals(graphics.providerId().value());
    }

    private float[] ensureFloatCapacity(float[] values, int required) {
        if (values.length >= required) {
            return values;
        }
        int next = values.length;
        while (next < required) {
            next *= 2;
        }
        float[] larger = new float[next];
        System.arraycopy(values, 0, larger, 0, values.length);
        return larger;
    }

    private void ensureDrawing() {
        ensureNotDisposed();
        if (!drawing) {
            throw new FdxException("SpriteBatch.begin must be called before drawing");
        }
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new FdxException("SpriteBatch is disposed");
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        disposeBuffers(vertexBuffers);
        vertexBuffers = null;
        if (indexBuffer != null) {
            indexBuffer.dispose();
            indexBuffer = null;
        }
        disposeBuffers(instancedQuadBuffers);
        instancedQuadBuffers = null;
        disposeBuffers(instanceBuffers);
        instanceBuffers = null;
        disposeBuffers(compactInstanceBuffers);
        compactInstanceBuffers = null;
        disposeBuffers(retiredBuffers);
        retiredBuffers = null;
        retiredBufferCount = 0;
        if (instancedIndexBuffer != null) {
            instancedIndexBuffer.dispose();
            instancedIndexBuffer = null;
        }
        if (compactInstancedPipeline != null) {
            compactInstancedPipeline.dispose();
        }
        if (compactInstancedShader != null) {
            compactInstancedShader.dispose();
        }
        if (instancedPipeline != null) {
            instancedPipeline.dispose();
        }
        if (instancedShader != null) {
            instancedShader.dispose();
        }
        if (whitePipeline != null) {
            whitePipeline.dispose();
        }
        if (whiteShader != null) {
            whiteShader.dispose();
        }
        pipeline.dispose();
        shader.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}

