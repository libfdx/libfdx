package io.github.libfdx.tests.desktopc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Verifies monitor identity and buffer references across repeated full collections. */
public final class DesktopCGarbageCollectionTest {
    private static Object[] allocationPressure;

    private DesktopCGarbageCollectionTest() {}

    public static void main(String[] args) throws Exception {
        Object lock = new Object();
        int identity = System.identityHashCode(lock);
        long checksum = 0;
        for (int round = 0; round < 256; round++) {
            synchronized (lock) {
                synchronized (lock) {
                    Object[] garbage = new Object[128];
                    for (int i = 0; i < garbage.length; i++) garbage[i] = new byte[4096 + i];
                    System.gc();
                    require(Thread.holdsLock(lock), "Collection lost monitor ownership");
                    require(
                            System.identityHashCode(lock) == identity,
                            "Collection changed identity");
                    checksum += verifyBuffers(round);
                }
                require(Thread.holdsLock(lock), "Nested exit released the outer monitor");
            }
            require(!Thread.holdsLock(lock), "Monitor remained locked after exit");
        }
        Thread worker =
                new Thread(
                        () -> {
                            synchronized (lock) {
                                System.gc();
                                require(
                                        Thread.holdsLock(lock),
                                        "Worker monitor ownership was lost");
                                lock.notifyAll();
                            }
                        });
        synchronized (lock) {
            worker.start();
            lock.wait();
            require(Thread.holdsLock(lock), "Wait failed to reacquire monitor");
        }
        worker.join();
        checksum += verifyYoungCollections();
        System.out.println("COLLECTION_PASS " + checksum);
    }

    private static long verifyYoungCollections() {
        Object[][] groups = new Object[64][];
        for (int i = 0; i < groups.length; i++) groups[i] = bufferGroup(i);
        System.gc();
        long checksum = 0;
        for (int round = 0; round < 128; round++) {
            groups[round % groups.length] = bufferGroup(round + 100);
            // Retain enough young allocations to trigger automatic collections,
            // with old direct buffers still reachable through the array.
            allocationPressure = new Object[256];
            for (int i = 0; i < allocationPressure.length; i++) {
                byte[] bytes = new byte[65536 + i];
                bytes[0] = (byte) round;
                allocationPressure[i] = bytes;
            }
            for (Object[] group : groups) {
                int seed = ((int[]) group[0])[0];
                ByteBuffer direct = (ByteBuffer) group[1];
                ByteBuffer heap = (ByteBuffer) group[2];
                FloatBuffer heapView = (FloatBuffer) group[3];
                ByteBuffer directView = (ByteBuffer) group[4];
                for (int i = 0; i < 64; i++) {
                    require(direct.getInt(i * 4) == seed + i, "Old direct buffer lost data");
                    require(
                            directView.getInt(i * 4) == seed + i + 16,
                            "Old direct slice lost data");
                    require(
                            heap.getFloat(i * 4) == seed + i * 0.25f,
                            "Moved heap buffer lost data");
                    require(heapView.get(i) == seed + i * 0.25f, "Moved float view lost data");
                }
                checksum += direct.getInt(0);
            }
        }
        allocationPressure = null;
        return checksum;
    }

    private static Object[] bufferGroup(int seed) {
        ByteBuffer direct = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder());
        ByteBuffer heap = ByteBuffer.allocate(4096).order(ByteOrder.nativeOrder());
        FloatBuffer floats = heap.asFloatBuffer();
        for (int i = 0; i < 1024; i++) {
            direct.putInt(i * 4, seed + i);
            floats.put(i, seed + i * 0.25f);
        }
        direct.position(64);
        ByteBuffer slice = direct.slice().order(ByteOrder.nativeOrder());
        direct.position(0);
        return new Object[] {new int[] {seed}, direct, heap, floats, slice};
    }

    private static long verifyBuffers(int round) {
        ByteBuffer heap = ByteBuffer.allocate(4096).order(ByteOrder.nativeOrder());
        ByteBuffer direct = ByteBuffer.allocateDirect(4096).order(ByteOrder.nativeOrder());
        FloatBuffer floats = direct.asFloatBuffer();
        for (int i = 0; i < 1024; i++) {
            heap.putInt(i * 4, i + round);
            floats.put(i, i * 0.125f);
        }
        ByteBuffer duplicate = direct.duplicate().order(ByteOrder.nativeOrder());
        direct.position(64);
        ByteBuffer slice = direct.slice().order(ByteOrder.nativeOrder());
        System.gc();
        long checksum = 0;
        for (int i = 0; i < 1024; i++) {
            require(heap.getInt(i * 4) == i + round, "Heap buffer lost data");
            require(floats.get(i) == i * 0.125f, "Direct float view lost data");
            require(duplicate.getFloat(i * 4) == i * 0.125f, "Duplicate lost data");
            if (i >= 16) require(slice.getFloat((i - 16) * 4) == i * 0.125f, "Slice lost data");
            checksum += heap.getInt(i * 4);
        }
        return checksum;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
