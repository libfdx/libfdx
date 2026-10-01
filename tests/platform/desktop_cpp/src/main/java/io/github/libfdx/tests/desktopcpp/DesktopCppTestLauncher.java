package io.github.libfdx.tests.desktopcpp;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopcpp.DesktopCppApplicationBackend;
import io.github.libfdx.backend.desktopcpp.DesktopCppApplicationConfig;
import io.github.libfdx.backend.desktopcpp.DesktopCppAudioProvider;
import io.github.libfdx.backend.desktopcpp.DesktopCppOpenGLProvider;
import io.github.libfdx.backend.desktopcpp.DesktopCppVulkanProvider;
import io.github.libfdx.graphics.vulkan.VulkanConfiguration;
import io.github.libfdx.testsupport.AutoTestApplication;
import io.github.libfdx.testsupport.PerformanceApplication;
import io.github.libfdx.testsupport.TestChooserApplication;
import io.github.libfdx.testsupport.TestSelector;
import io.github.libfdx.testsupport.desktopcpp.DesktopCppTestFactory;

/** Launches the shared tests with the native desktop backend. */
public final class DesktopCppTestLauncher {
    private DesktopCppTestLauncher() {}

    public static void main(String[] args) {
        String graphics = args.length > 0 ? args[0] : "gl";
        String testName = args.length > 1 ? args[1] : TestSelector.SELECTOR_NAME;
        long frames = args.length > 2 ? Long.parseLong(args[2]) : 0;
        System.setProperty("libfdx.test.frames", Long.toString(frames));
        for (int i = 3; i < args.length; i++) {
            String arg = args[i];
            int equals = arg.indexOf('=');
            if (!arg.startsWith("-D") || equals <= 2) {
                throw new IllegalArgumentException("Expected -Dname=value: " + arg);
            }
            System.setProperty(arg.substring(2, equals), arg.substring(equals + 1));
        }
        if (!graphics.equals("gl") && !graphics.equals("vulkan")) {
            throw new IllegalArgumentException("Expected gl or vulkan: " + graphics);
        }
        ApplicationListener test;
        if (testName.equals(TestSelector.SELECTOR_NAME)) {
            test =
                    new TestChooserApplication(
                            new String[] {graphics},
                            graphics,
                            null,
                            true,
                            false,
                            DesktopCppTestFactory::create);
        } else if (testName.equals(TestSelector.AUTO_TEST_NAME)) {
            test = new AutoTestApplication(null, true, DesktopCppTestFactory::create);
        } else {
            test = DesktopCppTestFactory.create(testName, frames);
        }
        DesktopCppApplicationConfig config =
                new DesktopCppApplicationConfig()
                        .audio(new DesktopCppAudioProvider())
                        .title("libFDX Tests - desktop_cpp " + graphics)
                        .size(
                                Integer.getInteger(
                                        "libfdx.test.width", defaultSize(testName, true)),
                                Integer.getInteger(
                                        "libfdx.test.height", defaultSize(testName, false)))
                        .graphics(
                                graphics.equals("gl")
                                        ? new DesktopCppOpenGLProvider()
                                        : new DesktopCppVulkanProvider().configuration(new VulkanConfiguration()
                                                .vSync(!PerformanceApplication.enabled())));
        if (PerformanceApplication.enabled()) {
            config.vSync(false).foregroundFps(0);
        }
        new DesktopCppApplicationBackend()
                .start(config, PerformanceApplication.wrap(test, testName));
    }

    private static int defaultSize(String testName, boolean width) {
        if (testName.equals(TestSelector.SELECTOR_NAME)) return width ? 900 : 740;
        int size =
                width ? TestSelector.defaultWidth(testName) : TestSelector.defaultHeight(testName);
        if (testName.equals(TestSelector.AUTO_TEST_NAME)) {
            for (var test : TestSelector.descriptors()) {
                size = Math.max(size, width ? test.defaultWidth() : test.defaultHeight());
            }
        }
        return size;
    }
}
