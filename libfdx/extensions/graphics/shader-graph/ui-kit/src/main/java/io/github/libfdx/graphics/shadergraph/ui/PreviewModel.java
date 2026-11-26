package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.files.FileSystem;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.g3d.DefaultModelInstance;
import io.github.libfdx.graphics.g3d.DefaultRenderQueue3D;
import io.github.libfdx.graphics.g3d.G3DAssetLoaders;
import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.Model;
import io.github.libfdx.graphics.g3d.ModelBuilder;
import io.github.libfdx.graphics.g3d.ModelNode;
import io.github.libfdx.graphics.g3d.ModelVertexUsage;
import io.github.libfdx.math.BoundingBox;
import io.github.libfdx.math.Vector3;

/** One turntable subject. Imported meshes and their buffers and textures belong to their asset manager. */
final class PreviewModel implements Disposable {
    enum Shape {
        SPHERE("Sphere"), CUBE("Cube"), PLANE("Plane");

        final String label;

        Shape(String label) {
            this.label = label;
        }

    }

    final String name;
    final Shape shape;
    final DefaultModelInstance instance;
    private final Disposable owner;
    private float centerX, centerY, centerZ;
    private float scale = 1;
    private Material applied;

    private PreviewModel(Model model, Disposable owner, String name, Shape shape) {
        this.owner = owner;
        this.name = name;
        this.shape = shape;
        instance = new DefaultModelInstance(model);
        if (shape == null) {
            fitImportedMesh();
        }
    }

    static PreviewModel primitive(GraphicsContext graphics, Shape shape) {
        ModelBuilder builder = new ModelBuilder(graphics);
        long usage = ModelVertexUsage.STANDARD_PBR;
        Model model = switch (shape) {
            case SPHERE -> builder.sphere("sphere", 1, 48, 32, usage);
            case CUBE -> builder.box("cube", 1.3f, 1.3f, 1.3f, usage);
            case PLANE -> builder.plane("plane", 1.65f, 1.65f, usage);
        };
        try {
            return new PreviewModel(model, model, shape.label, shape);
        } catch (RuntimeException failure) {
            model.dispose();
            throw failure;
        }
    }

    private void fitImportedMesh() {
        BoundingBox bounds = new BoundingBox(new Vector3(), new Vector3());
        if (!instance.calculatePoseBounds(bounds)) {
            // Skinned parts can omit culling bounds. Frame the displayed bind pose using the mesh bounds.
            var parts = new DefaultRenderQueue3D();
            instance.collectRenderables(parts);
            var point = new Vector3();
            bounds.min().set(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            bounds.max().set(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
            for (int i = 0; i < parts.size(); i++) {
                var part = parts.get(i);
                var box = part.bounds();
                if (box == null) continue;
                for (int corner = 0; corner < 8; corner++) {
                    point.set((corner & 1) == 0 ? box.min().x() : box.max().x(),
                            (corner & 2) == 0 ? box.min().y() : box.max().y(),
                            (corner & 4) == 0 ? box.min().z() : box.max().z());
                    part.worldTransform().transformPosition(point, point);
                    bounds.min().set(Math.min(bounds.min().x(), point.x()), Math.min(bounds.min().y(), point.y()),
                            Math.min(bounds.min().z(), point.z()));
                    bounds.max().set(Math.max(bounds.max().x(), point.x()), Math.max(bounds.max().y(), point.y()),
                            Math.max(bounds.max().z(), point.z()));
                }
            }
        }
        var min = bounds.min();
        var max = bounds.max();
        double diagonal = Math.hypot(Math.hypot((double) max.x() - min.x(), (double) max.y() - min.y()),
                (double) max.z() - min.z());
        if (!(diagonal > 0) || !Double.isFinite(diagonal)) {
            throw new FdxException("The model has no geometry with usable bounds");
        }
        centerX = min.x() * 0.5f + max.x() * 0.5f;
        centerY = min.y() * 0.5f + max.y() * 0.5f;
        centerZ = min.z() * 0.5f + max.z() * 0.5f;
        scale = (float) (2.1 / diagonal);
    }

    void pose(float angle, Material material) {
        if (applied != material) {
            var roots = instance.model().nodes();
            for (int i = 0; i < roots.size(); i++) {
                apply(roots.get(i), material);
            }
            applied = material;
        }
        instance.transform().setToRotationY(angle).scale(scale, scale, scale).translate(-centerX, -centerY, -centerZ);
    }

    private void apply(ModelNode node, Material material) {
        for (int i = 0; i < node.parts().size(); i++) {
            instance.nodeMaterial(node.id(), i, material);
        }
        for (int i = 0; i < node.children().size(); i++) {
            apply(node.children().get(i), material);
        }
    }

    @Override
    public void dispose() {
        owner.dispose();
    }

    @Override
    public boolean isDisposed() {
        return owner.isDisposed();
    }

    /** Keeps the chosen file's directory as the root for its glTF buffers and textures. */
    static final class Import implements Disposable {
        private final String name;
        private DefaultAssetManager assets;
        private final AssetHandle<Model> handle;

        Import(GraphicsContext graphics, FileSystem files, String path) {
            name = files.internal(path).name();
            assets = new DefaultAssetManager(files);
            try {
                G3DAssetLoaders.register(assets, graphics);
                handle = assets.load(AssetDescriptor.of(path, Model.class));
            } catch (RuntimeException failure) {
                dispose();
                throw failure;
            }
        }

        PreviewModel update() {
            assets.update(6, 2_000_000L);
            if (handle.future().isFailed()) handle.future().get();
            if (!handle.isLoaded()) return null;
            PreviewModel result = new PreviewModel(handle.asset(), assets, name, null);
            assets = null;
            return result;
        }

        @Override
        public void dispose() {
            if (assets != null) {
                assets.dispose();
                assets = null;
            }
        }

        @Override
        public boolean isDisposed() {
            return assets == null;
        }

    }

}
