package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

import java.util.Locale;

public enum ShaderProfile {
    PORTABLE_WEBGL2("fdx-wgsl-webgl2"),
    PORTABLE_WEBGPU("fdx-wgsl-webgpu"),
    NATIVE("fdx-native");

    private final String id;

    ShaderProfile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ShaderProfile fromId(String id, ShaderProfile fallback) {
        if (id == null || id.trim().isEmpty()) {
            return fallback != null ? fallback : PORTABLE_WEBGPU;
        }
        String value = id.trim().toLowerCase(Locale.ROOT);
        if ("webgl2".equals(value) || PORTABLE_WEBGL2.id.equals(value)) {
            return PORTABLE_WEBGL2;
        }
        if ("webgpu".equals(value) || PORTABLE_WEBGPU.id.equals(value)) {
            return PORTABLE_WEBGPU;
        }
        if ("native".equals(value) || NATIVE.id.equals(value)) {
            return NATIVE;
        }
        throw new FdxException("Unknown libFDX shader profile: " + id);
    }
}
