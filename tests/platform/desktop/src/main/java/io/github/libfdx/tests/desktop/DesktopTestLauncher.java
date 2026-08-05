package io.github.libfdx.tests.desktop;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.tests.AutoTestApplication;
import io.github.libfdx.tests.TestChooserApplication;
import io.github.libfdx.tests.TestLaunchHandler;
import io.github.libfdx.tests.TestSelector;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Launches the desktop test entry point.
 *
 * @author xpenatan
 */
public final class DesktopTestLauncher {
    private static final String LAUNCH_PROPERTIES_PATH = "libfdx-desktop-launch.properties";
    private static final String DEFAULT_TEST_MIN_HEAP = "-Xms64m";
    private static final String DEFAULT_TEST_MAX_HEAP = "-Xmx1g";
    private static final Properties LAUNCH_PROPERTIES = loadLaunchProperties();

    private DesktopTestLauncher() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        String graphics = graphicsName();
        String graphicsDisplayName = graphicsDisplayName(graphics);
        boolean vSync = Boolean.parseBoolean(System.getProperty("libfdx.test.vsync", "true"));
        boolean visible = Boolean.parseBoolean(System.getProperty("libfdx.test.visible", "true"));
        int foregroundFps = Integer.parseInt(System.getProperty("libfdx.test.foregroundFps", "0"));
        long frames = exitAfterFrames();
        String testName = selectedTestName(frames);
        boolean explicitSize = hasProperty("libfdx.test.width") || hasProperty("libfdx.test.height");
        boolean maximized = Boolean.parseBoolean(System.getProperty("libfdx.test.maximized",
                explicitSize ? "false" : "true"));
        int width = intProperty("libfdx.test.width", defaultWidth(testName));
        int height = intProperty("libfdx.test.height", defaultHeight(testName));
        System.out.println("[info] DesktopTestLauncher starting " + launchDisplayName(testName)
                + " with " + graphicsDisplayName
                + ", provider=" + graphics
                + ", java=" + System.getProperty("java.version", "")
                + ", multiRelease=" + System.getProperty("jdk.util.jar.enableMultiRelease", "true")
                + ", size=" + width + "x" + height
                + ", maximized=" + maximized
                + ", vSync=" + vSync
                + ", visible=" + visible
                + ", foregroundFps=" + foregroundFps);
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Test: " + testName + " - " + graphicsDisplayName)
                .size(width, height)
                .maximized(maximized)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(foregroundFps)
                .graphics(graphicsProvider(graphics, vSync));

        ApplicationListener test = applicationListener(testName, graphics, vSync);
        new DesktopApplicationBackend().start(config, test);
    }

    private static ApplicationListener applicationListener(String testName, String graphics, boolean vSync) {
        if (isSelector(testName)) {
            return new TestChooserApplication(graphicsOptions(graphics), graphics,
                    new DesktopProcessLaunchHandler(), false);
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return new AutoTestApplication();
        }
        if (DesktopSharedContextTest.NAME.equalsIgnoreCase(testName)) {
            return new DesktopSharedContextTest(graphicsProvider(graphics, vSync), exitAfterFrames());
        }
        return TestSelector.create(testName, exitAfterFrames());
    }

    private static String selectedTestName(long frames) {
        String explicit = trim(System.getProperty("libfdx.test.name"));
        String mode = trim(System.getProperty("libfdx.test.mode"));
        if (explicit != null && explicit.length() > 0) {
            if (isSelector(explicit)) {
                return TestSelector.SELECTOR_NAME;
            }
            if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(explicit)) {
                return TestSelector.AUTO_TEST_NAME;
            }
            return explicit;
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

    private static GraphicsAttachmentProvider graphicsProvider(String graphics, boolean vSync) {
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            DesktopVulkanProvider provider = new DesktopVulkanProvider().vSync(vSync);
            if (!vSync) {
                provider.configuration().preferMailboxPresentMode(false);
            }
            return provider;
        }
        if (isD3D12(graphics)) {
            return new D3D12Provider()
                    .vSync(vSync)
                    .validation(Boolean.getBoolean("libfdx.validation.d3d12"));
        }
        return new WGPUProvider().vSync(vSync);
    }

    private static String graphicsName() {
        return System.getProperty("libfdx.test.graphics", launchProperty("graphics", "wgpu"));
    }

    private static String graphicsDisplayName(String graphics) {
        String configured = System.getProperty("libfdx.test.graphicsLabel", launchProperty("graphicsLabel", null));
        if (configured != null && configured.trim().length() > 0) {
            return configured.trim();
        }
        return selectedGraphicsDisplayName(graphics);
    }

    private static String selectedGraphicsDisplayName(String graphics) {
        if ("gl".equalsIgnoreCase(graphics) || "opengl".equalsIgnoreCase(graphics)) {
            return "GL";
        }
        if ("vulkan".equalsIgnoreCase(graphics) || "vk".equalsIgnoreCase(graphics)) {
            return "Vulkan";
        }
        if (isD3D12(graphics)) {
            return "Direct3D 12";
        }
        return "WGPU";
    }

    private static boolean isD3D12(String graphics) {
        return "d3d12".equalsIgnoreCase(graphics)
                || "direct3d12".equalsIgnoreCase(graphics)
                || "directx12".equalsIgnoreCase(graphics)
                || "dx12".equalsIgnoreCase(graphics);
    }

    private static String[] graphicsOptions(String currentGraphics) {
        String configured = System.getProperty("libfdx.test.graphicsOptions", "");
        if (configured.trim().length() == 0) {
            return new String[] { "gl", "wgpu", "vulkan", "d3d12" };
        }
        String[] split = configured.split(",");
        ArrayList<String> options = new ArrayList<String>();
        for (int i = 0; i < split.length; i++) {
            String value = trim(split[i]);
            if (value != null && value.length() > 0) {
                options.add(value);
            }
        }
        if (options.size() == 0) {
            return new String[] { "gl", "wgpu", "vulkan", "d3d12" };
        }
        return options.toArray(new String[options.size()]);
    }

    private static long exitAfterFrames() {
        String value = System.getProperty("libfdx.test.frames", "0");
        return Long.parseLong(value);
    }

    private static int intProperty(String name, String fallback) {
        return Integer.parseInt(System.getProperty(name, fallback));
    }

    private static boolean hasProperty(String name) {
        String value = System.getProperty(name);
        return value != null && value.trim().length() > 0;
    }

    private static String defaultWidth(String testName) {
        if (isSelector(testName)) {
            return "900";
        }
        if (DesktopSharedContextTest.NAME.equalsIgnoreCase(testName)) {
            return "640";
        }
        return String.valueOf(TestSelector.defaultWidth(testName));
    }

    private static String defaultHeight(String testName) {
        if (isSelector(testName)) {
            return "740";
        }
        if (DesktopSharedContextTest.NAME.equalsIgnoreCase(testName)) {
            return "480";
        }
        return String.valueOf(TestSelector.defaultHeight(testName));
    }

    private static String launchDisplayName(String testName) {
        if (isSelector(testName)) {
            return "selector";
        }
        if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
            return "auto";
        }
        return testName;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 0 ? trimmed : null;
    }

    /**
     * Represents a desktop process launch handler.
     *
     * @author xpenatan
     */
    private static final class DesktopProcessLaunchHandler implements TestLaunchHandler {
        private Process activeProcess;

        /**
         * Runs the launch step.
         *
         * @param testName the test name
         * @param graphicsName the graphics name
         * @return true if launch succeeds or is active; false otherwise
         */
        @Override
        public boolean launch(String testName, String graphicsName) {
            List<String> command = new ArrayList<String>();
            command.add(javaExecutable());
            addForwardedJvmArguments(command);
            addSystemProperty(command, "libfdx.test.graphics", graphicsName);
            addSystemProperty(command, "libfdx.test.graphicsLabel", selectedGraphicsDisplayName(graphicsName));
            addSystemProperty(command, "libfdx.test.frames", "0");
            addCopiedProperty(command, "libfdx.test.vsync");
            addCopiedProperty(command, "libfdx.test.width");
            addCopiedProperty(command, "libfdx.test.height");
            addCopiedProperty(command, "libfdx.test.maximized");
            addCopiedProperty(command, "libfdx.test.foregroundFps");
            addCopiedProperty(command, "libfdx.test.fpsLogSeconds");
            addCopiedProperty(command, "libfdx.test.modelAsset");
            addCopiedProperty(command, "libfdx.test.uiScale");
            addCopiedProperty(command, "libfdx.test.safeArea");
            addCopiedProperty(command, "libfdx.test.uiDebugLines");
            addCopiedProperty(command, "libfdx.test.uiSection");
            if (TestSelector.AUTO_TEST_NAME.equalsIgnoreCase(testName)) {
                addSystemProperty(command, "libfdx.test.mode", TestSelector.AUTO_TEST_NAME);
            } else {
                addSystemProperty(command, "libfdx.test.name", testName);
            }
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(DesktopTestLauncher.class.getName());

            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(new File(System.getProperty("user.dir")))
                    .inheritIO();
            try {
                activeProcess = builder.start();
                return true;
            } catch (IOException error) {
                error.printStackTrace();
                return false;
            }
        }

        /**
         * Returns whether this instance has active launch.
         *
         * @return true if this instance has active launch; false otherwise
         */
        @Override
        public boolean hasActiveLaunch() {
            if (activeProcess == null) {
                return false;
            }
            if (activeProcess.isAlive()) {
                return true;
            }
            activeProcess = null;
            return false;
        }

        private static void addForwardedJvmArguments(List<String> command) {
            List<String> arguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (int i = 0; i < arguments.size(); i++) {
                String argument = arguments.get(i);
                if (argument.startsWith("--enable-native-access")
                        || argument.startsWith("-Dorg.lwjgl.system.stackSize")
                        || argument.startsWith("-Dlibfdx.test.")
                        || argument.startsWith("-Dlibfdx.validation.")
                        || argument.startsWith("-Xms")
                        || argument.startsWith("-Xmx")) {
                    command.add(argument);
                }
            }
            if (!containsPrefix(command, "--enable-native-access")) {
                command.add("--enable-native-access=ALL-UNNAMED");
            }
            if (!containsPrefix(command, "-Xms")) {
                command.add(DEFAULT_TEST_MIN_HEAP);
            }
            if (!containsPrefix(command, "-Xmx")) {
                command.add(DEFAULT_TEST_MAX_HEAP);
            }
        }

        private static boolean containsPrefix(List<String> values, String prefix) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i).startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        private static void addCopiedProperty(List<String> command, String name) {
            String value = System.getProperty(name);
            if (value != null && value.trim().length() > 0) {
                addSystemProperty(command, name, value.trim());
            }
        }

        private static void addSystemProperty(List<String> command, String name, String value) {
            command.add("-D" + name + "=" + value);
        }

        private static String javaExecutable() {
            String executable = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "java.exe" : "java";
            return new File(new File(System.getProperty("java.home"), "bin"), executable).getAbsolutePath();
        }
    }

    private static String launchProperty(String name, String fallback) {
        String value = LAUNCH_PROPERTIES.getProperty(name);
        if (value != null && value.trim().length() > 0) {
            return value.trim();
        }
        return fallback;
    }

    private static Properties loadLaunchProperties() {
        Properties properties = new Properties();
        try (InputStream stream = DesktopTestLauncher.class.getClassLoader()
                .getResourceAsStream(LAUNCH_PROPERTIES_PATH)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // Launch properties are optional outside release jars.
        }
        return properties;
    }
}
