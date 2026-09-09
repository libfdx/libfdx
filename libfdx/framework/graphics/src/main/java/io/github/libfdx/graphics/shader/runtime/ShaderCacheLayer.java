package io.github.libfdx.graphics.shader.runtime;

/** Independent artifact identities; a driver change need not invalidate compiler output. */
public enum ShaderCacheLayer {
    SOURCE, TRANSLATION, DXIL, SPIRV, DRIVER_PIPELINE
}
