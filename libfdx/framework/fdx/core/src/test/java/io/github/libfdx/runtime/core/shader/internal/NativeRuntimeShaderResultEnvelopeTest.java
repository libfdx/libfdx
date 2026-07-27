package io.github.libfdx.runtime.core.shader.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class NativeRuntimeShaderResultEnvelopeTest {
    private static final byte[] REFLECTION = {'F', 'D', 'X', 'I', 1, 0, 0, 0};

    @Test
    void decodesTextAndSpirvResultsWithReflection() {
        RuntimeShaderCompileResult text = decode(0, 1, "hello".getBytes(StandardCharsets.UTF_8),
                new byte[0], REFLECTION, new byte[0]);
        RuntimeShaderCompileResult spirv = decode(0, 2, new byte[] {3, 2, 1}, new byte[0],
                REFLECTION, new byte[0]);

        assertTrue(text.success());
        assertEquals(RuntimeShaderCompileOutputKind.TEXT, text.outputKind());
        assertEquals("hello", text.outputText());
        assertTrue(text.hasReflection());

        assertTrue(spirv.success());
        assertEquals(RuntimeShaderCompileOutputKind.SPIRV, spirv.outputKind());
        assertArrayEquals(new byte[] {3, 2, 1}, spirv.output());
        assertTrue(spirv.hasReflection());
    }

    @Test
    void decodesVersionTwoTargetInterfaceAndKeepsVersionOneCompatible() {
        byte[] targetInterface = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN)
                .put((byte)'F').put((byte)'D').put((byte)'X').put((byte)'T')
                .putInt(1)
                .putInt(1)
                .putInt(1)
                .putInt(2).put((byte)'v').put((byte)'s')
                .putInt(2).put((byte)'v').put((byte)'s')
                .putInt(0)
                .array();
        byte[] output = "shader".getBytes(StandardCharsets.UTF_8);
        ByteBuffer envelope = ByteBuffer.allocate(32 + output.length + REFLECTION.length
                + targetInterface.length).order(ByteOrder.LITTLE_ENDIAN);
        envelope.put((byte)'F').put((byte)'D').put((byte)'X').put((byte)'R');
        envelope.putInt(2).putInt(0).putInt(1)
                .putInt(output.length).putInt(0).putInt(REFLECTION.length)
                .putInt(targetInterface.length)
                .put(output).put(REFLECTION).put(targetInterface);

        RuntimeShaderCompileResult result = decodeBytes(envelope.array());

        assertTrue(result.success());
        assertTrue(result.hasTargetInterface());
        assertEquals("vs", result.targetInterface().entryPoints()[0].targetName());
    }

    @Test
    void preservesNativeFailureDiagnosticWithoutRequiringReflection() {
        RuntimeShaderCompileResult result = decode(1, 0, new byte[0],
                "invalid WGSL".getBytes(StandardCharsets.UTF_8), new byte[0], new byte[0]);

        assertFalse(result.success());
        assertEquals("invalid WGSL", result.diagnostics()[0].message());
    }

    @Test
    void rejectsInvalidEnvelopeBoundaries() {
        assertFailure(NativeRuntimeShaderResultEnvelope.decodeBase64("%%%"), "invalid base64");

        byte[] truncated = new byte[27];
        assertFailure(decodeBytes(truncated), "truncated");

        byte[] badMagic = envelope(0, 1, new byte[0], new byte[0], REFLECTION, new byte[0]);
        badMagic[0] = 'B';
        assertFailure(decodeBytes(badMagic), "magic");

        byte[] badVersion = envelope(0, 1, new byte[0], new byte[0], REFLECTION, new byte[0]);
        badVersion[4] = 3;
        assertFailure(decodeBytes(badVersion), "version");

        byte[] negativeLength = envelope(0, 1, new byte[0], new byte[0], REFLECTION, new byte[0]);
        ByteBuffer.wrap(negativeLength).order(ByteOrder.LITTLE_ENDIAN).putInt(16, -1);
        assertFailure(decodeBytes(negativeLength), "length");

        byte[] incomplete = envelope(0, 1, new byte[] {1}, new byte[0], REFLECTION, new byte[0]);
        assertFailure(decodeBytes(java.util.Arrays.copyOf(incomplete, incomplete.length - 1)), "incomplete");

        assertFailure(decode(0, 1, new byte[0], new byte[0], REFLECTION, new byte[] {1}), "trailing");
    }

    @Test
    void rejectsSuccessfulResultWithoutValidReflectionOrOutputKind() {
        assertFailure(decode(0, 1, new byte[0], new byte[0], new byte[0], new byte[0]),
                "no reflection");
        assertFailure(decode(0, 0, new byte[0], new byte[0], REFLECTION, new byte[0]),
                "no output");

        byte[] invalidReflection = {'B', 'A', 'D', '!', 1, 0, 0, 0};
        assertFailure(decode(0, 1, new byte[0], new byte[0], invalidReflection, new byte[0]),
                "invalid reflection");
    }

    private static RuntimeShaderCompileResult decode(int status, int kind, byte[] output,
            byte[] diagnostics, byte[] reflection, byte[] trailing) {
        return decodeBytes(envelope(status, kind, output, diagnostics, reflection, trailing));
    }

    private static RuntimeShaderCompileResult decodeBytes(byte[] bytes) {
        return NativeRuntimeShaderResultEnvelope.decodeBase64(Base64.getEncoder().encodeToString(bytes));
    }

    private static byte[] envelope(int status, int kind, byte[] output, byte[] diagnostics,
            byte[] reflection, byte[] trailing) {
        ByteBuffer buffer = ByteBuffer.allocate(28 + output.length + diagnostics.length
                + reflection.length + trailing.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put((byte)'F').put((byte)'D').put((byte)'X').put((byte)'R');
        buffer.putInt(1);
        buffer.putInt(status);
        buffer.putInt(kind);
        buffer.putInt(output.length);
        buffer.putInt(diagnostics.length);
        buffer.putInt(reflection.length);
        buffer.put(output).put(diagnostics).put(reflection).put(trailing);
        return buffer.array();
    }

    private static void assertFailure(RuntimeShaderCompileResult result, String messagePart) {
        assertFalse(result.success());
        assertTrue(result.diagnostics()[0].message().contains(messagePart),
                () -> "Expected diagnostic containing '" + messagePart + "' but got '"
                        + result.diagnostics()[0].message() + "'");
    }
}
