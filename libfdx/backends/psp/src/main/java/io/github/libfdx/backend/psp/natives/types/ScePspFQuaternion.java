package io.github.libfdx.backend.psp.natives.types;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Structure;

/**
 * Represents a sce psp f quaternion.
 *
 * @author xpenatan
 */
public class ScePspFQuaternion extends Structure {
    public float x;
    public float y;
    public float z;
    public float w;

    /**
     * Creates a sce PSP f quaternion.
     *
     * @return a new sce PSP f quaternion
     */
    public static ScePspFQuaternion malloc() {
        return Memory.malloc(sizeOf(ScePspFQuaternion.class)).toStructure();
    }
}
