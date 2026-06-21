package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.backend.web.WebStorageBackend;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.tests.AutoTestApplication;
import io.github.libfdx.tests.TestChooserApplication;
import io.github.libfdx.tests.TestSelector;
import io.github.libfdx.tests.graphics.FramebufferCapture;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.storage.DefaultStorage;
import io.github.libfdx.storage.KeyValueStore;
import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.Locale;
import org.teavm.jso.JSBody;

/**
 * Represents a web test launcher support.
 *
 * @author xpenatan
 */
final class WebTestLauncherSupport {
    private static final String SELECTED_TEST_PROPERTY = "libfdx.test.selected";
    private static final String SELECTED_TEST_STORE = "test-selector";
    private static final String SELECTED_TEST_KEY = "selected";

    private WebTestLauncherSupport() {
    }

    static void start(String runtimeName, String[] args) {
        String graphics = option(args, "graphics", query("graphics"), "");
        if (graphics.length() == 0) {
            graphics = option(args, "api", query("api"), "webgl");
        }
        boolean webgpu = isWebGPU(graphics);
        String graphicsName = webgpu ? "WebGPU" : "WebGL";
        String requestedTestName = option(args, "test", query("test"), "");
        String mode = option(args, "mode", query("mode"), hasOption(args, "auto") || hasQuery("auto")
                ? TestSelector.AUTO_TEST_NAME : "");
        long frames = Long.parseLong(option(args, "frames", query("frames"), "0"));
        String modelAsset = option(args, "modelAsset", query("modelAsset"), ModelBatchTest.DEFAULT_GLTF_ASSET);
        String capture = option(args, "capture", query("capture"), "");
        long captureFrame = Long.parseLong(option(args, "captureFrame", query("captureFrame"), "2"));
        long captureEvery = Long.parseLong(option(args, "captureEvery", query("captureEvery"), "0"));
        applyTestProperties(args);
        applyCaptureOrbitDefault(args, capture, captureEvery);
        System.setProperty("libfdx.test.modelAsset", modelAsset);
        String testName = selectedTestName(requestedTestName, mode, frames);
        syncSelectedTest(testName);
        System.out.println("[info] WebTestLauncher starting " + testName
                + " with " + graphicsName + " " + runtimeName
                + ", frames=" + frames
                + ", modelAsset=" + modelAsset);
        System.setProperty("libfdx.test.desktopImageCapture", "false");

        WebApplicationConfig config = new WebApplicationConfig()
                .title("libfdx Tests - " + graphicsName + " " + runtimeName + ": " + testName)
                .size(0, 0)
                .canvasId("libfdx-canvas");
        if (webgpu) {
            config.graphics(new WebWGPUProvider());
        } else {
            config.graphics(new WebGLProvider());
        }

        ApplicationListener selectedTest = test(testName, frames, modelAsset, webgpu);
        if (capture.length() > 0) {
            selectedTest = new WebCaptureApplicationListener(selectedTest, capture, captureFrame, captureEvery, webgpu);
        }
        new WebApplicationBackend().start(config, selectedTest);
    }

    private static boolean isWebGPU(String graphics) {
        return "webgpu".equalsIgnoreCase(graphics) || "wgpu".equalsIgnoreCase(graphics);
    }

    private static ApplicationListener test(String testName, long frames, String modelAsset, boolean webgpu) {
        if (isSelector(testName)) {
            return new TestChooserApplication(new String[] { webgpu ? "webgpu" : "webgl" },
                    webgpu ? "webgpu" : "webgl", null, true);
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return new AutoTestApplication();
        }
        if ("model".equalsIgnoreCase(testName)) {
            return new ModelBatchTest(frames, modelAsset);
        }
        return TestSelector.create(testName, frames);
    }

    private static String selectedTestName(String requestedTestName, String mode, long frames) {
        String requested = trim(requestedTestName);
        if (requested != null) {
            if (isSelector(requested)) {
                return TestSelector.SELECTOR_NAME;
            }
            if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(requested)) {
                return TestSelector.AUTO_TEST_NAME;
            }
            return requested;
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(mode)) {
            return TestSelector.AUTO_TEST_NAME;
        }
        if (isSelector(mode) || shouldOpenSelector(frames)) {
            return TestSelector.SELECTOR_NAME;
        }
        return TestSelector.DEFAULT_TEST_NAME;
    }

    private static void syncSelectedTest(String testName) {
        if (isSelector(testName)) {
            String storedTestName = validTestName(selectedTestStore().getString(SELECTED_TEST_KEY, ""));
            if (storedTestName != null) {
                System.setProperty(SELECTED_TEST_PROPERTY, storedTestName);
            }
            return;
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return;
        }
        String selectedTestName = validTestName(testName);
        if (selectedTestName == null) {
            return;
        }
        System.setProperty(SELECTED_TEST_PROPERTY, selectedTestName);
        selectedTestStore().putString(SELECTED_TEST_KEY, selectedTestName).flush();
    }

    private static KeyValueStore selectedTestStore() {
        return new DefaultStorage(new WebStorageBackend()).local(SELECTED_TEST_STORE).load();
    }

    private static String validTestName(String testName) {
        String selectedTestName = trim(testName);
        if (selectedTestName == null) {
            return null;
        }
        TestSelector.TestDescriptor descriptor = TestSelector.descriptor(selectedTestName);
        if (descriptor == null) {
            return null;
        }
        return descriptor.name();
    }

    private static boolean shouldOpenSelector(long frames) {
        if (frames > 0L) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("libfdx.test.validate", "false"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("libfdx.test.driveInput", "false"))) {
            return false;
        }
        if (Boolean.parseBoolean(System.getProperty("libfdx.test.visualValidate", "false"))) {
            return false;
        }
        return trim(System.getProperty("libfdx.test.capture")) == null;
    }

    private static boolean isSelector(String value) {
        return TestSelector.SELECTOR_NAME.equalsIgnoreCase(value)
                || "menu".equalsIgnoreCase(value)
                || "chooser".equalsIgnoreCase(value);
    }

    private static boolean hasOption(String[] args, String name) {
        if (args == null) {
            return false;
        }
        String expected = "--" + name;
        for (int i = 0; i < args.length; i++) {
            if (expected.equals(args[i])) {
                return true;
            }
        }
        return false;
    }

    private static String option(String[] args, String name, String queryValue, String fallback) {
        if (args != null) {
            String prefix = "--" + name + "=";
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg != null && arg.startsWith(prefix)) {
                    return arg.substring(prefix.length());
                }
            }
        }
        return queryValue != null && queryValue.length() > 0 ? queryValue : fallback;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }

    private static void applyTestProperties(String[] args) {
        setTestProperty(args, "driveInput", "libfdx.test.driveInput");
        setTestProperty(args, "validate", "libfdx.test.validate");
        setTestProperty(args, "visualValidate", "libfdx.test.visualValidate");
        setTestProperty(args, "stepDelaySeconds", "libfdx.validation.stepDelaySeconds");
        setTestProperty(args, "uiScale", "libfdx.test.uiScale");
        setTestProperty(args, "uiSection", "libfdx.test.uiSection");
        setTestProperty(args, "fpsLogSeconds", "libfdx.test.fpsLogSeconds");
        setTestProperty(args, "uiPerfLogSeconds", "libfdx.test.uiPerfLogSeconds");
        setTestProperty(args, "cameraOrbit", "libfdx.test.cameraOrbit");
        setTestProperty(args, "cameraOrbitStartDegrees", "libfdx.test.cameraOrbitStartDegrees");
        setTestProperty(args, "cameraOrbitDegrees", "libfdx.test.cameraOrbitDegrees");
        setTestProperty(args, "wgpuDebugInit", "libfdx.wgpu.debugInit");
    }

    private static void applyCaptureOrbitDefault(String[] args, String capture, long captureEvery) {
        String configuredCameraOrbit = option(args, "cameraOrbit", query("cameraOrbit"), "");
        if (configuredCameraOrbit.length() == 0
                && captureEvery > 0L
                && capture != null
                && capture.indexOf('%') >= 0) {
            System.setProperty("libfdx.test.cameraOrbit", "true");
        }
    }

    private static void setTestProperty(String[] args, String optionName, String propertyName) {
        String value = option(args, optionName, query(optionName), "");
        if (value.length() > 0) {
            System.setProperty(propertyName, value);
        }
    }

    @JSBody(params = { "name" }, script =
            "var params = new URLSearchParams(window.location.search || '');\n" +
            "return params.get(name) || '';")
    private static native String query(String name);

    @JSBody(params = { "name" }, script =
            "var params = new URLSearchParams(window.location.search || '');\n" +
            "return params.has(name);")
    private static native boolean hasQuery(String name);

    @JSBody(params = { "name", "width", "height", "base64" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var capture = { name: name, width: width, height: height, base64: base64, mime: 'image/x-portable-pixmap' };\n" +
            "root.libfdxTestCaptures = root.libfdxTestCaptures || [];\n" +
            "root.libfdxTestCaptures.push(capture);\n" +
            "root.libfdxLastTestCapture = capture;\n" +
            "mirrorCaptureToDom(capture);")
    private static native void publishCapture(String name, int width, int height, String base64);

    @JSBody(params = { "name" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "root.requestAnimationFrame(function() {\n" +
            "  var canvas = document.getElementById('libfdx-canvas');\n" +
            "  if (!canvas) throw new Error('libfdx canvas not found');\n" +
            "  var dataUrl = canvas.toDataURL('image/png');\n" +
            "  var comma = dataUrl.indexOf(',');\n" +
            "  if (comma < 0) throw new Error('canvas did not return a PNG data URL');\n" +
            "  var capture = { name: name, width: canvas.width || 0, height: canvas.height || 0,\n" +
            "    base64: dataUrl.substring(comma + 1), mime: 'image/png' };\n" +
            "  root.libfdxTestCaptures = root.libfdxTestCaptures || [];\n" +
            "  root.libfdxTestCaptures.push(capture);\n" +
            "  root.libfdxLastTestCapture = capture;\n" +
            "  mirrorCaptureToDom(capture);\n" +
            "});")
    private static native void publishCanvasCapture(String name);

    @JSBody(params = { }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "root.mirrorCaptureToDom = root.mirrorCaptureToDom || function(capture) {\n" +
            "  var doc = root.document;\n" +
            "  if (!doc || !doc.documentElement || !capture) return;\n" +
            "  var list = doc.getElementById('libfdx-captures');\n" +
            "  if (!list) {\n" +
            "    list = doc.createElement('div');\n" +
            "    list.id = 'libfdx-captures';\n" +
            "    list.style.display = 'none';\n" +
            "    doc.documentElement.appendChild(list);\n" +
            "  }\n" +
            "  var entry = doc.createElement('textarea');\n" +
            "  entry.setAttribute('data-libfdx-capture-index', String(list.children.length));\n" +
            "  entry.setAttribute('data-libfdx-capture-name', capture.name || '');\n" +
            "  entry.setAttribute('data-libfdx-capture-width', String(capture.width || 0));\n" +
            "  entry.setAttribute('data-libfdx-capture-height', String(capture.height || 0));\n" +
            "  entry.setAttribute('data-libfdx-capture-mime', capture.mime || '');\n" +
            "  entry.value = capture.base64 || '';\n" +
            "  list.appendChild(entry);\n" +
            "  doc.documentElement.setAttribute('data-libfdx-last-capture-name', capture.name || '');\n" +
            "  doc.documentElement.setAttribute('data-libfdx-last-capture-width', String(capture.width || 0));\n" +
            "  doc.documentElement.setAttribute('data-libfdx-last-capture-height', String(capture.height || 0));\n" +
            "  doc.documentElement.setAttribute('data-libfdx-last-capture-mime', capture.mime || '');\n" +
            "};")
    private static native void installCaptureDomMirror();

    @JSBody(params = { "status", "captureName", "captureFrame", "captureEvery", "renderedFrames", "capturedFrames",
            "error" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "root.libfdxTestCaptureState = { status: status, captureName: captureName,\n" +
            "  captureFrame: Number(captureFrame), captureEvery: Number(captureEvery),\n" +
            "  renderedFrames: Number(renderedFrames), capturedFrames: Number(capturedFrames), error: error || null };\n" +
            "var doc = root.document;\n" +
            "if (doc && doc.documentElement) {\n" +
            "  var target = doc.documentElement;\n" +
            "  target.setAttribute('data-libfdx-capture-status', status);\n" +
            "  target.setAttribute('data-libfdx-capture-name', captureName);\n" +
            "  target.setAttribute('data-libfdx-capture-frame', String(captureFrame));\n" +
            "  target.setAttribute('data-libfdx-capture-every', String(captureEvery));\n" +
            "  target.setAttribute('data-libfdx-capture-rendered-frames', String(renderedFrames));\n" +
            "  target.setAttribute('data-libfdx-capture-captured-frames', String(capturedFrames));\n" +
            "  if (error) {\n" +
            "    target.setAttribute('data-libfdx-capture-error', error);\n" +
            "  } else {\n" +
            "    target.removeAttribute('data-libfdx-capture-error');\n" +
            "  }\n" +
            "}")
    private static native void publishCaptureState(String status, String captureName, long captureFrame,
            long captureEvery, long renderedFrames, int capturedFrames, String error);

    /**
     * Captures web test frames into a browser-visible base64 PPM record.
     *
     * @author xpenatan
     */
    private static final class WebCaptureApplicationListener implements ApplicationListener {
        private final ApplicationListener delegate;
        private final String captureName;
        private final long captureFrame;
        private final long captureEvery;
        private final boolean browserCanvasCapture;
        private Fdx fdx;
        private int capturedFrames;
        private long renderedFrames;

        WebCaptureApplicationListener(ApplicationListener delegate, String captureName, long captureFrame,
                long captureEvery, boolean browserCanvasCapture) {
            this.delegate = delegate;
            this.captureName = captureName;
            this.captureFrame = Math.max(1L, captureFrame);
            this.captureEvery = Math.max(0L, captureEvery);
            this.browserCanvasCapture = browserCanvasCapture;
        }

        @Override
        public void create(Fdx fdx) {
            this.fdx = fdx;
            installCaptureDomMirror();
            publishCaptureState("created", captureName, captureFrame, captureEvery, renderedFrames, capturedFrames,
                    null);
            delegate.create(fdx);
        }

        @Override
        public void resize(int width, int height) {
            delegate.resize(width, height);
        }

        @Override
        public void render() {
            delegate.render();
        }

        @Override
        public void onFrameEnd() {
            delegate.onFrameEnd();
            renderedFrames++;
            publishCaptureState("frame", captureName, captureFrame, captureEvery, renderedFrames, capturedFrames,
                    null);
            if (shouldCapture()) {
                captureFrame(captureName(capturedFrames));
                capturedFrames++;
                publishCaptureState("captured", captureName, captureFrame, captureEvery, renderedFrames,
                        capturedFrames, null);
            }
        }

        @Override
        public void pause() {
            delegate.pause();
        }

        @Override
        public void resume() {
            delegate.resume();
        }

        @Override
        public void dispose() {
            delegate.dispose();
            if (capturedFrames == 0) {
                throw new FdxException("WebTestLauncher did not capture framebuffer to " + captureName);
            }
            publishCaptureState("complete", captureName, captureFrame, captureEvery, renderedFrames, capturedFrames,
                    null);
        }

        private boolean shouldCapture() {
            if (renderedFrames < captureFrame) {
                return false;
            }
            if (captureEvery <= 0L) {
                return capturedFrames == 0;
            }
            return (renderedFrames - captureFrame) % captureEvery == 0L;
        }

        private String captureName(int index) {
            if (captureName.indexOf('%') >= 0) {
                return String.format(Locale.ROOT, captureName, index);
            }
            return captureName;
        }

        private void captureFrame(String name) {
            try {
                if (browserCanvasCapture) {
                    String pngName = pngName(name);
                    publishCanvasCapture(pngName);
                    fdx.logger().info("WebTestLauncher captured canvas to " + pngName);
                    return;
                }
                GraphicsContext graphics = fdx.graphics().main();
                FrameBuffer frameBuffer = graphics.currentFrame().frameBuffer();
                ByteBuffer pixels = frameBuffer.readPixelsRgba8();
                byte[] ppm = FramebufferCapture.ppmBytes(frameBuffer.width(), frameBuffer.height(), pixels);
                publishCapture(name, frameBuffer.width(), frameBuffer.height(),
                        Base64.getEncoder().encodeToString(ppm));
                fdx.logger().info("WebTestLauncher captured framebuffer to " + name);
            } catch (Exception e) {
                publishCaptureState("error", captureName, captureFrame, captureEvery, renderedFrames, capturedFrames,
                        e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                throw new FdxException("Could not capture web framebuffer to " + name, e);
            }
        }

        private String pngName(String name) {
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (lowerName.endsWith(".png")) {
                return name;
            }
            int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
            int dot = name.lastIndexOf('.');
            if (dot > slash) {
                return name.substring(0, dot) + ".png";
            }
            return name + ".png";
        }
    }
}
