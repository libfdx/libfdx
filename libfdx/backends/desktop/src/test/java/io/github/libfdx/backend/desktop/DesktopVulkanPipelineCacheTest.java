package io.github.libfdx.backend.desktop;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopVulkanPipelineCacheTest {
    @Test
    void rejectsTruncationVersionAndForeignDeviceBeforePassingBytesToTheDriver() {
        byte[] uuid = new byte[16]; uuid[4] = 9;
        byte[] bytes = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(32).putInt(1).putInt(10).putInt(20).put(uuid).array();
        assertTrue(DesktopVulkanPipelineCache.validHeader(bytes, 10, 20, uuid));
        assertFalse(DesktopVulkanPipelineCache.validHeader(Arrays.copyOf(bytes, 31), 10, 20, uuid));
        assertFalse(DesktopVulkanPipelineCache.validHeader(bytes, 11, 20, uuid));
        assertFalse(DesktopVulkanPipelineCache.validHeader(bytes, 10, 21, uuid));
        assertFalse(DesktopVulkanPipelineCache.validHeader(bytes, 10, 20, new byte[16]));
        bytes[0] = 40;
        assertFalse(DesktopVulkanPipelineCache.validHeader(bytes, 10, 20, uuid));
        bytes[0] = 32; bytes[4] = 2;
        assertFalse(DesktopVulkanPipelineCache.validHeader(bytes, 10, 20, uuid));
    }
}
