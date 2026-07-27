package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shader.runtime.ShaderProvider;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.SpriteBatchConfig;
import io.github.libfdx.graphics.g2d.StandardSpriteTechnique;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment3D;
import io.github.libfdx.graphics.g3d.GraphPbrMaterial;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.StandardPbrTechnique;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCacheContext;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphCompileOptions;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocument;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphDocumentCodec;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphMaterialDefinition;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeAsset;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeGraph;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeLoader;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.math.Color;
import io.github.libfdx.math.Matrix4;

import java.nio.ByteBuffer;

/**
 * Public headless shader-graph sample.
 *
 * <p>The application demonstrates direct Java authoring, serialized graph
 * loading, standard PBR surface replacement, the common ShaderProvider
 * contract for both batch families, and atomic last-good reload behavior. It
 * deliberately has no UI Kit dependency.</p>
 */
public final class ShaderGraphSampleApplication extends ApplicationAdapter {
    private static final int CHECKER_SIZE = 16;
    private static final ShaderGraphType VEC4 =
            ShaderGraphType.vector(ShaderScalarType.F32, 4);

    private final long exitAfterFrames;
    private final InputAdapter reloadInput = new InputAdapter() {
        @Override
        public boolean keyDown(KeyEvent event) {
            if (event.key() == Key.R) {
                reloadFromAsset();
                return true;
            }
            return false;
        }
    };
    private final Matrix4 translation = new Matrix4();
    private final Matrix4 rotation = new Matrix4();
    private final Matrix4 transform = new Matrix4();

    private Application application;
    private Display display;
    private FileSystem files;
    private GraphicsContext graphics;
    private Input input;
    private Logger logger;
    private Camera camera;
    private Environment3D environment;
    private ModelBatch modelBatch;
    private SpriteBatch spriteBatch;
    private ShaderProvider modelShaderProvider;
    private ShaderProvider spriteShaderProvider;
    private ShaderGraphProvider modelGraphProvider;
    private ShaderGraphProvider spriteGraphProvider;
    private StandardPbrTechnique activePbrTechnique;
    private Model floorModel;
    private Model leftModel;
    private Model rightModel;
    private DefaultModelInstance floor;
    private DefaultModelInstance left;
    private DefaultModelInstance right;
    private Texture spriteTexture;
    private TextureRegion spriteRegion;
    private String capturePath;
    private long captureFrame;
    private long renderedFrames;
    private boolean captured;

    /**
     * Creates an interactive sample.
     */
    public ShaderGraphSampleApplication() {
        this(0L);
    }

    /**
     * Creates a sample with an optional finite validation run.
     *
     * @param exitAfterFrames frames to render, or zero to run interactively
     */
    public ShaderGraphSampleApplication(long exitAfterFrames) {
        this.exitAfterFrames = exitAfterFrames;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        files = fdx.files();
        graphics = fdx.graphics().main();
        input = fdx.input();
        logger = fdx.logger();

        activePbrTechnique = selectedTechnique();
        ShaderGraph surface = activePbrTechnique.surfaceGraph();
        modelGraphProvider = new ShaderGraphProvider(graphics,
                activePbrTechnique.technique());
        spriteGraphProvider = new ShaderGraphProvider(graphics,
                StandardSpriteTechnique.compile(graphics));
        modelShaderProvider = modelGraphProvider;
        spriteShaderProvider = spriteGraphProvider;

        environment = new Environment3D()
                .ambientColor(new Color(0.065f, 0.075f, 0.105f, 1.0f))
                .neutralToneMapping(1.18f)
                .add(new DirectionalLight()
                        .direction(-0.32f, -0.84f, -0.42f)
                        .color(new Color(1.0f, 0.91f, 0.78f, 1.0f))
                        .intensity(2.15f));
        modelBatch = new ModelBatch(graphics,
                new ModelBatchConfig()
                        .shaderProvider(modelShaderProvider))
                .environment(environment);
        spriteBatch = new SpriteBatch(graphics,
                new SpriteBatchConfig()
                        .shaderProvider(spriteShaderProvider));

        GraphPbrMaterial floorMaterial = material("floor", 0.08f,
                0.30f, 0.36f, 0.48f, 0.86f);
        GraphPbrMaterial leftMaterial = material("cool", 0.0f,
                0.20f, 0.62f, 0.95f, 0.34f);
        GraphPbrMaterial rightMaterial = material("warm", 1.0f,
                0.98f, 0.38f, 0.12f, 0.56f);
        floorModel = ShaderGraphSampleModelFactory.box(graphics,
                "shader graph floor", 7.0f, 0.12f, 5.0f,
                floorMaterial);
        leftModel = ShaderGraphSampleModelFactory.box(graphics,
                "shader graph cool cube", 1.35f, 1.35f, 1.35f,
                leftMaterial);
        rightModel = ShaderGraphSampleModelFactory.box(graphics,
                "shader graph warm cube", 1.35f, 1.35f, 1.35f,
                rightMaterial);
        floor = new DefaultModelInstance(floorModel)
                .transform(Matrix4.translation(0.0f, -1.05f, -1.2f));
        left = new DefaultModelInstance(leftModel);
        right = new DefaultModelInstance(rightModel);

        camera = new Camera()
                .projection(CameraProjection.PERSPECTIVE)
                .fieldOfView(58.0f)
                .viewport(framebufferWidth(), framebufferHeight())
                .nearFar(0.1f, 60.0f)
                .position(5.2f, 3.1f, 6.7f)
                .lookAt(0.0f, -0.10f, -1.2f)
                .update();
        spriteTexture = createCheckerTexture();
        spriteRegion = new TextureRegion(spriteTexture);
        capturePath =
                System.getProperty("libfdx.sample.capture", "");
        captureFrame = Long.parseLong(System.getProperty(
                "libfdx.sample.captureFrame", "3"));
        input.addProcessor(reloadInput);

        if (Boolean.getBoolean("libfdx.sample.validateReload")) {
            String uncachedSource = ShaderGraphDocumentCodec.write(
                    ShaderGraphDocument.of(
                            ShaderGraphSampleGraphs
                                    .codeAuthoredSurface()));
            ShaderGraphRuntimeAsset miss =
                    loadRuntimeSurface(
                            uncachedSource,
                            "uncached validation source");
            if (!miss.cacheMiss()) {
                throw new FdxException(
                        "Uncached shader graph did not compile "
                                + "through a cache miss");
            }
            String embeddedSource = ShaderGraphDocumentCodec.write(
                    miss.documentWithCompiledCache());
            ShaderGraphRuntimeAsset hit =
                    loadRuntimeSurface(
                            embeddedSource,
                            "embedded-cache validation source");
            if (!hit.cacheHit()) {
                throw new FdxException(
                        "Embedded shader graph cache was not used");
            }
            requireEquivalentRuntimeGraph(miss.graph(), hit.graph());

            long revision = modelGraphProvider.revision();
            if (!replaceSurface(embeddedSource,
                    "valid embedded-cache validation source")
                    || modelGraphProvider.revision() <= revision) {
                throw new FdxException(
                        "Valid shader reload was not published");
            }
            revision = modelGraphProvider.revision();
            if (replaceSurface("{\"format\":1}", "invalid validation source")
                    || revision != modelGraphProvider.revision()) {
                throw new FdxException(
                        "Failed shader reload replaced the last-good technique");
            }
            logger.info("Shader graph sample verified one-file cache "
                    + "miss/hit parity and last-good reload behavior");
        }
        logger.info("Shader graph sample loaded "
                + surface.id() + " through "
                + graphSource() + " authoring for provider "
                + graphics.providerId().value());
    }

    @Override
    public void resize(int width, int height) {
        if (camera != null && width > 0 && height > 0) {
            camera.viewport(width, height).update();
        }
    }

    @Override
    public void render() {
        float angle = renderedFrames * 0.0125f;
        setTransform(left, -1.35f, -0.22f, -1.15f, angle);
        setTransform(right, 1.35f, -0.22f, -1.15f, -angle * 0.82f);

        modelBatch.begin(LoadOp.clear(
                0.026f, 0.036f, 0.065f, 1.0f), camera);
        modelBatch.render(floor);
        modelBatch.render(left);
        modelBatch.render(right);
        modelBatch.end();

        spriteBatch.begin(LoadOp.load());
        spriteBatch.color(0.34f, 0.78f, 1.0f, 0.95f);
        spriteBatch.draw(spriteRegion,
                -0.93f, 0.70f, 0.20f, 0.20f);
        spriteBatch.color(1.0f, 0.58f, 0.24f, 0.95f);
        spriteBatch.draw(spriteRegion,
                -0.69f, 0.70f, 0.20f, 0.20f);
        spriteBatch.color(1.0f, 1.0f, 1.0f, 1.0f);
        spriteBatch.end();

        if (!captured && capturePath != null
                && !capturePath.isBlank()
                && renderedFrames >= captureFrame) {
            capture(capturePath);
            captured = true;
        }
        renderedFrames++;
        if (exitAfterFrames > 0L
                && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        if (input != null) {
            input.removeProcessor(reloadInput);
        }
        dispose(spriteBatch);
        dispose(modelBatch);
        dispose(spriteTexture);
        dispose(rightModel);
        dispose(leftModel);
        dispose(floorModel);
        dispose(spriteGraphProvider);
        dispose(modelGraphProvider);
        spriteBatch = null;
        modelBatch = null;
        spriteTexture = null;
        rightModel = null;
        leftModel = null;
        floorModel = null;
        spriteGraphProvider = null;
        modelGraphProvider = null;
        modelShaderProvider = null;
        spriteShaderProvider = null;
        if (capturePath != null && !capturePath.isBlank()
                && !captured) {
            throw new FdxException(
                    "Shader graph sample did not capture " + capturePath);
        }
        if (exitAfterFrames > 0L
                && renderedFrames < exitAfterFrames) {
            throw new FdxException("Shader graph sample rendered "
                    + renderedFrames + " of " + exitAfterFrames
                    + " required frames");
        }
        if (logger != null) {
            logger.info("Shader graph sample rendered "
                    + renderedFrames + " frames");
        }
    }

    private StandardPbrTechnique selectedTechnique() {
        if ("code".equalsIgnoreCase(graphSource())) {
            return StandardPbrTechnique.create(graphics,
                    ShaderGraphSampleGraphs.codeAuthoredSurface());
        }
        return StandardPbrTechnique.create(graphics,
                loadRuntimeSurface(
                        ShaderGraphSampleGraphs
                                .loadSurfaceSource(files),
                        ShaderGraphSampleGraphs.SURFACE_ASSET)
                        .graph());
    }

    private String graphSource() {
        return System.getProperty(
                "libfdx.sample.graphSource", "asset").trim();
    }

    private GraphPbrMaterial material(String id, float warmth,
            float red, float green, float blue, float roughness) {
        GraphPbrMaterial material = activePbrTechnique.material(id);
        material.baseColor(1.0f, 1.0f, 1.0f, 1.0f)
                .metallicFactor(0.16f)
                .roughnessFactor(roughness);
        material.graphMaterial()
                .set("tint", ShaderGraphLiteral.composite(VEC4,
                        ShaderGraphLiteral.f32(red),
                        ShaderGraphLiteral.f32(green),
                        ShaderGraphLiteral.f32(blue),
                        ShaderGraphLiteral.f32(1.0f)))
                .set("warmth", ShaderGraphLiteral.f32(warmth));
        return material;
    }

    private void reloadFromAsset() {
        replaceSurface(ShaderGraphSampleGraphs.loadSurfaceSource(files),
                ShaderGraphSampleGraphs.SURFACE_ASSET);
    }

    private boolean replaceSurface(String source, String label) {
        try {
            ShaderGraphRuntimeAsset loaded =
                    loadRuntimeSurface(source, label);
            StandardPbrTechnique replacement =
                    StandardPbrTechnique.create(
                            graphics, loaded.graph());
            requireCompatibleMaterialSchema(
                    activePbrTechnique.materialDefinition(),
                    replacement.materialDefinition());
            modelGraphProvider.replace(replacement.technique());
            activePbrTechnique = replacement;
            logger.info("Shader graph sample accepted " + label
                    + " through a cache "
                    + (loaded.cacheHit() ? "hit" : "miss")
                    + " at provider revision "
                    + modelGraphProvider.revision());
            return true;
        } catch (RuntimeException failure) {
            logger.warn("Shader graph sample kept its last-good shader after "
                    + label + " failed: " + message(failure));
            return false;
        }
    }

    private ShaderGraphRuntimeAsset loadRuntimeSurface(
            String source, String label) {
        ShaderGraphRuntimeAsset loaded =
                new ShaderGraphRuntimeLoader().load(
                        source, ShaderGraphCacheContext.wgpu(
                                ShaderGraphCompileOptions.builder()
                                        .profile(preferredProfile())
                                        .capabilities(
                                                graphics.device()
                                                        .capabilities())
                                        .build()));
        if (loaded.graph() == null
                || loaded.graph().graph().kind()
                        != io.github.libfdx.graphics.shadergraph.model
                                .ShaderGraphKind.SURFACE) {
            throw new FdxException(
                    "Shader graph asset is not a surface: " + label);
        }
        return loaded;
    }

    private ShaderProfile preferredProfile() {
        if (graphics.device().capabilities().supports(
                ShaderProfile.PORTABLE_WEBGPU)) {
            return ShaderProfile.PORTABLE_WEBGPU;
        }
        if (graphics.device().capabilities().supports(
                ShaderProfile.PORTABLE_WEBGL2)) {
            return ShaderProfile.PORTABLE_WEBGL2;
        }
        return ShaderProfile.NATIVE;
    }

    private static void requireCompatibleMaterialSchema(
            ShaderGraphMaterialDefinition current,
            ShaderGraphMaterialDefinition replacement) {
        ShaderGraphParameter[] currentParameters = current.parameters();
        ShaderGraphParameter[] replacementParameters =
                replacement.parameters();
        if (currentParameters.length != replacementParameters.length) {
            throw new FdxException(
                    "Reloaded surface changed its material parameter count");
        }
        for (int i = 0; i < currentParameters.length; i++) {
            ShaderGraphParameter left = currentParameters[i];
            ShaderGraphParameter right = replacementParameters[i];
            if (!left.id().equals(right.id())
                    || !left.type().equals(right.type())) {
                throw new FdxException(
                        "Reloaded surface changed material parameter "
                                + left.id());
            }
        }
        ShaderGraphResource[] currentResources = current.resources();
        ShaderGraphResource[] replacementResources =
                replacement.resources();
        if (currentResources.length != replacementResources.length) {
            throw new FdxException(
                    "Reloaded surface changed its material resource count");
        }
        for (int i = 0; i < currentResources.length; i++) {
            ShaderGraphResource left = currentResources[i];
            ShaderGraphResource right = replacementResources[i];
            if (!left.id().equals(right.id())
                    || !left.type().equals(right.type())
                    || left.group() != right.group()
                    || left.binding() != right.binding()) {
                throw new FdxException(
                        "Reloaded surface changed material resource "
                                + left.id());
            }
        }
    }

    private static void requireEquivalentRuntimeGraph(
            ShaderGraphRuntimeGraph expected,
            ShaderGraphRuntimeGraph actual) {
        if (expected == null || actual == null
                || !expected.graph().semanticHash().equals(
                        actual.graph().semanticHash())
                || !expected.wgsl().equals(actual.wgsl())
                || !expected.libraryWgsl().equals(
                        actual.libraryWgsl())
                || !expected.shaderInterface().equals(
                        actual.shaderInterface())
                || !expected.libraryInterface().equals(
                        actual.libraryInterface())) {
            throw new FdxException(
                    "Cache hit and miss produced different runtime shaders");
        }
    }

    private void setTransform(DefaultModelInstance instance,
            float x, float y, float z, float angle) {
        translation.setToTranslation(x, y, z);
        rotation.setToRotationY(angle);
        transform.setToMul(translation, rotation);
        instance.transform(transform);
    }

    private Texture createCheckerTexture() {
        Texture texture = graphics.device().createTexture(
                TextureDescriptor.rgba8(
                        "shader graph sample sprite",
                        CHECKER_SIZE, CHECKER_SIZE));
        ByteBuffer pixels = ByteBuffer.allocateDirect(
                CHECKER_SIZE * CHECKER_SIZE * 4);
        for (int y = 0; y < CHECKER_SIZE; y++) {
            for (int x = 0; x < CHECKER_SIZE; x++) {
                boolean bright = ((x / 4) + (y / 4)) % 2 == 0;
                pixels.put((byte)(bright ? 255 : 48));
                pixels.put((byte)(bright ? 255 : 96));
                pixels.put((byte)(bright ? 255 : 180));
                pixels.put((byte)255);
            }
        }
        pixels.flip();
        graphics.device().writeTexture(texture, pixels);
        return texture;
    }

    private void capture(String path) {
        try {
            ShaderGraphFramebufferCapture.writePpm(path,
                    framebufferWidth(), framebufferHeight(),
                    graphics.currentFrame().frameBuffer()
                            .readPixelsRgba8());
            logger.info("Shader graph sample captured " + path);
        } catch (Exception failure) {
            throw new FdxException(
                    "Could not capture shader graph sample", failure);
        }
    }

    private int framebufferWidth() {
        int width = display.framebufferWidth() > 0
                ? display.framebufferWidth() : display.width();
        return Math.max(1, width);
    }

    private int framebufferHeight() {
        int height = display.framebufferHeight() > 0
                ? display.framebufferHeight() : display.height();
        return Math.max(1, height);
    }

    private static String message(Throwable failure) {
        return failure.getMessage() != null
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
    }

    private static void dispose(Disposable value) {
        if (value != null && !value.isDisposed()) {
            value.dispose();
        }
    }
}
