package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;

/**
 * Stable generated symbol contract used by structured renderer templates.
 */
public final class ShaderGraphSymbols {
    private ShaderGraphSymbols() {
    }

    public static String function(ShaderGraphId graphId) {
        return "fdx_graph_" + sanitize(graphId.value());
    }

    public static String output(ShaderGraphId outputId) {
        return "fdx_" + sanitize(outputId.value());
    }

    public static String parameter(ShaderGraphId parameterId) {
        return "fdx_" + sanitize(parameterId.value());
    }

    public static String resultType(ShaderGraphId graphId) {
        return "FdxResult_" + sanitize(graphId.value());
    }

    private static String sanitize(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            result.append(character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || character == '_' ? character : '_');
        }
        return result.toString();
    }
}
