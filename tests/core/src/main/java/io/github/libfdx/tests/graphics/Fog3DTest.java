package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.g3d.DefaultModel;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.MeshPart;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Vector3;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Runs the 3D fog shader test scenario.
 *
 * @author xpenatan
 */
public final class Fog3DTest extends ApplicationAdapter {
    private static final int INSTANCE_COUNT = 5;
    private static final Color FOG_COLOR = new Color(0.12f, 0.17f, 0.24f, 1.0f);

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private ModelBatch batch;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model cubeModel;
    private DefaultModelInstance[] instances;
    private boolean created;
    private String capturePath;
    private int captureEvery;
    private int capturedFrames;
    private boolean captured;
    private long renderedFrames;

    /**
     * Creates a 3D fog test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public Fog3DTest(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        logger = fdx.logger();
        fpsLogger = TestFpsLogger.create(logger, "Fog3DTest");

        Environment3D environment = new Environment3D()
                .ambientColor(new Color(0.08f, 0.09f, 0.11f, 1.0f))
                .fog(FOG_COLOR, 3.0f, 8.2f)
                .add(new DirectionalLight()
                        .direction(-0.28f, -0.75f, -0.55f)
                        .color(new Color(1.0f, 0.93f, 0.82f, 1.0f))
                        .intensity(1.85f));
        batch = new ModelBatch(graphics).environment(environment);
        cubeModel = createCubeModel(graphics);
        instances = createInstances(cubeModel);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(62.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 40.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(0.0f, 1.2f, 5.0f, 0.05f, 0.0f, -2.6f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureEvery = Integer.parseInt(System.getProperty("libfdx.test.captureEvery", "0"));

        created = true;
        logger.info("Fog3DTest created generated PBR scene with distance fog for provider "
                + graphics.providerId());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        batch.begin(LoadOp.clear(FOG_COLOR.red(), FOG_COLOR.green(), FOG_COLOR.blue(), 1.0f), camera);
        for (int i = 0; i < instances.length; i++) {
            batch.render(instances[i]);
        }
        batch.end();
        if (capturePath != null && capturePath.length() > 0) {
            if (captureEvery > 0 && capturePath.indexOf('%') >= 0) {
                if (renderedFrames % captureEvery == 0) {
                    captureFrame(String.format(Locale.ROOT, capturePath, capturedFrames));
                    capturedFrames++;
                }
            }
            else if (!captured && renderedFrames >= 10) {
                captureFrame(capturePath);
                captured = true;
            }
        }

        renderedFrames++;
        fpsLogger.frame(deltaSeconds, renderedFrames);
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (cubeModel != null) {
            cubeModel.dispose();
            cubeModel = null;
        }
        if (!created) {
            throw new FdxException("Fog3DTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("Fog3DTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        logger.info("Fog3DTest rendered " + renderedFrames + " frames");
    }

    private DefaultModelInstance[] createInstances(Model model) {
        DefaultModelInstance[] result = new DefaultModelInstance[INSTANCE_COUNT];
        float[] x = { -1.75f, -0.72f, 0.35f, 1.38f, 0.15f };
        float[] y = { -0.18f, 0.05f, -0.08f, 0.14f, 0.42f };
        float[] z = { -0.15f, -1.55f, -2.85f, -4.25f, -5.65f };
        float[] yaw = { -0.38f, 0.25f, -0.2f, 0.42f, -0.55f };
        for (int i = 0; i < result.length; i++) {
            result[i] = new DefaultModelInstance(model);
            result[i].transform().setToTranslation(x[i], y[i], z[i])
                    .rotateY(yaw[i]);
        }
        return result;
    }

    private static Model createCubeModel(GraphicsContext graphics) {
        ArrayList<Float> positions = new ArrayList<Float>();
        ArrayList<Float> normals = new ArrayList<Float>();
        ArrayList<Float> texCoords = new ArrayList<Float>();
        ArrayList<Float> colors = new ArrayList<Float>();
        ArrayList<Float> pbr = new ArrayList<Float>();
        ArrayList<Float> emissive = new ArrayList<Float>();
        float h = 0.42f;
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -h, -h, h, h, -h, h, h, h, h, -h, h, h,
                0.0f, 0.0f, 1.0f, 0.95f, 0.42f, 0.32f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                h, -h, -h, -h, -h, -h, -h, h, -h, h, h, -h,
                0.0f, 0.0f, -1.0f, 0.30f, 0.48f, 0.94f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -h, h, h, h, h, h, h, h, -h, -h, h, -h,
                0.0f, 1.0f, 0.0f, 0.98f, 0.76f, 0.34f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -h, -h, -h, h, -h, -h, h, -h, h, -h, -h, h,
                0.0f, -1.0f, 0.0f, 0.22f, 0.68f, 0.48f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                h, -h, h, h, -h, -h, h, h, -h, h, h, h,
                1.0f, 0.0f, 0.0f, 0.62f, 0.42f, 0.92f);
        addFace(positions, normals, texCoords, colors, pbr, emissive,
                -h, -h, -h, -h, -h, h, -h, h, h, -h, h, -h,
                -1.0f, 0.0f, 0.0f, 0.28f, 0.76f, 0.86f);
        float[] sourcePositions = toFloatArray(positions);
        Mesh mesh = Mesh.positionColor3D(graphics, "fog-3d cube", sourcePositions,
                toFloatArray(colors), toFloatArray(normals), toFloatArray(texCoords),
                toFloatArray(pbr), toFloatArray(emissive), bounds(sourcePositions));
        MeshPart meshPart = new MeshPart("fog-3d cube part", mesh, null, 0, mesh.vertexCount());
        Material material = new Material("fog-3d material")
                .set(PbrAttributes.roughnessFactor(0.72f))
                .set(PbrAttributes.metallicFactor(0.02f));
        return DefaultModel.singleNode("fog-3d cube", meshPart, material);
    }

    private static void addFace(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3, float nx, float ny, float nz,
            float red, float green, float blue) {
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x0, y0, z0, nx, ny, nz, 0.0f, 1.0f, red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x1, y1, z1, nx, ny, nz, 1.0f, 1.0f, red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x2, y2, z2, nx, ny, nz, 1.0f, 0.0f, red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x0, y0, z0, nx, ny, nz, 0.0f, 1.0f, red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x2, y2, z2, nx, ny, nz, 1.0f, 0.0f, red, green, blue);
        addVertex(positions, normals, texCoords, colors, pbr, emissive,
                x3, y3, z3, nx, ny, nz, 0.0f, 0.0f, red, green, blue);
    }

    private static void addVertex(ArrayList<Float> positions, ArrayList<Float> normals,
            ArrayList<Float> texCoords, ArrayList<Float> colors, ArrayList<Float> pbr,
            ArrayList<Float> emissive, float x, float y, float z, float nx, float ny, float nz,
            float u, float v, float red, float green, float blue) {
        positions.add(x);
        positions.add(y);
        positions.add(z);
        normals.add(nx);
        normals.add(ny);
        normals.add(nz);
        texCoords.add(u);
        texCoords.add(v);
        colors.add(red);
        colors.add(green);
        colors.add(blue);
        colors.add(1.0f);
        pbr.add(1.0f);
        pbr.add(0.02f);
        pbr.add(0.72f);
        emissive.add(0.0f);
        emissive.add(0.0f);
        emissive.add(0.0f);
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
        return width > 0 ? width : 640;
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
        return height > 0 ? height : 480;
    }

    private void captureFrame(String path) {
        try {
            ByteBuffer pixels = FramebufferCapture.readPixelsRgba8(graphics);
            int width = framebufferWidth();
            int height = framebufferHeight();
            FramebufferCapture.validateSceneFrame(width, height, pixels);
            FramebufferCapture.writePpm(path, width, height, pixels);
            logger.info("Fog3DTest captured framebuffer to " + path);
        } catch (Exception e) {
            throw new FdxException("Could not capture Fog3DTest framebuffer", e);
        }
    }

    private static BoundingBox bounds(float[] positions) {
        float minX = positions[0];
        float minY = positions[1];
        float minZ = positions[2];
        float maxX = minX;
        float maxY = minY;
        float maxZ = minZ;
        for (int i = 3; i < positions.length; i += 3) {
            minX = Math.min(minX, positions[i]);
            minY = Math.min(minY, positions[i + 1]);
            minZ = Math.min(minZ, positions[i + 2]);
            maxX = Math.max(maxX, positions[i]);
            maxY = Math.max(maxY, positions[i + 1]);
            maxZ = Math.max(maxZ, positions[i + 2]);
        }
        return BoundingBox.of(new Vector3(minX, minY, minZ), new Vector3(maxX, maxY, maxZ));
    }

    private static float[] toFloatArray(ArrayList<Float> values) {
        float[] result = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }
}
