package io.github.libfdx.imgui;

import com.github.xpenatan.jParser.api.NativeObject;
import com.github.xpenatan.jparser.runtime.helper.NativeUtils;
import imgui.ImDrawCmd;
import imgui.ImDrawData;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImGuiDrawCallbacks;
import imgui.ImGuiIO;
import imgui.ImTemp;
import imgui.ImTextureData;
import imgui.ImVec2;
import imgui.ImVec4;
import imgui.ImVectorImDrawCmd;
import imgui.ImVectorImDrawIdx;
import imgui.ImVectorImDrawListPtr;
import imgui.ImVectorImDrawVert;
import imgui.ImVectorImTextureDataPtr;
import imgui.enums.ImGuiBackendFlags;
import imgui.enums.ImGuiDrawCallbackType;
import imgui.enums.ImTextureFormat;
import imgui.enums.ImTextureStatus;
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
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shader.ShaderBundle;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.reflection.ShaderAttribute;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderBindingType;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FdxImGuiGraphicsRenderer implements FdxImGuiRenderer, FdxImGuiViewportRendererFactory {
    private static final int IM_DRAW_VERT_SIZE = 20;
    private static final int IM_DRAW_IDX_SIZE = 2;
    private static final int DEFAULT_VERTEX_BYTES = 8192 * IM_DRAW_VERT_SIZE;
    private static final int DEFAULT_INDEX_BYTES = 16384 * IM_DRAW_IDX_SIZE;
    private static final VertexLayout IMGUI_VERTEX_LAYOUT = VertexLayout.of(
            IM_DRAW_VERT_SIZE,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8),
            VertexAttribute.of(2, VertexFormat.UNORM8X4, 16));
    private static final String IMGUI_WGSL_PREFIX = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) texCoord : vec2f,
                @location(2) color : vec4f,
            };
            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) texCoord : vec2f,
                @location(1) color : vec4f,
            };
            @group(0) @binding(0) var u_texture : texture_2d<f32>;
            @group(0) @binding(1) var u_sampler : sampler;
            fn sampleTexel(texel : vec2i, dimensions : vec2i) -> vec4f {
                let clamped = clamp(texel, vec2i(0), dimensions - vec2i(1));
                let texCoord = (vec2f(clamped) + vec2f(0.5)) / vec2f(dimensions);
                return textureSampleLevel(u_texture, u_sampler, texCoord, 0.0);
            }
            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.texCoord = input.texCoord;
                output.color = input.color;
                return output;
            }
            """;
    private static final String IMGUI_WGSL_LINEAR = IMGUI_WGSL_PREFIX + """
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let dimensions = vec2i(textureDimensions(u_texture));
                let position = input.texCoord * vec2f(dimensions) - vec2f(0.5);
                let base = vec2i(floor(position));
                let weight = fract(position);
                let c00 = sampleTexel(base, dimensions);
                let c10 = sampleTexel(base + vec2i(1, 0), dimensions);
                let c01 = sampleTexel(base + vec2i(0, 1), dimensions);
                let c11 = sampleTexel(base + vec2i(1, 1), dimensions);
                return mix(mix(c00, c10, weight.x), mix(c01, c11, weight.x), weight.y) * input.color;
            }
            """;
    private static final String IMGUI_WGSL_NEAREST = IMGUI_WGSL_PREFIX + """
            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                let dimensions = vec2i(textureDimensions(u_texture));
                let texel = clamp(vec2i(floor(input.texCoord * vec2f(dimensions))), vec2i(0), dimensions - vec2i(1));
                return sampleTexel(texel, dimensions) * input.color;
            }
            """;
    private static final ShaderBundle IMGUI_LINEAR_SHADER = createShaderBundle(
            "imgui-linear",
            IMGUI_WGSL_LINEAR);
    private static final ShaderBundle IMGUI_NEAREST_SHADER = createShaderBundle(
            "imgui-nearest",
            IMGUI_WGSL_NEAREST);

    private static ShaderBundle createShaderBundle(String label, String wgsl) {
        return ShaderBundle.builder(label)
            .profile(ShaderProfile.PORTABLE_WEBGPU)
            .wgsl(wgsl)
            .reflection(ShaderReflection.of(
                    new ShaderBinding[] {
                            ShaderBinding.of(0, 0, "u_texture", ShaderBindingType.TEXTURE),
                            ShaderBinding.of(0, 1, "u_sampler", ShaderBindingType.SAMPLER)
                    },
                    new ShaderAttribute[] {
                            ShaderAttribute.of(0, "position", VertexFormat.FLOAT32X2),
                            ShaderAttribute.of(1, "texCoord", VertexFormat.FLOAT32X2),
                            ShaderAttribute.of(2, "color", VertexFormat.UNORM8X4)
                    }))
            .build();
    }

    private final String rendererLabel;
    private final boolean ownsPlatformCallbacks;
    private GraphicsContext graphics;
    private FdxImGuiTextureRegistry textures;
    private RenderPassDescriptor renderPassDescriptor;
    private ShaderModule linearShader;
    private ShaderModule nearestShader;
    private RenderPipeline linearPipeline;
    private RenderPipeline nearestPipeline;
    private Buffer vertexBuffer;
    private Buffer indexBuffer;
    private ByteBuffer vertexBytes;
    private ByteBuffer indexBytes;
    private ByteBuffer textureUploadBytes;
    private ByteBuffer alphaSourceBytes;
    private final int[] clipRect = new int[4];
    private boolean supportsBaseVertex;
    private boolean initialized;
    private boolean disposed;

    public FdxImGuiGraphicsRenderer() {
        this("imgui", true);
    }

    private FdxImGuiGraphicsRenderer(String rendererLabel, boolean ownsPlatformCallbacks) {
        this.rendererLabel = rendererLabel != null ? rendererLabel : "imgui";
        this.ownsPlatformCallbacks = ownsPlatformCallbacks;
    }

    @Override
    public final void initialize(FdxImGuiRendererContext context) {
        if (context == null) {
            throw new FdxException("FdxImGuiRendererContext cannot be null");
        }
        graphics = context.graphics();
        textures = context.textures();
        String providerId = graphics.providerId().value();
        if (!supportsProvider(providerId)) {
            throw new FdxException(rendererLabel + " does not support graphics provider: " + providerId);
        }
        supportsBaseVertex = supportsBaseVertex(providerId);
        linearShader = graphics.device().createShaderModule(IMGUI_LINEAR_SHADER.descriptorForProvider(providerId));
        nearestShader = graphics.device().createShaderModule(IMGUI_NEAREST_SHADER.descriptorForProvider(providerId));
        linearPipeline = createPipeline(linearShader, rendererLabel + " linear");
        nearestPipeline = createPipeline(nearestShader, rendererLabel + " nearest");
        renderPassDescriptor = new RenderPassDescriptor().label(rendererLabel + " pass");
        setBackendFlags();
        ensureBuffers(DEFAULT_VERTEX_BYTES, DEFAULT_INDEX_BYTES);
        if (ownsPlatformCallbacks) {
            ImGuiDrawCallbacks.InstallStandardCallbacks(ImGui.GetPlatformIO());
        }
        initialized = true;
    }

    private RenderPipeline createPipeline(ShaderModule shader, String label) {
        return graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shader, graphics.surfaceFormat())
                .label(label)
                .primitiveTopology(PrimitiveTopology.TRIANGLE_LIST)
                .vertexEntryPoint("vertexMain")
                .fragmentEntryPoint("fragmentMain")
                .vertexLayout(IMGUI_VERTEX_LAYOUT)
                .sampledTextureCount(1)
                .depthTestEnabled(false)
                .depthWriteEnabled(false));
    }

    @Override
    public final void render(ImDrawData drawData) {
        ensureReady();
        if (drawData == null || drawData.native_isNULL()) {
            return;
        }
        updateTextures(drawData.get_Textures());
        if (!drawData.get_Valid() || drawData.get_CmdLists().size() <= 0 || drawData.get_TotalVtxCount() <= 0
                || drawData.get_TotalIdxCount() <= 0) {
            return;
        }
        ImVec2 displaySize = drawData.get_DisplaySize();
        ImVec2 framebufferScale = drawData.get_FramebufferScale();
        int framebufferWidth = (int) (displaySize.get_x() * framebufferScale.get_x());
        int framebufferHeight = (int) (displaySize.get_y() * framebufferScale.get_y());
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            return;
        }
        ensureBuffers(drawData.get_TotalVtxCount() * IM_DRAW_VERT_SIZE,
                drawData.get_TotalIdxCount() * IM_DRAW_IDX_SIZE);
        uploadBuffers(drawData);

        GraphicsFrame frame = graphics.currentFrame();
        RenderPass pass = frame.commandEncoder().beginRenderPass(renderPassDescriptor
                .colorAttachment(frame.colorAttachment())
                .colorLoadOp(LoadOp.load())
                .colorStoreOp(StoreOp.store()));
        setupRenderState(pass, linearPipeline, framebufferWidth, framebufferHeight);
        renderDrawLists(drawData, pass, framebufferWidth, framebufferHeight);
        pass.end();
    }

    private static boolean supportsProvider(String providerId) {
        return "gl".equals(providerId)
                || "gles".equals(providerId)
                || "webgl".equals(providerId)
                || "wgpu".equals(providerId)
                || "vulkan".equals(providerId)
                || "d3d12".equals(providerId);
    }

    private static boolean supportsBaseVertex(String providerId) {
        return "gl".equals(providerId)
                || "wgpu".equals(providerId)
                || "vulkan".equals(providerId)
                || "d3d12".equals(providerId);
    }

    @Override
    public final FdxImGuiRenderer createViewportRenderer() {
        return new FdxImGuiGraphicsRenderer(rendererLabel, false);
    }

    private void renderDrawLists(ImDrawData drawData, RenderPass pass, int framebufferWidth, int framebufferHeight) {
        ImVec2 displayPos = drawData.get_DisplayPos();
        ImVec2 clipScale = drawData.get_FramebufferScale();
        ImVectorImDrawListPtr commandLists = drawData.get_CmdLists();
        int vertexStart = 0;
        int indexStart = 0;
        for (int listIndex = 0; listIndex < commandLists.size(); listIndex++) {
            ImDrawList commandList = commandLists.getData(listIndex);
            ImVectorImDrawCmd commands = commandList.get_CmdBuffer();
            for (int commandIndex = 0; commandIndex < commands.size(); commandIndex++) {
                ImDrawCmd command = commands.getData(commandIndex);
                ImGuiDrawCallbackType callbackType = ImGuiDrawCallbacks.GetType(command);
                if (callbackType != ImGuiDrawCallbackType.None) {
                    if (callbackType == ImGuiDrawCallbackType.ResetRenderState) {
                        setupRenderState(pass, linearPipeline, framebufferWidth, framebufferHeight);
                    }
                    else if (callbackType == ImGuiDrawCallbackType.SetSamplerLinear) {
                        setupPipelineState(pass, linearPipeline);
                    }
                    else if (callbackType == ImGuiDrawCallbackType.SetSamplerNearest) {
                        setupPipelineState(pass, nearestPipeline);
                    }
                    else if (callbackType == ImGuiDrawCallbackType.User) {
                        ImGuiDrawCallbacks.InvokeUserCallback(commandList, command);
                    }
                    continue;
                }
                if (command.get_ElemCount() <= 0) {
                    continue;
                }
                setClipRect(command.get_ClipRect(), displayPos, clipScale, framebufferWidth, framebufferHeight);
                if (clipRect[2] <= 0 || clipRect[3] <= 0) {
                    continue;
                }
                pass.setScissor(clipRect[0], clipRect[1], clipRect[2], clipRect[3]);
                long textureId = command.GetTexID().Get();
                pass.setTexture(0, textures.texture(textureId));
                int baseVertex = supportsBaseVertex ? vertexStart + command.get_VtxOffset() : 0;
                if (!supportsBaseVertex && command.get_VtxOffset() != 0) {
                    throw new FdxException(rendererLabel + " provider does not support ImGui vertex offsets");
                }
                pass.drawIndexed(command.get_ElemCount(), 1, indexStart + command.get_IdxOffset(), baseVertex, 0);
            }
            vertexStart += commandList.get_VtxBuffer().size();
            indexStart += commandList.get_IdxBuffer().size();
        }
    }

    private void setupRenderState(RenderPass pass, RenderPipeline pipeline, int framebufferWidth,
            int framebufferHeight) {
        setupPipelineState(pass, pipeline);
        pass.setViewport(0, 0, framebufferWidth, framebufferHeight);
    }

    private void setupPipelineState(RenderPass pass, RenderPipeline pipeline) {
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(vertexBuffer);
        pass.setIndexBuffer(indexBuffer);
    }

    private void setClipRect(ImVec4 clip, ImVec2 displayPos, ImVec2 clipScale, int framebufferWidth,
            int framebufferHeight) {
        int clipX = Math.max(0, (int) ((clip.get_x() - displayPos.get_x()) * clipScale.get_x()));
        int clipY = Math.max(0, (int) ((clip.get_y() - displayPos.get_y()) * clipScale.get_y()));
        int clipZ = Math.min(framebufferWidth, (int) ((clip.get_z() - displayPos.get_x()) * clipScale.get_x()));
        int clipW = Math.min(framebufferHeight, (int) ((clip.get_w() - displayPos.get_y()) * clipScale.get_y()));
        int width = clipZ - clipX;
        int height = clipW - clipY;
        clipY = framebufferHeight - clipW;
        clipRect[0] = clipX;
        clipRect[1] = clipY;
        clipRect[2] = width;
        clipRect[3] = height;
    }

    private void uploadBuffers(ImDrawData drawData) {
        ImVec2 displayPos = drawData.get_DisplayPos();
        ImVec2 displaySize = drawData.get_DisplaySize();
        int vertexSize = drawData.get_TotalVtxCount() * IM_DRAW_VERT_SIZE;
        int indexSize = drawData.get_TotalIdxCount() * IM_DRAW_IDX_SIZE;
        vertexBytes.clear();
        indexBytes.clear();
        ImVectorImDrawListPtr commandLists = drawData.get_CmdLists();
        int vertexOffset = 0;
        int indexOffset = 0;
        int vertexStart = 0;
        for (int listIndex = 0; listIndex < commandLists.size(); listIndex++) {
            ImDrawList commandList = commandLists.getData(listIndex);
            ImVectorImDrawVert vertices = commandList.get_VtxBuffer();
            ImVectorImDrawIdx indices = commandList.get_IdxBuffer();
            int listVertexSize = vertices.size() * IM_DRAW_VERT_SIZE;
            int listIndexSize = indices.size() * IM_DRAW_IDX_SIZE;
            NativeUtils.copyToByteBuffer(vertices.get_Data(), vertexBytes, vertexOffset, listVertexSize);
            NativeUtils.copyToByteBuffer(indices.get_Data(), indexBytes, indexOffset, listIndexSize);
            transformVertexPositions(vertexBytes, vertexOffset, vertices.size(), displayPos, displaySize);
            if (!supportsBaseVertex && vertexStart != 0) {
                adjustIndices(indexBytes, indexOffset, indices.size(), vertexStart);
            }
            vertexOffset += listVertexSize;
            indexOffset += listIndexSize;
            vertexStart += vertices.size();
        }
        vertexBytes.position(0).limit(vertexSize);
        indexBytes.position(0).limit(indexSize);
        graphics.device().writeBuffer(vertexBuffer, vertexBytes);
        graphics.device().writeBuffer(indexBuffer, indexBytes);
    }

    private void transformVertexPositions(ByteBuffer bytes, int byteOffset, int vertexCount, ImVec2 displayPos,
            ImVec2 displaySize) {
        float scaleX = displaySize.get_x() != 0.0f ? 2.0f / displaySize.get_x() : 0.0f;
        float scaleY = displaySize.get_y() != 0.0f ? 2.0f / displaySize.get_y() : 0.0f;
        float offsetX = displayPos.get_x();
        float offsetY = displayPos.get_y();
        for (int i = 0; i < vertexCount; i++) {
            int offset = byteOffset + i * IM_DRAW_VERT_SIZE;
            float x = bytes.getFloat(offset);
            float y = bytes.getFloat(offset + 4);
            bytes.putFloat(offset, (x - offsetX) * scaleX - 1.0f);
            bytes.putFloat(offset + 4, 1.0f - (y - offsetY) * scaleY);
        }
    }

    private void adjustIndices(ByteBuffer bytes, int byteOffset, int indexCount, int vertexOffset) {
        for (int i = 0; i < indexCount; i++) {
            int offset = byteOffset + i * IM_DRAW_IDX_SIZE;
            int index = bytes.getShort(offset) & 0xffff;
            int adjusted = index + vertexOffset;
            if (adjusted > 0xffff) {
                throw new FdxException(rendererLabel
                        + " cannot draw more than 65535 vertices without base-vertex support");
            }
            bytes.putShort(offset, (short) adjusted);
        }
    }

    private void updateTextures(ImVectorImTextureDataPtr textureData) {
        if (textureData == null || textureData.native_isNULL()) {
            return;
        }
        for (int i = 0; i < textureData.size(); i++) {
            ImTextureData texture = textureData.getData(i);
            ImTextureStatus status = texture.get_Status();
            if (status == ImTextureStatus.WantCreate) {
                createTexture(texture);
            }
            else if (status == ImTextureStatus.WantUpdates) {
                updateTexture(texture);
            }
            else if (status == ImTextureStatus.WantDestroy && texture.get_UnusedFrames() > 0) {
                destroyTexture(texture);
            }
        }
    }

    private void createTexture(ImTextureData textureData) {
        Texture texture = graphics.device().createTexture(TextureDescriptor
                .rgba8("imgui texture " + textureData.get_UniqueID(), textureData.get_Width(),
                        textureData.get_Height()));
        ByteBuffer pixels = texturePixels(textureData);
        graphics.device().writeTexture(texture, pixels);
        long textureId = textures.registerOwned(texture);
        textureData.SetTexID(ImTemp.ImTextureIDRef_1(textureId));
        textureData.SetStatus(ImTextureStatus.OK);
    }

    private void updateTexture(ImTextureData textureData) {
        long textureId = textureData.GetTexID().Get();
        Texture texture = textures.texture(textureId);
        ByteBuffer pixels = texturePixels(textureData);
        graphics.device().writeTexture(texture, pixels);
        textureData.SetStatus(ImTextureStatus.OK);
    }

    private void destroyTexture(ImTextureData textureData) {
        long textureId = textureData.GetTexID().Get();
        if (textureId != 0L) {
            textures.removeAndDisposeOwned(textureId);
        }
        textureData.SetTexID(ImTemp.ImTextureIDRef_1(0L));
        textureData.SetStatus(ImTextureStatus.Destroyed);
    }

    private ByteBuffer texturePixels(ImTextureData textureData) {
        if (textureData.get_Format() == ImTextureFormat.RGBA32) {
            int size = textureData.GetSizeInBytes();
            textureUploadBytes = ensureBuffer(textureUploadBytes, size);
            textureUploadBytes.clear();
            NativeUtils.copyToByteBuffer(textureData.GetPixels(), textureUploadBytes, 0, size);
            textureUploadBytes.position(0).limit(size);
            return textureUploadBytes;
        }
        if (textureData.get_Format() == ImTextureFormat.Alpha8) {
            return alpha8ToRgba(textureData.GetPixels(), textureData.GetSizeInBytes());
        }
        throw new FdxException("Unsupported ImGui texture format: " + textureData.get_Format());
    }

    private ByteBuffer alpha8ToRgba(NativeObject pixels, int size) {
        alphaSourceBytes = ensureBuffer(alphaSourceBytes, size);
        alphaSourceBytes.clear();
        NativeUtils.copyToByteBuffer(pixels, alphaSourceBytes, 0, size);
        alphaSourceBytes.position(0).limit(size);
        textureUploadBytes = ensureBuffer(textureUploadBytes, size * 4);
        textureUploadBytes.clear();
        for (int i = 0; i < size; i++) {
            byte alpha = alphaSourceBytes.get(i);
            textureUploadBytes.put((byte) 0xff);
            textureUploadBytes.put((byte) 0xff);
            textureUploadBytes.put((byte) 0xff);
            textureUploadBytes.put(alpha);
        }
        textureUploadBytes.flip();
        return textureUploadBytes;
    }

    private void setBackendFlags() {
        ImGuiIO io = ImGui.GetIO();
        int flags = io.get_BackendFlags().getValue() | ImGuiBackendFlags.RendererHasTextures.getValue();
        if (supportsBaseVertex) {
            flags |= ImGuiBackendFlags.RendererHasVtxOffset.getValue();
        }
        io.set_BackendFlags(ImGuiBackendFlags.CUSTOM.setValue(flags));
    }

    private void ensureBuffers(int vertexByteCount, int indexByteCount) {
        int actualVertexBytes = Math.max(vertexByteCount, DEFAULT_VERTEX_BYTES);
        int actualIndexBytes = Math.max(indexByteCount, DEFAULT_INDEX_BYTES);
        if (vertexBuffer == null || vertexBuffer.size() < actualVertexBytes) {
            if (vertexBuffer != null && !vertexBuffer.isDisposed()) {
                vertexBuffer.dispose();
            }
            vertexBuffer = graphics.device().createBuffer(BufferDescriptor.vertex(rendererLabel + " vertices",
                    actualVertexBytes));
            vertexBytes = null;
        }
        if (indexBuffer == null || indexBuffer.size() < actualIndexBytes) {
            if (indexBuffer != null && !indexBuffer.isDisposed()) {
                indexBuffer.dispose();
            }
            indexBuffer = graphics.device().createBuffer(BufferDescriptor.index(rendererLabel + " indices",
                    actualIndexBytes));
            indexBytes = null;
        }
        vertexBytes = ensureBuffer(vertexBytes, actualVertexBytes);
        indexBytes = ensureBuffer(indexBytes, actualIndexBytes);
    }

    private ByteBuffer ensureBuffer(ByteBuffer buffer, int byteCount) {
        if (buffer != null && buffer.capacity() >= byteCount) {
            return buffer.clear().order(ByteOrder.LITTLE_ENDIAN);
        }
        return ByteBuffer.allocateDirect(byteCount).order(ByteOrder.LITTLE_ENDIAN);
    }

    private void ensureReady() {
        if (disposed) {
            throw new FdxException(rendererLabel + " has been disposed");
        }
        if (!initialized) {
            throw new FdxException(rendererLabel + " has not been initialized");
        }
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (ownsPlatformCallbacks && initialized) {
            ImGuiDrawCallbacks.ClearStandardCallbacks(ImGui.GetPlatformIO());
        }
        if (vertexBuffer != null && !vertexBuffer.isDisposed()) {
            vertexBuffer.dispose();
        }
        if (indexBuffer != null && !indexBuffer.isDisposed()) {
            indexBuffer.dispose();
        }
        if (linearPipeline != null && !linearPipeline.isDisposed()) {
            linearPipeline.dispose();
        }
        if (nearestPipeline != null && !nearestPipeline.isDisposed()) {
            nearestPipeline.dispose();
        }
        if (linearShader != null && !linearShader.isDisposed()) {
            linearShader.dispose();
        }
        if (nearestShader != null && !nearestShader.isDisposed()) {
            nearestShader.dispose();
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
