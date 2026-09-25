package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.graphics.g3d.ModelInstance;

/**
 * Borrowed ready-instance table for loaded or generated geometry. Owns only CPU
 * bookkeeping; never disposes models, meshes, materials or instances. Single-thread
 * confined. Supply separate instances per logical object; model assets may be shared.
 * The default guard accepts unchanged static DefaultModelInstances with root motion.
 * Independently moving rigid groups can each use their own table/binding. Skins,
 * animations, node/material edits and unknown implementations fall back unless an
 * explicit synchronizer supplies a compatible mapping.
 */
public final class ModelLodModels implements ModelLodSource {
    private final ModelInstance[] models;
    private final LodStaticState[] states;
    private ModelLodSynchronizer synchronizer;

    /** Copies the instance array; base must be ready, reduced levels may be null. */
    public ModelLodModels(ModelInstance base, ModelInstance... reduced) {
        if (base == null || reduced == null || reduced.length > ModelLodConfig.MAX_REDUCED_LEVELS)
            throw new IllegalArgumentException("LOD requires a base and zero to eight reduced instances");
        models = new ModelInstance[reduced.length + 1];
        states = new LodStaticState[models.length];
        models[0] = base;
        states[0] = new LodStaticState(base);
        for (int i = 0; i < reduced.length; i++) level(i + 1, reduced[i]);
    }

    /**
     * Publishes/replaces a reduced level, or null to make it unavailable. Captures
     * compatibility state now, outside draw submission. Does not dispose the old
     * resource; its owner must wait until previously submitted work no longer uses it.
     * Recreate the table/binding when replacing the base model.
     */
    public ModelLodModels level(int level, ModelInstance model) {
        if (level < 1 || level >= models.length) throw new IndexOutOfBoundsException("Reduced LOD level: " + level);
        if (model != null) for (int i = 0; i < models.length; i++)
            if (i != level && models[i] == model) throw new IllegalArgumentException("Each LOD needs a distinct model instance");
        LodStaticState state = model == null ? null : new LodStaticState(model);
        models[level] = model; states[level] = state;
        return this;
    }

    /** Sets a borrowed explicit mapper, or null to restore static compatibility checks. */
    public ModelLodModels synchronizer(ModelLodSynchronizer synchronizer) {
        this.synchronizer = synchronizer; return this;
    }
    @Override public int levelCount() { return models.length; }
    @Override public ModelInstance model(int level) { return models[level]; }
    @Override public boolean synchronize(int level) {
        if (level < 1 || level >= models.length) throw new IndexOutOfBoundsException("Reduced LOD level: " + level);
        if (models[level] == null) return false;
        return synchronizer != null ? synchronizer.synchronize(models[0], models[level], level)
                : states[0].unchanged() && states[level].unchanged();
    }
}
