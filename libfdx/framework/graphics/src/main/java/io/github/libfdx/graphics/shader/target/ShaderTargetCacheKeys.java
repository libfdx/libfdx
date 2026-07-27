package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;

/**
 * Canonical cache-key construction for target compilation and verification.
 *
 * @author xpenatan
 */
public final class ShaderTargetCacheKeys {
    private ShaderTargetCacheKeys() {
    }

    /**
     * Creates a target compilation cache key.
     *
     * @param request the request
     * @param compiler the compiler ID
     * @param version the compiler version
     * @return the key
     */
    public static String compilation(ShaderTargetCompileRequest request,
            ShaderCompilerId compiler, String version) {
        if (request == null || compiler == null || version == null || version.trim().length() == 0) {
            throw new FdxException("Shader compilation cache-key inputs cannot be empty");
        }
        return new PortableSha256().updateSizedUtf8("fdx-shader-compilation-cache-v1")
                .updateSizedUtf8(request.cacheKey())
                .updateSizedUtf8(compiler.value())
                .updateSizedUtf8(version.trim())
                .digestHex();
    }

    /**
     * Creates a target verification cache key.
     *
     * @param compileCacheKey the compilation key
     * @param verifier the verifier ID
     * @param version the verifier version
     * @param environment the environment
     * @return the key
     */
    public static String verification(String compileCacheKey, ShaderVerifierId verifier,
            String version, ShaderTargetEnvironment environment) {
        if (compileCacheKey == null || compileCacheKey.length() == 0 || verifier == null
                || version == null || version.trim().length() == 0 || environment == null) {
            throw new FdxException("Shader verification cache-key inputs cannot be empty");
        }
        return new PortableSha256().updateSizedUtf8("fdx-shader-verification-cache-v1")
                .updateSizedUtf8(compileCacheKey)
                .updateSizedUtf8(verifier.value())
                .updateSizedUtf8(version.trim())
                .updateSizedUtf8(environment.cacheKey())
                .digestHex();
    }
}
