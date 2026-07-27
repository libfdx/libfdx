package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

/**
 * Typed semantic metadata for one reflected buffer-member path.
 */
public final class ShaderParameterSemantic {
    private final String path;
    private final String stableId;
    private final ShaderParameterDomain domain;
    private final ShaderUpdateFrequency updateFrequency;

    private ShaderParameterSemantic(String path, String stableId, ShaderParameterDomain domain,
            ShaderUpdateFrequency updateFrequency) {
        this.path = require(path, "Shader parameter semantic path");
        this.stableId = require(stableId, "Shader parameter semantic stable ID");
        this.domain = domain != null ? domain : ShaderParameterDomain.UNSPECIFIED;
        this.updateFrequency = updateFrequency != null
                ? updateFrequency : ShaderUpdateFrequency.UNSPECIFIED;
    }

    public static ShaderParameterSemantic of(String path, String stableId, ShaderParameterDomain domain,
            ShaderUpdateFrequency updateFrequency) {
        return new ShaderParameterSemantic(path, stableId, domain, updateFrequency);
    }

    public String path() {
        return path;
    }

    public String stableId() {
        return stableId;
    }

    public ShaderParameterDomain domain() {
        return domain;
    }

    public ShaderUpdateFrequency updateFrequency() {
        return updateFrequency;
    }

    private static String require(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new FdxException(label + " cannot be empty");
        }
        return value;
    }
}
