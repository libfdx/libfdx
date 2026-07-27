package io.github.libfdx.graphics.shadergraph.model;

/**
 * Memory scope synchronized by a compute graph barrier node.
 */
public enum ShaderGraphBarrierScope {
    WORKGROUP,
    STORAGE,
    WORKGROUP_AND_STORAGE
}
