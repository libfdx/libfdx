package io.github.libfdx.samples.shadergraph;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.display.Display;
import io.github.libfdx.files.FileHandle;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.GraphMaterial;
import io.github.libfdx.graphics.g3d.MaterialAlphaMode;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.ModelShaderPlan;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.StandardPbrTechnique;
import io.github.libfdx.graphics.shader.runtime.ShaderPassId;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparation;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationReport;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationScope;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewAnimation;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewPlayback;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeAsset;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphRuntimeLoader;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.TouchEvent;
import io.github.libfdx.json.JsonReader;
import io.github.libfdx.math.Color;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleGraphs.Example;

import java.nio.charset.StandardCharsets;

/** A game-side surface graph example using a mesh, a graph material and a ModelBatch. */
public final class ShaderGraphSampleApplication extends ApplicationAdapter {
    private static final Example[] EXAMPLES = Example.values();
    private static final LoadOp BACKGROUND = LoadOp.clear(0.024f, 0.031f, 0.047f, 1);
    private final boolean fromCode;
    private final long frameLimit;
    private ShaderGraphPreviewPlayback playback;
    private Fdx fdx;
    private GraphicsContext graphics;
    private Display display;
    private DefaultAssetManager assets;
    private AssetHandle<Model> crystal;
    private DefaultModelInstance instance;
    private final Camera camera = new Camera();
    private ModelBatch batch;
    private ShaderGraphProvider provider;
    private ShaderPreparation shaders;
    private ModelShaderPlan shaderPlan;
    private ShaderPreparationScope effectScope;
    private FdxFuture<ShaderPreparationReport> preparing;
    private GraphMaterial material;
    private Example requested;
    private Example active;
    private int animatedParameter;
    private boolean playing = true;
    private double shaderTime;
    private long frames;

    private final InputAdapter controls = new InputAdapter() {
        @Override
        public boolean touchDown(TouchEvent event) {
            requested = EXAMPLES[(requested.ordinal() + 1) % EXAMPLES.length];
            return true;
        }

        @Override
        public boolean keyDown(KeyEvent event) {
            switch (event.key()) {
                case LEFT -> requested = EXAMPLES[Math.floorMod(requested.ordinal() - 1, EXAMPLES.length)];
                case RIGHT -> requested = EXAMPLES[(requested.ordinal() + 1) % EXAMPLES.length];
                case NUM_1 -> requested = Example.DISSOLVE;
                case NUM_2 -> requested = Example.BASALT;
                case NUM_3 -> requested = Example.VORTEX;
                case SPACE -> playing = !playing;
                default -> { return false; }
            }
            return true;
        }

    };

    /** Creates an interactive gallery starting at the chosen effect. */
    public ShaderGraphSampleApplication(Example example, boolean fromCode) {
        this(example, fromCode, 0);
    }

    /** A positive frame limit closes the sample after that many rendered model frames. */
    public ShaderGraphSampleApplication(Example example, boolean fromCode, long frameLimit) {
        this.requested = example;
        this.fromCode = fromCode;
        this.frameLimit = frameLimit;
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        graphics = fdx.graphics().main();
        display = fdx.displays().main();
        camera.projection(CameraProjection.PERSPECTIVE).fieldOfView(39).nearFar(0.1f, 30)
                .position(0, 0.64f, 4.25f).lookAt(0, 0, 0);
        resize(display.framebufferWidth(), display.framebufferHeight());
        assets = new DefaultAssetManager(fdx.files());
        G3DAssetLoaders.register(assets, graphics);
        crystal = assets.load(AssetDescriptor.of("models/crystal.gltf", Model.class));
        fdx.input().addProcessor(controls);
    }

    private FileHandle surfaceFile(Example example) {
        FileHandle edited = fdx.files().local("assets/" + example.asset);
        return edited.exists() ? edited : fdx.files().internal(example.asset);
    }

    /** Loads a locally available or preloaded surface document on the graphics thread. */
    public static StandardPbrTechnique loadSurface(GraphicsContext graphics, FileHandle file) {
        return StandardPbrTechnique.create(graphics, loadAsset(file).graph());
    }

    private static ShaderGraphRuntimeAsset loadAsset(FileHandle file) {
        var loaded = new ShaderGraphRuntimeLoader().load(file.readString(StandardCharsets.UTF_8).get());
        if (loaded.graph() == null || loaded.graph().graph().kind() != ShaderGraphKind.SURFACE) {
            throw new FdxException("Expected a surface graph in " + file.path());
        }
        return loaded;
    }

    private void show(Example example) {
        StandardPbrTechnique technique;
        ShaderGraphPreviewAnimation preset;
        if (fromCode) {
            technique = StandardPbrTechnique.create(graphics, example.build());
            preset = example.animation;
        } else {
            var asset = loadAsset(surfaceFile(example));
            technique = StandardPbrTechnique.create(graphics, asset.graph());
            preset = null;
            if (!technique.surfaceGraph().usesTime() && asset.document().hasEditor()) {
                try {
                    preset = ShaderGraphPreviewAnimation.readFrom(new JsonReader().parse(asset.document().editorJson()));
                } catch (RuntimeException invalidPreset) {
                    fdx.logger().warn("Ignoring preview animation in " + example.asset + ": " + invalidPreset.getMessage());
                }
            }
        }
        GraphMaterial next = technique.material("crystal");
        next.set(MaterialAttributes.baseColor(0.11f, 0.29f, 0.42f, 1)).set(PbrAttributes.roughnessFactor(0.38f));
        for (var output : technique.surfaceGraph().outputs()) {
            if ("alpha".equalsIgnoreCase(output.semantic()) || "alpha".equalsIgnoreCase(output.id().value())) {
                next.alphaMode(MaterialAlphaMode.MASK).set(MaterialAttributes.alphaCutoff(0.35f));
                break;
            }
        }
        if (provider == null) {
            provider = new ShaderGraphProvider(graphics, technique.technique());
            shaders = new ShaderPreparation(graphics);
            shaderPlan = new ModelShaderPlan(graphics, provider);
            batch = new ModelBatch(graphics, new ModelBatchConfig().preparation(shaders).shaderPlan(shaderPlan))
                    .environment(new Environment().ambientColor(new Color(0.22f, 0.25f, 0.30f, 1))
                            .add(new DirectionalLight().direction(-0.4f, -0.8f, -0.6f).intensity(2.0f)));
        } else {
            provider.replace(technique.technique());
        }
        instance.nodeMaterial(crystal.asset().nodes().get(0).id(), 0, next);
        if (effectScope != null) effectScope.dispose();
        effectScope = shaders.createScope(example.title);
        shaderPlan.include(effectScope, instance, ShaderPassId.FORWARD, shaderPlan.surfaceTarget(graphics.currentFrame()));
        preparing = shaders.prepareAsync(effectScope.seal());
        material = next;
        animatedParameter = !technique.surfaceGraph().usesTime() && preset != null && preset.appliesTo(technique.surfaceGraph())
                ? next.graphMaterial().definition().parameterIndex(preset.parameter()) : -1;
        playback = animatedParameter < 0 ? null : new ShaderGraphPreviewPlayback(preset);
        shaderTime = 0;
        if (example == Example.VORTEX) instance.transform().setToRotationX(-0.43f).rotateY(-0.45f);
        else instance.transform().setToRotationY(0.3f);
        active = example;
        display.title(example.title + "  |  " + (fromCode ? "Java" : "File") + "  |  1–3 or arrows: effect   Space: pause");
        fdx.logger().info("Showing " + technique.surfaceGraph().id() + " from " + (fromCode ? "Java" : example.asset));
    }

    @Override
    public void render() {
        if (instance == null) {
            assets.update();
            if (crystal.future().isFailed()) crystal.future().get();
            if (!crystal.isLoaded()) {
                graphics.clear(0.024f, 0.031f, 0.047f, 1);
                return;
            }
            instance = new DefaultModelInstance(crystal.asset());
        }
        if (active != requested) {
            try {
                show(requested);
            } catch (RuntimeException failure) {
                if (active == null) throw failure;
                requested = active;
                fdx.logger().error("Could not switch shader graph", failure);
            }
        }
        if (preparing != null) {
            shaders.updateLoading();
            if (!preparing.isDone()) {
                graphics.clear(0.024f, 0.031f, 0.047f, 1);
                return;
            }
            var report = preparing.get();
            if (!report.allReady()) throw new FdxException("Could not prepare " + active.title + ": " + report.items());
            preparing = null;
        } else {
            shaders.update();
        }
        float delta = playing ? Math.max(0f, Math.min(fdx.app().deltaTime(), 0.1f)) : 0f;
        shaderTime += delta;
        batch.shaderTimeSeconds((float) shaderTime);
        if (playback != null) {
            material.graphMaterial().set(animatedParameter, playback.advance(delta));
        }
        batch.begin(BACKGROUND, camera);
        batch.render(instance);
        batch.end();
        if (frameLimit > 0 && ++frames >= frameLimit) fdx.app().requestExit();
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) camera.viewport(width, height).update();
    }

    @Override
    public void dispose() {
        if (fdx != null) fdx.input().removeProcessor(controls);
        if (batch != null) batch.dispose();
        if (effectScope != null) effectScope.dispose();
        if (shaders != null) shaders.dispose();
        if (shaderPlan != null) shaderPlan.dispose();
        if (provider != null) provider.dispose();
        if (assets != null) assets.dispose();
    }

}
