package io.github.libfdx.graphics.g3d.lod;

import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.ModelInstance;
import java.util.function.BiPredicate;

/**
 * Reusable snapshot for application-owned LOD synchronizers. Capture a DefaultModelInstance
 * before asynchronous loading. Root motion is allowed; changed poses/topology fall back.
 * A material predicate may accept replacements that the caller explicitly synchronizes.
 * Shared material values may be ignored only when every LOD borrows the same materials.
 */
public final class ModelLodStaticGuard {
    private final LodStaticState state;
    public ModelLodStaticGuard(ModelInstance instance) {
        if (instance == null) throw new IllegalArgumentException("Base instance required");
        state = new LodStaticState(instance);
    }
    public boolean unchanged() { return state.unchanged(); }
    public boolean unchanged(boolean checkMaterialValues, BiPredicate<Material,Material> synchronizedReplacement) {
        return state.unchanged(checkMaterialValues,synchronizedReplacement);
    }
}
