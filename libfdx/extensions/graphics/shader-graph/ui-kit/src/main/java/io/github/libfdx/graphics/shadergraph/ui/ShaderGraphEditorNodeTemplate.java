package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import java.util.Arrays;

/**
 * Extensible, UI-independent palette entry for one node shape.
 */
public final class ShaderGraphEditorNodeTemplate
        implements Comparable<ShaderGraphEditorNodeTemplate> {
    private final String category;
    private final String label;
    private final ShaderGraphId definitionId;
    private final int definitionVersion;
    private final ShaderGraphKind[] graphKinds;
    private final ShaderGraphEditorNodeFactory factory;

    public ShaderGraphEditorNodeTemplate(String category, String label,
            String definitionId, int definitionVersion,
            ShaderGraphEditorNodeFactory factory,
            ShaderGraphKind... graphKinds) {
        if (empty(category) || empty(label) || definitionVersion <= 0
                || factory == null || graphKinds == null
                || graphKinds.length == 0) {
            throw new FdxException(
                    "Shader graph editor node template is incomplete");
        }
        this.category = category.trim();
        this.label = label.trim();
        this.definitionId = ShaderGraphId.of(definitionId);
        this.definitionVersion = definitionVersion;
        this.factory = factory;
        this.graphKinds = graphKinds.clone();
        Arrays.sort(this.graphKinds);
        for (int i = 0; i < this.graphKinds.length; i++) {
            if (this.graphKinds[i] == null || i > 0
                    && this.graphKinds[i - 1] == this.graphKinds[i]) {
                throw new FdxException(
                        "Shader graph editor node template kinds must be unique");
            }
        }
    }

    public String category() {
        return category;
    }

    public String label() {
        return label;
    }

    public ShaderGraphId definitionId() {
        return definitionId;
    }

    public int definitionVersion() {
        return definitionVersion;
    }

    public ShaderGraphKind[] graphKinds() {
        return graphKinds.clone();
    }

    public boolean supports(ShaderGraphKind kind) {
        return kind != null && Arrays.binarySearch(graphKinds, kind) >= 0;
    }

    public ShaderNode create(String nodeId) {
        ShaderNode node = factory.create(nodeId);
        if (node == null || !node.id().equals(ShaderGraphId.of(nodeId))
                || !node.definitionId().equals(definitionId)
                || node.definitionVersion() != definitionVersion) {
            throw new FdxException("Palette template " + label
                    + " returned a node with a different identity or definition");
        }
        return node;
    }

    @Override
    public int compareTo(ShaderGraphEditorNodeTemplate other) {
        int categoryOrder = category.compareTo(other.category);
        if (categoryOrder != 0) {
            return categoryOrder;
        }
        int labelOrder = label.compareTo(other.label);
        if (labelOrder != 0) {
            return labelOrder;
        }
        int definitionOrder = definitionId.compareTo(other.definitionId);
        return definitionOrder != 0 ? definitionOrder
                : Integer.compare(definitionVersion,
                        other.definitionVersion);
    }

    private static boolean empty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
