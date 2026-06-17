package io.github.libfdx.tools.shader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shared native bridge helpers.
 *
 * @author xpenatan
 */
public final class FdxTintNativeBridgeSupport {
    private FdxTintNativeBridgeSupport() {
    }

    /**
     * Decodes the bridge wire format.
     *
     * @param encoded the base64 encoded data
     * @return the result
     */
    public static FdxTintCompilerBridgeResult decodeBase64(String encoded) {
        if (encoded == null || encoded.length() == 0) {
            return FdxTintCompilerBridgeResult.failure("Native shader compiler returned no result");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int status = buffer.getInt();
        int kind = buffer.getInt();
        int outputSize = buffer.getInt();
        int diagnosticSize = buffer.getInt();
        byte[] output = new byte[Math.max(0, outputSize)];
        buffer.get(output);
        byte[] diagnostics = new byte[Math.max(0, diagnosticSize)];
        buffer.get(diagnostics);
        return FdxTintCompilerBridgeResult.of(status, FdxShaderTargets.outputKind(kind), output,
                new String(diagnostics, StandardCharsets.UTF_8));
    }

    /**
     * Converts a bridge result into the public compiler result.
     *
     * @param result the bridge result
     * @return the compiler result
     */
    public static FdxShaderCompilerResult toCompilerResult(FdxTintCompilerBridgeResult result) {
        if (result.success()) {
            return FdxShaderCompilerResult.binary(result.outputKind(), result.output());
        }
        return FdxShaderCompilerResult.failure(new FdxShaderCompilerDiagnostic[] {
                FdxShaderCompilerDiagnostic.of(result.diagnostics())
        });
    }
}
