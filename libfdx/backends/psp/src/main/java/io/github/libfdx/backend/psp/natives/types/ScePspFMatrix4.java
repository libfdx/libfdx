package io.github.libfdx.backend.psp.natives.types;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Structure;

/**
 * Represents a sce psp f matrix4.
 *
 * @author xpenatan
 */
public class ScePspFMatrix4 extends Structure {
    public ScePspFVector4 x;
    public ScePspFVector4 y;
    public ScePspFVector4 z;
    public ScePspFVector4 w;

    /**
     * Creates a sce PSP f matrix4.
     *
     * @return a new sce PSP f matrix4
     */
    public static ScePspFMatrix4 malloc() {
        return Memory.malloc(sizeOf(ScePspFMatrix4.class)).toStructure();
    }
}