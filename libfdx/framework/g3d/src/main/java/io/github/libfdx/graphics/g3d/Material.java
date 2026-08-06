package io.github.libfdx.graphics.g3d;

import io.github.libfdx.collections.ObjectMap;
import io.github.libfdx.collections.ObjectMapView;

/**
 * Provider-neutral material composed from independently typed attributes.
 *
 * <p>A shader decides which attributes it understands. Standard shared
 * surface values are declared by {@link MaterialAttributes}; a PBR shader
 * additionally consumes {@link PbrAttributes}. Attribute values, shader
 * providers, bindings, textures, and other resources are borrowed. Their
 * owner must outlive every model that uses this material.</p>
 */
public class Material {
    private final String id;
    private final ObjectMap<MaterialAttributeType<?>, MaterialAttribute>
            attributes = new ObjectMap<MaterialAttributeType<?>, MaterialAttribute>(8);
    private final ObjectMapView<MaterialAttributeType<?>, MaterialAttribute>
            readOnlyAttributes = attributes.view();
    private ShadingModel shadingModel = ShadingModel.PBR;
    private MaterialAlphaMode alphaMode = MaterialAlphaMode.OPAQUE;
    private boolean doubleSided;
    private ShaderProvider3D shaderProvider;
    private ShaderMaterialBinding shaderBinding;

    /**
     * Creates an empty material.
     *
     * @param id material identifier
     */
    public Material(String id) {
        this.id = id != null ? id : "";
    }

    /**
     * Creates a material containing the supplied attributes.
     *
     * @param id material identifier
     * @param attributes initial attributes
     */
    public Material(String id, MaterialAttribute... attributes) {
        this(id);
        if (attributes != null) {
            for (MaterialAttribute attribute : attributes) {
                set(attribute);
            }
        }
    }

    /**
     * Adds or replaces one attribute.
     *
     * @param attribute attribute value
     * @return this material
     */
    public Material set(MaterialAttribute attribute) {
        if (attribute == null) {
            throw new IllegalArgumentException("Material attribute cannot be null");
        }
        MaterialAttributeType<?> type = attribute.type();
        if (type == null) {
            throw new IllegalArgumentException("Material attribute type cannot be null");
        }
        type.cast(attribute);
        MaterialAttribute existing = attributes.get(type);
        if (existing != null) {
            type.cast(existing);
        }
        attributes.put(type, attribute);
        return this;
    }

    /**
     * Finds an attribute.
     *
     * @param type attribute type
     * @param <T> attribute value type
     * @return attribute, or {@code null}
     */
    public <T extends MaterialAttribute> T find(
            MaterialAttributeType<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Material attribute type cannot be null");
        }
        return type.cast(attributes.get(type));
    }

    /**
     * Requires an attribute.
     *
     * @param type attribute type
     * @param <T> attribute value type
     * @return attribute
     */
    public <T extends MaterialAttribute> T get(
            MaterialAttributeType<T> type) {
        T attribute = find(type);
        if (attribute == null) {
            throw new IllegalStateException(
                    "Material '" + id + "' has no attribute " + type.id());
        }
        return attribute;
    }

    /**
     * Returns whether an attribute is present.
     *
     * @param type attribute type
     * @return true when present
     */
    public boolean has(MaterialAttributeType<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Material attribute type cannot be null");
        }
        return attributes.containsKey(type);
    }

    /**
     * Removes an attribute.
     *
     * @param type attribute type
     * @return this material
     */
    public Material remove(MaterialAttributeType<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("Material attribute type cannot be null");
        }
        attributes.remove(type);
        return this;
    }

    /**
     * Returns a cached live read-only view of the attributes.
     *
     * @return attribute view
     */
    public ObjectMapView<MaterialAttributeType<?>, MaterialAttribute>
            attributes() {
        return readOnlyAttributes;
    }

    /** @return material ID */
    public String id() {
        return id;
    }

    /**
     * Selects the surface shading model independently from the render path.
     *
     * @param shadingModel shading model, or {@code null} for standard PBR
     * @return this material
     */
    public Material shadingModel(ShadingModel shadingModel) {
        this.shadingModel = shadingModel != null
                ? shadingModel : ShadingModel.PBR;
        return this;
    }

    /** @return selected shading model */
    public ShadingModel shadingModel() {
        return shadingModel;
    }

    /** @param alphaMode alpha mode @return this material */
    public Material alphaMode(MaterialAlphaMode alphaMode) {
        this.alphaMode = alphaMode != null
                ? alphaMode : MaterialAlphaMode.OPAQUE;
        return this;
    }

    /** @return alpha mode */
    public MaterialAlphaMode alphaMode() {
        return alphaMode;
    }

    /** @param doubleSided double-sided state @return this material */
    public Material doubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    /** @return double-sided state */
    public boolean doubleSided() {
        return doubleSided;
    }

    /**
     * Sets a borrowed optional per-material shader provider.
     *
     * @param shaderProvider shader provider, or {@code null}
     * @return this material
     */
    public Material shaderProvider(ShaderProvider3D shaderProvider) {
        this.shaderProvider = shaderProvider;
        return this;
    }

    /** @return shader provider, or {@code null} */
    public ShaderProvider3D shaderProvider() {
        return shaderProvider;
    }

    /**
     * Sets a borrowed optional provider-neutral shader binding.
     *
     * @param shaderBinding shader binding, or {@code null}
     * @return this material
     */
    public Material shaderBinding(ShaderMaterialBinding shaderBinding) {
        this.shaderBinding = shaderBinding;
        return this;
    }

    /** @return shader binding, or {@code null} */
    public ShaderMaterialBinding shaderBinding() {
        return shaderBinding;
    }
}
