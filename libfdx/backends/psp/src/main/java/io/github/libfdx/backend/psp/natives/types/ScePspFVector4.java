package io.github.libfdx.backend.psp.natives.types;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Structure;

/**
 * Represents a sce psp f vector4.
 *
 * @author xpenatan
 */
public class ScePspFVector4 extends Structure {
    public float x;
    public float y;
    public float z;
    public float w;

    /**
     * Creates a sce PSP f vector4.
     *
     * @return a new sce PSP f vector4
     */
    public static ScePspFVector4 malloc() {
        return Memory.malloc(sizeOf(ScePspFVector4.class)).toStructure();
    }
}