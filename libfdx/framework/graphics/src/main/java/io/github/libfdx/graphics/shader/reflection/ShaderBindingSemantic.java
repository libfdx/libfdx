package io.github.libfdx.graphics.shader.reflection;

import io.github.libfdx.core.FdxException;

/**
 * Typed semantic metadata for one reflected binding and its buffer members.
 */
public final class ShaderBindingSemantic {
    private static final ShaderParameterSemantic[] EMPTY_PARAMETERS = new ShaderParameterSemantic[0];

    private final int group;
    private final int binding;
    private final String stableId;
    private final ShaderParameterDomain domain;
    private final ShaderUpdateFrequency updateFrequency;
    private final ShaderParameterSemantic[] parameters;

    private ShaderBindingSemantic(Builder builder) {
        if (builder.group < 0 || builder.binding < 0) {
            throw new FdxException("Shader binding semantic group and binding cannot be negative");
        }
        if (builder.stableId == null || builder.stableId.trim().isEmpty()) {
            throw new FdxException("Shader binding semantic stable ID cannot be empty");
        }
        group = builder.group;
        binding = builder.binding;
        stableId = builder.stableId;
        domain = builder.domain != null ? builder.domain : ShaderParameterDomain.UNSPECIFIED;
        updateFrequency = builder.updateFrequency != null
                ? builder.updateFrequency : ShaderUpdateFrequency.UNSPECIFIED;
        parameters = builder.parameters != null ? builder.parameters.clone() : EMPTY_PARAMETERS;
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] == null) {
                throw new FdxException("Shader binding parameter semantic cannot be null");
            }
            for (int j = 0; j < i; j++) {
                if (parameters[i].path().equals(parameters[j].path())) {
                    throw new FdxException("Duplicate shader parameter semantic path: " + parameters[i].path());
                }
            }
        }
    }

    public static Builder builder(int group, int binding, String stableId) {
        return new Builder(group, binding, stableId);
    }

    public int group() {
        return group;
    }

    public int binding() {
        return binding;
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

    public ShaderParameterSemantic[] parameters() {
        return parameters.clone();
    }

    public int parameterCount() {
        return parameters.length;
    }

    public ShaderParameterSemantic parameter(int index) {
        return parameters[index];
    }

    /**
     * Builds binding semantic metadata.
     */
    public static final class Builder {
        private final int group;
        private final int binding;
        private final String stableId;
        private ShaderParameterDomain domain = ShaderParameterDomain.UNSPECIFIED;
        private ShaderUpdateFrequency updateFrequency = ShaderUpdateFrequency.UNSPECIFIED;
        private ShaderParameterSemantic[] parameters = EMPTY_PARAMETERS;

        private Builder(int group, int binding, String stableId) {
            this.group = group;
            this.binding = binding;
            this.stableId = stableId;
        }

        public Builder semantics(ShaderParameterDomain domain, ShaderUpdateFrequency updateFrequency) {
            this.domain = domain;
            this.updateFrequency = updateFrequency;
            return this;
        }

        public Builder parameters(ShaderParameterSemantic... parameters) {
            this.parameters = parameters != null ? parameters.clone() : EMPTY_PARAMETERS;
            return this;
        }

        public ShaderBindingSemantic build() {
            return new ShaderBindingSemantic(this);
        }
    }
}
