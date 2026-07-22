package io.github.libfdx.ecs.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationLifecycle;
import io.github.libfdx.core.Logger;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.display.Displays;
import io.github.libfdx.ecs.World;
import io.github.libfdx.ecs.tooling.schema.EcsEntityAdapter;
import io.github.libfdx.ecs.tooling.schema.EcsProjectSchema;
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
import io.github.libfdx.input.Input;
import io.github.libfdx.net.Network;
import io.github.libfdx.storage.Storage;
import org.junit.jupiter.api.Test;

final class EcsProjectApplicationTest {
    @Test
    void exposesExactBundleAbiVersions() {
        assertEquals(1, EcsTooling.TOOLING_ABI);
        assertEquals("0.0.2", EcsTooling.LIBFDX_ABI);
    }

    @Test
    void ownsNormalizedProjectMetadata() {
        EcsProject project = new TestProject(null);

        assertEquals("test.project", project.id());
        assertEquals("Test", project.name());
        assertEquals("assets", project.assetsPath());
        assertEquals("scenes/main.fdxscene", project.defaultScene());
    }

    @Test
    void adaptsSeparateRuntimeCallbacksToApplicationLifecycle() {
        RecordingRuntime runtime = new RecordingRuntime();
        EcsProject project = new TestProject(runtime);
        EcsProjectApplication application = new EcsProjectApplication(project);
        TestFdx fdx = new TestFdx(0.25f);

        application.create(fdx);
        application.resize(640, 360);
        application.render();
        application.onFrameEnd();
        application.pause();
        application.resume();

        assertSame(runtime, application.runtime());
        assertSame(fdx, runtime.fdx);
        assertEquals(640, runtime.width);
        assertEquals(360, runtime.height);
        assertEquals(1, runtime.updateCount);
        assertEquals(0.25f, runtime.lastDelta);
        assertEquals(1, runtime.renderCount);
        assertSame(runtime.world, runtime.renderWorld);
        assertEquals(EcsRenderPurpose.GAME, runtime.renderPurpose);
        assertEquals(1, runtime.frameEndCount);
        assertEquals(1, runtime.pauseCount);
        assertEquals(1, runtime.resumeCount);

        application.dispose();

        assertEquals(1, runtime.disposeCount);
        assertThrows(IllegalStateException.class, application::runtime);
        application.dispose();
        assertEquals(1, runtime.disposeCount);
    }

    @Test
    void rejectsNullRuntimeAndTerminatesApplication() {
        EcsProject project = new TestProject(null);
        EcsProjectApplication application = new EcsProjectApplication(project);

        assertThrows(IllegalStateException.class, () -> application.create(new TestFdx(0.1f)));
        assertThrows(IllegalStateException.class, () -> application.create(new TestFdx(0.1f)));
    }

    private static final class TestProject extends EcsProject {
        private static final EcsProjectSchema SCHEMA = EcsProjectSchema.builder(new EmptyEntityAdapter()).build();
        private final EcsProjectRuntime runtime;

        TestProject(EcsProjectRuntime runtime) {
            super(" test.project ", " Test ", "./assets", "scenes\\main.fdxscene");
            this.runtime = runtime;
        }

        public EcsProjectSchema schema() {
            return SCHEMA;
        }

        public EcsProjectRuntime createRuntime() {
            return runtime;
        }
    }

    private static final class RecordingRuntime implements EcsProjectRuntime {
        final World world = new World();
        Fdx fdx;
        World renderWorld;
        EcsRenderPurpose renderPurpose;
        int width;
        int height;
        int updateCount;
        int renderCount;
        int frameEndCount;
        int pauseCount;
        int resumeCount;
        int disposeCount;
        float lastDelta;

        public void create(Fdx fdx) {
            this.fdx = fdx;
        }

        public World world() {
            return world;
        }

        public void resize(int width, int height) {
            this.width = width;
            this.height = height;
        }

        public void update(float deltaTime) {
            updateCount++;
            lastDelta = deltaTime;
        }

        public void render(EcsRenderContext context) {
            renderCount++;
            renderWorld = context.world();
            renderPurpose = context.purpose();
            assertEquals(320, context.width());
            assertEquals(180, context.height());
        }

        public void onFrameEnd() {
            frameEndCount++;
        }

        public void pause() {
            pauseCount++;
        }

        public void resume() {
            resumeCount++;
        }

        public void dispose() {
            disposeCount++;
        }
    }

    private static final class EmptyEntityAdapter implements EcsEntityAdapter {
        public int create(World world, long persistentId, String name) { return world.createEntity(); }
        public long persistentId(World world, int entity) { return entity; }
        public String name(World world, int entity) { return ""; }
        public void name(World world, int entity, String name) { }
        public long parentId(World world, int entity) { return 0L; }
        public void parentId(World world, int entity, long parentId) { }
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
