/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

// Adapted from libGDX Sprite, MathUtils and SpriteBatch for the libFDX CPU benchmark.
package io.github.libfdx.benchmark.graphics;

import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BlendComponent;
import io.github.libfdx.graphics.BlendFactor;
import io.github.libfdx.graphics.BlendOperation;
import io.github.libfdx.graphics.BlendState;
import io.github.libfdx.graphics.ColorTargetState;
import io.github.libfdx.graphics.ColorWriteMask;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Preserves the same non-instanced, indexed 20-float CPU workload across TeaVM C, jNative and GraalVM. */
final class CpuSpriteBatch {
    private static final String SOURCE = """
            struct Input { @location(0) position: vec2f, @location(1) color: vec4f, @location(2) uv: vec2f };
            struct Output { @builtin(position) position: vec4f, @location(0) color: vec4f, @location(1) uv: vec2f };
            struct Projection { matrix: mat4x4<f32> };
            @group(0) @binding(0) var spriteTexture: texture_2d<f32>;
            @group(0) @binding(1) var spriteSampler: sampler;
            @group(1) @binding(0) var<uniform> projection: Projection;
            @vertex fn vertexMain(input: Input) -> Output {
                var output: Output;
                output.position = projection.matrix * vec4f(input.position, 0.0, 1.0);
                output.color = vec4f(input.color.rgb, input.color.a * (255.0 / 254.0));
                output.uv = input.uv;
                return output;
            }
            @fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
                return input.color * textureSample(spriteTexture, spriteSampler, input.uv);
            }
            """;
    private final GraphicsContext graphics;
    private final float[] vertices;
    private final Buffer[] vertexBuffers;
    private final Buffer indexBuffer;
    private final ByteBuffer upload;
    private final FloatBuffer floats;
    private final ShaderModule shader;
    private final RenderPipeline pipeline;
    private final ShaderParameterBlock projection;
    private final ShaderParameterHandle matrixHandle;
    private final float[] matrix = new float[16];
    private final RenderPassDescriptor descriptor = new RenderPassDescriptor().label("libFDX CPU sprite benchmark");
    private Texture lastTexture;
    private RenderPass pass;
    private boolean drawing;
    private int idx;
    private int renderCalls;
    private int uploadedBytes;
    CpuSpriteFrameProfile frameProfile;

    CpuSpriteBatch(GraphicsContext graphics, int capacity, int spriteCount) {
        if (capacity < 1 || capacity > 8191) throw new IllegalArgumentException("Batch capacity must be 1..8191");
        this.graphics = graphics;
        vertices = new float[capacity * 20];
        upload = ByteBuffer.allocateDirect(vertices.length * 4).order(ByteOrder.nativeOrder());
        floats = upload.asFloatBuffer();
        // Separate storage per flush preserves queued draws on deferred providers too.
        vertexBuffers = new Buffer[(spriteCount + capacity - 1) / capacity];
        for (int i = 0; i < vertexBuffers.length; i++) {
            vertexBuffers[i] = graphics.device().createBuffer(BufferDescriptor.vertex("libFDX sprite vertices", upload.capacity()));
        }
        indexBuffer = graphics.device().createBuffer(BufferDescriptor.staticIndex("libFDX sprite indices", capacity * 12));
        ByteBuffer indices = ByteBuffer.allocateDirect(capacity * 12).order(ByteOrder.nativeOrder());
        for (int i = 0; i < capacity; i++) {
            int v = i * 4;
            indices.putShort((short)v).putShort((short)(v + 1)).putShort((short)(v + 2));
            indices.putShort((short)(v + 2)).putShort((short)(v + 3)).putShort((short)v);
        }
        indices.flip();
        graphics.device().writeBuffer(indexBuffer, indices);
        shader = graphics.device().createShaderModule(ShaderModuleDescriptor.wgsl("libFDX CPU sprite benchmark", SOURCE));
        ShaderParameterLayout layout = shader.reflection().requireBinding(1, 0).bufferLayout();
        projection = ShaderParameterBlock.allocate(layout);
        matrixHandle = layout.requireHandle("matrix");
        BlendComponent alphaBlend = BlendComponent.of(BlendFactor.SOURCE_ALPHA,
                BlendFactor.ONE_MINUS_SOURCE_ALPHA, BlendOperation.ADD);
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor.shader(shader, graphics.surfaceFormat())
                .colorTargets(ColorTargetState.of(graphics.surfaceFormat(), BlendState.of(alphaBlend, alphaBlend), ColorWriteMask.ALL))
                .vertexLayout(VertexLayout.of(20, VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
                        VertexAttribute.of(1, VertexFormat.UNORM8X4, 8), VertexAttribute.of(2, VertexFormat.FLOAT32X2, 12)))
                .sampledTextureCount(1).depthWriteEnabled(false));
    }

    void viewport(int width, int height) {
        matrix[0] = 2f / width;
        matrix[5] = 2f / height;
        matrix[10] = -1;
        matrix[12] = -1;
        matrix[13] = -1;
        matrix[15] = 1;
        projection.setFloatMatrix(matrixHandle, matrix, 0);
    }

    void begin() {
        if (drawing) throw new IllegalStateException("Batch already drawing");
        GraphicsFrame frame = graphics.currentFrame();
        pass = frame.commandEncoder().beginRenderPass(descriptor.colorAttachment(frame.colorAttachment())
                .colorLoadOp(LoadOp.clear(0.08f, 0.08f, 0.1f, 1)));
        drawing = true;
        renderCalls = uploadedBytes = 0;
    }

    void draw(Texture texture, float[] spriteVertices, int offset, int count) {
        if (!drawing) throw new IllegalStateException("CpuSpriteBatch.begin must be called before draw.");
        int verticesLength = vertices.length;
        int remainingVertices = verticesLength;
        if (texture != lastTexture) switchTexture(texture);
        else {
            remainingVertices -= idx;
            if (remainingVertices == 0) {
                flush();
                remainingVertices = verticesLength;
            }
        }
        int copyCount = Math.min(remainingVertices, count);
        System.arraycopy(spriteVertices, offset, vertices, idx, copyCount);
        idx += copyCount;
        count -= copyCount;
        while (count > 0) {
            offset += copyCount;
            flush();
            copyCount = Math.min(verticesLength, count);
            System.arraycopy(spriteVertices, offset, vertices, 0, copyCount);
            idx += copyCount;
            count -= copyCount;
        }
    }

    private void switchTexture(Texture texture) {
        flush();
        lastTexture = texture;
    }

    private void flush() {
        if (idx == 0) return;
        long mark = frameProfile == null ? 0 : System.nanoTime();
        floats.clear();
        floats.put(vertices, 0, idx);
        upload.position(0);
        upload.limit(idx * 4);
        Buffer vertexBuffer = vertexBuffers[renderCalls];
        if (frameProfile != null) {
            long now = System.nanoTime();
            frameProfile.add(CpuSpriteFrameProfile.STAGING, now - mark);
            mark = now;
        }
        graphics.device().writeBuffer(vertexBuffer, upload);
        if (frameProfile != null) {
            long now = System.nanoTime();
            frameProfile.add(CpuSpriteFrameProfile.UPLOAD, now - mark);
            mark = now;
        }
        pass.setPipeline(pipeline);
        pass.setParameterBlock(1, 0, projection);
        pass.setTexture(0, lastTexture);
        pass.setVertexBuffer(vertexBuffer);
        pass.setIndexBuffer(indexBuffer);
        pass.drawIndexed(idx / 20 * 6, 1, 0, 0, 0);
        if (frameProfile != null) frameProfile.add(CpuSpriteFrameProfile.SUBMIT, System.nanoTime() - mark);
        renderCalls++;
        uploadedBytes += idx * 4;
        idx = 0;
    }

    void end() {
        if (!drawing) throw new IllegalStateException("Batch is not drawing");
        flush();
        long mark = frameProfile == null ? 0 : System.nanoTime();
        pass.end();
        pass = null;
        lastTexture = null;
        drawing = false;
        if (frameProfile != null) frameProfile.add(CpuSpriteFrameProfile.END, System.nanoTime() - mark);
    }

    int renderCalls() { return renderCalls; }
    int uploadedBytes() { return uploadedBytes; }

    // Diagnostic-only access: repeat a full CPU fill without submitting to the GPU.
    // The diagnostic requires exactly one batch and checks its final cursor.
    void diagnosticReset() { idx = 0; }
    float[] diagnosticVertices() { return vertices; }
    int diagnosticCursor() { return idx; }
    void diagnosticAppend(float[] source) {
        System.arraycopy(source, 0, vertices, idx, 20);
        idx += 20;
    }

    void dispose() {
        for (Buffer vertexBuffer : vertexBuffers) vertexBuffer.dispose();
        indexBuffer.dispose();
        pipeline.dispose();
        shader.dispose();
    }
}
