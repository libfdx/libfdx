package io.github.libfdx.ecs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationLifecycle;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.display.Displays;
import io.github.libfdx.ecs.component.TransformComponent;
import io.github.libfdx.ecs.manager.CameraManager;
import io.github.libfdx.ecs.system.RenderSystem;
import io.github.libfdx.ecs.system.UiRenderSystem;
import io.github.libfdx.ecs.system.UpdateSystem;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.CommandEncoder;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.Graphics;
import io.github.libfdx.graphics.GraphicsAttachment;
import io.github.libfdx.graphics.GraphicsConfig;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.GraphicsDevice;
import io.github.libfdx.graphics.GraphicsFrame;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.TextureView;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.input.Input;
import io.github.libfdx.net.Network;
import io.github.libfdx.storage.Storage;
import org.junit.jupiter.api.Test;

final class EcsApplicationTest {
    @Test
    void exposesExactBundleAbiVersions() {
        assertEquals(6, EcsProjectFormat.PROJECT_ABI);
        assertEquals("0.0.2", EcsProjectFormat.LIBFDX_ABI);
        assertEquals(2, EcsProjectFormat.PROJECT_VERSION);
    }

    @Test
    void adaptsWorldCapabilitiesToApplicationLifecycle() {
        RecordingProject project = new RecordingProject(true);
        EcsApplication application = new EcsApplication(project);
        TestFdx fdx = new TestFdx(0.25f);

        application.create(fdx);
        application.resize(640, 360);
        application.render();
        application.onFrameEnd();
        application.pause();
        application.resume();

        assertSame(project, application.project());
        assertSame(fdx, project.fdx);
        assertSame(application.world(), project.world);
        assertEquals(1, project.system.updateCount);
        assertEquals(0.25f, project.system.lastDelta);
        assertEquals(1, project.renderer.renderCount);
        assertEquals(1, project.renderer.uiRenderCount);
        assertSame(project.managedGameCamera, project.renderer.renderCamera);
        assertSame(project.managedUiCamera, project.renderer.uiRenderCamera);
        assertEquals(320, project.renderer.renderWidth);
        assertEquals(180, project.renderer.renderHeight);

        application.dispose();

        assertEquals(1, project.renderer.detachCount);
        assertEquals(1, project.system.detachCount);
        assertEquals(0, application.world().managerCount());
        assertEquals(0, application.world().systemCount());
        assertThrows(IllegalStateException.class, application::render);
        application.dispose();
        assertEquals(1, project.renderer.detachCount);
    }

    @Test
    void initializationFailureTerminatesApplication() {
        EcsApplication application = new EcsApplication((fdx, world) -> {
            throw new IllegalStateException("failed");
        });

        assertThrows(IllegalStateException.class, () -> application.create(new TestFdx(0.1f)));
        assertThrows(IllegalStateException.class, () -> application.create(new TestFdx(0.1f)));
    }

    @Test
    void initializationFailureDiscardsQueuedSetupWithoutExecutingIt() {
        RecordingRenderer renderer = new RecordingRenderer();
        RecordingSystem system = new RecordingSystem();
        IllegalStateException initializationFailure = new IllegalStateException("failed");
        EcsApplication application = new EcsApplication((fdx, world) -> {
            int entity = world.createEntity();
            world.add(entity, new TransformComponent());
            world.addSystem(renderer);
            world.addSystem(system);
            throw initializationFailure;
        });

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> application.create(new TestFdx(0.1f)));

        assertSame(initializationFailure, thrown);
        assertEquals(0, renderer.detachCount);
        assertEquals(0, system.detachCount);
        assertEquals(0, application.world().entityCount());
        assertEquals(0, application.world().managerCount());
        assertEquals(0, application.world().systemCount());
        assertEquals(0, application.world().commands().size());
    }

    @Test
    void disposalDiscardsQueuedWorkWithoutExecutingIt() {
        RecordingSystem attached = new RecordingSystem();
        RecordingRenderer pending = new RecordingRenderer();
        EcsApplication application = new EcsApplication((fdx, world) -> world.addSystem(attached));
        application.create(new TestFdx(0.1f));
        World world = application.world();
        int pendingEntity = world.createEntity();
        world.add(pendingEntity, new TransformComponent());
        world.addSystem(pending);

        application.dispose();

        assertEquals(1, attached.detachCount);
        assertEquals(0, pending.detachCount);
        assertEquals(0, world.entityCount());
        assertEquals(0, world.managerCount());
        assertEquals(0, world.systemCount());
        assertEquals(0, world.commands().size());
    }

    @Test
    void supportsCameraFreeRenderingWithoutAManager() {
        RecordingProject project = new RecordingProject(false);
        EcsApplication application = new EcsApplication(project);

        application.create(new TestFdx(0.1f));
        application.render();

        assertNull(project.renderer.renderCamera);
        assertNull(project.renderer.uiRenderCamera);
        application.dispose();
    }

    private static final class RecordingProject implements EcsProject {
        final boolean registerCameraManager;
        final Camera managedGameCamera = new Camera();
        final Camera managedUiCamera = new Camera();
        Fdx fdx;
        World world;
        RecordingRenderer renderer;
        RecordingSystem system;

        RecordingProject(boolean registerCameraManager) {
            this.registerCameraManager = registerCameraManager;
        }

        public void initialize(Fdx fdx, World world) {
            this.fdx = fdx;
            this.world = world;
            renderer = new RecordingRenderer();
            system = new RecordingSystem();
            world.addSystem(renderer);
            world.addSystem(system);
            if (registerCameraManager) {
                world.addManager(
                        new CameraManager().game(managedGameCamera).ui(managedUiCamera),
                        CameraManager.class);
            }
        }
    }

    private static final class RecordingRenderer implements RenderSystem, UiRenderSystem {
        Camera renderCamera;
        Camera uiRenderCamera;
        int renderCount;
        int uiRenderCount;
        int detachCount;
        int renderWidth;
        int renderHeight;
        boolean enabled = true;

        public void onAttach(World world) {
        }

        public void onDetach(World world) {
            detachCount++;
        }

        public void render(
                GraphicsFrame frame,
                TextureView colorTarget,
                TextureView depthTarget,
                int width,
                int height,
                Camera camera) {
            renderCount++;
            renderCamera = camera;
            renderWidth = width;
            renderHeight = height;
        }

        public void renderUi(
                GraphicsFrame frame,
                TextureView colorTarget,
                TextureView depthTarget,
                int width,
                int height,
                Camera camera) {
            uiRenderCount++;
            uiRenderCamera = camera;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    private static final class RecordingSystem implements UpdateSystem {
        World world;
        int updateCount;
        int detachCount;
        float lastDelta;
        boolean enabled = true;

        public void onAttach(World world) {
            this.world = world;
        }

        public void onDetach(World world) {
            detachCount++;
            this.world = null;
        }

        public void update() {
            updateCount++;
            lastDelta = world.deltaTime();
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    private static final class TestFdx implements Fdx {
        private final TestApplication application;
        private final TestGraphics graphics = new TestGraphics();

        TestFdx(float deltaTime) {
            application = new TestApplication(deltaTime);
        }

        public Application app() { return application; }
        public Displays displays() { return null; }
        public Graphics graphics() { return graphics; }
        public Input input() { return null; }
        public FileSystem files() { return null; }
        public Storage storage() { return null; }
        public Network network() { return null; }
        public Logger logger() { return null; }
    }

    private static final class TestApplication implements Application {
        private final float deltaTime;

        TestApplication(float deltaTime) {
            this.deltaTime = deltaTime;
        }

        public ApplicationLifecycle lifecycle() { return null; }
        public float deltaTime() { return deltaTime; }
        public long frameId() { return 1L; }
        public void requestExit() { }
        public ProviderId providerId() { return TEST_PROVIDER; }
        public <T> T as() { return null; }
    }

    private static final class TestGraphics implements Graphics {
        private final GraphicsContext context = new TestGraphicsContext();

        public GraphicsContext main() { return context; }
        public boolean supportsMultiple() { return false; }
        public GraphicsAttachment create(GraphicsConfig config) { return null; }
    }

    private static final class TestGraphicsContext implements GraphicsContext {
        private final GraphicsFrame frame = new TestGraphicsFrame();

        public GraphicsDevice device() { return null; }
        public TextureFormat surfaceFormat() { return TextureFormat.RGBA8_UNORM; }
        public GraphicsFrame currentFrame() { return frame; }
        public void clear(float red, float green, float blue, float alpha) { }
        public ProviderId providerId() { return TEST_PROVIDER; }
        public <T> T as() { return null; }
    }

    private static final class TestGraphicsFrame implements GraphicsFrame {
        private final TextureView color = new TestTextureView();

        public CommandEncoder commandEncoder() { return null; }
        public FrameBuffer frameBuffer() { return null; }
        public TextureView colorAttachment() { return color; }
        public int width() { return 320; }
        public int height() { return 180; }
        public ProviderId providerId() { return TEST_PROVIDER; }
        public <T> T as() { return null; }
    }

    private static final class TestTextureView implements TextureView {
        public TextureFormat format() { return TextureFormat.RGBA8_UNORM; }
        public ProviderId providerId() { return TEST_PROVIDER; }
        public <T> T as() { return null; }
    }

    private static final ProviderId TEST_PROVIDER = ProviderId.of("test");
}
