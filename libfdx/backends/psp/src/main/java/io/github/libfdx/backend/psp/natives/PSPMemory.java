package io.github.libfdx.backend.psp.natives;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.Unmanaged;
import org.teavm.interop.c.Include;

/**
 * Represents a PSP memory.
 *
 * @author xpenatan
 */
@Include("PSPMemory.h")
public class PSPMemory {

    private PSPMemory() {
    }

    /**
     * Runs the malloc step.
     *
     * @param size the size
     * @return the malloc
     */
    public static Address malloc(int size) {
        return Memory.malloc(size);
    }

    /**
     * Runs the free step.
     *
     * @param address the address
     */
    public static void free(Address address) {
        Memory.free(address);
    }

    /**
     * Runs the memcpy step.
     *
     * @param target the target value
     * @param source the source value
     * @param size the size
     */
    public static void memcpy(Address target, Address source, int size) {
        Memory.memcpy(target, source, size);
    }

    /**
     * Calls the memalign native function.
     *
     * @param alignment the alignment
     * @param size the size
     * @return the memalign
     */
    @Include("malloc.h")
    @Import(name = "memalign")
    @Unmanaged
    public static native Address memalign(int alignment, int size);

    /**
     * Calls the libfdx PSP write float native function.
     *
     * @param target the target value
     * @param offset the offset
     * @param value the value
     */
    @Import(name = "libfdx_psp_write_float")
    public static native void writeFloat(Address target, int offset, float value);

    /**
     * Calls the libfdx PSP write int native function.
     *
     * @param target the target value
     * @param offset the offset
     * @param value the value
     */
    @Import(name = "libfdx_psp_write_int")
    public static native void writeInt(Address target, int offset, int value);

}
