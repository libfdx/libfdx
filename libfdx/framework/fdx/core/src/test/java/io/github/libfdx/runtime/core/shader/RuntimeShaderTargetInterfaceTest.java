package io.github.libfdx.runtime.core.shader;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RuntimeShaderTargetInterfaceTest {
    @Test
    void decodesEntryScopedDirectCombinedAndExpandedBindings() {
        Writer writer = new Writer();
        writer.magic("FDXT").u32(1).u32(1)
                .u32(2).string("fs_main").string("fs_translated")
                .u32(3);
        writer.u32(0).u32(1).u32(1).u32(1)
                .string("texture").u32(2).u32(4).string("resource").string("albedo");
        writer.u32(0).u32(2).u32(2).u32(1)
                .string("sampler").u32(2).u32(4).string("resource").string("linearSampler");
        writer.u32(1).u32(0).u32(0).u32(3)
                .string("buffer").u32(3).u32(0).string("metadata").string("")
                .string("texture").u32(3).u32(1).string("plane0").string("")
                .string("texture").u32(3).u32(2).string("plane1").string("");

        RuntimeShaderTargetInterface targetInterface =
                RuntimeShaderTargetInterface.fromBytes(writer.bytes());

        assertEquals(1, targetInterface.entryPoints().length);
        assertEquals(RuntimeShaderCompileStage.FRAGMENT,
                targetInterface.entryPoints()[0].stage());
        assertEquals("fs_translated", targetInterface.entryPoints()[0].targetName());
        assertEquals(3, targetInterface.bindings().length);
        assertEquals(RuntimeShaderBindingRemapKind.COMBINED_TEXTURE,
                targetInterface.bindings()[0].kind());
        assertEquals(RuntimeShaderBindingRemapKind.COMBINED_SAMPLER,
                targetInterface.bindings()[1].kind());
        assertEquals(3, targetInterface.bindings()[2].targets().length);
    }

    @Test
    void rejectsTruncatedUnknownAndTrailingPayloads() {
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeShaderTargetInterface.fromBytes(new byte[8]));

        Writer unknownVersion = new Writer();
        unknownVersion.magic("FDXT").u32(2).u32(0);
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeShaderTargetInterface.fromBytes(unknownVersion.bytes()));

        Writer trailing = new Writer();
        trailing.magic("FDXT").u32(1).u32(0).u32(0).u32(99);
        assertThrows(IllegalArgumentException.class,
                () -> RuntimeShaderTargetInterface.fromBytes(trailing.bytes()));
    }

    private static final class Writer {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private Writer magic(String value) {
            output.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
            return this;
        }

        private Writer u32(int value) {
            output.writeBytes(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(value).array());
            return this;
        }

        private Writer string(String value) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            return u32(bytes.length).raw(bytes);
        }

        private Writer raw(byte[] value) {
            output.writeBytes(value);
            return this;
        }

        private byte[] bytes() {
            return output.toByteArray();
        }
    }
}
