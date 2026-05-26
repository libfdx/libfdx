package io.github.libfdx.graphics.g3d;

public final class ShaderMaterial implements Material {
    private final String id;
    private final ShaderProvider3D shaderProvider;
    private MaterialAlphaMode alphaMode = MaterialAlphaMode.OPAQUE;
    private boolean doubleSided;

    public ShaderMaterial(String id, ShaderProvider3D shaderProvider) {
        this.id = id != null ? id : "";
        this.shaderProvider = shaderProvider;
    }

    public ShaderMaterial alphaMode(MaterialAlphaMode alphaMode) {
        this.alphaMode = alphaMode != null ? alphaMode : MaterialAlphaMode.OPAQUE;
        return this;
    }

    public ShaderMaterial doubleSided(boolean doubleSided) {
        this.doubleSided = doubleSided;
        return this;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public MaterialAlphaMode alphaMode() {
        return alphaMode;
    }

    @Override
    public boolean doubleSided() {
        return doubleSided;
    }

    @Override
    public ShaderProvider3D shaderProvider() {
        return shaderProvider;
    }
}
