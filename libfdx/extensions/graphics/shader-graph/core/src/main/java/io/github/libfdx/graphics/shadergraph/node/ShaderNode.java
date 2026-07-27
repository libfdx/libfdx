package io.github.libfdx.graphics.shadergraph.node;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.core.FdxException;

import java.util.Arrays;
import java.util.Objects;

/**
 * Immutable semantic node. It contains typed data only and never generated
 * shader-language snippets.
 */
public final class ShaderNode implements Comparable<ShaderNode> {
    private final ShaderGraphId id;
    private final ShaderGraphId definitionId;
    private final int definitionVersion;
    private final ShaderGraphPort[] inputs;
    private final ShaderGraphPort[] outputs;
    private final ShaderNodeProperty[] properties;

    private ShaderNode(ShaderGraphId id, ShaderGraphId definitionId,
            int definitionVersion, ShaderGraphPort[] inputs,
            ShaderGraphPort[] outputs, ShaderNodeProperty[] properties) {
        if (id == null || definitionId == null || definitionVersion <= 0
                || inputs == null || outputs == null || outputs.length == 0
                || properties == null) {
            throw new FdxException("Shader node requires IDs, a positive version, and output ports");
        }
        this.id = id;
        this.definitionId = definitionId;
        this.definitionVersion = definitionVersion;
        this.inputs = sortedUnique(inputs, "input");
        this.outputs = sortedUnique(outputs, "output");
        this.properties = properties.clone();
        Arrays.sort(this.properties);
        for (int i = 0; i < this.properties.length; i++) {
            if (this.properties[i] == null) {
                throw new FdxException("Shader node property cannot be null");
            }
            if (i > 0 && this.properties[i - 1].id().equals(this.properties[i].id())) {
                throw new FdxException("Duplicate shader node property: "
                        + this.properties[i].id());
            }
        }
    }

    public static ShaderNode of(String id, String definitionId, int version,
            ShaderGraphPort[] inputs, ShaderGraphPort[] outputs,
            ShaderNodeProperty... properties) {
        return new ShaderNode(ShaderGraphId.of(id), ShaderGraphId.of(definitionId),
                version, inputs != null ? inputs : new ShaderGraphPort[0],
                outputs, properties != null ? properties : new ShaderNodeProperty[0]);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraphId definitionId() {
        return definitionId;
    }

    public int definitionVersion() {
        return definitionVersion;
    }

    public ShaderGraphPort[] inputs() {
        return inputs.clone();
    }

    public ShaderGraphPort input(ShaderGraphId portId) {
        return port(inputs, portId);
    }

    public ShaderGraphPort[] outputs() {
        return outputs.clone();
    }

    public ShaderGraphPort output(ShaderGraphId portId) {
        return port(outputs, portId);
    }

    public ShaderNodeProperty[] properties() {
        return properties.clone();
    }

    public ShaderNodeProperty property(String id) {
        ShaderGraphId key = ShaderGraphId.of(id);
        for (ShaderNodeProperty property : properties) {
            if (property.id().equals(key)) {
                return property;
            }
        }
        return null;
    }

    @Override
    public int compareTo(ShaderNode other) {
        return id.compareTo(other.id);
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ShaderNode other
                && id.equals(other.id) && definitionId.equals(other.definitionId)
                && definitionVersion == other.definitionVersion
                && Arrays.equals(inputs, other.inputs)
                && Arrays.equals(outputs, other.outputs)
                && Arrays.equals(properties, other.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, definitionId, definitionVersion,
                Arrays.hashCode(inputs), Arrays.hashCode(outputs),
                Arrays.hashCode(properties));
    }

    private static ShaderGraphPort[] sortedUnique(ShaderGraphPort[] source,
            String kind) {
        ShaderGraphPort[] result = source.clone();
        Arrays.sort(result);
        for (int i = 0; i < result.length; i++) {
            if (result[i] == null) {
                throw new FdxException("Shader node " + kind + " port cannot be null");
            }
            if (i > 0 && result[i - 1].id().equals(result[i].id())) {
                throw new FdxException("Duplicate shader node " + kind + " port: "
                        + result[i].id());
            }
        }
        return result;
    }

    private static ShaderGraphPort port(ShaderGraphPort[] ports, ShaderGraphId id) {
        for (ShaderGraphPort port : ports) {
            if (port.id().equals(id)) {
                return port;
            }
        }
        return null;
    }
}
