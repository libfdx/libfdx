package io.github.libfdx.backend.cshared;

import org.teavm.interop.Address;
import org.teavm.interop.Structure;
import org.teavm.interop.Unmanaged;
import org.teavm.runtime.GC;
import org.teavm.runtime.RuntimeObject;

/** Traces the C runtime's compressed monitor reference stored in an object header. */
public final class CGarbageCollectionSupport {
    private CGarbageCollectionSupport() {}

    @Unmanaged
    public static boolean markMonitor(RuntimeObject object) {
        int header = object.hashCode;
        return header < 0 && enqueueMonitor(unpack(header).toStructure());
    }

    @Unmanaged
    public static void relocateMonitor(RuntimeObject object) {
        RelocationHeader record = relocation(object.toAddress());
        int header = record != null ? record.hashBackup : object.hashCode;
        if (header >= 0) return;
        Address updated = relocated(unpack(header));
        int packed = (int) (updated.diff(GC.heapAddress()) / 4) | Integer.MIN_VALUE;
        if (record != null) record.hashBackup = packed;
        else object.hashCode = packed;
    }

    @Unmanaged
    private static Address unpack(int header) {
        return GC.heapAddress().add((long) (header & Integer.MAX_VALUE) * 4);
    }

    // The compiler plugin binds these calls to the collector's internal operations.
    @Unmanaged
    private static native boolean enqueueMonitor(RuntimeObject object);

    @Unmanaged
    private static native RelocationHeader relocation(Address object);

    @Unmanaged
    private static native Address relocated(Address object);

    /** Layout of the collector's header backup, used only during its relocation pass. */
    public static final class RelocationHeader extends Structure {
        public int classBackup;
        public int hashBackup;
        public Address newAddress;
    }
}
