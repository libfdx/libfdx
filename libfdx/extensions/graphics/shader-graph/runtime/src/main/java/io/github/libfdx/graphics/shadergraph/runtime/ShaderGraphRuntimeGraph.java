package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.cache.ShaderGraphCompiledInterface;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;

/**
 * Compiled graph WGSL used directly or composed into a renderer template.
 */
public final class ShaderGraphRuntimeGraph {
    private final ShaderGraph graph;
    private final String wgsl;
    private final String libraryWgsl;
    private final ShaderGraphCompiledInterface shaderInterface;
    private final ShaderGraphCompiledInterface libraryInterface;

    ShaderGraphRuntimeGraph(ShaderGraph graph, String wgsl,
            String libraryWgsl,
            ShaderGraphCompiledInterface shaderInterface,
            ShaderGraphCompiledInterface libraryInterface) {
        if (graph == null || wgsl == null || wgsl.isEmpty()
                || libraryWgsl == null || libraryWgsl.isEmpty()
                || shaderInterface == null
                || libraryInterface == null) {
            throw new FdxException(
                    "Shader graph runtime graph is incomplete");
        }
        this.graph = graph;
        this.wgsl = wgsl;
        this.libraryWgsl = libraryWgsl;
        this.shaderInterface = shaderInterface;
        this.libraryInterface = libraryInterface;
    }

    public ShaderGraph graph() {
        return graph;
    }

    public String wgsl() {
        return wgsl;
    }

    public String libraryWgsl() {
        return libraryWgsl;
    }

    public ShaderGraphCompiledInterface shaderInterface() {
        return shaderInterface;
    }

    public ShaderGraphCompiledInterface libraryInterface() {
        return libraryInterface;
    }
}
