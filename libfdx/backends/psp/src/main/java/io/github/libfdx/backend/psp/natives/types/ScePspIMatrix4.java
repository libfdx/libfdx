package io.github.libfdx.backend.psp.natives.types;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Structure;

/**
 * Represents a sce psp i matrix4.
 *
 * @author xpenatan
 */
public class ScePspIMatrix4 extends Structure {
    public ScePspIVector4 x;
    public ScePspIVector4 y;
    public ScePspIVector4 z;
    public ScePspIVector4 w;

    /**
     * Creates a sce PSP i matrix4.
     *
     * @return a new sce PSP i matrix4
     */
    public static ScePspIMatrix4 malloc() {
        return Memory.malloc(sizeOf(ScePspIMatrix4.class)).toStructure();
    }
}