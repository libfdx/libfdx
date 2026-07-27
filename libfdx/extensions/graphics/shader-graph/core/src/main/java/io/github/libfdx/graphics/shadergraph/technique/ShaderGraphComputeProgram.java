package io.github.libfdx.graphics.shadergraph.technique;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.GraphicsCapabilities;
import io.github.libfdx.graphics.GraphicsFeature;
import io.github.libfdx.graphics.GraphicsLimits;
import io.github.libfdx.graphics.internal.PortableSha256;

/**
 * Immutable complete compute entry point backed by one compute graph.
 */
public final class ShaderGraphComputeProgram {
    private final ShaderGraphId id;
    private final ShaderGraph graph;
    private final String entryPoint;
    private final int workgroupX;
    private final int workgroupY;
    private final int workgroupZ;
    private final String semanticHash;

    private ShaderGraphComputeProgram(Builder builder) {
        if (builder.id == null || builder.graph == null
                || builder.graph.kind() != ShaderGraphKind.COMPUTE
                || !identifier(builder.entryPoint)
                || builder.workgroupX <= 0 || builder.workgroupY <= 0
                || builder.workgroupZ <= 0) {
            throw new FdxException(
                    "Shader graph compute program is incomplete");
        }
        id = builder.id;
        graph = builder.graph;
        entryPoint = builder.entryPoint;
        workgroupX = builder.workgroupX;
        workgroupY = builder.workgroupY;
        workgroupZ = builder.workgroupZ;
        semanticHash = PortableSha256.hashUtf8(
                "fdx-compute-program-v1\n" + id.value() + '\n'
                        + graph.semanticHash() + '\n' + entryPoint + '\n'
                        + workgroupX + ':' + workgroupY + ':'
                        + workgroupZ);
    }

    public static Builder builder(String id, ShaderGraph graph) {
        return new Builder(id, graph);
    }

    public ShaderGraphId id() {
        return id;
    }

    public ShaderGraph graph() {
        return graph;
    }

    public String entryPoint() {
        return entryPoint;
    }

    public int workgroupX() {
        return workgroupX;
    }

    public int workgroupY() {
        return workgroupY;
    }

    public int workgroupZ() {
        return workgroupZ;
    }

    public String semanticHash() {
        return semanticHash;
    }

    /**
     * Validates all provider limits that are known before native module or
     * pipeline creation.
     */
    public void validate(GraphicsCapabilities capabilities) {
        if (capabilities == null) {
            throw new FdxException(
                    "Compute program validation requires capabilities");
        }
        capabilities.require(GraphicsFeature.COMPUTE);
        GraphicsLimits limits = capabilities.limits();
        if (workgroupX > limits.maxComputeWorkgroupSizeX()
                || workgroupY > limits.maxComputeWorkgroupSizeY()
                || workgroupZ > limits.maxComputeWorkgroupSizeZ()
                || (long) workgroupX * workgroupY * workgroupZ
                        > limits.maxComputeInvocationsPerWorkgroup()) {
            throw new FdxException(
                    "Compute graph workgroup size exceeds provider limits");
        }
        int storageBuffers = 0;
        int storageTextures = 0;
        long workgroupStorage = 0;
        for (ShaderGraphResource resource : graph.resources()) {
            if (resource.bound()
                    && (resource.group() >= limits.maxBindGroups()
                            || resource.binding()
                                    >= limits.maxBindingsPerGroup())) {
                throw new FdxException("Compute graph resource "
                        + resource.id() + " exceeds provider binding limits");
            }
            if (resource.type().kind()
                    == ShaderGraphTypeKind.STORAGE_BUFFER) {
                storageBuffers++;
            } else if (resource.type().kind()
                    == ShaderGraphTypeKind.STORAGE_TEXTURE) {
                storageTextures++;
            } else if (resource.type().kind()
                    == ShaderGraphTypeKind.WORKGROUP_ARRAY) {
                workgroupStorage += resource.type().workgroupStorageSize();
            }
        }
        if (storageBuffers > limits.maxStorageBuffersPerStage()
                || storageTextures
                        > limits.maxStorageTexturesPerStage()
                || workgroupStorage
                        > limits.maxComputeWorkgroupStorageSize()) {
            throw new FdxException(
                    "Compute graph resources exceed provider limits");
        }
    }

    private static boolean identifier(String value) {
        if (value == null || value.isEmpty()
                || !Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            if (!Character.isJavaIdentifierPart(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Mutable compute-program construction scope.
     */
    public static final class Builder {
        private final ShaderGraphId id;
        private final ShaderGraph graph;
        private String entryPoint = "computeMain";
        private int workgroupX = 1;
        private int workgroupY = 1;
        private int workgroupZ = 1;

        private Builder(String id, ShaderGraph graph) {
            this.id = ShaderGraphId.of(id);
            this.graph = graph;
        }

        public Builder entryPoint(String value) {
            entryPoint = value;
            return this;
        }

        public Builder workgroupSize(int x, int y, int z) {
            workgroupX = x;
            workgroupY = y;
            workgroupZ = z;
            return this;
        }

        public ShaderGraphComputeProgram build() {
            return new ShaderGraphComputeProgram(this);
        }
    }
}
