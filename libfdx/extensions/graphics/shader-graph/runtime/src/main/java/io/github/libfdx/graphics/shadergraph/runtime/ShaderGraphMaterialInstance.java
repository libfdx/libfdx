package io.github.libfdx.graphics.shadergraph.runtime;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.Sampler;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphTypeKind;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Mutable material-owned values for one immutable graph definition.
 *
 * <p>Textures and samplers are borrowed. Mutation is setup/edit-time work;
 * rendering reads by pre-resolved indexes and revisions without string
 * lookup.</p>
 */
public final class ShaderGraphMaterialInstance {
    private static final AtomicLong NEXT_IDENTITY = new AtomicLong(1);

    private final long identity = NEXT_IDENTITY.getAndIncrement();
    private final ShaderGraphMaterialDefinition definition;
    private final ShaderGraphLiteral[] values;
    private final Texture[] textures;
    private final Sampler[] samplers;
    private long revision;

    public ShaderGraphMaterialInstance(ShaderGraphMaterialDefinition definition) {
        if (definition == null) {
            throw new FdxException("Shader graph material definition cannot be null");
        }
        this.definition = definition;
        values = new ShaderGraphLiteral[definition.parameterCount()];
        for (int i = 0; i < values.length; i++) {
            ShaderGraphLiteral value = definition.parameter(i).defaultValue();
            values[i] = value != null ? value
                    : ShaderGraphLiteral.zero(definition.parameter(i).type());
        }
        textures = new Texture[definition.resourceCount()];
        samplers = new Sampler[definition.resourceCount()];
    }

    public ShaderGraphMaterialDefinition definition() {
        return definition;
    }

    public long identity() {
        return identity;
    }

    public long revision() {
        return revision;
    }

    public ShaderGraphMaterialInstance set(String parameter,
            ShaderGraphLiteral value) {
        int index = definition.parameterIndex(parameter);
        if (index < 0) {
            throw new FdxException("Unknown shader graph material parameter: "
                    + parameter);
        }
        return set(index, value);
    }

    public ShaderGraphMaterialInstance set(int index,
            ShaderGraphLiteral value) {
        if (index < 0 || index >= values.length || value == null
                || !definition.parameter(index).type().equals(value.type())) {
            throw new FdxException("Shader graph material value does not match parameter "
                    + index);
        }
        if (!value.equals(values[index])) {
            values[index] = value;
            revision++;
        }
        return this;
    }

    public ShaderGraphLiteral value(int index) {
        return values[index];
    }

    public ShaderGraphMaterialInstance texture(String resource, Texture value) {
        int index = definition.resourceIndex(resource);
        if (index < 0) {
            throw new FdxException("Unknown shader graph texture resource: "
                    + resource);
        }
        return texture(index, value);
    }

    public ShaderGraphMaterialInstance texture(int index, Texture value) {
        requireResource(index, ShaderGraphTypeKind.TEXTURE);
        if (textures[index] != value) {
            textures[index] = value;
            revision++;
        }
        return this;
    }

    public ShaderGraphMaterialInstance sampler(String resource, Sampler value) {
        int index = definition.resourceIndex(resource);
        if (index < 0) {
            throw new FdxException("Unknown shader graph sampler resource: "
                    + resource);
        }
        return sampler(index, value);
    }

    public ShaderGraphMaterialInstance sampler(int index, Sampler value) {
        requireResource(index, ShaderGraphTypeKind.SAMPLER);
        if (samplers[index] != value) {
            samplers[index] = value;
            revision++;
        }
        return this;
    }

    public Texture texture(int index) {
        return textures[index];
    }

    public Sampler sampler(int index) {
        return samplers[index];
    }

    private void requireResource(int index, ShaderGraphTypeKind kind) {
        if (index < 0 || index >= definition.resourceCount()
                || definition.resource(index).type().kind() != kind) {
            throw new FdxException("Shader graph material resource " + index
                    + " is not a " + kind);
        }
    }
}
