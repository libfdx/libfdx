package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPU;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Android device regression for the published JNI worker callbacks and native input ownership. */
public final class WGPUAndroidPreparationTest extends ApplicationAdapter {
    private static final String SHADER = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }
            """;
    private final AtomicInteger done = new AtomicInteger(), valid = new AtomicInteger(), rejected = new AtomicInteger();
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final RenderPassDescriptor pass = new RenderPassDescriptor().colorLoadOp(LoadOp.clear(.05f, .2f, .1f, 1));
    private GraphicsContext graphics;
    private Application application;
    private long deadline;
    private volatile boolean cancelled;

    @Override public void create(Fdx fdx) {
        graphics = fdx.graphics().main();
        application = fdx.app();
        WGPUContext context = graphics.as();
        require(context.configuration().backend() == WGPUBackend.VULKAN, "Audit requires explicit Vulkan");
        require(WGPU.isDawnBackend() == (context.configuration().loaderBackend() == WGPULoaderBackend.DAWN),
                "Loaded native implementation does not match the requested loader");
        deadline = System.nanoTime() + 20_000_000_000L;
        for (int i = 0; i < 2; i++) {
            boolean invalid = i != 0;
            context.resourceDomain().retainPreparation();
            Thread worker = new Thread(() -> {
                try {
                    for (int request = 0; request < 64 && !cancelled; request++) {
                        try {
                            WGPUShaderModuleHandle module = WGPUGraphicsDevice.createNativeShader(context.nativeDevice(),
                                    context.resourceDomain(), context.creationErrors(), ShaderModuleDescriptor.wgsl(
                                            "android-worker-" + invalid + "-" + request, invalid ? "invalid WGSL" : SHADER));
                            module.dispose();
                            require(!invalid, "Invalid shader was accepted");
                            valid.incrementAndGet();
                        } catch (RuntimeException error) {
                            if (!invalid || !error.getMessage().startsWith("Could not create WGPU shader module:")) throw error;
                            rejected.incrementAndGet();
                        }
                    }
                } catch (Throwable error) { failure.compareAndSet(null, error); }
                finally { context.resourceDomain().releasePreparation(); done.incrementAndGet(); }
            }, "android-wgpu-binding-audit-" + i);
            worker.setDaemon(true);
            worker.start();
        }
    }

    @Override public void render() {
        require(System.nanoTime() < deadline, "Android binding audit timed out");
        if (failure.get() != null) throw new AssertionError("Android binding worker failed", failure.get());
        var frame = graphics.currentFrame();
        var encoder = frame.commandEncoder().beginRenderPass(pass.colorAttachment(frame.colorAttachment()));
        encoder.end();
        if (done.get() == 2) {
            require(valid.get() == 64 && rejected.get() == 64, "Incomplete callback audit");
            System.out.println("[wgpu-android] AUDIT_PASS valid=64 invalid=64 backend=VULKAN");
            application.requestExit();
        }
    }

    @Override public void dispose() { cancelled = true; }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
