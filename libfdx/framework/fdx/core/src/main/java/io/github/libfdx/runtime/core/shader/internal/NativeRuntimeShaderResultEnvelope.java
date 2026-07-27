package io.github.libfdx.runtime.core.shader.internal;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetInterface;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes the versioned native result envelope shared by Android and Web shader compiler bridges.
 *
 * <p>This class is an internal provider transport contract rather than an application shader API.</p>
 *
 * @author xpenatan
 */
public final class NativeRuntimeShaderResultEnvelope {
    private static final int VERSION_1_HEADER_SIZE = 28;
    private static final int VERSION_2_HEADER_SIZE = 32;

    private NativeRuntimeShaderResultEnvelope() {
    }

    /**
     * Decodes a base64-encoded {@code FDXR} result.
     *
     * @param encoded the native result
     * @return a successful compiler result or a diagnostic failure
     */
    public static RuntimeShaderCompileResult decodeBase64(String encoded) {
        if (encoded == null || encoded.length() == 0) {
            return failure("Native shader compiler returned no result");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException error) {
            return failure("Native shader compiler returned invalid base64");
        }
        if (bytes.length < VERSION_1_HEADER_SIZE) {
            return failure("Native shader compiler returned a truncated result");
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.get() != 'F' || buffer.get() != 'D' || buffer.get() != 'X' || buffer.get() != 'R') {
            return failure("Native shader compiler returned an invalid result magic");
        }
        int version = buffer.getInt();
        if (version != 1 && version != 2) {
            return failure("Native shader compiler returned unsupported result version " + version);
        }
        int headerSize = version == 1 ? VERSION_1_HEADER_SIZE : VERSION_2_HEADER_SIZE;
        if (bytes.length < headerSize) {
            return failure("Native shader compiler returned a truncated result");
        }

        int status = buffer.getInt();
        int kind = buffer.getInt();
        int outputSize = buffer.getInt();
        int diagnosticSize = buffer.getInt();
        int reflectionSize = buffer.getInt();
        int targetInterfaceSize = version >= 2 ? buffer.getInt() : 0;
        if (outputSize < 0 || diagnosticSize < 0 || reflectionSize < 0 || targetInterfaceSize < 0) {
            return failure("Native shader compiler returned an unsupported result length");
        }
        long payloadSize = (long)outputSize + diagnosticSize + reflectionSize + targetInterfaceSize;
        if (payloadSize > buffer.remaining()) {
            return failure("Native shader compiler returned an incomplete result");
        }
        if (payloadSize < buffer.remaining()) {
            return failure("Native shader compiler returned trailing result data");
        }

        byte[] output = new byte[outputSize];
        buffer.get(output);
        byte[] diagnostics = new byte[diagnosticSize];
        buffer.get(diagnostics);
        byte[] reflectionBytes = new byte[reflectionSize];
        buffer.get(reflectionBytes);
        byte[] targetInterfaceBytes = new byte[targetInterfaceSize];
        buffer.get(targetInterfaceBytes);
        if (status != 0) {
            return failure(new String(diagnostics, StandardCharsets.UTF_8));
        }
        if (reflectionSize == 0) {
            return failure("Native shader compiler returned no reflection");
        }

        RuntimeShaderReflection reflection;
        try {
            reflection = RuntimeShaderReflection.fromBytes(reflectionBytes);
        } catch (RuntimeException error) {
            return failure("Native shader compiler returned invalid reflection: " + error.getMessage());
        }
        RuntimeShaderTargetInterface targetInterface = null;
        if (targetInterfaceSize > 0) {
            try {
                targetInterface = RuntimeShaderTargetInterface.fromBytes(targetInterfaceBytes);
            } catch (RuntimeException error) {
                return failure("Native shader compiler returned invalid target interface: " + error.getMessage());
            }
        }
        RuntimeShaderCompileOutputKind outputKind = outputKind(kind);
        if (outputKind == RuntimeShaderCompileOutputKind.TEXT) {
            return RuntimeShaderCompileResult.text(new String(output, StandardCharsets.UTF_8),
                    reflection, targetInterface);
        }
        if (outputKind == RuntimeShaderCompileOutputKind.SPIRV) {
            return RuntimeShaderCompileResult.spirv(output, reflection, targetInterface);
        }
        return failure("Native shader compiler returned no output");
    }

    private static RuntimeShaderCompileOutputKind outputKind(int value) {
        if (value == 1) {
            return RuntimeShaderCompileOutputKind.TEXT;
        }
        if (value == 2) {
            return RuntimeShaderCompileOutputKind.SPIRV;
        }
        return RuntimeShaderCompileOutputKind.NONE;
    }

    private static RuntimeShaderCompileResult failure(String message) {
        return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                RuntimeShaderCompileDiagnostic.of(message)
        });
    }
}
