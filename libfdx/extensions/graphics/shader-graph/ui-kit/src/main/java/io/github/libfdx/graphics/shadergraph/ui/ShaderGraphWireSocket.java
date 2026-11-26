package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;

/** A canvas wire can end at a node port or at a surface output binding. */
record ShaderGraphWireSocket(ShaderEndpoint endpoint, MaterialOutputChannel channel, boolean input, ShaderGraphType type) {
    static ShaderGraphWireSocket port(ShaderGraphId node, ShaderGraphPort port, boolean input) {
        return new ShaderGraphWireSocket(ShaderEndpoint.of(node, port.id()), null, input, port.type());
    }

    static ShaderGraphWireSocket material(MaterialOutputChannel channel) {
        return new ShaderGraphWireSocket(null, channel, true, channel.type);
    }

    ShaderGraph connect(ShaderGraph graph, ShaderGraphWireSocket other) {
        if (input == other.input) throw new FdxException("A wire needs an output and an input");
        var sink = input ? this : other;
        var source = input ? other.endpoint : endpoint;
        return sink.channel == null ? ShaderGraphSemanticEdits.connect(graph, source, sink.endpoint)
                : ShaderGraphSemanticEdits.materialOutput(graph, sink.channel, source);
    }

}
