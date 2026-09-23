package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class BufferInitializationTest {
    @Test
    void validatesRangesWithoutChangingBorrowedData() {
        Buffer buffer = buffer(BufferUsage.VERTEX, 16);
        ByteBuffer data = ByteBuffer.allocate(16).position(4).limit(12);
        BufferInitialization.validate(buffer, 8, data);
        assertEquals(4, data.position()); assertEquals(12, data.limit());
        for (int offset : new int[]{-4, 1, 12, Integer.MAX_VALUE})
            assertThrows(FdxException.class, () -> BufferInitialization.validate(buffer, offset, data));
        assertThrows(FdxException.class, () -> BufferInitialization.validate(buffer, 0, ByteBuffer.allocate(3)));
        assertThrows(FdxException.class, () -> BufferInitialization.validate(buffer, 0, ByteBuffer.allocate(0)));
        assertThrows(FdxException.class, () -> BufferInitialization.validate(buffer(BufferUsage.STORAGE,16), 0, data));
        assertThrows(FdxException.class, () -> BufferInitialization.validate(buffer, 0, null));
    }

    private static Buffer buffer(BufferUsage usage, int size) {
        return (Buffer) Proxy.newProxyInstance(Buffer.class.getClassLoader(), new Class<?>[]{Buffer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "usage" -> usage;
                    case "size" -> size;
                    default -> throw new AssertionError(method.getName());
                });
    }
}
