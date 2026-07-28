package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.tools.project.generator.ui.ProjectGeneratorApplication;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiNodeType;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

/**
 * Captures a rendered project-generator frame for visual smoke inspection.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorVisualSmokeTest extends ApplicationAdapter {
    private static final long STARTER_CAPTURE_FRAME = 3L;
    private static final long ECS_CAPTURE_FRAME = 6L;
    private static final String[] SAMPLE_NAMES = {
        "Starter Project",
        "ECS Platformer Example",
        "Multiplayer Webrtc",
        "2D Sprite Movement",
        "Shader Graph Sample"
    };

    private final ProjectGeneratorApplication generator =
            new ProjectGeneratorApplication(new DesktopProjectExportTarget(
                    "build/generated/project-generator/libfdx-game"));
    private final File starterCaptureFile;
    private final File ecsCaptureFile;
    private Fdx fdx;
    private float[] sampleChoiceY;
    private float projectNameY;
    private float platformsY;
    private float desktopY;
    private float dependencyY;
    private long renderedFrames;
    private boolean starterCaptured;
    private boolean ecsCaptured;
    private boolean renderFailed;

    private ProjectGeneratorVisualSmokeTest(File captureFile) {
        starterCaptureFile = captureFile;
        String name = captureFile.getName();
        int extension = name.lastIndexOf('.');
        String ecsName = extension >= 0
                ? name.substring(0, extension) + "-ecs" + name.substring(extension)
                : name + "-ecs.png";
        ecsCaptureFile = new File(captureFile.getParentFile(), ecsName);
    }

    /**
     * Runs the visual smoke capture.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        String capturePath = System.getProperty("libfdx.projectGenerator.visualCapture", "").trim();
        if (capturePath.length() == 0) {
            throw new IllegalArgumentException("Missing project-generator visual capture path.");
        }
        DesktopApplicationConfig config = new DesktopApplicationConfig()
                .title("libfdx Project Generator Visual Smoke")
                .size(980, 760)
                .visible(false)
                .vSync(false)
                .foregroundFps(60)
                .graphics(new DesktopOpenGLProvider());
        new DesktopApplicationBackend().start(
                config, new ProjectGeneratorVisualSmokeTest(new File(capturePath)));
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        DesktopBundledFont.install(fdx);
        generator.create(fdx);
    }

    @Override
    public void resize(int width, int height) {
        generator.resize(width, height);
    }

    @Override
    public void render() {
        try {
            renderAndCapture();
        } catch (RuntimeException | Error failure) {
            renderFailed = true;
            throw failure;
        }
    }

    private void renderAndCapture() {
        generator.render();
        renderedFrames++;
        if (!starterCaptured && renderedFrames >= STARTER_CAPTURE_FRAME) {
            UiRoot root = generatorRoot();
            recordStableAnchors(root);
            capture(starterCaptureFile);
            require(findNode(root, UiNodeType.RADIO_BUTTON, "Starter Project").checked(),
                    "Starter Project was not initially selected.");
            findNode(root, UiNodeType.RADIO_BUTTON, "ECS Platformer Example").activate();
            starterCaptured = true;
        } else if (!ecsCaptured && renderedFrames >= ECS_CAPTURE_FRAME) {
            UiRoot root = generatorRoot();
            verifyStableAnchors(root);
            require(findNode(root, UiNodeType.RADIO_BUTTON, "ECS Platformer Example").checked(),
                    "ECS Platformer Example was not selected.");
            capture(ecsCaptureFile);
            ecsCaptured = true;
            fdx.app().requestExit();
        }
    }

    @Override
    public void dispose() {
        generator.dispose();
        if (!renderFailed && (!starterCaptured || !ecsCaptured)) {
            throw new FdxException("Project generator did not capture both visual smoke states.");
        }
    }

    private UiRoot generatorRoot() {
        try {
            Field rootField = ProjectGeneratorApplication.class.getDeclaredField("root");
            rootField.setAccessible(true);
            return (UiRoot) rootField.get(generator);
        } catch (Exception error) {
            throw new FdxException("Could not inspect the project-generator UI root.", error);
        }
    }

    private void recordStableAnchors(UiRoot root) {
        sampleChoiceY = new float[SAMPLE_NAMES.length];
        for (int index = 0; index < SAMPLE_NAMES.length; index++) {
            sampleChoiceY[index] = findNode(root, UiNodeType.RADIO_BUTTON, SAMPLE_NAMES[index]).bounds().y();
        }
        projectNameY = findNode(root, UiNodeType.TEXT, "Project name").bounds().y();
        platformsY = findNode(root, UiNodeType.TEXT, "Platforms").bounds().y();
        desktopY = findNode(root, UiNodeType.CHECKBOX, "Desktop").bounds().y();
        dependencyY = findNode(root, UiNodeType.TEXT, "Dependency: libFDX snapshot").bounds().y();
    }

    private void verifyStableAnchors(UiRoot root) {
        for (int index = 0; index < SAMPLE_NAMES.length; index++) {
            requireSameY(SAMPLE_NAMES[index], sampleChoiceY[index],
                    findNode(root, UiNodeType.RADIO_BUTTON, SAMPLE_NAMES[index]).bounds());
        }
        requireSameY("Project name", projectNameY, findNode(root, UiNodeType.TEXT, "Project name").bounds());
        requireSameY("Platforms", platformsY, findNode(root, UiNodeType.TEXT, "Platforms").bounds());
        requireSameY("Desktop", desktopY, findNode(root, UiNodeType.CHECKBOX, "Desktop").bounds());
        requireSameY("Dependency", dependencyY,
                findNode(root, UiNodeType.TEXT, "Dependency: libFDX snapshot").bounds());
    }

    private UiNode findNode(UiRoot root, UiNodeType type, String text) {
        UiNode node = findNode(root.rootNode(), type, text);
        if (node == null) {
            throw new FdxException("Could not find " + type + " node: " + text);
        }
        return node;
    }

    private UiNode findNode(UiNode node, UiNodeType type, String text) {
        if (node != null && node.type() == type && text.equals(node.text())) {
            return node;
        }
        if (node != null) {
            for (UiNode child : node.children()) {
                UiNode result = findNode(child, type, text);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private void requireSameY(String label, float expected, UiRect actual) {
        require(Math.abs(expected - actual.y()) < 0.01f,
                label + " moved vertically from " + expected + " to " + actual.y() + ".");
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new FdxException(message);
        }
    }

    private void capture(File captureFile) {
        try {
            FrameBuffer frameBuffer = fdx.graphics().main().currentFrame().frameBuffer();
            int width = frameBuffer.width();
            int height = frameBuffer.height();
            ByteBuffer pixels = frameBuffer.readPixelsRgba8();
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < height; y++) {
                int sourceY = height - 1 - y;
                for (int x = 0; x < width; x++) {
                    int offset = (sourceY * width + x) * 4;
                    int red = pixels.get(offset) & 0xFF;
                    int green = pixels.get(offset + 1) & 0xFF;
                    int blue = pixels.get(offset + 2) & 0xFF;
                    int alpha = pixels.get(offset + 3) & 0xFF;
                    image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
                }
            }
            File parent = captureFile.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new FdxException("Could not create visual capture directory: " + parent);
            }
            if (!ImageIO.write(image, "png", captureFile)) {
                throw new FdxException("No PNG writer is available.");
            }
            System.out.println("[info] Captured project generator frame to " + captureFile);
        } catch (FdxException error) {
            throw error;
        } catch (Exception error) {
            throw new FdxException("Could not capture the project generator frame.", error);
        }
    }
}
