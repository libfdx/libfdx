package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.internal.PortableSha256;

/**
 * Exact artifact identity. Compute off the render thread, once immutable compilation inputs are
 * available. Identity parts must include every relevant compiler/validator/schema version, input,
 * entry point, option and target environment. Driver keys additionally require device/driver IDs.
 * The envelope schema and layer are always included; length framing prevents concatenation aliases.
 */
public record ShaderCacheKey(ShaderCacheLayer layer, String digest) {
    public ShaderCacheKey {
        if (layer == null || digest == null || digest.length() != 64) {
            throw new IllegalArgumentException("A cache key requires a layer and SHA-256 digest");
        }
        for (int i = 0; i < digest.length(); i++) {
            char c = digest.charAt(i);
            if (!(c >= '0' && c <= '9') && !(c >= 'a' && c <= 'f')) {
                throw new IllegalArgumentException("Cache digest must be lowercase hexadecimal");
            }
        }
    }

    public static ShaderCacheKey of(ShaderCacheLayer layer, String... identity) {
        if (layer == null || identity == null) throw new NullPointerException("cache identity");
        PortableSha256 hash = new PortableSha256().updateUtf8("libfdx-shader-cache:1:" + layer.name());
        for (String part : identity) {
            if (part == null) throw new NullPointerException("cache identity part");
            hash.updateUtf8(":" + part.length() + ":").updateUtf8(part);
        }
        return new ShaderCacheKey(layer, hash.digestHex());
    }
}
