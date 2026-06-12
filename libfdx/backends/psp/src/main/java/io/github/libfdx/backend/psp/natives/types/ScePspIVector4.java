package io.github.libfdx.backend.psp.natives.types;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Structure;

/**
 * Represents a sce psp i vector4.
 *
 * @author xpenatan
 */
public class ScePspIVector4 extends Structure {
    public int x;
    public int y;
    public int z;
    public int w;

    /**
     * Creates a sce PSP i vector4.
     *
     * @return a new sce PSP i vector4
     */
    public static ScePspIVector4 malloc() {
        return Memory.malloc(sizeOf(ScePspIVector4.class)).toStructure();
    }
}