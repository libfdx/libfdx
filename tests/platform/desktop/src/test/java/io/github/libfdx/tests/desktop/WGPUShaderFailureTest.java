package io.github.libfdx.tests.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipeline;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.shader.ShaderModule;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptors;
import io.github.libfdx.graphics.shader.target.ShaderArtifactStage;
import io.github.libfdx.graphics.shader.target.ShaderCompilerId;
import io.github.libfdx.graphics.shader.target.ShaderStageArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTarget;
import io.github.libfdx.graphics.shader.target.ShaderTargetArtifact;
import io.github.libfdx.graphics.shader.target.ShaderTargetVerification;
import io.github.libfdx.graphics.shader.target.ShaderTranslatedInterface;
import io.github.libfdx.graphics.wgpu.WGPUBackend;
import io.github.libfdx.graphics.wgpu.WGPUContext;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in native JNI/FFM regression; deliberately reaches wgpu-native validation rather than Tint. */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "libfdx.test.nativeWgpuShaderFailure", matches = "true")
@Timeout(30)
final class WGPUShaderFailureTest extends ApplicationAdapter {
    private static final int SIZE = 128;
    private static final String VALID_SHADER = """
            @vertex fn vertexMain(@builtin(vertex_index) index: u32) -> @builtin(position) vec4f {
                let vertices = array<vec2f, 3>(vec2f(-0.8, -0.8), vec2f(0.8, -0.8), vec2f(0.0, 0.8));
                return vec4f(vertices[index], 0.0, 1.0);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0.0, 1.0, 0.0, 1.0); }
            """;
    private Application application;
    private GraphicsContext graphics;
    private WGPUContext nativeContext;
    private ShaderModule module;
    private RenderPipeline pipeline;
    private int renderedFrames;

    @Test
    void invalidShaderAndPipelineDoNotPoisonLaterRendering() {
        WGPUProvider provider = new WGPUProvider();
        provider.configuration().backend(WGPUBackend.VULKAN).offscreenReadback(true).vSync(false);
        new DesktopApplicationBackend().start(new DesktopApplicationConfig()
                .title("WGPU shader failure isolation").size(SIZE, SIZE).visible(false).vSync(false)
                .graphics(provider), this);
        assertEquals(3, renderedFrames);
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        graphics = fdx.graphics().main();
        nativeContext = graphics.as();
        ShaderModule unexpected = null;
        RuntimeException shaderFailure = null;
        try {
            unexpected = graphics.device().createShaderModule(nativeDescriptor("intentional-invalid-wgsl", "this is invalid WGSL"));
        } catch (RuntimeException failure) {
            shaderFailure = failure;
        } finally {
            if (unexpected != null) unexpected.dispose();
        }
        if (shaderFailure == null) {
            try { nativeContext.processEvents(); }
            catch (RuntimeException delayed) { System.out.println("DELAYED_CONTEXT_FAILURE: " + delayed.getMessage()); }
        }
        assertNotNull(shaderFailure, "Native shader creation returned an invalid handle instead of failing this request");
        assertTrue(shaderFailure.getMessage().contains("wgpuDeviceCreateShaderModule"), shaderFailure::getMessage);
        System.out.println("INVALID_SHADER_REJECTED: " + shaderFailure.getMessage());
        nativeContext.processEvents();

        module = graphics.device().createShaderModule(nativeDescriptor("valid-after-invalid", VALID_SHADER));
        FdxException pipelineFailure = assertThrows(FdxException.class, () -> {
            RenderPipeline unexpectedPipeline = graphics.device().createRenderPipeline(
                    pipelineDescriptor().vertexEntryPoint("missingVertex"));
            unexpectedPipeline.dispose();
        });
        assertTrue(pipelineFailure.getMessage().contains("wgpuDeviceCreateRenderPipeline"), pipelineFailure::getMessage);
        System.out.println("INVALID_PIPELINE_REJECTED: " + pipelineFailure.getMessage());
        nativeContext.processEvents();
        pipeline = graphics.device().createRenderPipeline(pipelineDescriptor());
        System.out.println("VALID_PIPELINE_CREATED binding=" + System.getProperty("libfdx.test.wgpuBridge", "ffm")
                + " loader=wgpu-native backend=Vulkan");
    }

    private RenderPipelineDescriptor pipelineDescriptor() {
        return RenderPipelineDescriptor.shader(module, graphics.surfaceFormat()).label("valid-after-invalid")
                .depthTestEnabled(false).depthWriteEnabled(false);
    }

    private static ShaderModuleDescriptor nativeDescriptor(String label, String source) {
        // A provider-gated artifact deliberately bypasses Tint, so this regression exercises native errors.
        ShaderTarget target = ShaderTarget.WGPU_WGSL;
        ShaderTargetArtifact artifact = ShaderTargetArtifact.compiled(target.id(), target.format(), target.environment(),
                new ShaderStageArtifact[] { ShaderStageArtifact.text(ShaderArtifactStage.MODULE, "", target.format(), source) },
                ShaderTranslatedInterface.identity(null, null), ShaderCompilerId.of("test.wgpu-native-failure"), "1", "");
        artifact = artifact.withVerification(ShaderTargetVerification.providerPipeline(target.environment(), null, artifact.compileCacheKey()));
        return ShaderModuleDescriptors.descriptor(artifact, label, source);
    }

    @Override
    public void render() {
        RenderPass pass = graphics.currentFrame().commandEncoder().beginRenderPass(
                new RenderPassDescriptor().colorAttachment(graphics.currentFrame().colorAttachment())
                        .colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
        try { pass.setPipeline(pipeline); pass.draw(3, 1, 0, 0); }
        finally { pass.end(); }
        if (++renderedFrames == 3) {
            int width = graphics.currentFrame().width(), height = graphics.currentFrame().height();
            ByteBuffer pixels = nativeContext.readPixelsRgba8();
            int center = (height / 2 * width + width / 2) * 4;
            assertTrue((pixels.get(center + 1) & 255) > 240, "Triangle did not render after failures");
            assertTrue((pixels.get(center) & 255) < 10 && (pixels.get(center + 2) & 255) < 10);
            assertTrue((pixels.get(0) & 255) < 10 && (pixels.get(1) & 255) < 10 && (pixels.get(2) & 255) < 10,
                    "Background must remain black, not a full-frame green clear");
            saveFrame(pixels, width, height);
            nativeContext.processEvents();
            System.out.println("VALID_SHADER_RENDERED frames=3 pixels=PASS");
            application.requestExit();
        }
    }

    private static void saveFrame(ByteBuffer pixels, int width, int height) {
        try {
            Path output = Path.of(System.getProperty("libfdx.test.wgpuFailureOutput"));
            Files.createDirectories(output);
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
                int index = (y * width + x) * 4;
                image.setRGB(x, y, 0xff000000 | (pixels.get(index) & 255) << 16
                        | (pixels.get(index + 1) & 255) << 8 | (pixels.get(index + 2) & 255));
            }
            ImageIO.write(image, "png", output.resolve("valid-after-invalid.png").toFile());
        } catch (Exception failure) { throw new AssertionError("Could not save native frame", failure); }
    }

    @Override
    public void dispose() {
        if (pipeline != null) pipeline.dispose();
        if (module != null) module.dispose();
    }
}
