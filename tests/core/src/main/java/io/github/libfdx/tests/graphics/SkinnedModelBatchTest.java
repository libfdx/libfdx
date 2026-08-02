package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.collections.Array;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.camera.controller.OrbitCameraController3D;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.g3d.AnimationClip;
import io.github.libfdx.graphics.g3d.Bone;
import io.github.libfdx.graphics.g3d.CpuSkinnedModelAnimator;
import io.github.libfdx.graphics.g3d.DefaultModel;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.MeshPart;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.ModelNode;
import io.github.libfdx.graphics.g3d.ModelNodePart;
import io.github.libfdx.graphics.g3d.PbrMaterial;
import io.github.libfdx.graphics.g3d.Skeleton;
import io.github.libfdx.graphics.g3d.ShaderGraphPbrTestSupport;
import io.github.libfdx.graphics.g3d.Skin;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;
import io.github.libfdx.math.Vector3;
import io.github.libfdx.tests.TestFpsLogger;

import java.nio.ByteBuffer;

/**
 * Runs the skinned model batch test scenario.
 *
 * @author xpenatan
 */
public final class SkinnedModelBatchTest extends ApplicationAdapter {
    private static final int STRIP_ROWS = 9;
    private static final int STRIP_SEGMENTS = STRIP_ROWS - 1;
    private static final int STRIP_VERTICES = STRIP_SEGMENTS * 6;
    private static final float STRIP_WIDTH = 0.34f;
    private static final float STRIP_BOTTOM = -0.82f;
    private static final float STRIP_TOP = 0.82f;
    private static final float BONE_BASE_Y = STRIP_BOTTOM;
    private static final float BONE_MIDDLE_Y = 0.0f;
    private static final float BONE_TIP_Y = STRIP_TOP;

    private final long exitAfterFrames;
    private Application application;
    private Display display;
    private Logger logger;
    private TestFpsLogger fpsLogger;
    private GraphicsContext graphics;
    private ModelBatch batch;
    private ShaderProvider graphShaderProvider;
    private Camera camera;
    private OrbitCameraController3D cameraInput;
    private Model model;
    private DefaultModelInstance instance;
    private CpuSkinnedModelAnimator animation;
    private String capturePath;
    private long captureFrame;
    private boolean captured;
    private boolean created;
    private long renderedFrames;

    /**
     * Creates a skinned model batch test.
     *
     * @param exitAfterFrames the exit after frames
     */
    public SkinnedModelBatchTest(long exitAfterFrames) {
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
        fpsLogger = TestFpsLogger.create(logger, "SkinnedModelBatchTest");
        if (Boolean.getBoolean("libfdx.test.shaderGraphPbr")) {
            graphShaderProvider = ShaderGraphPbrTestSupport.provider(graphics);
            batch = new ModelBatch(graphics, new ModelBatchConfig()
                    .shaderProvider(graphShaderProvider));
        } else {
            batch = new ModelBatch(graphics);
        }
        batch.environment(new Environment3D()
                .ambientColor(new Color(0.42f, 0.42f, 0.45f, 1.0f))
                .add(new DirectionalLight().direction(-0.35f, -0.75f, -1.0f).intensity(1.35f)));
        model = createModel();
        instance = new DefaultModelInstance(model);
        animation = new CpuSkinnedModelAnimator(graphics, instance).play(animation(), true);
        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(58.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 20.0f);
        cameraInput = new OrbitCameraController3D(fdx.input(), camera)
                .position(0.0f, 0.1f, 2.35f, 0.0f, 0.1f, 0.0f)
                .autoOrbit(TestCameraControllers.autoOrbitEnabled(), 0.75f, exitAfterFrames,
                        TestCameraControllers.autoOrbitStartDegrees(), TestCameraControllers.autoOrbitDegrees());
        capturePath = System.getProperty("libfdx.test.capture", "");
        captureFrame = Long.parseLong(System.getProperty("libfdx.test.captureFrame", "44"));
        created = true;
        logger.info("SkinnedModelBatchTest created with graphics provider " + graphics.providerId());
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        float deltaSeconds = application.deltaTime();
        animation.time((renderedFrames % 120L) / 60.0f);
        camera.viewport(framebufferWidth(), framebufferHeight());
        cameraInput.update(deltaSeconds);
        batch.begin(LoadOp.clear(0.035f, 0.04f, 0.055f, 1.0f), camera);
        try {
            batch.render(instance);
        } catch (RuntimeException failure) {
            failure.printStackTrace(System.err);
            throw failure;
        } finally {
            try {
                batch.end();
            } catch (RuntimeException failure) {
                failure.printStackTrace(System.err);
                throw failure;
            }
        }
        if (capturePath != null && capturePath.length() > 0 && !captured && renderedFrames >= captureFrame) {
            captureFrame(capturePath);
            captured = true;
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
        ShaderGraphPbrTestSupport.dispose(graphShaderProvider);
        graphShaderProvider = null;
        if (model != null) {
            model.dispose();
            model = null;
        }
        if (!created) {
            throw new FdxException("SkinnedModelBatchTest did not create graphics resources");
        }
        if (exitAfterFrames > 0L && renderedFrames < exitAfterFrames) {
            throw new FdxException("SkinnedModelBatchTest rendered " + renderedFrames + " of "
                    + exitAfterFrames + " required frames");
        }
        if (capturePath != null && capturePath.length() > 0 && !captured) {
            throw new FdxException("SkinnedModelBatchTest did not capture framebuffer to " + capturePath);
        }
        logger.info("SkinnedModelBatchTest rendered " + renderedFrames + " frames");
    }

    private Model createModel() {
        Skin skin = skin();
        float[] positions = new float[STRIP_VERTICES * 3];
        float[] colors = new float[STRIP_VERTICES * 4];
        float[] normals = new float[STRIP_VERTICES * 3];
        float[] texCoords = new float[STRIP_VERTICES * 2];
        float[] pbr = new float[STRIP_VERTICES * 3];
        float[] emissive = new float[STRIP_VERTICES * 3];
        int[] joints = new int[STRIP_VERTICES * 4];
        float[] weights = new float[STRIP_VERTICES * 4];
        int vertex = 0;
        for (int segment = 0; segment < STRIP_SEGMENTS; segment++) {
            vertex = writeStripVertex(vertex, segment, -1.0f, positions, colors, normals, texCoords, pbr,
                    emissive, joints, weights);
            vertex = writeStripVertex(vertex, segment, 1.0f, positions, colors, normals, texCoords, pbr,
                    emissive, joints, weights);
            vertex = writeStripVertex(vertex, segment + 1, 1.0f, positions, colors, normals, texCoords, pbr,
                    emissive, joints, weights);
            vertex = writeStripVertex(vertex, segment, -1.0f, positions, colors, normals, texCoords, pbr,
                    emissive, joints, weights);
            vertex = writeStripVertex(vertex, segment + 1, 1.0f, positions, colors, normals, texCoords, pbr,
                    emissive, joints, weights);
            vertex = writeStripVertex(vertex, segment + 1, -1.0f, positions, colors, normals, texCoords, pbr,
                    emissive, joints, weights);
        }
        Mesh mesh = Mesh.positionColor3D(graphics, "skinned ribbon",
                positions,
                colors,
                null,
                normals,
                texCoords,
                pbr,
                null,
                emissive,
                null,
                null,
                null,
                BoundingBox.of(new Vector3(-1.35f, -1.15f, -0.16f), new Vector3(1.35f, 1.15f, 0.16f)),
                true);
        PbrMaterial material = new PbrMaterial("skinned material")
                .baseColor(1.0f, 1.0f, 1.0f, 1.0f)
                .roughnessFactor(0.82f)
                .doubleSided(true);
        ModelNode root = new ModelNode("root").addPart(new ModelNodePart(
                new MeshPart("skinned ribbon part", mesh, null, 0, mesh.vertexCount()), material, skin,
                joints, weights));
        ModelNode base = new ModelNode("bone-base").localTransform(Matrix4.translation(0.0f, BONE_BASE_Y, 0.0f));
        ModelNode middle = new ModelNode("bone-middle")
                .localTransform(Matrix4.translation(0.0f, BONE_MIDDLE_Y - BONE_BASE_Y, 0.0f));
        ModelNode tip = new ModelNode("bone-tip")
                .localTransform(Matrix4.translation(0.0f, BONE_TIP_Y - BONE_MIDDLE_Y, 0.0f));
        root.addChild(base);
        base.addChild(middle);
        middle.addChild(tip);
        Array<ModelNode> nodes = new Array<ModelNode>();
        nodes.add(root);
        Array<Material> materials = new Array<Material>();
        materials.add(material);
        Array<Skin> skins = new Array<Skin>();
        skins.add(skin);
        Array<Mesh> meshes = new Array<Mesh>();
        meshes.add(mesh);
        return new DefaultModel(nodes, materials, new Array<AnimationClip>(0), skins, meshes);
    }

    private Skin skin() {
        Array<Bone> bones = new Array<Bone>();
        bones.add(new Bone("bone-base", -1, Matrix4.translation(0.0f, -BONE_BASE_Y, 0.0f)));
        bones.add(new Bone("bone-middle", 0, Matrix4.translation(0.0f, -BONE_MIDDLE_Y, 0.0f)));
        bones.add(new Bone("bone-tip", 1, Matrix4.translation(0.0f, -BONE_TIP_Y, 0.0f)));
        return new Skin("skin", new Skeleton(bones));
    }

    private AnimationClip animation() {
        return new AnimationClip("bone-wave", 2.0f, new AnimationClip.NodeTransformChannel[] {
                AnimationClip.nodeTransform("bone-base",
                        zRotationKeyframe(0.0f, BONE_BASE_Y, -7.0f),
                        zRotationKeyframe(1.0f, BONE_BASE_Y, 7.0f),
                        zRotationKeyframe(2.0f, BONE_BASE_Y, -7.0f)),
                AnimationClip.nodeTransform("bone-middle",
                        zRotationKeyframe(0.0f, BONE_MIDDLE_Y - BONE_BASE_Y, 18.0f),
                        zRotationKeyframe(1.0f, BONE_MIDDLE_Y - BONE_BASE_Y, -24.0f),
                        zRotationKeyframe(2.0f, BONE_MIDDLE_Y - BONE_BASE_Y, 18.0f)),
                AnimationClip.nodeTransform("bone-tip",
                        zRotationKeyframe(0.0f, BONE_TIP_Y - BONE_MIDDLE_Y, -38.0f),
                        zRotationKeyframe(1.0f, BONE_TIP_Y - BONE_MIDDLE_Y, 36.0f),
                        zRotationKeyframe(2.0f, BONE_TIP_Y - BONE_MIDDLE_Y, -38.0f))
        });
    }

    private int writeStripVertex(int vertex, int row, float side, float[] positions, float[] colors, float[] normals,
            float[] texCoords, float[] pbr, float[] emissive, int[] joints, float[] weights) {
        float t = row / (float)(STRIP_ROWS - 1);
        float y = STRIP_BOTTOM + (STRIP_TOP - STRIP_BOTTOM) * t;
        float width = STRIP_WIDTH * (1.0f - t * 0.28f);
        int positionOffset = vertex * 3;
        int colorOffset = vertex * 4;
        int texCoordOffset = vertex * 2;
        int influenceOffset = vertex * 4;
        positions[positionOffset] = side * width;
        positions[positionOffset + 1] = y;
        positions[positionOffset + 2] = 0.0f;
        normals[positionOffset] = 0.0f;
        normals[positionOffset + 1] = 0.0f;
        normals[positionOffset + 2] = 1.0f;
        texCoords[texCoordOffset] = side > 0.0f ? 1.0f : 0.0f;
        texCoords[texCoordOffset + 1] = t;
        colors[colorOffset] = 0.28f + t * 0.62f;
        colors[colorOffset + 1] = 0.88f - t * 0.34f;
        colors[colorOffset + 2] = 1.0f - t * 0.56f;
        colors[colorOffset + 3] = 1.0f;
        pbr[positionOffset] = 1.0f;
        pbr[positionOffset + 1] = 0.0f;
        pbr[positionOffset + 2] = 0.72f;
        emissive[positionOffset] = 0.03f + t * 0.04f;
        emissive[positionOffset + 1] = 0.05f + t * 0.02f;
        emissive[positionOffset + 2] = 0.08f;
        writeSkinWeights(t, influenceOffset, joints, weights);
        return vertex + 1;
    }

    private void writeSkinWeights(float t, int influenceOffset, int[] joints, float[] weights) {
        if (t < 0.5f) {
            joints[influenceOffset] = 0;
            joints[influenceOffset + 1] = 1;
            weights[influenceOffset] = 1.0f - t * 2.0f;
            weights[influenceOffset + 1] = t * 2.0f;
        } else {
            joints[influenceOffset] = 1;
            joints[influenceOffset + 1] = 2;
            weights[influenceOffset] = 1.0f - (t - 0.5f) * 2.0f;
            weights[influenceOffset + 1] = (t - 0.5f) * 2.0f;
        }
        joints[influenceOffset + 2] = 0;
        joints[influenceOffset + 3] = 0;
        weights[influenceOffset + 2] = 0.0f;
        weights[influenceOffset + 3] = 0.0f;
    }

    private AnimationClip.TransformKeyframe zRotationKeyframe(float timeSeconds, float y, float degrees) {
        float radians = (float)Math.toRadians(degrees);
        float half = radians * 0.5f;
        return AnimationClip.keyframe(timeSeconds, 0.0f, y, 0.0f,
                0.0f, 0.0f, (float)Math.sin(half), (float)Math.cos(half),
                1.0f, 1.0f, 1.0f);
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
            logger.info("SkinnedModelBatchTest captured framebuffer to " + path);
        }
        catch (Exception e) {
            throw new FdxException("Could not capture SkinnedModelBatchTest framebuffer", e);
        }
    }
}
