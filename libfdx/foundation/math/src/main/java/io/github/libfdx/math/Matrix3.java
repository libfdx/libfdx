package io.github.libfdx.math;

import io.github.libfdx.core.FdxException;

public final class Matrix3 {
    public static final int VALUE_COUNT = 9;

    private final float[] values = new float[VALUE_COUNT];

    public Matrix3() {
        idt();
    }

    public Matrix3(float[] values) {
        set(values);
    }

    public static Matrix3 identity() {
        return new Matrix3();
    }

    public Matrix3 idt() {
        for (int i = 0; i < VALUE_COUNT; i++) {
            values[i] = 0.0f;
        }
        values[0] = 1.0f;
        values[4] = 1.0f;
        values[8] = 1.0f;
        return this;
    }

    public Matrix3 set(float[] source) {
        if (source == null || source.length != VALUE_COUNT) {
            throw new FdxException("Matrix3 requires 9 values");
        }
        System.arraycopy(source, 0, values, 0, VALUE_COUNT);
        return this;
    }

    public float[] values() {
        return values.clone();
    }
}
