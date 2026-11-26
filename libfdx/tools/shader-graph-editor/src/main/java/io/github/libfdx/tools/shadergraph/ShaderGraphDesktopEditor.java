package io.github.libfdx.tools.shadergraph;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.backend.desktop.DesktopVulkanProvider;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsAttachmentProvider;
import io.github.libfdx.graphics.d3d12.D3D12Provider;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewAnimation;
import io.github.libfdx.graphics.wgpu.WGPUProvider;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * Opens the shader graph editor on a desktop graphics provider.
 */
public final class ShaderGraphDesktopEditor implements ApplicationListener {
    private static final String LAUNCH_PROPERTIES_PATH =
            "libfdx-desktop-launch.properties";
    private static final Properties LAUNCH_PROPERTIES =
            loadLaunchProperties();

    private final ApplicationListener sample;
    private final Path capturePath;
    private final long captureFrame;
    private Fdx fdx;
    private long frame;
    private boolean captured;

    private ShaderGraphDesktopEditor(ApplicationListener sample, Path capturePath, long captureFrame) {
        this.sample = sample;
        this.capturePath = capturePath;
        this.captureFrame = captureFrame;
    }

    /** Launches the standalone editor. Use Open effect to choose a graph from disk. */
    public static void main(String[] args) {
        openEffects(args, List.of(), -1);
    }

    /**
     * A named editor document. With a null builder, the editor opens the editable or bundled
     * asset. Otherwise it calls the builder on the graphics thread on first open. In both cases
     * explicit saves write under assets/ at assetPath. The preview parameter and range are optional.
     *
     * @param title label in the Effects panel
     * @param builder in-memory graph factory, or null to read the asset
     * @param assetPath relative path used for explicit saves and file-backed loading
     * @param previewAnimation optional initial parameter and range for the material preview
     */
    public record Effect(String title, Supplier<ShaderGraph> builder, String assetPath, ShaderGraphPreviewAnimation previewAnimation) {
        /** Opens a file using its saved editor settings. */
        public Effect(String title, String assetPath) {
            this(title, null, assetPath, null);
        }

        /** Creates an entry with an optional zero-to-one preview range. */
        public Effect(String title, String assetPath, Supplier<ShaderGraph> builder, String dissolveParameter) {
            this(title, builder, assetPath, dissolveParameter == null ? null : new ShaderGraphPreviewAnimation(dissolveParameter, 0, 1));
        }

        /** Legacy accessor for the optional preview parameter. */
        public String dissolveParameter() {
            return previewAnimation == null ? null : previewAnimation.parameter();
        }

        /** Creates an entry with a nonblank label and path. */
        public Effect {
            if (title == null || title.isBlank() || assetPath == null || assetPath.isBlank()) {
                throw new IllegalArgumentException("An editor effect requires a title and an asset path");
            }
        }

    }

    /** Opens a .fdxgraph asset using its saved graph and node layout. */
    public static void openFile(String[] args, String assetPath) {
        openFile(args, assetPath, null);
    }

    /** Opens a .fdxgraph asset with an optional scalar parameter for a zero-to-one preview range. */
    public static void openFile(String[] args, String assetPath, String dissolveParameter) {
        openEffects(args, List.of(new Effect(assetPath, assetPath, null, dissolveParameter)), 0);
    }

    /** Opens an in-memory graph. Explicit saves write to assets/ followed by savePath. */
    public static void openGraph(String[] args, ShaderGraph graph, String savePath) {
        openGraph(args, graph, savePath, null);
    }

    /** Opens an in-memory graph with an optional scalar parameter for a zero-to-one preview range. */
    public static void openGraph(String[] args, ShaderGraph graph, String savePath, String dissolveParameter) {
        Objects.requireNonNull(graph);
        openEffects(args, List.of(new Effect(savePath, savePath, () -> graph, dissolveParameter)), 0);
    }

    /** Resolves a desktop launch option from the JVM or the packaged Gradle target defaults. */
    public static String launchOption(String name, String fallback) {
        return System.getProperty("libfdx.sample." + name, launchProperty(name, fallback));
    }

    /**
     * Opens the effect at the zero-based initial index. With multiple effects the editor shows a
     * selector and retains each visited document, its undo history and its preview until close.
     *
     * @param args desktop launcher options
     * @param effects initial effects in display order, or an empty list to start with the file picker action
     * @param initialIndex index of the effect to show at startup, or -1 for an empty list
     */
    public static void openEffects(String[] args, List<Effect> effects, int initialIndex) {
        ApplicationListener listener = new ShaderGraphEditorApplication(effects, initialIndex, exitAfterFrames(args));
        String graphics = graphicsName(args);
        boolean maximized = Boolean.parseBoolean(option(args,
                "--maximized=", System.getProperty(
                        "libfdx.sample.maximized", "false")));
        boolean visible = Boolean.parseBoolean(option(args,
                "--visible=", System.getProperty(
                        "libfdx.sample.visible", "true")));
        boolean vSync = Boolean.parseBoolean(option(args,
                "--vsync=", System.getProperty(
                        "libfdx.sample.vsync", "true")));
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Shader Graph Editor - " + graphicsDisplayName(args, graphics))
                .size(1280, 720)
                .maximized(maximized)
                .visible(visible)
                .vSync(vSync)
                .foregroundFps(visible ? 60 : 0)
                .graphics(graphicsProvider(graphics, vSync));
        String capture = System.getProperty("libfdx.sample.capture", "");
        if (!capture.isBlank()) {
            listener = new ShaderGraphDesktopEditor(listener, Path.of(capture).toAbsolutePath(),
                    Long.parseLong(System.getProperty("libfdx.sample.captureFrame", "3")));
        }
        new DesktopApplicationBackend().start(config, listener);
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        sample.create(fdx);
    }

    @Override
    public void render() {
        sample.render();
    }

    @Override
    public void onFrameEnd() {
        sample.onFrameEnd();
        if (!captured && frame >= captureFrame) {
            writeCapture(capturePath, fdx.graphics().main().currentFrame().frameBuffer());
            captured = true;
            fdx.logger().info("Captured " + capturePath);
        }
        frame++;
    }

    static void writeCapture(Path capturePath, FrameBuffer buffer) {
        int width = buffer.width();
        int height = buffer.height();
        ByteBuffer rgba = buffer.readPixelsRgba8();
        byte[] row = new byte[width * 3];
        try {
            Files.createDirectories(capturePath.getParent());
            try (OutputStream out = Files.newOutputStream(capturePath)) {
                out.write(("P6\n" + width + " " + height + "\n255\n").getBytes(StandardCharsets.US_ASCII));
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = 0; x < width; x++) {
                        int pixel = (y * width + x) * 4;
                        rgba.get(pixel, row, x * 3, 3);
                    }
                    out.write(row);
                }
            }
        } catch (IOException e) {
            throw new FdxException("Could not write framebuffer capture " + capturePath, e);
        }
    }

    @Override
    public void resize(int width, int height) {
        sample.resize(width, height);
    }

    @Override
    public void pause() {
        sample.pause();
    }

    @Override
    public void resume() {
        sample.resume();
    }

    @Override
    public void dispose() {
        sample.dispose();
        if (!captured) {
            throw new FdxException("Sample did not capture " + capturePath);
        }
    }

    private static GraphicsAttachmentProvider graphicsProvider(
            String graphics, boolean vSync) {
        if ("gl".equalsIgnoreCase(graphics)
                || "opengl".equalsIgnoreCase(graphics)) {
            return new DesktopOpenGLProvider();
        }
        if ("vulkan".equalsIgnoreCase(graphics)
                || "vk".equalsIgnoreCase(graphics)) {
            return new DesktopVulkanProvider().vSync(vSync);
        }
        if (isD3D12(graphics)) {
            return new D3D12Provider().vSync(vSync);
        }
        return new WGPUProvider().vSync(vSync);
    }

    private static String graphicsName(String[] args) {
        return option(args, "--graphics=",
                System.getProperty("libfdx.sample.graphics",
                        launchProperty("graphics", "wgpu")));
    }

    private static String graphicsDisplayName(String[] args,
            String graphics) {
        String configured = option(args, "--graphics-label=",
                System.getProperty("libfdx.sample.graphicsLabel",
                        launchProperty("graphicsLabel", null)));
        if (configured != null && !configured.isBlank()) {
            return configured.trim();
        }
        if ("gl".equalsIgnoreCase(graphics)
                || "opengl".equalsIgnoreCase(graphics)) {
            return "GL";
        }
        if ("vulkan".equalsIgnoreCase(graphics)
                || "vk".equalsIgnoreCase(graphics)) {
            return "Vulkan";
        }
        return isD3D12(graphics) ? "Direct3D 12" : "WGPU";
    }

    private static boolean isD3D12(String graphics) {
        return "d3d12".equalsIgnoreCase(graphics)
                || "direct3d12".equalsIgnoreCase(graphics)
                || "directx12".equalsIgnoreCase(graphics)
                || "dx12".equalsIgnoreCase(graphics);
    }

    private static long exitAfterFrames(String[] args) {
        String value = option(args, "--exit-after-frames=",
                System.getProperty(
                        "libfdx.sample.exitAfterFrames"));
        return value != null && !value.isBlank()
                ? Long.parseLong(value) : 0L;
    }

    private static String option(String[] args, String prefix,
            String fallback) {
        if (args != null) {
            for (String arg : args) {
                if (arg != null && arg.startsWith(prefix)) {
                    return arg.substring(prefix.length());
                }
            }
        }
        return fallback;
    }

    private static String launchProperty(String name,
            String fallback) {
        String value = LAUNCH_PROPERTIES.getProperty(name);
        return value != null && !value.isBlank()
                ? value.trim() : fallback;
    }

    private static Properties loadLaunchProperties() {
        Properties properties = new Properties();
        try (InputStream stream =
                ShaderGraphDesktopEditor.class
                        .getClassLoader().getResourceAsStream(
                                LAUNCH_PROPERTIES_PATH)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (IOException ignored) {
            // Launch properties are optional outside packaged applications.
        }
        return properties;
    }
}
