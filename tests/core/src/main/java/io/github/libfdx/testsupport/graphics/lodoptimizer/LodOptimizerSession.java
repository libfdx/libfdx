package io.github.libfdx.testsupport.graphics.lodoptimizer;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.*;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.*;
import io.github.libfdx.graphics.g3d.lod.ModelLodBinding;
import io.github.libfdx.graphics.meshoptimizer.*;

/** Owns loaded assets and generated meshes. Only detached CPU preparation runs on the worker. */
public final class LodOptimizerSession implements Disposable {
    public static final String HELMET = "data/g3d/gltf/DamagedHelmet/DamagedHelmet.gltf";
    public static final String DUCK = "data/g3d/gltf/Ducky/ducky.gltf";
    public static final String DRAGON = "data/g3d/gltf/StanfordDragon/stanfordDragon.gltf";
    public static final String ANIMATED = "gltf-animation/lod/combined.gltf";
    private final GraphicsContext graphics;
    private final DefaultAssetManager assets;
    private final AssetExecutor executor;
    private final MeshLodSimplifier simplifier;
    private AssetLease<Model> source, loading;
    private ModelLodInput input;
    private DefaultModelInstance base;
    private GeneratedModelLods generated;
    private ModelLodBinding binding;
    private AnimatedModelLods animated;
    private CpuMorphModelAnimator baseMorph;
    private AnimationController controller;
    private float animationTime;
    private FdxFuture<PreparedModelLods> work;
    private ModelLodPreparation preparation;
    private ModelLodUpload upload;
    private String status = "Load a glTF or GLB model to begin.";
    private int revision, generation;
    private volatile boolean disposed;

    /** Takes ownership of the executor, borrows the stateless simplifier. */
    public LodOptimizerSession(Fdx fdx, MeshLodSimplifier simplifier, AssetExecutor executor) {
        this.graphics = fdx.graphics().main(); this.simplifier = simplifier; this.executor = executor;
        assets = executor == null ? new DefaultAssetManager(fdx.files()) : new DefaultAssetManager(fdx.files(), executor);
        G3DAssetLoaders.register(assets, graphics);
        assets.registerLoader(Model.class, G3DAssetLoaders.modelLoader(graphics, true));
        if (!supported()) status = "LOD generation requires a simplifier.";
    }

    public boolean supported() { return simplifier != null; }
    public boolean busy() { return loading != null || work != null || upload != null || preparation != null; }
    public DefaultModelInstance base() { return base; }
    public ModelLodInput input() { return input; }
    public GeneratedModelLods generated() { return generated; }
    public ModelLodBinding binding() { return binding; }
    public String status() { return status; }
    public int revision() { return revision; }
    public int generation() { return generation; }
    public void message(String value) { status = value; revision++; }
    public float animationTime() { return animationTime; }
    public void animate(float delta) {
        if (controller != null) { controller.update(delta); animationTime=controller.timeSeconds(); }
        if (baseMorph != null) baseMorph.update();
    }
    public void animationTime(float seconds) {
        if (controller != null) { controller.time(seconds); animationTime=controller.timeSeconds(); }
        if (baseMorph != null) baseMorph.update();
    }

    public void load(String path) {
        if (busy() || disposed || !supported()) return;
        try {
            loading = assets.acquire(AssetDescriptor.of(path.trim(), Model.class));
            message("Loading " + path.trim());
        } catch (RuntimeException failure) { message("Load failed: " + failure.getMessage()); }
    }

    public void generate(ModelLodSettings settings) {
        if (busy() || disposed || base == null || !supported()) return;
        if (executor == null) {
            preparation = new ModelLodOptimizer(simplifier).begin(input,settings,() -> disposed);
            message("Generating " + settings.levelCount() + " LODs in cooperative CPU steps...");
            return;
        }
        FdxFuture<PreparedModelLods> future = FdxFuture.pending();
        ModelLodInput snapshot = input;
        boolean accepted = executor.submit(() -> {
            try { future.complete(new ModelLodOptimizer(simplifier).prepare(snapshot, settings, () -> disposed)); }
            catch (Throwable failure) { future.completeExceptionally(failure); }
        });
        if (!accepted) { message("Worker queue is full; try Generate again."); return; }
        work = future;
        message("Generating " + settings.levelCount() + " LODs on the CPU worker...");
    }

    public void update() {
        assets.update(4, 1_000_000L);
        if (preparation != null) {
            try {
                // Drain cheap stages within a short frame budget rather than spending a frame
                // on every allocation/channel slice. A single work unit can still exceed it.
                long started=System.nanoTime();
                do {
                    if (preparation.step(512)) {
                        upload = preparation.result().beginUpload(graphics); preparation = null;
                        message("Uploading generated meshes...");
                        break;
                    }
                } while (System.nanoTime()-started<4_000_000L);
            } catch (RuntimeException failure) { preparation = null; message("Generation failed: " + failure.getMessage()); }
        }
        if (loading != null && loading.future().isDone()) {
            try {
                Model next = loading.future().get();
                ModelLodInput nextInput = ModelLodInput.capture(next);
                DefaultModelInstance nextBase = new DefaultModelInstance(next);
                AnimationController nextController = new AnimationController(nextBase);
                if (next.animations().notEmpty()) nextController.play(next.animations().get(0),true);
                CpuMorphModelAnimator nextMorph = new CpuMorphModelAnimator(graphics,nextBase);
                releaseGenerated();
                if (baseMorph != null) baseMorph.dispose();
                if (source != null) source.dispose();
                source = loading; loading = null; input = nextInput; base = nextBase;
                baseMorph=nextMorph; controller=nextController; animationTime=0;
                message("Loaded " + input.triangleCount() + " triangles. Configure levels, then Generate.");
            } catch (RuntimeException failure) {
                loading.dispose(); loading = null;
                message("Load rejected: " + failure.getMessage());
            }
        }
        if (work != null && work.isDone()) {
            try { upload = work.get().beginUpload(graphics); message("Uploading generated meshes..."); }
            catch (RuntimeException failure) { message("Generation failed: " + failure.getMessage()); }
            finally { work = null; }
        }
        if (upload != null) {
            try {
                if (upload.step(1024 * 1024)) {
                    GeneratedModelLods next = upload.take();
                    upload.dispose(); upload = null;
                    AnimatedModelLods nextAnimated;
                    try { nextAnimated = next.bindAnimated(graphics,base,baseMorph); }
                    catch (RuntimeException | Error failure) { next.dispose(); throw failure; }
                    releaseGenerated(); generated = next; animated=nextAnimated; binding = nextAnimated.binding(); generation++;
                    message("Ready: " + next.levelCount() + " generated LODs. Edit settings and Generate to rebuild.");
                }
            } catch (RuntimeException failure) {
                if (upload != null) { upload.dispose(); upload = null; }
                message("Upload failed: " + failure.getMessage());
            }
        }
    }

    private void releaseGenerated() {
        binding = null;
        if (animated != null) { animated.dispose(); animated = null; }
        if (generated != null) { generated.dispose(); generated = null; }
    }
    @Override public boolean isDisposed() { return disposed; }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        preparation = null;
        try {
            if (upload != null) upload.dispose();
            releaseGenerated();
            if (loading != null) loading.dispose();
            if (baseMorph != null) baseMorph.dispose();
            if (source != null) source.dispose();
        } finally { try { assets.dispose(); } finally { if (executor != null) executor.dispose(); } }
    }
}
