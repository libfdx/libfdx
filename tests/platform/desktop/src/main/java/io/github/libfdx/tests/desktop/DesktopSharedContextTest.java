package io.github.libfdx.tests.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;
import io.github.libfdx.display.DisplayConfig;
import io.github.libfdx.graphics.Buffer;
import io.github.libfdx.graphics.BufferDescriptor;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.GraphicsConfig;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.StoreOp;
import io.github.libfdx.graphics.VertexAttribute;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.VertexLayout;
import io.github.libfdx.tests.graphics.FramebufferCapture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Validates real desktop resource sharing between two contexts of one provider.
 */
final class DesktopSharedContextTest extends ApplicationAdapter {
    static final String NAME = "shared-context";

    private static final int VERTEX_COUNT = 3;
    private static final int BYTES_PER_VERTEX = 6 * 4;
    private static final VertexLayout VERTEX_LAYOUT = VertexLayout.of(BYTES_PER_VERTEX,
            VertexAttribute.of(0, VertexFormat.FLOAT32X2, 0),
            VertexAttribute.of(1, VertexFormat.FLOAT32X4, 8));
    private static final float[] VERTICES = {
            0.0f, 0.78f, 1.0f, 0.22f, 0.18f, 1.0f,
            -0.78f, -0.68f, 0.18f, 0.92f, 0.38f, 1.0f,
            0.78f, -0.68f, 0.18f, 0.42f, 1.0f, 1.0f
    };
    private static final String SHADER_SOURCE = """
            struct VertexInput {
                @location(0) position : vec2f,
                @location(1) color : vec4f,
            };

            struct VertexOutput {
                @builtin(position) position : vec4f,
                @location(0) color : vec4f,
            };

            @vertex
            fn vertexMain(input : VertexInput) -> VertexOutput {
                var output : VertexOutput;
                output.position = vec4f(input.position, 0.0, 1.0);
                output.color = input.color;
                return output;
            }

            @fragment
            fn fragmentMain(input : VertexOutput) -> @location(0) vec4f {
                return input.color;
            }
            """;

    private final GraphicsAttachmentProvider provider;
    private final long exitAfterFrames;
    private final RenderPassDescriptor mainPassDescriptor = new RenderPassDescriptor()
            .label("shared context main pass")
            .colorLoadOp(LoadOp.clear(0.015f, 0.02f, 0.03f, 1.0f))
            .colorStoreOp(StoreOp.store());
    private final RenderPassDescriptor secondaryPassDescriptor = new RenderPassDescriptor()
            .label("shared context secondary pass")
            .colorLoadOp(LoadOp.clear(0.02f, 0.03f, 0.055f, 1.0f))
            .colorStoreOp(StoreOp.store());

    private Fdx fdx;
    private GraphicsContext main;
    private Display secondaryDisplay;
    private GraphicsAttachment secondary;
    private Buffer vertexBuffer;
    private ShaderModule shaderModule;
    private RenderPipeline pipeline;
    private String capturePath;
    private long captureFrame;
    private long renderedFrames;
    private boolean captured;

    DesktopSharedContextTest(GraphicsAttachmentProvider provider, long exitAfterFrames) {
        if (provider == null) {
            throw new FdxException("Shared-context test provider cannot be null");
        }
        this.provider = provider;
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        main = fdx.graphics().main();
        if (!fdx.displays().supportsMultiple() || !fdx.graphics().supportsMultiple()) {
            throw new FdxException("Desktop shared-context test requires multiple displays and graphics contexts");
        }
        secondaryDisplay = fdx.displays().create(new DisplayConfig()
                .title("libfdx Shared Context - " + provider.providerId())
                .size(480, 360)
                .visible(Boolean.parseBoolean(System.getProperty("libfdx.test.visible", "true")))
                .maximized(false)
                .vSync(Boolean.parseBoolean(System.getProperty("libfdx.test.vsync", "true"))));
        secondary = fdx.graphics().create(GraphicsConfig.provider(provider).display(secondaryDisplay));
        if (!main.providerId().equals(secondary.providerId())) {
            throw new FdxException("Shared-context provider does not match the main graphics provider");
        }
        if (main.surfaceFormat() != secondary.surfaceFormat()) {
            throw new FdxException("Shared-context surfaces use incompatible color formats");
        }

        vertexBuffer = main.device().createBuffer(BufferDescriptor.staticVertex(
                "shared context triangle", VERTEX_COUNT * BYTES_PER_VERTEX));
        ByteBuffer vertices = ByteBuffer.allocateDirect(VERTICES.length * 4).order(ByteOrder.nativeOrder());
        vertices.asFloatBuffer().put(VERTICES);
        main.device().writeBuffer(vertexBuffer, vertices);
        shaderModule = main.device().createShaderModule(ShaderModuleDescriptor.wgsl(
                "shared context shader", SHADER_SOURCE));
        pipeline = main.device().createRenderPipeline(RenderPipelineDescriptor
                .shader(shaderModule, main.surfaceFormat())
                .label("shared context pipeline")
                .vertexLayout(VERTEX_LAYOUT)
                .depthWriteEnabled(false));

        capturePath = System.getProperty("libfdx.test.capture", "").trim();
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "2"));
        System.out.println("[info] DesktopSharedContextTest created main-owned resources for provider "
                + main.providerId());
    }

    @Override
    public void render() {
        draw(main, mainPassDescriptor);

        secondary.processEvents();
        if (!secondary.beginFrame()) {
            throw new FdxException("Could not begin the secondary shared-context frame");
        }
        try {
            draw(secondary, secondaryPassDescriptor);
            renderedFrames++;
            if (!captured && capturePath.length() > 0 && renderedFrames >= captureFrame) {
                GraphicsFrame frame = secondary.currentFrame();
                ByteBuffer pixels = frame.frameBuffer().readPixelsRgba8();
                FramebufferCapture.writePpm(capturePath, frame.width(), frame.height(), pixels);
                captured = true;
                System.out.println("[info] DesktopSharedContextTest captured secondary framebuffer to "
                        + capturePath);
            }
        } catch (FdxException error) {
            throw error;
        } catch (Exception error) {
            throw new FdxException("Could not capture the secondary shared-context framebuffer", error);
        } finally {
            secondary.endFrame();
        }

        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            fdx.app().requestExit();
        }
    }

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
        if (secondary != null) {
            fdx.graphics().destroy(secondary);
            secondary = null;
        }
        if (secondaryDisplay != null) {
            fdx.displays().destroy(secondaryDisplay);
            secondaryDisplay = null;
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("DesktopSharedContextTest did not capture the secondary framebuffer to "
                    + capturePath);
        }
        System.out.println("[info] DesktopSharedContextTest rendered " + renderedFrames + " shared frames");
    }

    private void draw(GraphicsContext graphics, RenderPassDescriptor descriptor) {
        GraphicsFrame frame = graphics.currentFrame();
        descriptor.colorAttachment(frame.colorAttachment());
        RenderPass pass = frame.commandEncoder().beginRenderPass(descriptor);
        pass.setPipeline(pipeline);
        pass.setVertexBuffer(vertexBuffer);
        pass.draw(VERTEX_COUNT, 1, 0, 0);
        pass.end();
    }
}
