package io.github.libfdx.runtime.core.shader;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.core.FdxException;
import org.junit.jupiter.api.Test;

class RuntimeShaderReflectionTest {
    @Test
    void wrapsSupportedEnvelopeDefensively() {
        byte[] encoded = minimalPayload();
        RuntimeShaderReflection reflection = RuntimeShaderReflection.fromBytes(encoded);
        encoded[0] = 0;

        assertArrayEquals(minimalPayload(), reflection.bytes());
        assertEquals(RuntimeShaderReflection.SCHEMA_VERSION, reflection.schemaVersion());

        byte[] returned = reflection.bytes();
        returned[0] = 0;
        assertArrayEquals(minimalPayload(), reflection.bytes());
    }

    @Test
    void validatesMagicVersionAndLength() {
        assertThrows(FdxException.class, () -> RuntimeShaderReflection.fromBytes(null));
        assertThrows(FdxException.class, () -> RuntimeShaderReflection.fromBytes(new byte[7]));

        byte[] badMagic = minimalPayload();
        badMagic[3] = 'R';
        assertThrows(FdxException.class, () -> RuntimeShaderReflection.fromBytes(badMagic));

        byte[] badVersion = minimalPayload();
        badVersion[4] = 2;
        assertThrows(FdxException.class, () -> RuntimeShaderReflection.fromBytes(badVersion));
    }

    @Test
    void compileResultOverloadsRemainOptionalAndValueBased() {
        RuntimeShaderReflection reflection = RuntimeShaderReflection.fromBytes(minimalPayload());
        RuntimeShaderCompileResult reflected = RuntimeShaderCompileResult.text("shader", reflection);
        RuntimeShaderCompileResult original = RuntimeShaderCompileResult.text("shader");

        assertTrue(reflected.hasReflection());
        assertEquals(reflection, reflected.reflection());
        assertFalse(original.hasReflection());
        assertNull(original.reflection());
        assertNotEquals(reflected.reflection(), original.reflection());
    }

    private static byte[] minimalPayload() {
        return new byte[] {'F', 'D', 'X', 'I', 1, 0, 0, 0};
    }
}
