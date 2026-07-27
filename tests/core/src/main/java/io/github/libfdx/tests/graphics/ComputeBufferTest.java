package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.BufferUsage;
import io.github.libfdx.graphics.ComputePass;
import io.github.libfdx.graphics.ComputePassDescriptor;
import io.github.libfdx.graphics.ComputePipeline;
import io.github.libfdx.graphics.ComputePipelineDescriptor;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.runtime.ShaderResourceSet;
import io.github.libfdx.graphics.StoreOp;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Executes and verifies the provider-neutral compute-buffer contract.
 */
public final class ComputeBufferTest extends GraphicsParityTest {
    private static final int VALUE_COUNT = 4;
    private static final int BYTE_COUNT = VALUE_COUNT * 4;
    private static final int[] EXPECTED = { 3, 5, 7, 9 };
    private static final String SOURCE = """
            struct Data {
                values : array<u32>,
            };

            @group(0) @binding(0)
            var<storage, read_write> data : Data;

            @compute @workgroup_size(1)
            fn computeMain(@builtin(global_invocation_id) id : vec3<u32>) {
                if (id.x < 4u) {
                    data.values[id.x] = data.values[id.x] * 2u + 1u;
                }
            }
            """;

    private ShaderModule shaderModule;
    private ComputePipeline pipeline;
    private Buffer storage;
    private Buffer readback;
    private ShaderResourceSet resources;
    private boolean commandsRecorded;
    private boolean verified;

    public ComputeBufferTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ComputeBufferTest");
        graphics.device().capabilities().require(GraphicsFeature.COMPUTE);
        shaderModule = graphics.device().createShaderModule(
                ShaderModuleDescriptor.wgsl("compute buffer shader", SOURCE));
        ShaderResourceLayout layout = ShaderResourceLayout.compute(
                shaderModule.reflection(), "computeMain");
        pipeline = graphics.device().createComputePipeline(
                ComputePipelineDescriptor.shader(shaderModule)
                        .label("compute buffer pipeline")
                        .entryPoint("computeMain")
                        .resourceLayout(layout));
        storage = graphics.device().createBuffer(new BufferDescriptor()
                .label("compute storage")
                .size(BYTE_COUNT)
                .usage(BufferUsage.STORAGE));
        readback = graphics.device().createBuffer(new BufferDescriptor()
                .label("compute readback")
                .size(BYTE_COUNT)
                .usage(BufferUsage.READBACK));
        ByteBuffer initial = ByteBuffer.allocateDirect(BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        for (int value = 1; value <= VALUE_COUNT; value++) {
            initial.putInt(value);
        }
        initial.flip();
        graphics.device().writeBuffer(storage, initial);
        resources = ShaderResourceSet.builder(layout, 0)
                .buffer(0, storage)
                .build();
        markCreated();
    }

    @Override
    public void render() {
        if (commandsRecorded && !verified) {
            verifyReadback();
        }
        GraphicsFrame frame = graphics.currentFrame();
        if (!commandsRecorded) {
            ComputePass pass = frame.commandEncoder().beginComputePass(
                    ComputePassDescriptor.create("compute buffer pass"));
            pass.setPipeline(pipeline);
            pass.setResourceSet(resources);
            pass.dispatch(VALUE_COUNT);
            pass.end();
            frame.commandEncoder().copyBufferToBuffer(
                    storage, 0, readback, 0, BYTE_COUNT);
            commandsRecorded = true;
        }
        RenderPass clear = frame.commandEncoder().beginRenderPass(
                RenderPassDescriptor.color(frame.colorAttachment(),
                        verified ? LoadOp.clear(0.04f, 0.45f, 0.12f, 1.0f)
                                : LoadOp.clear(0.03f, 0.08f, 0.18f, 1.0f),
                        StoreOp.store())
                        .label("compute result status"));
        clear.end();
        finishFrame();
        if (verified && exitAfterFrames == 0L) {
            application.requestExit();
        }
    }

    private void verifyReadback() {
        ByteBuffer result = graphics.device().readBuffer(readback, 0, BYTE_COUNT)
                .order(ByteOrder.nativeOrder());
        for (int i = 0; i < EXPECTED.length; i++) {
            int actual = result.getInt(i * 4);
            if (actual != EXPECTED[i]) {
                throw new FdxException("Compute result mismatch at " + i
                        + ": expected " + EXPECTED[i] + ", got " + actual);
            }
        }
        verified = true;
        logger.info("ComputeBufferTest verified [3, 5, 7, 9]");
    }

    @Override
    public void dispose() {
        dispose(readback);
        dispose(storage);
        dispose(pipeline);
        dispose(shaderModule);
        if (!verified) {
            throw new FdxException("ComputeBufferTest exited before GPU readback was verified");
        }
        verifyDisposed();
    }

}
