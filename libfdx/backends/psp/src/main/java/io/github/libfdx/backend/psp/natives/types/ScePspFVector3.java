package io.github.libfdx.backend.psp.natives.types;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Structure;

/**
 * Represents a sce psp f vector3.
 *
 * @author xpenatan
 */
public class ScePspFVector3 extends Structure {
    public float x;
    public float y;
    public float z;

    private ScePspFVector3() {}

    /**
     * Creates a sce PSP f vector3.
     *
     * @return a new sce PSP f vector3
     */
    public static ScePspFVector3 malloc() {
        return Memory.malloc(sizeOf(ScePspFVector3.class)).toStructure();
    }

    /**
     * Runs the set step.
     *
     * @param obj the obj
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    public static void set(ScePspFVector3 obj, float x, float y, float z) {
        obj.x = x;
        obj.y = y;
        obj.z = z;
    }
}