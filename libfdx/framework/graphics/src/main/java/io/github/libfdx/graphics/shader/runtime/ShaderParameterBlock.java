package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.core.FdxException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Reusable, application-owned storage for one shader parameter layout.
 *
 * <p>The block is not thread-safe. Setters update a monotonically increasing revision only when
 * the stored bits change. Callers should retain the block for as long as queued draws can refer to
 * its identity and revision.</p>
 */
public final class ShaderParameterBlock {
    private static final AtomicLong NEXT_IDENTITY = new AtomicLong(1);

    private final long identity;
    private final ShaderParameterLayout layout;
    private final ByteBuffer storage;
    private long revision;
    private int dirtyStart = -1;
    private int dirtyEnd;

    private ShaderParameterBlock(ShaderParameterLayout layout, long byteSize) {
        if (layout == null) {
            throw new FdxException("Shader parameter block layout cannot be null");
        }
        if (byteSize < layout.minimumBindingSize()) {
            throw new FdxException("Shader parameter block byte size is smaller than the minimum binding size");
        }
        if (byteSize > Integer.MAX_VALUE) {
            throw new FdxException("Shader parameter layout is too large for Java direct storage: "
                    + byteSize);
        }
        identity = nextIdentity();
        this.layout = layout;
        storage = ByteBuffer.allocateDirect(Math.toIntExact(byteSize)).order(ByteOrder.nativeOrder());
    }

    /**
     * Allocates reusable direct, native-order storage.
     *
     * @param layout the immutable layout
     * @return the block
     */
    public static ShaderParameterBlock allocate(ShaderParameterLayout layout) {
        if (layout == null) {
            throw new FdxException("Shader parameter block layout cannot be null");
        }
        return new ShaderParameterBlock(layout, layout.minimumBindingSize());
    }

    /**
     * Allocates explicit storage for a layout containing a runtime-sized array.
     *
     * @param layout the immutable layout
     * @param byteSize the concrete buffer byte size
     * @return the block
     */
    public static ShaderParameterBlock allocate(ShaderParameterLayout layout, long byteSize) {
        return new ShaderParameterBlock(layout, byteSize);
    }

    public long identity() {
        return identity;
    }

    public long revision() {
        return revision;
    }

    public ShaderParameterLayout layout() {
        return layout;
    }

    public int byteSize() {
        return storage.capacity();
    }

    public boolean isDirty() {
        return dirtyStart >= 0;
    }

    public int dirtyStart() {
        return dirtyStart;
    }

    public int dirtyEnd() {
        return dirtyEnd;
    }

    public void clearDirty() {
        dirtyStart = -1;
        dirtyEnd = 0;
    }

    /**
     * Returns a read-only direct view. Mutations must use validated setters so revisions and dirty
     * ranges cannot be bypassed.
     *
     * @return the read-only native-order view
     */
    public ByteBuffer readOnlyData() {
        return storage.asReadOnlyBuffer().order(ByteOrder.nativeOrder());
    }

    /**
     * Copies the complete block into caller-owned reusable storage without
     * exposing mutable parameter storage.
     *
     * <p>The destination position and limit are not changed. Providers use
     * this method to snapshot a block while recording a draw or dispatch, so a
     * later application mutation cannot change already-recorded work.</p>
     *
     * @param destination destination buffer
     * @param destinationOffset first destination byte
     */
    public void copyTo(ByteBuffer destination, int destinationOffset) {
        if (destination == null || destinationOffset < 0
                || destinationOffset > destination.capacity() - storage.capacity()) {
            throw new FdxException("Shader parameter block destination range is out of bounds");
        }
        int size = storage.capacity();
        int destinationLimit = destination.limit();
        try {
            destination.limit(destination.capacity());
            for (int offset = 0; offset < size; offset++) {
                destination.put(destinationOffset + offset, storage.get(offset));
            }
        }
        finally {
            destination.limit(destinationLimit);
        }
    }

    public ShaderParameterBlock setFloat(ShaderParameterHandle handle, float value) {
        requireScalar(handle, ShaderScalarType.F32);
        writeIntBits(writableIndex(handle, 4), Float.floatToRawIntBits(value));
        return this;
    }

    public ShaderParameterBlock setInt(ShaderParameterHandle handle, int value) {
        requireScalar(handle, ShaderScalarType.I32);
        writeIntBits(writableIndex(handle, 4), value);
        return this;
    }

    public ShaderParameterBlock setUnsignedInt(ShaderParameterHandle handle, int value) {
        requireScalar(handle, ShaderScalarType.U32);
        writeIntBits(writableIndex(handle, 4), value);
        return this;
    }

    public ShaderParameterBlock setBoolean(ShaderParameterHandle handle, boolean value) {
        requireScalar(handle, ShaderScalarType.BOOL);
        writeIntBits(writableIndex(handle, 4), value ? 1 : 0);
        return this;
    }

    public ShaderParameterBlock setHalfBits(ShaderParameterHandle handle, short bits) {
        requireScalar(handle, ShaderScalarType.F16);
        int offset = writableIndex(handle, 2);
        if (storage.getShort(offset) == bits) {
            return this;
        }
        ensureRevisionAvailable();
        storage.putShort(offset, bits);
        revision++;
        markDirty(offset, offset + 2);
        return this;
    }

    public ShaderParameterBlock setSignedByte(ShaderParameterHandle handle, byte value) {
        requireScalar(handle, ShaderScalarType.I8);
        return writeByte(handle, value);
    }

    public ShaderParameterBlock setUnsignedByte(ShaderParameterHandle handle, int value) {
        requireScalar(handle, ShaderScalarType.U8);
        if (value < 0 || value > 255) {
            throw new FdxException("Unsigned shader byte value is out of range: " + value);
        }
        return writeByte(handle, (byte) value);
    }

    /**
     * Writes the exact occupied bytes of any reflected handle, including types without a dedicated
     * convenience setter.
     *
     * @param handle the validated handle
     * @param source the source bytes
     * @param sourceOffset the first source byte
     * @return this block
     */
    public ShaderParameterBlock setRawBytes(ShaderParameterHandle handle, byte[] source, int sourceOffset) {
        requireOwned(handle);
        if (handle.occupiedSize() > Integer.MAX_VALUE) {
            throw new FdxException("Shader parameter is too large for a Java byte-array write: " + handle.path());
        }
        int length = Math.toIntExact(handle.occupiedSize());
        if (source == null || sourceOffset < 0 || sourceOffset > source.length - length) {
            throw new FdxException("Shader parameter raw source range is out of bounds");
        }
        int destination = writableIndex(handle, length);
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (int i = 0; i < length; i++) {
            if (storage.get(destination + i) != source[sourceOffset + i]) {
                first = Math.min(first, destination + i);
                last = destination + i + 1;
            }
        }
        if (last < 0) {
            return this;
        }
        ensureRevisionAvailable();
        for (int i = 0; i < length; i++) {
            byte value = source[sourceOffset + i];
            if (storage.get(destination + i) != value) {
                storage.put(destination + i, value);
            }
        }
        revision++;
        markDirty(first, last);
        return this;
    }

    public ShaderParameterBlock setFloat2(ShaderParameterHandle handle, float x, float y) {
        requireFloatVector(handle, 2);
        writeFloat2(writableIndex(handle, 8), x, y);
        return this;
    }

    public ShaderParameterBlock setFloat3(ShaderParameterHandle handle, float x, float y, float z) {
        requireFloatVector(handle, 3);
        writeFloat3(writableIndex(handle, 12), x, y, z);
        return this;
    }

    public ShaderParameterBlock setFloat4(ShaderParameterHandle handle, float x, float y, float z, float w) {
        requireFloatVector(handle, 4);
        writeFloat4(writableIndex(handle, 16), x, y, z, w);
        return this;
    }

    /**
     * Writes a float vector without retaining or modifying the source array.
     *
     * @param handle the vector handle
     * @param values the source values
     * @param sourceOffset the first source value
     * @return this block
     */
    public ShaderParameterBlock setFloatVector(ShaderParameterHandle handle, float[] values, int sourceOffset) {
        requireOwned(handle);
        ShaderValueType type = handle.valueType();
        if (type.kind() != ShaderValueKind.VECTOR || type.scalarType() != ShaderScalarType.F32) {
            throw wrongType(handle, "F32 vector");
        }
        requireSource(values, sourceOffset, type.rows());
        writeFloatSequence(writableIndex(handle, type.rows() * 4), values, sourceOffset, type.rows());
        return this;
    }

    /**
     * Writes a column-major float matrix using its reflected matrix stride.
     *
     * @param handle the matrix handle
     * @param values the source values
     * @param sourceOffset the first source value
     * @return this block
     */
    public ShaderParameterBlock setFloatMatrix(ShaderParameterHandle handle, float[] values, int sourceOffset) {
        requireOwned(handle);
        ShaderValueType type = handle.valueType();
        if (type.kind() != ShaderValueKind.MATRIX || type.scalarType() != ShaderScalarType.F32) {
            throw wrongType(handle, "F32 matrix");
        }
        requireSource(values, sourceOffset, type.columns() * type.rows());
        int writeSize = matrixWriteSize(type, handle.matrixStride());
        writeMatrix(writableIndex(handle, writeSize), index(handle.matrixStride()), type, values, sourceOffset);
        return this;
    }

    public ShaderParameterBlock setArrayElementFloat(ShaderParameterHandle array, int arrayIndex, float value) {
        requireArrayElement(array, arrayIndex, ShaderValueKind.SCALAR, ShaderScalarType.F32);
        int destination = arrayElementOffset(array, arrayIndex, 4);
        writeIntBits(destination, Float.floatToRawIntBits(value));
        return this;
    }

    public ShaderParameterBlock setArrayElementFloatVector(ShaderParameterHandle array, int arrayIndex,
            float[] values, int sourceOffset) {
        ShaderValueType element = requireArrayElement(array, arrayIndex, ShaderValueKind.VECTOR, ShaderScalarType.F32);
        requireSource(values, sourceOffset, element.rows());
        writeFloatSequence(arrayElementOffset(array, arrayIndex, element.rows() * 4), values, sourceOffset,
                element.rows());
        return this;
    }

    public ShaderParameterBlock setArrayElementFloatMatrix(ShaderParameterHandle array, int arrayIndex,
            float[] values, int sourceOffset) {
        ShaderValueType element = requireArrayElement(array, arrayIndex, ShaderValueKind.MATRIX, ShaderScalarType.F32);
        requireSource(values, sourceOffset, element.columns() * element.rows());
        int writeSize = matrixWriteSize(element, array.matrixStride());
        writeMatrix(arrayElementOffset(array, arrayIndex, writeSize), index(array.matrixStride()), element, values,
                sourceOffset);
        return this;
    }

    private void writeFloat2(int destination, float x, float y) {
        int xBits = Float.floatToRawIntBits(x);
        int yBits = Float.floatToRawIntBits(y);
        boolean xChanged = storage.getInt(destination) != xBits;
        boolean yChanged = storage.getInt(destination + 4) != yBits;
        if (!xChanged && !yChanged) {
            return;
        }
        ensureRevisionAvailable();
        if (xChanged) {
            storage.putInt(destination, xBits);
        }
        if (yChanged) {
            storage.putInt(destination + 4, yBits);
        }
        revision++;
        markDirty(xChanged ? destination : destination + 4, yChanged ? destination + 8 : destination + 4);
    }

    private void writeFloat3(int destination, float x, float y, float z) {
        int xBits = Float.floatToRawIntBits(x);
        int yBits = Float.floatToRawIntBits(y);
        int zBits = Float.floatToRawIntBits(z);
        boolean xChanged = storage.getInt(destination) != xBits;
        boolean yChanged = storage.getInt(destination + 4) != yBits;
        boolean zChanged = storage.getInt(destination + 8) != zBits;
        if (!xChanged && !yChanged && !zChanged) {
            return;
        }
        ensureRevisionAvailable();
        if (xChanged) {
            storage.putInt(destination, xBits);
        }
        if (yChanged) {
            storage.putInt(destination + 4, yBits);
        }
        if (zChanged) {
            storage.putInt(destination + 8, zBits);
        }
        revision++;
        int first = xChanged ? destination : yChanged ? destination + 4 : destination + 8;
        int end = zChanged ? destination + 12 : yChanged ? destination + 8 : destination + 4;
        markDirty(first, end);
    }

    private void writeFloat4(int destination, float x, float y, float z, float w) {
        int xBits = Float.floatToRawIntBits(x);
        int yBits = Float.floatToRawIntBits(y);
        int zBits = Float.floatToRawIntBits(z);
        int wBits = Float.floatToRawIntBits(w);
        boolean xChanged = storage.getInt(destination) != xBits;
        boolean yChanged = storage.getInt(destination + 4) != yBits;
        boolean zChanged = storage.getInt(destination + 8) != zBits;
        boolean wChanged = storage.getInt(destination + 12) != wBits;
        if (!xChanged && !yChanged && !zChanged && !wChanged) {
            return;
        }
        ensureRevisionAvailable();
        if (xChanged) {
            storage.putInt(destination, xBits);
        }
        if (yChanged) {
            storage.putInt(destination + 4, yBits);
        }
        if (zChanged) {
            storage.putInt(destination + 8, zBits);
        }
        if (wChanged) {
            storage.putInt(destination + 12, wBits);
        }
        revision++;
        int first = xChanged ? destination : yChanged ? destination + 4 : zChanged ? destination + 8
                : destination + 12;
        int end = wChanged ? destination + 16 : zChanged ? destination + 12 : yChanged ? destination + 8
                : destination + 4;
        markDirty(first, end);
    }

    private void writeFloatSequence(int destination, float[] values, int sourceOffset, int count) {
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (int i = 0; i < count; i++) {
            int bits = Float.floatToRawIntBits(values[sourceOffset + i]);
            int offset = destination + i * 4;
            if (storage.getInt(offset) != bits) {
                first = Math.min(first, offset);
                last = Math.max(last, offset + 4);
            }
        }
        if (last < 0) {
            return;
        }
        ensureRevisionAvailable();
        for (int i = 0; i < count; i++) {
            int bits = Float.floatToRawIntBits(values[sourceOffset + i]);
            int offset = destination + i * 4;
            if (storage.getInt(offset) != bits) {
                storage.putInt(offset, bits);
            }
        }
        revision++;
        markDirty(first, last);
    }

    private void writeMatrix(int base, int matrixStride, ShaderValueType type, float[] values, int sourceOffset) {
        int first = Integer.MAX_VALUE;
        int last = -1;
        for (int column = 0; column < type.columns(); column++) {
            int destination = base + column * matrixStride;
            for (int row = 0; row < type.rows(); row++) {
                int bits = Float.floatToRawIntBits(values[sourceOffset + column * type.rows() + row]);
                int offset = destination + row * 4;
                if (storage.getInt(offset) != bits) {
                    first = Math.min(first, offset);
                    last = Math.max(last, offset + 4);
                }
            }
        }
        if (last < 0) {
            return;
        }
        ensureRevisionAvailable();
        for (int column = 0; column < type.columns(); column++) {
            int destination = base + column * matrixStride;
            for (int row = 0; row < type.rows(); row++) {
                int bits = Float.floatToRawIntBits(values[sourceOffset + column * type.rows() + row]);
                int offset = destination + row * 4;
                if (storage.getInt(offset) != bits) {
                    storage.putInt(offset, bits);
                }
            }
        }
        revision++;
        markDirty(first, last);
    }

    private ShaderValueType requireArrayElement(ShaderParameterHandle handle, int index, ShaderValueKind kind,
            ShaderScalarType scalarType) {
        requireOwned(handle);
        ShaderValueType array = handle.valueType();
        if (array.kind() != ShaderValueKind.ARRAY || array.elementType().kind() != kind
                || array.elementType().scalarType() != scalarType) {
            throw wrongType(handle, "array of " + scalarType + ' ' + kind);
        }
        long count = array.arrayCount();
        if (index < 0 || (count >= 0 && index >= count)) {
            throw new FdxException("Shader array index is out of range for " + handle.path() + ": " + index);
        }
        return array.elementType();
    }

    private int arrayElementOffset(ShaderParameterHandle handle, int index, int writeSize) {
        long offset;
        try {
            offset = Math.addExact(handle.byteOffset(), Math.multiplyExact((long) index, handle.arrayStride()));
        } catch (ArithmeticException exception) {
            throw new FdxException("Shader array element byte offset overflows: " + handle.path(), exception);
        }
        requireWritableRange(offset, writeSize, handle.path());
        return index(offset);
    }

    private void requireFloatVector(ShaderParameterHandle handle, int width) {
        requireOwned(handle);
        ShaderValueType type = handle.valueType();
        if (type.kind() != ShaderValueKind.VECTOR || type.scalarType() != ShaderScalarType.F32
                || type.rows() != width) {
            throw wrongType(handle, "vec" + width + "<F32>");
        }
    }

    private void requireScalar(ShaderParameterHandle handle, ShaderScalarType scalarType) {
        requireOwned(handle);
        ShaderValueType type = handle.valueType();
        if (!type.isScalar() || type.scalarType() != scalarType) {
            throw wrongType(handle, scalarType + " scalar");
        }
    }

    private void requireOwned(ShaderParameterHandle handle) {
        if (handle == null) {
            throw new FdxException("Shader parameter handle cannot be null");
        }
        if (!layout.owns(handle)) {
            throw new FdxException("Shader parameter handle is foreign or stale for this layout: " + handle.path());
        }
    }

    private ShaderParameterBlock writeByte(ShaderParameterHandle handle, byte value) {
        int offset = writableIndex(handle, 1);
        if (storage.get(offset) == value) {
            return this;
        }
        ensureRevisionAvailable();
        storage.put(offset, value);
        revision++;
        markDirty(offset, offset + 1);
        return this;
    }

    private int writableIndex(ShaderParameterHandle handle, int writeSize) {
        requireOwned(handle);
        requireWritableRange(handle.byteOffset(), writeSize, handle.path());
        return index(handle.byteOffset());
    }

    private void requireWritableRange(long offset, long writeSize, String path) {
        if (offset < 0 || writeSize < 0 || offset > storage.capacity()
                || writeSize > storage.capacity() - offset) {
            throw new FdxException("Shader parameter write exceeds block storage: " + path);
        }
    }

    private static int matrixWriteSize(ShaderValueType type, long matrixStride) {
        long size;
        try {
            size = Math.addExact(Math.multiplyExact(type.columns() - 1L, matrixStride),
                    Math.multiplyExact(type.rows(), 4L));
        } catch (ArithmeticException exception) {
            throw new FdxException("Shader matrix write size overflows", exception);
        }
        return index(size);
    }

    private void writeIntBits(int offset, int bits) {
        if (storage.getInt(offset) == bits) {
            return;
        }
        ensureRevisionAvailable();
        storage.putInt(offset, bits);
        revision++;
        markDirty(offset, offset + 4);
    }

    private void ensureRevisionAvailable() {
        if (revision == Long.MAX_VALUE) {
            throw new FdxException("Shader parameter block revision space is exhausted");
        }
    }

    private void markDirty(int start, int end) {
        if (dirtyStart < 0) {
            dirtyStart = start;
            dirtyEnd = end;
        } else {
            dirtyStart = Math.min(dirtyStart, start);
            dirtyEnd = Math.max(dirtyEnd, end);
        }
    }

    private static int index(long value) {
        try {
            return Math.toIntExact(value);
        } catch (ArithmeticException exception) {
            throw new FdxException("Shader parameter byte index is too large for Java direct storage: " + value,
                    exception);
        }
    }

    private static void requireSource(float[] values, int sourceOffset, int count) {
        if (values == null) {
            throw new FdxException("Shader parameter source values cannot be null");
        }
        if (sourceOffset < 0 || sourceOffset > values.length - count) {
            throw new FdxException("Shader parameter source range is out of bounds");
        }
    }

    private static FdxException wrongType(ShaderParameterHandle handle, String expected) {
        return new FdxException("Shader parameter " + handle.path() + " has type " + handle.valueType()
                + ", expected " + expected);
    }

    private static long nextIdentity() {
        long value = NEXT_IDENTITY.getAndIncrement();
        if (value <= 0) {
            throw new FdxException("Shader parameter block identity space is exhausted");
        }
        return value;
    }
}
