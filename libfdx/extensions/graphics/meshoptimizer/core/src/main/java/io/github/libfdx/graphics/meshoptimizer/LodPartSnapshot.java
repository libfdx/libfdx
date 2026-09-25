package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.g3d.Material;
import io.github.libfdx.graphics.g3d.Skin;
import io.github.libfdx.graphics.g3d.MorphTarget;

record LodPartSnapshot(String id, int geometry, Material material, Skin skin, MorphTarget[] morphTargets) {}
