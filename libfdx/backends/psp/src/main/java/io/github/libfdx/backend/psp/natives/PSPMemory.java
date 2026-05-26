package io.github.libfdx.backend.psp.natives;

import org.teavm.backend.c.runtime.Memory;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.Unmanaged;
import org.teavm.interop.c.Include;

@Include("PSPMemory.h")
public class PSPMemory {

    private PSPMemory() {
    }

    public static Address malloc(int size) {
        return Memory.malloc(size);
    }

    public static void free(Address address) {
        Memory.free(address);
    }

    public static void memcpy(Address target, Address source, int size) {
        Memory.memcpy(target, source, size);
    }

    @Include("malloc.h")
    @Import(name = "memalign")
    @Unmanaged
    public static native Address memalign(int alignment, int size);

    @Import(name = "libfdx_psp_write_float")
    public static native void writeFloat(Address target, int offset, float value);

    @Import(name = "libfdx_psp_write_int")
    public static native void writeInt(Address target, int offset, int value);

}
