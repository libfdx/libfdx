package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.node.ShaderNode;

/**
 * Creates one semantic node for a palette template.
 */
@FunctionalInterface
public interface ShaderGraphEditorNodeFactory {
    ShaderNode create(String nodeId);
}
