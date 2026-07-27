package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.shader.reflection.ShaderParameter;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;
import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderParameterBlockTest {
    @Test
    void ownsDirectNativeOrderStorageAndTracksBitwiseChanges() {
        ShaderParameterLayout layout = layout();
        ShaderParameterBlock block = ShaderParameterBlock.allocate(layout);
        ShaderParameterHandle scalar = layout.requireHandle("scalar");
        ShaderParameterHandle vector = layout.requireHandle("vector");

        assertTrue(block.readOnlyData().isDirect());
        assertTrue(block.readOnlyData().isReadOnly());
        assertEquals(ByteOrder.nativeOrder(), block.readOnlyData().order());
        assertThrows(ReadOnlyBufferException.class, () -> block.readOnlyData().put(0, (byte) 1));

        block.setFloat(scalar, 0.0f);
        assertEquals(0, block.revision());
        block.setFloat(scalar, -0.0f);
        assertEquals(1, block.revision());
        assertEquals(0, block.dirtyStart());
        assertEquals(4, block.dirtyEnd());
        block.setFloat(scalar, -0.0f);
        assertEquals(1, block.revision());

        block.clearDirty();
        block.setFloat4(vector, 1, 2, 3, 4);
        assertEquals(2, block.revision());
        assertEquals(16, block.dirtyStart());
        assertEquals(32, block.dirtyEnd());
        assertEquals(4.0f, block.readOnlyData().getFloat(28));

        block.clearDirty();
        block.setFloat(vector.component(3), 9.0f);
        assertEquals(3, block.revision());
        assertEquals(28, block.dirtyStart());
        assertEquals(32, block.dirtyEnd());
        assertSame(vector.component(3), vector.component(3));
    }

    @Test
    void writesMatricesAndCachedFixedArrayElementsAtReflectedStrides() {
        ShaderParameterLayout layout = layout();
        ShaderParameterBlock block = ShaderParameterBlock.allocate(layout);
        ShaderParameterHandle matrix = layout.requireHandle("matrix");
        ShaderParameterHandle array = layout.requireHandle("array");
        float[] matrixValues = { 1, 2, 3, 4, 5, 6, 7, 8, 9 };

        block.setFloatMatrix(matrix, matrixValues, 0);
        assertEquals(1.0f, block.readOnlyData().getFloat(32));
        assertEquals(4.0f, block.readOnlyData().getFloat(48));
        assertEquals(7.0f, block.readOnlyData().getFloat(64));

        ShaderParameterHandle second = array.element(1);
        assertSame(second, array.element(1));
        assertEquals(96, second.byteOffset());
        block.setFloat4(second, 10, 11, 12, 13);
        assertEquals(13.0f, block.readOnlyData().getFloat(108));
        assertThrows(FdxException.class, () -> array.element(2));
    }

    @Test
    void rejectsWrongForeignAndStructurallyEqualButStaleHandles() {
        ShaderParameterLayout first = layout();
        ShaderParameterLayout replacement = layout();
        ShaderParameterBlock block = ShaderParameterBlock.allocate(first);

        assertNotEquals(first.identity(), replacement.identity());
        assertTrue(first.physicallyEquivalent(replacement));
        assertThrows(FdxException.class, () -> block.setInt(first.requireHandle("scalar"), 1));
        assertThrows(FdxException.class, () -> block.setFloat(replacement.requireHandle("scalar"), 1));
        assertThrows(FdxException.class, () -> block.setFloat(null, 1));
        assertThrows(FdxException.class, () -> first.requireHandle("missing"));
    }

    @Test
    void runtimeArrayWritesUseExplicitCapacityAndFailBeforeBufferAccess() {
        ShaderValueType runtimeType = ShaderValueType.runtimeArray(
                ShaderValueType.scalar(ShaderScalarType.F32), 4);
        ShaderParameter runtime = ShaderParameter.builder("values", "values", runtimeType, 16, 0, 4)
                .minimumRequiredSize(0)
                .arrayStride(4)
                .build();
        ShaderParameterLayout layout = ShaderParameterLayout.of(16, 4, runtime);
        ShaderParameterBlock block = ShaderParameterBlock.allocate(layout, 32);
        ShaderParameterHandle values = layout.requireHandle("values");

        block.setArrayElementFloat(values, 3, 7.0f);
        assertEquals(7.0f, block.readOnlyData().getFloat(28));
        assertThrows(FdxException.class, () -> block.setArrayElementFloat(values, 4, 8.0f));
        assertThrows(FdxException.class, () -> ShaderParameterBlock.allocate(layout, 15));
    }

    @Test
    void supportsExactRawAndNarrowScalarWritesWithoutUntrackedMutation() {
        ShaderParameterLayout layout = layout();
        ShaderParameterBlock block = ShaderParameterBlock.allocate(layout);
        ShaderParameterHandle signedByte = layout.requireHandle("signedByte");

        block.setSignedByte(signedByte, (byte) -4);
        assertEquals(-4, block.readOnlyData().get(112));
        long revision = block.revision();
        block.setRawBytes(signedByte, new byte[] { (byte) -4 }, 0);
        assertEquals(revision, block.revision());
        block.setRawBytes(signedByte, new byte[] { 7 }, 0);
        assertEquals(7, block.readOnlyData().get(112));
        assertThrows(FdxException.class, () -> block.setUnsignedByte(signedByte, 256));
    }

    @Test
    void copiesUsingDestinationCapacityWithoutChangingPositionOrLimit() {
        ShaderParameterLayout layout = layout();
        ShaderParameterBlock block = ShaderParameterBlock.allocate(layout);
        block.setFloat(layout.requireHandle("scalar"), 3.5f);
        ByteBuffer destination = ByteBuffer.allocateDirect(256)
                .order(ByteOrder.nativeOrder());
        destination.position(7);
        destination.limit(16);

        block.copyTo(destination, 64);

        assertEquals(7, destination.position());
        assertEquals(16, destination.limit());
        destination.limit(destination.capacity());
        assertEquals(3.5f, destination.getFloat(64));
    }

    private static ShaderParameterLayout layout() {
        ShaderValueType f32 = ShaderValueType.scalar(ShaderScalarType.F32);
        ShaderValueType vec4 = ShaderValueType.vector(ShaderScalarType.F32, 4);
        ShaderValueType matrix = ShaderValueType.matrix(ShaderScalarType.F32, 3, 3, 16);
        ShaderValueType array = ShaderValueType.array(vec4, 2, 16);
        return ShaderParameterLayout.of(128, 16,
                ShaderParameter.of("scalar", f32, 0, 4, 4),
                ShaderParameter.of("vector", vec4, 16, 16, 16),
                ShaderParameter.builder("matrix", "matrix", matrix, 32, 48, 16)
                        .matrixStride(16)
                        .build(),
                ShaderParameter.builder("array", "array", array, 80, 32, 16)
                        .arrayStride(16)
                        .build(),
                ShaderParameter.of("signedByte", ShaderValueType.scalar(ShaderScalarType.I8), 112, 1, 1));
    }
}
