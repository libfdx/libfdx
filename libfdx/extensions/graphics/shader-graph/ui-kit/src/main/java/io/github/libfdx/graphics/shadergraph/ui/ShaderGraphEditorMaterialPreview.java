package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.OffscreenTarget;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.camera.Camera;
import io.github.libfdx.graphics.camera.CameraProjection;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.graphics.g3d.DirectionalLight;
import io.github.libfdx.graphics.g3d.Environment;
import io.github.libfdx.graphics.g3d.GraphMaterial;
import io.github.libfdx.graphics.g3d.MaterialAlphaMode;
import io.github.libfdx.graphics.g3d.MaterialAttributes;
import io.github.libfdx.graphics.g3d.ModelBatch;
import io.github.libfdx.graphics.g3d.ModelBatchConfig;
import io.github.libfdx.graphics.g3d.PbrAttributes;
import io.github.libfdx.graphics.g3d.StandardPbrTechnique;
import io.github.libfdx.graphics.shadergraph.document.ShaderGraphPreviewAnimation;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.runtime.ShaderGraphProvider;
import io.github.libfdx.math.Color;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiCustomContext;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiDrawFunction;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;

/** Editor-owned model preview. Meshes, shaders and render targets live on the graphics thread. */
public final class ShaderGraphEditorMaterialPreview implements ShaderGraphEditorPreviewSurface, UiDrawFunction {
    private static final int SIZE = 384;
    private static final UiModifier ROW = UiModifier.none().fillWidth().gap(4);
    private static final UiModifier BUTTON = UiModifier.none().weight(1);
    private final GraphicsContext graphics;
    private final ShaderGraphEditorSession session;
    private final Camera camera = new Camera().projection(CameraProjection.PERSPECTIVE)
            .viewport(SIZE, SIZE).fieldOfView(40).nearFar(0.1f, 10).position(0, 1.45f, 3.1f).lookAt(0, 0, 0).update();
    private final OffscreenTarget[] targets = new OffscreenTarget[2];
    private final TextureRegion[] regions = new TextureRegion[2];
    private final UiState<String> subject = Ui.state("");
    private final UiState<String> notice = Ui.state("");
    private final UiBooleanState rotating = Ui.state(false);
    private final ShaderGraphEditorAnimationControls animation;
    private PreviewModel model;
    private PreviewModel.Shape requested = PreviewModel.Shape.SPHERE;
    private PreviewModel.Import loading;
    private PreviewShader shader;
    private ShaderGraph pending;
    private int displayed = -1;
    private float angle = 0.45f;
    private boolean renderFailed;
    private Runnable chooseModel;
    private boolean disposed;

    ShaderGraphEditorMaterialPreview(GraphicsContext graphics, ShaderGraphEditorSession session) {
        this.graphics = graphics;
        this.session = session;
        animation = new ShaderGraphEditorAnimationControls(session);
    }

    @Override
    public void present(ShaderGraphEditorCompilation compilation, ShaderGraphEditorPreviewMode mode) {
        pending = mode == ShaderGraphEditorPreviewMode.MATERIAL_BALL ? session.document().graph() : null;
    }

    @Override
    public void buildControls(UiScope ui) {
        String selected = subject.get();
        ui.row(ROW, row -> {
            for (PreviewModel.Shape shape : PreviewModel.Shape.values()) {
                row.button(shape.label, BUTTON.enabled(!shape.label.equals(selected) || loading != null),
                        () -> selectPrimitive(shape));
            }
        });
        ui.row(ROW, row -> {
            if (chooseModel != null) row.button("Load model…", BUTTON, chooseModel);
            row.button(rotating.get() ? "Pause rotation" : "Rotate", BUTTON, () -> rotating.set(!rotating.get()));
        });
        animation.build(ui);
        if (model != null && model.shape == null) ui.selectableText(selected);
        if (!notice.get().isEmpty()) ui.selectableText(notice.get());
    }

    private void selectPrimitive(PreviewModel.Shape shape) {
        cancelLoad();
        requested = shape;
        notice.set("");
    }

    /** Installs an optional host action to choose a model on the application's platform. */
    public void modelChooser(Runnable chooseModel) {
        this.chooseModel = chooseModel;
    }

    /** Sets a zero-to-one preview range for a float material parameter, or clears it with null. */
    public void dissolveParameter(String name) {
        session.previewAnimation(name == null ? null : new ShaderGraphPreviewAnimation(name, 0, 1));
    }

    /** Loads a glTF or GLB from a borrowed file system. Call on the graphics thread. */
    public void loadModel(FileSystem files, String path) {
        if (disposed) throw new IllegalStateException("Shader graph preview is disposed");
        cancelLoad();
        requested = null;
        try {
            loading = new PreviewModel.Import(graphics, files, path);
            notice.set("Loading " + path + "…");
        } catch (RuntimeException failure) {
            report(failure);
        }
    }

    private void cancelLoad() {
        if (loading != null) {
            loading.dispose();
            loading = null;
        }
    }

    private PreviewModel pollModel() {
        if (requested != null) {
            var shape = requested;
            requested = null;
            return PreviewModel.primitive(graphics, shape);
        }
        if (loading == null) return null;
        PreviewModel loaded = loading.update();
        if (loaded != null) loading = null;
        return loaded;
    }

    void render(float delta) {
        PreviewModel candidate = null;
        try {
            candidate = pollModel();
        } catch (RuntimeException failure) {
            cancelLoad();
            report(failure);
        }
        PreviewShader replacement = null;
        ShaderGraph graph = pending;
        pending = null;
        try {
            if (model == null && candidate != null) {
                model = candidate;
                candidate = null;
                subject.set(model.name);
            }
            if (graph != null) replacement = new PreviewShader(graphics, graph);
            PreviewShader live = replacement != null ? replacement : shader;
            PreviewModel mesh = candidate != null ? candidate : model;
            if (live == null || mesh == null || (renderFailed && replacement == null && candidate == null)) return;
            float dt = Math.clamp(delta, 0, 0.1f);
            if (rotating.get()) angle = (angle + dt * 0.55f) % (float) (2 * Math.PI);
            live.batch.shaderTimeSeconds(animation.update(live.material.graphMaterial(), dt));
            mesh.pose(angle, live.material);
            drawFrame(live.batch, mesh);
            renderFailed = false;
            if (candidate != null) {
                if (model != null) model.dispose();
                model = candidate;
                candidate = null;
                subject.set(model.name);
                notice.set("");
            }
            if (replacement != null) {
                if (shader != null) shader.dispose();
                shader = replacement;
                replacement = null;
                if (loading == null) notice.set("");
            }
        } catch (RuntimeException failure) {
            renderFailed = graph == null && candidate == null;
            report(failure);
        } finally {
            if (candidate != null) candidate.dispose();
            if (replacement != null) replacement.dispose();
        }
    }

    private void report(RuntimeException failure) {
        String reason = failure.getMessage();
        notice.set("Preview failed: " + (reason != null ? reason : failure.getClass().getSimpleName()));
    }

    private void drawFrame(ModelBatch batch, PreviewModel mesh) {
        int next = displayed == 0 ? 1 : 0;
        if (targets[next] == null) {
            targets[next] = new OffscreenTarget(graphics.device(), true).clearColor(0.075f, 0.075f, 0.075f, 1);
        }
        if (regions[next] == null) {
            targets[next].resize(SIZE, SIZE);
            regions[next] = TextureRegion.rendered(targets[next]);
        }
        RenderPass pass = targets[next].begin(graphics.currentFrame(), true);
        try {
            batch.begin(pass, camera);
            try {
                batch.render(mesh.instance);
            } finally {
                batch.end();
            }
        } finally {
            pass.end();
        }
        displayed = next;
    }

    @Override
    public void build(UiCustomContext context) {
        context.draw(this);
    }

    @Override
    public void draw(UiDrawContext draw, UiRect bounds) {
        if (displayed >= 0) {
            float size = Math.min(bounds.width(), bounds.height());
            draw.image(regions[displayed], bounds.x() + (bounds.width() - size) / 2, bounds.y(), size, size, UiColor.WHITE);
        }
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        cancelLoad();
        if (model != null) model.dispose();
        if (shader != null) shader.dispose();
        for (OffscreenTarget target : targets) {
            if (target != null) {
                target.dispose();
            }
        }
    }

    private static final class PreviewShader {
        final GraphMaterial material;
        final ModelBatch batch;
        final ShaderGraphProvider provider;

        PreviewShader(GraphicsContext graphics, ShaderGraph graph) {
            var technique = StandardPbrTechnique.create(graphics, graph);
            material = technique.material("preview");
            material.alphaMode(MaterialAlphaMode.MASK).set(MaterialAttributes.alphaCutoff(0.35f))
                    .set(MaterialAttributes.baseColor(0.08f, 0.22f, 0.31f, 1)).set(PbrAttributes.roughnessFactor(0.4f));
            provider = new ShaderGraphProvider(graphics, technique.technique());
            try {
                batch = new ModelBatch(graphics, new ModelBatchConfig().shaderProvider(provider))
                        .environment(new Environment().ambientColor(new Color(0.14f, 0.17f, 0.19f, 1))
                                .add(new DirectionalLight().direction(-0.4f, -0.75f, -0.55f).intensity(2)));
            } catch (RuntimeException failure) {
                provider.dispose();
                throw failure;
            }
        }

        void dispose() {
            batch.dispose();
            provider.dispose();
        }

    }

}
