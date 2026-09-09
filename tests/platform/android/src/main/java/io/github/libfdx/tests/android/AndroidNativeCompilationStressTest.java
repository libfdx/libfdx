package io.github.libfdx.tests.android;

import android.os.Handler;
import android.os.Looper;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.android.AndroidApplicationBackend;
import io.github.libfdx.backend.android.AndroidVulkanProvider;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.RenderPassDescriptor;
import io.github.libfdx.graphics.RenderPipelineDescriptor;
import io.github.libfdx.graphics.RenderTargetLayout;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderModuleSource;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPipelineRequest;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationCapabilities;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationOperation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationPhase;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparedResult;
import io.github.libfdx.graphics.wgpu.WGPUContext;
import io.github.libfdx.testsupport.graphics.FramebufferCapture;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/** Opt-in Android JNI driver-call probe. Select nativeStress=cancel or shutdown with one worker.
 * Run repeatedly in fresh activities. A missed native sampling window fails, never counts as overlap.
 * Direct Vulkan may drain compilation during its frame-fence wait: a separate observer brackets
 * the worker sample with owner native-destroy samples. That proves concurrent calls, not teardown
 * returning before compilation ends. WGPU and cancellation require samples across the return.
 * The initial clear is read back before pausing/submission. Frames resume for cancellation recovery. Handler callbacks
 * perform teardown outside a frame; no artificial source/cache barrier is used. */
public final class AndroidNativeCompilationStressTest extends ApplicationAdapter {
    private static final String VERTEX = """
            @vertex fn vertexMain(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
                let p = array<vec2f, 3>(vec2f(-.8, -.8), vec2f(.8, -.8), vec2f(0, .8));
                return vec4f(p[i], 0, 1);
            }
            """;
    private static final String SIMPLE = VERTEX
            + "@fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(0, 1, 0, 1); }";
    private final Handler owner = new Handler(Looper.getMainLooper());
    private final String mode = System.getProperty("libfdx.test.nativeStress", "cancel");
    private volatile Thread worker;
    private Application application;
    private GraphicsAttachment graphics;
    private GraphicsDevice device;
    private WGPUContext wgpu;
    private Object queue;
    private RenderTargetLayout target;
    private ShaderPreparationOperation operation, display;
    private ShaderPreparedResult ready;
    private long deadline;
    private int frames;
    private boolean direct, started, acted, closed, drained;
    private volatile boolean observing;
    private final AtomicReference<ShutdownSample> concurrentDestroy = new AtomicReference<>();

    private record ShutdownSample(StackTraceElement[] ownerBefore, StackTraceElement[] worker,
            StackTraceElement[] ownerAfter) { }

    @Override public void create(Fdx fdx) {
        require(mode.equals("cancel") || mode.equals("shutdown"), "Unknown nativeStress mode");
        application = fdx.app();
        graphics = (GraphicsAttachment) fdx.graphics().main();
        device = graphics.device();
        direct = graphics.providerId().equals(AndroidVulkanProvider.ID);
        if (!direct) wgpu = graphics.as();
        var capabilities = device.shaderPreparationCapabilities();
        require(capabilities.nativeExecution() == ShaderPreparationCapabilities.Execution.WORKERS
                && capabilities.workerLimit() == 1, "This probe requires one native worker; GLES is loading-only");
        target = RenderTargetLayout.color(graphics.surfaceFormat());
        deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(40);
    }

    private void startWork() {
        AndroidApplicationBackend backend = application.as();
        backend.pause();
        String source = complexSource();
        operation = prepare(ShaderModuleSource.deferred("vertexMain", "fragmentMain", () -> {
            worker = Thread.currentThread();
            return ShaderModuleDescriptor.wgsl("Android native overlap", source);
        }));
        queue = field(direct ? device : wgpu, "preparation");
        owner.post(this::sample);
    }

    private ShaderPreparationOperation prepare(ShaderModuleSource source) {
        return device.prepareRenderPipeline(new ShaderPipelineRequest(source, new RenderPipelineDescriptor()
                .renderTargetLayout(target).depthTestEnabled(false).depthWriteEnabled(false), ShaderPassId.FORWARD, 0));
    }

    private void sample() {
        require(System.nanoTime() < deadline, "Native sampling timed out at " + operation.phase());
        StackTraceElement[] before = operation.phase() == ShaderPreparationPhase.PIPELINE ? nativeStack() : new StackTraceElement[0];
        if (before.length == 0) {
            require(!operation.isDone(), "Native sampling window missed; failure=" + field(operation, "failure"));
            owner.postDelayed(this::sample, 1);
            return;
        }
        acted = true;
        if (direct && mode.equals("shutdown")) observeDestroy(Thread.currentThread());
        long actionStart = System.nanoTime();
        try {
            if (mode.equals("shutdown")) {
                AndroidApplicationBackend backend = application.as();
                backend.dispose();
            } else { operation.cancel(); operation.cancel(); }
        } finally { observing = false; }
        StackTraceElement[] after = nativeStack();
        log("ACTION_MS " + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - actionStart));
        log("BEFORE " + Arrays.toString(before));
        log("AFTER " + Arrays.toString(after));
        ShutdownSample simultaneous = concurrentDestroy.get();
        require(after.length != 0 || simultaneous != null, "No native overlap during/after action; overlap is unproven");
        if (simultaneous != null) {
            log("DESTROY_BEFORE " + Arrays.toString(simultaneous.ownerBefore()));
            log("DURING_WORKER " + Arrays.toString(simultaneous.worker()));
            log("DESTROY_AFTER " + Arrays.toString(simultaneous.ownerAfter()));
        }
        if (mode.equals("shutdown")) {
            require(graphics.isDisposed(), "Context was not disposed");
            if (wgpu != null) require(!wgpu.nativeDevice().isDisposed(), "Device released inside native call");
        }
        log("NATIVE_OVERLAP action=" + mode + " activeAfterReturn=" + (after.length != 0)
                + " concurrentDestroy=" + (simultaneous != null));
        owner.post(this::drain);
    }

    private void observeDestroy(Thread applicationThread) {
        observing = true;
        Thread observer = new Thread(() -> {
            while (observing && System.nanoTime() < deadline) {
                StackTraceElement[] ownerBefore = applicationThread.getStackTrace();
                if (isNativeDestroy(ownerBefore)) {
                    StackTraceElement[] nativeWorker = nativeStack();
                    StackTraceElement[] ownerAfter = applicationThread.getStackTrace();
                    if (nativeWorker.length != 0 && isNativeDestroy(ownerAfter)) {
                        concurrentDestroy.set(new ShutdownSample(ownerBefore, nativeWorker, ownerAfter));
                        return;
                    }
                }
                LockSupport.parkNanos(1_000_000);
            }
        }, "native-shutdown-observer");
        observer.setDaemon(true);
        observer.start();
    }

    private static boolean isNativeDestroy(StackTraceElement[] stack) {
        return stack.length != 0 && stack[0].isNativeMethod()
                && stack[0].getClassName().equals("io.github.libfdx.backend.android.AndroidVulkanNative")
                && stack[0].getMethodName().equals("destroy");
    }

    private StackTraceElement[] nativeStack() {
        if (worker == null) return new StackTraceElement[0];
        StackTraceElement[] stack = worker.getStackTrace();
        boolean pipeline = false, nativeCall = false;
        for (StackTraceElement frame : stack) {
            // JNI generators use a hashed native thunk; require the actual native top frame
            // and its exact WGPU wrapper, or the direct provider's native entry itself.
            nativeCall |= frame == stack[0] && frame.isNativeMethod();
            pipeline |= direct
                    ? frame.isNativeMethod() && frame.getClassName().equals("io.github.libfdx.backend.android.AndroidVulkanNative")
                        && frame.getMethodName().equals("createRenderPipeline")
                    : frame.getClassName().equals("com.github.xpenatan.webgpu.WGPUDevice")
                        && frame.getMethodName().equals("internal_native_CreateRenderPipeline");
        }
        return nativeCall && pipeline ? stack : new StackTraceElement[0];
    }

    private void drain() {
        require(System.nanoTime() < deadline, "Native work did not drain");
        if (!operation.isDone()) { owner.postDelayed(this::drain, 5); return; }
        require(field(operation, "failure") == null, "Native completion/cleanup failed: " + field(operation, "failure"));
        try { operation.finish(); throw new AssertionError("Cancelled result was published"); }
        catch (CancellationException expected) { }
        operation.dispose(); operation.dispose();
        require(operation.isDisposed(), "Operation not disposed");
        drained = true;
        if (closed) verifyClosed();
        else {
            display = prepare(ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("recovery", SIMPLE)));
            AndroidApplicationBackend backend = application.as();
            backend.resume();
        }
    }

    @Override public void render() {
        require(System.nanoTime() < deadline, "Recovery rendering timed out");
        if (display != null && display.isDone() && ready == null) {
            ready = display.finish(); display.dispose();
        }
        var frame = graphics.currentFrame();
        var pass = frame.commandEncoder().beginRenderPass(new RenderPassDescriptor()
                .colorAttachment(frame.colorAttachment()).colorLoadOp(LoadOp.clear(0, 0, 0, 1)));
        try { if (ready != null) { pass.setPipeline(ready.pass().pipeline()); pass.draw(3, 1, 0, 0); } }
        finally { pass.end(); }
        if (!started) {
            FramebufferCapture.readPixelsRgba8(graphics);
            started = true;
            AndroidApplicationBackend backend = application.as();
            backend.pause();
            owner.post(this::startWork);
            return;
        }
        if (ready != null && ++frames == 5) {
            int width = frame.width(), height = frame.height();
            var pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int center = (height / 2 * width + width / 2) * 4;
            require((pixels.get(center) & 255) == 0 && (pixels.get(center + 1) & 255) == 255
                    && (pixels.get(center + 2) & 255) == 0 && (pixels.get(1) & 255) == 0, "Recovery pixels failed");
            String capture = System.getProperty("libfdx.test.capture", "");
            if (!capture.isEmpty()) {
                try { FramebufferCapture.writePpm(capture + ".ppm", width, height, pixels); }
                catch (Exception error) { throw new AssertionError(error); }
            }
            log("RECOVERY_PIXELS_PASS");
        }
        if (ready != null && frames == 30) application.requestExit();
    }

    @Override public void dispose() {
        closed = true;
        require(acted, "Closed before native overlap");
        if (ready != null) ready.dispose();
        if (drained) owner.post(this::verifyClosed);
    }

    private void verifyClosed() {
        require(graphics.isDisposed(), "Context still alive");
        try { prepare(ShaderModuleSource.fixed(ShaderModuleDescriptor.wgsl("closed", SIMPLE)));
            throw new AssertionError("Closed device accepted new work");
        } catch (FdxException expected) { }
        Object executor = field(queue, "executor");
        if (!(executor instanceof ThreadPoolExecutor)) executor = field(executor, "executor");
        if (!((ThreadPoolExecutor) executor).isTerminated()) {
            require(System.nanoTime() < deadline, "Worker did not terminate");
            owner.postDelayed(this::verifyClosed, 5); return;
        }
        require(((Set<?>) field(queue, "jobs")).isEmpty(), "Queue retained jobs");
        if (wgpu != null) {
            require(wgpu.nativeDevice().isDisposed(), "Native device did not retire");
            Object domain = field(wgpu, "resourceDomain");
            require((int) field(domain, "preparationReferences") == 0 && (int) field(domain, "contextReferences") == 0,
                    "Resource domain retained references");
            require(field(domain, "nativeRelease") == null, "Native release callback retained");
        }
        log("NATIVE_STRESS_PASS action=" + mode);
        if (mode.equals("shutdown")) application.requestExit();
    }

    private static String complexSource() {
        StringBuilder source = new StringBuilder(VERTEX).append("\n@fragment fn fragmentMain(@builtin(position) p: vec4f) -> @location(0) vec4f {\nvar v = p * ")
                .append(0.001 + (System.nanoTime() & 0xffff) * 0.00000001).append(";\n");
        int steps = Integer.parseInt(System.getProperty("libfdx.test.nativeStressSteps", "1024"));
        require(steps > 0 && steps <= 8192, "nativeStressSteps must be in 1..8192");
        for (int i = 0; i < steps; i++) source.append("v = sin(v.yzwx * 1.00001 + vec4f(")
                .append(0.001 + i * 0.00001).append("));\n");
        return source.append("return abs(v);\n}\n").toString();
    }

    private static Object field(Object object, String name) {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
            } catch (NoSuchFieldException ignored) { }
            catch (IllegalAccessException error) { throw new AssertionError(error); }
        }
        throw new AssertionError("No field " + name);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void log(String text) { System.out.println("[native-android] provider=" + (direct ? "vulkan" : "wgpu") + " " + text); }
}
