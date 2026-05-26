package io.github.libfdx.tests.web;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.tests.AutoTestApplication;
import io.github.libfdx.tests.TestChooserApplication;
import io.github.libfdx.tests.TestSelector;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import org.teavm.jso.JSBody;

final class WebTestLauncherSupport {
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
        applyTestProperties(args);
        System.setProperty("libfdx.test.modelAsset", modelAsset);
        String testName = selectedTestName(requestedTestName, mode, frames);
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

        new WebApplicationBackend().start(config, test(testName, frames, modelAsset, webgpu));
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
}
