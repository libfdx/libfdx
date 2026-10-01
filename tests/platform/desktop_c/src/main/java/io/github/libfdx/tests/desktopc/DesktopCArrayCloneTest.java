package io.github.libfdx.tests.desktopc;

/** Exercises array clone sizes and heap integrity in the C runtime and on the JVM. */
public final class DesktopCArrayCloneTest {
    private DesktopCArrayCloneTest() {}

    public static void main(String[] args) {
        long checksum = 0;
        Object[] survivors = new Object[64];
        for (int round = 0; round < 3000; round++) {
            int size = 1 + round % 1025;
            boolean[] booleans = new boolean[size];
            byte[] bytes = new byte[size];
            char[] chars = new char[size];
            short[] shorts = new short[size];
            int[] ints = new int[size];
            float[] floats = new float[size];
            long[] longs = new long[size];
            double[] doubles = new double[size];
            Object[] objects = new Object[size];
            Object sentinel = new Object();
            for (int i = 0; i < size; i++) {
                booleans[i] = (i & 1) == 0;
                bytes[i] = (byte) (i * 17);
                chars[i] = (char) (i * 31);
                shorts[i] = (short) (i * 101);
                ints[i] = i * 1234567;
                floats[i] = i * 0.125f;
                longs[i] = 0x123456789abcL + i;
                doubles[i] = -i * 0.03125;
                objects[i] = sentinel;
            }
            boolean[] bc = booleans.clone();
            byte[] yc = bytes.clone();
            char[] cc = chars.clone();
            short[] sc = shorts.clone();
            int[] ic = ints.clone();
            float[] fc = floats.clone();
            long[] lc = longs.clone();
            double[] dc = doubles.clone();
            Object[] oc = objects.clone();
            survivors[round % survivors.length] = new Object[] {yc, cc, ic, fc, oc};
            if ((round & 15) == 0) System.gc();
            for (int i = 0; i < size; i++) {
                require(bc[i] == booleans[i] && yc[i] == bytes[i] && cc[i] == chars[i]);
                require(sc[i] == shorts[i] && ic[i] == ints[i] && fc[i] == floats[i]);
                require(lc[i] == longs[i] && dc[i] == doubles[i] && oc[i] == sentinel);
                checksum += ic[i] + lc[i] + cc[i];
            }
            yc[0] = 99;
            oc[0] = null;
            require(bytes[0] == 0 && objects[0] == sentinel);
            require(new int[0].clone().length == 0 && new Object[0].clone().length == 0);
        }
        System.gc();
        for (Object survivor : survivors) {
            Object[] arrays = (Object[]) survivor;
            require(((byte[]) arrays[0])[0] == 99 && ((Object[]) arrays[4])[0] == null);
        }
        System.out.println("ARRAY_CLONE_PASS " + checksum);
    }

    private static void require(boolean condition) {
        if (!condition)
            throw new AssertionError("Array clone corrupted its values or neighboring objects");
    }
}
