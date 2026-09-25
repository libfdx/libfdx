package io.github.libfdx.graphics.meshoptimizer;

import io.github.libfdx.graphics.Mesh;
import io.github.libfdx.graphics.g3d.MorphTarget;

record LodPreparedMesh(Mesh.PositionColor3DPreparation preparation, short[] indices, int vertices, MorphTarget[] morphTargets) {
    LodPreparedMesh(Mesh.PositionColor3DPreparation preparation,short[] indices,int vertices) { this(preparation,indices,vertices,new MorphTarget[0]); }
}
