package io.github.libfdx.graphics.shadergraph.model;

import io.github.libfdx.core.FdxException;

/**
 * Builder-time typed reference to one node output.
 */
public final class ShaderExpression {
    private final ShaderEndpoint endpoint;
    private final ShaderGraphType type;

    ShaderExpression(ShaderEndpoint endpoint, ShaderGraphType type) {
        if (endpoint == null || type == null) {
            throw new FdxException("Shader expression requires an endpoint and type");
        }
        this.endpoint = endpoint;
        this.type = type;
    }

    public ShaderEndpoint endpoint() {
        return endpoint;
    }

    public ShaderGraphType type() {
        return type;
    }
}
