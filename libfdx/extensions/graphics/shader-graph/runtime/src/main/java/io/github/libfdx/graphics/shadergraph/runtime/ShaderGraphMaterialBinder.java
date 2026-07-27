package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.RenderPass;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.runtime.ShaderParameterBlock;
import io.github.libfdx.graphics.shader.reflection.ShaderParameterHandle;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceKind;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceLayout;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderValueKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphSymbols;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;

/**
 * Reusable binder for one graph material schema and reflected program
 * interface.
 */
public final class ShaderGraphMaterialBinder {
    private final ShaderGraphMaterialDefinition definition;
    private final int materialGroup;
    private final int materialBinding;
    private long layoutIdentity = -1;
    private ShaderParameterBlock block;
    private boolean sharedBlock;
    private ShaderParameterHandle[] handles = new ShaderParameterHandle[0];
    private final float[] matrixValues = new float[16];
    private long boundIdentity = -1;
    private long boundRevision = -1;
    private long boundBlockRevision = -1;

    /**
     * Creates a binder for the material uniform binding used during program
     * composition.
     *
     * @param definition immutable material definition
     * @param materialGroup uniform group
     * @param materialBinding uniform binding
     */
    public ShaderGraphMaterialBinder(
            ShaderGraphMaterialDefinition definition,
            int materialGroup, int materialBinding) {
        if (definition == null || materialGroup < 0
                || materialBinding < 0) {
            throw new FdxException(
                    "Shader graph material binder configuration is invalid");
        }
        this.definition = definition;
        this.materialGroup = materialGroup;
        this.materialBinding = materialBinding;
    }

    /**
     * Binds one compatible material instance.
     *
     * @param pass active render pass
     * @param layout resolved shader interface
     * @param material material values/resources
     */
    public void bind(RenderPass pass, ShaderResourceLayout layout,
            ShaderGraphMaterialInstance material) {
        if (pass == null || layout == null || material == null
                || material.definition() != definition) {
            throw new FdxException(
                    "Shader graph material binding is incompatible");
        }
        prepare(layout, null);
        if (block != null) {
            writeParameters(material);
            pass.setParameterBlock(materialGroup,
                    materialBinding, block);
        }
        bindResources(pass, material);
    }

    /**
     * Writes graph-owned values into a renderer-owned parameter block whose
     * reflected layout contains this material schema.
     *
     * @param layout resolved complete shader interface
     * @param material graph material values
     * @param target renderer-owned shared block
     */
    public void writeParameters(ShaderResourceLayout layout,
            ShaderGraphMaterialInstance material,
            ShaderParameterBlock target) {
        if (layout == null || material == null || target == null
                || material.definition() != definition) {
            throw new FdxException(
                    "Shader graph shared material binding is incompatible");
        }
        prepare(layout, target);
        writeParameters(material);
    }

    /**
     * Binds graph-owned textures and samplers without binding a parameter
     * block. This is used when parameters share a renderer-owned block.
     *
     * @param pass active render pass
     * @param material graph material resources
     */
    public void bindResources(RenderPass pass,
            ShaderGraphMaterialInstance material) {
        if (pass == null || material == null
                || material.definition() != definition) {
            throw new FdxException(
                    "Shader graph material resources are incompatible");
        }
        for (int i = 0; i < definition.resourceCount(); i++) {
            ShaderGraphResource resource = definition.resource(i);
            if (resource.type().kind()
                    == ShaderGraphTypeKind.TEXTURE) {
                if (material.texture(i) == null) {
                    throw new FdxException(
                            "Graph material texture is not bound: "
                                    + resource.id());
                }
                pass.setTextureBinding(resource.group(),
                        resource.binding(), material.texture(i));
            } else if (resource.type().kind()
                    == ShaderGraphTypeKind.SAMPLER) {
                if (material.sampler(i) == null) {
                    throw new FdxException(
                            "Graph material sampler is not bound: "
                                    + resource.id());
                }
                pass.setSamplerBinding(resource.group(),
                        resource.binding(), material.sampler(i));
            } else {
                throw new FdxException(
                        "Graph material runtime resource is unsupported: "
                                + resource.id());
            }
        }
    }

    private void prepare(ShaderResourceLayout layout,
            ShaderParameterBlock target) {
        boolean useShared = target != null;
        if (layout.identity() == layoutIdentity
                && sharedBlock == useShared
                && (!useShared || block == target)) {
            return;
        }
        if (definition.parameterCount() == 0) {
            block = null;
            handles = new ShaderParameterHandle[0];
        } else {
            ShaderBinding binding = layout.require(materialGroup,
                    materialBinding);
            if (binding.resourceKind()
                    != ShaderResourceKind.UNIFORM_BUFFER) {
                throw new FdxException(
                        "Graph material binding is not a uniform buffer");
            }
            if (useShared) {
                if (target.layout()
                        != binding.bufferLayout()) {
                    throw new FdxException(
                            "Shared graph material parameter block has the wrong reflected layout");
                }
                block = target;
            } else {
                block = ShaderParameterBlock.allocate(
                        binding.bufferLayout());
            }
            handles = new ShaderParameterHandle[
                    definition.parameterCount()];
            for (int i = 0; i < handles.length; i++) {
                handles[i] = binding.bufferLayout().requireHandle(
                        ShaderGraphSymbols.parameter(
                                definition.parameter(i).id()));
            }
        }
        layoutIdentity = layout.identity();
        sharedBlock = useShared;
        boundIdentity = -1;
        boundRevision = -1;
        boundBlockRevision = -1;
    }

    private void writeParameters(
            ShaderGraphMaterialInstance material) {
        if (block == null || boundIdentity == material.identity()
                && boundRevision == material.revision()
                && (!sharedBlock
                || boundBlockRevision == block.revision())) {
            return;
        }
        for (int i = 0; i < handles.length; i++) {
            write(block, handles[i], material.value(i));
        }
        boundIdentity = material.identity();
        boundRevision = material.revision();
        boundBlockRevision = block.revision();
    }

    private void write(ShaderParameterBlock block,
            ShaderParameterHandle handle,
            ShaderGraphLiteral literal) {
        ShaderValueKind kind = handle.valueType().kind();
        ShaderScalarType scalar =
                handle.valueType().scalarType();
        if (kind == ShaderValueKind.SCALAR) {
            switch (scalar) {
                case F32 -> block.setFloat(handle,
                        Float.intBitsToFloat((int) literal.bits()));
                case I32 -> block.setInt(handle,
                        (int) literal.bits());
                case U32 -> block.setUnsignedInt(handle,
                        (int) literal.bits());
                case BOOL -> block.setBoolean(handle,
                        literal.bits() != 0);
                default -> throw new FdxException(
                        "Unsupported graph material scalar: "
                                + scalar);
            }
            return;
        }
        if (kind == ShaderValueKind.VECTOR
                && scalar == ShaderScalarType.F32) {
            int width = handle.valueType().rows();
            float x = component(literal, 0);
            float y = width > 1 ? component(literal, 1) : 0;
            float z = width > 2 ? component(literal, 2) : 0;
            float w = width > 3 ? component(literal, 3) : 0;
            switch (width) {
                case 2 -> block.setFloat2(handle, x, y);
                case 3 -> block.setFloat3(handle, x, y, z);
                case 4 -> block.setFloat4(handle, x, y, z, w);
                default -> throw new FdxException(
                        "Unsupported graph material vector width");
            }
            return;
        }
        if (kind == ShaderValueKind.MATRIX
                && scalar == ShaderScalarType.F32) {
            int count = handle.valueType().columns()
                    * handle.valueType().rows();
            for (int i = 0; i < count; i++) {
                matrixValues[i] = component(literal, i);
            }
            block.setFloatMatrix(handle, matrixValues, 0);
            return;
        }
        throw new FdxException(
                "Unsupported graph material parameter type: "
                        + handle.valueType());
    }

    private static float component(ShaderGraphLiteral literal,
            int index) {
        return Float.intBitsToFloat(
                (int) literal.element(index).bits());
    }
}
