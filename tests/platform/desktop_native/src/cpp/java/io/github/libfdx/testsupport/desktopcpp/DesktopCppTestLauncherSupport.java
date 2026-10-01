package io.github.libfdx.testsupport.desktopcpp;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopcpp.DesktopCppApplicationBackend;
import io.github.libfdx.backend.desktopcpp.DesktopCppApplicationConfig;
import io.github.libfdx.backend.desktopcpp.DesktopCppAudioProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.testsupport.AutoTestApplication;
import io.github.libfdx.testsupport.PerformanceApplication;
import io.github.libfdx.testsupport.TestChooserApplication;
import io.github.libfdx.testsupport.TestSelector;
import io.github.libfdx.testsupport.nativeprocess.NativeTestLaunchHandler;
import java.util.function.Supplier;

/** Shared setup for the jNative graphics test entry points. */
public final class DesktopCppTestLauncherSupport {
    private DesktopCppTestLauncherSupport() {}

    public static void launch(
            String graphics, String[] args, Supplier<GraphicsAttachmentProvider> provider) {
        String testName = args.length > 0 ? args[0] : TestSelector.SELECTOR_NAME;
        long frames = args.length > 1 ? Long.parseLong(args[1]) : 0;
        System.setProperty("libfdx.test.frames", Long.toString(frames));
        for (int i = 2; i < args.length; i++) {
            String arg = args[i];
            int equals = arg.indexOf('=');
            if (!arg.startsWith("-D") || equals <= 2) {
                throw new IllegalArgumentException("Expected -Dname=value: " + arg);
            }
            System.setProperty(arg.substring(2, equals), arg.substring(equals + 1));
        }
        NativeTestLaunchHandler launches = testName.equals(TestSelector.SELECTOR_NAME)
                ? new NativeTestLaunchHandler(graphics, new DesktopCppTestProcesses()) : null;
        ApplicationListener test;
        if (testName.equals(TestSelector.SELECTOR_NAME)) {
            test =
                    new TestChooserApplication(
                            new String[] {graphics},
                            graphics,
                            launches,
                            false,
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
                        .title(testName.equals(TestSelector.SELECTOR_NAME)
                                ? "libFDX Tests - desktop_cpp " + graphics
                                : "libFDX Test: " + testName + " - desktop_cpp " + graphics)
                        .size(
                                Integer.getInteger(
                                        "libfdx.test.width", defaultSize(testName, true)),
                                Integer.getInteger(
                                        "libfdx.test.height", defaultSize(testName, false)))
                        .maximized(Boolean.getBoolean("libfdx.test.maximized"))
                        .vSync(Boolean.parseBoolean(System.getProperty("libfdx.test.vsync", "true")))
                        .foregroundFps(Integer.getInteger("libfdx.test.foregroundFps", 60))
                        .graphics(provider.get());
        if (PerformanceApplication.enabled()) {
            config.vSync(false).foregroundFps(0);
        }
        try {
            new DesktopCppApplicationBackend().start(config, PerformanceApplication.wrap(test, testName));
        } finally {
            if (launches != null) launches.dispose();
        }
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
