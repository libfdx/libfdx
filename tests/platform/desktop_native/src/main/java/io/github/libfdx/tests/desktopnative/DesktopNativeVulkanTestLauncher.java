package io.github.libfdx.tests.desktopnative;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopnative.DesktopNativeApplicationBackend;
import io.github.libfdx.backend.desktopnative.DesktopNativeApplicationConfig;
import io.github.libfdx.backend.desktopnative.DesktopNativeVulkanProvider;
import io.github.libfdx.tests.TestSelector;

/**
 * Launches the desktop native vulkan test entry point.
 *
 * @author xpenatan
 */
public final class DesktopNativeVulkanTestLauncher {
    private DesktopNativeVulkanTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        String testName = option(args, "test", System.getProperty("libfdx.test.name", TestSelector.DEFAULT_TEST_NAME));
        String frames = option(args, "frames", System.getProperty("libfdx.test.frames", "0"));
        String validate = option(args, "validate", System.getProperty("libfdx.test.validate", "true"));
        String driveInput = option(args, "driveInput", System.getProperty("libfdx.test.driveInput", "false"));
        boolean vSync = Boolean.parseBoolean(option(args, "vsync", System.getProperty("libfdx.test.vsync", "true")));
        boolean visible = Boolean.parseBoolean(option(args, "visible", System.getProperty("libfdx.test.visible", "true")));
        int width = parseInt(option(args, "width", System.getProperty("libfdx.test.width", defaultWidth(testName))),
                Integer.parseInt(defaultWidth(testName)));
        int height = parseInt(option(args, "height", System.getProperty("libfdx.test.height", defaultHeight(testName))),
                Integer.parseInt(defaultHeight(testName)));
        int foregroundFps = parseInt(option(args, "foregroundFps",
                System.getProperty("libfdx.test.foregroundFps", "0")), 0);
        String desktopImageCapture = option(args, "desktopImageCapture",
                System.getProperty("libfdx.test.desktopImageCapture", "true"));
        setPropertyFromOption(args, "capture", "libfdx.test.capture");
        setPropertyFromOption(args, "captureEvery", "libfdx.test.captureEvery");
        setPropertyFromOption(args, "captureFrame", "libfdx.test.captureFrame");
        setPropertyFromOption(args, "visualValidate", "libfdx.test.visualValidate");
        setPropertyFromOption(args, "visualCaptureAllScenarios", "libfdx.test.visualCaptureAllScenarios");
        setPropertyFromOption(args, "visualBaselineDir", "libfdx.test.visualBaselineDir");
        setPropertyFromOption(args, "visualBaselineTemplate", "libfdx.test.visualBaselineTemplate");
        setPropertyFromOption(args, "visualRequireBaselines", "libfdx.test.visualRequireBaselines");
        setPropertyFromOption(args, "visualMismatchRatio", "libfdx.test.visualMismatchRatio");
        setPropertyFromOption(args, "visualChannelTolerance", "libfdx.test.visualChannelTolerance");
        setPropertyFromOption(args, "uiScale", "libfdx.test.uiScale");
        setPropertyFromOption(args, "safeArea", "libfdx.test.safeArea");
        setPropertyFromOption(args, "uiDebugLines", "libfdx.test.uiDebugLines");
        setPropertyFromOption(args, "uiSection", "libfdx.test.uiSection");
        setPropertyFromOption(args, "hoverLabel", "libfdx.test.hoverLabel");
        setPropertyFromOption(args, "fpsLogSeconds", "libfdx.test.fpsLogSeconds");
        setPropertyFromOption(args, "stepDelaySeconds", "libfdx.validation.stepDelaySeconds");
        setPropertyFromOption(args, "reportEveryFrames", "libfdx.test.reportEveryFrames");
        setPropertyFromOption(args, "stallFrameMs", "libfdx.test.stallFrameMs");
        setPropertyFromOption(args, "stallLimit", "libfdx.test.stallLimit");

        System.setProperty("libfdx.test.name", testName);
        System.setProperty("libfdx.test.frames", frames);
        System.setProperty("libfdx.test.validate", validate);
        System.setProperty("libfdx.test.driveInput", driveInput);
        System.setProperty("libfdx.test.vsync", String.valueOf(vSync));
        System.setProperty("libfdx.test.visible", String.valueOf(visible));
        System.setProperty("libfdx.test.width", String.valueOf(width));
        System.setProperty("libfdx.test.height", String.valueOf(height));
        System.setProperty("libfdx.test.foregroundFps", String.valueOf(foregroundFps));
        System.setProperty("libfdx.test.desktopImageCapture", desktopImageCapture);

        System.out.println("[info] DesktopNativeVulkanTestLauncher starting " + testName
                + " with desktop_native Vulkan"
                + ", size=" + width + "x" + height
                + ", validate=" + validate
                + ", driveInput=" + driveInput
                + ", vSync=" + vSync
                + ", visible=" + visible
                + ", foregroundFps=" + foregroundFps
                + ", desktopImageCapture=" + desktopImageCapture);
        System.out.flush();

        DesktopNativeVulkanProvider provider = new DesktopNativeVulkanProvider().vSync(vSync);
        if (!vSync) {
            provider.configuration().preferMailboxPresentMode(false);
        }

        DesktopNativeApplicationConfig config = new DesktopNativeApplicationConfig()
                .title("libfdx Test: " + testName + " - desktop_native Vulkan")
                .size(width, height)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(foregroundFps)
                .graphics(provider);

        ApplicationListener test = TestSelector.create(testName, Long.parseLong(frames));
        new DesktopNativeApplicationBackend().start(config, test);
    }

    private static void setPropertyFromOption(String[] args, String option, String property) {
        String value = option(args, option, System.getProperty(property));
        if (value != null && value.trim().length() > 0) {
            System.setProperty(property, value);
        }
    }

    private static String option(String[] args, String name, String defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        String prefix = "--" + name + "=";
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return defaultValue;
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.trim().length() == 0) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private static String defaultWidth(String testName) {
        return "ui".equalsIgnoreCase(testName) ? "1440" : "640";
    }

    private static String defaultHeight(String testName) {
        return "ui".equalsIgnoreCase(testName) ? "1000" : "480";
    }
}
