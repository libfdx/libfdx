package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.ShaderModule;
import io.github.libfdx.graphics.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Runs a procedural WGSL shader scene test scenario.
 *
 * @author xpenatan
 */
public final class ShaderSceneTest extends ApplicationAdapter {
    private static final int FLOATS_PER_VERTEX = 4;
    private static final int BYTES_PER_VERTEX = FLOATS_PER_VERTEX * 4;
    private static final int VERTEX_COUNT = 6;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X2, 8));
    private static final float[] QUAD_VERTICES = {
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, -1.0f, 1.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, -1.0f, 0.0f, 0.0f,
            1.0f, 1.0f, 1.0f, 1.0f,
            -1.0f, 1.0f, 0.0f, 1.0f
    };
    private static final LoadOp CLEAR = LoadOp.clear(0.02f, 0.025f, 0.035f, 1.0f);
    private static final StoreOp STORE = StoreOp.store();
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2<f32>,
                @location(1) uv : vec2<f32>,
            };

            struct VertexOutput {
                @builtin(position) position : vec4<f32>,
                @location(0) uv : vec2<f32>,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4<f32>(input.position, 0.0, 1.0);
                output.uv = input.uv;
                return output;
            }

            fn stripe(value : f32, scale : f32) -> f32 {
                return 0.5 + 0.5 * sin(value * scale);
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4<f32> {
                let p = input.uv * vec2<f32>(2.0, 2.0) - vec2<f32>(1.0, 1.0);
                let radius = length(p);
                let angle = atan2(p.y, p.x);
                let ring = stripe(radius - angle * 0.08, 38.0);
                let wave = stripe(p.x * 0.82 + p.y * 1.27 + radius, 17.0);
                let glow = max(0.0, 1.0 - radius);
                let red = 0.10 + 0.85 * ring * glow + 0.12 * wave;
                let green = 0.18 + 0.55 * stripe(angle, 5.0) * glow + 0.18 * wave;
                let blue = 0.28 + 0.62 * wave + 0.22 * (1.0 - ring);
                return vec4<f32>(red, green, blue, 1.0);
            }
            """;

    private final long exitAfterFrames;
    private final RenderPassDescriptor passDescriptor = new RenderPassDescriptor()
            .label("shader scene pass")
            .colorLoadOp(CLEAR)
            .colorStoreOp(STORE);
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private Buffer vertexBuffer;
    private String capturePath;
    private long captureFrame;
    private boolean created;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a procedural shader scene test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public ShaderSceneTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "ShaderSceneTest");
        vertexBuffer = graphics.device().createBuffer(BufferDescriptor.staticVertex("shader scene quad vertices",
                VERTEX_COUNT * BYTES_PER_VERTEX));
        graphics.device().writeBuffer(vertexBuffer, vertexData());
        shaderModule = graphics.device().createShaderModule(ShaderModuleDescriptor
                .wgsl("procedural shader scene", SHADER_SOURCE));
        pipeline = graphics.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, graphics.surfaceFormat())
                .label("procedural shader scene")
                .vertexLayout(VERTEX_LAYOUT)
                .depthWriteEnabled(false));
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));
        created = true;
        logger.info("ShaderSceneTest created WGSL procedural shader scene for provider "
                + graphics.providerId().value());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        GraphicsFrame frame = graphics.currentFrame();
        passDescriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(passDescriptor);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();

        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
        }
        renderedFrames++;
        fpsLogger.frame(application.deltaTime(), renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (pipeline != null) {
            pipeline.dispose();
            pipeline = null;
        }
        if (shaderModule != null) {
            shaderModule.dispose();
            shaderModule = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.dispose();
            vertexBuffer = null;
        }
        if (!created) {
            throw new FdxException("ShaderSceneTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("ShaderSceneTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("ShaderSceneTest did not capture framebuffer to " + capturePath);
        }
        logger.info("ShaderSceneTest rendered " + renderedFrames + " frames");
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            FramebufferCapture.writePpm(path, framebufferWidth(), framebufferHeight(), pixels);
            logger.info("ShaderSceneTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture ShaderSceneTest framebuffer", e);
        }
    }

    private ByteBuffer vertexData() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4).order(ByteOrder.nativeOrder());
        for (int i = 0; i < QUAD_VERTICES.length; i++) {
            buffer.putFloat(QUAD_VERTICES[i]);
        }
        buffer.flip();
        return buffer;
    }
}
