package io.github.libfdx.graphics.g3d;

public interface Material {
    String id();

    MaterialAlphaMode alphaMode();

    boolean doubleSided();

    ShaderProvider3D shaderProvider();
}
