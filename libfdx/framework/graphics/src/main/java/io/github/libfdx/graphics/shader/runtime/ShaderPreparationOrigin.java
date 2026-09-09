package io.github.libfdx.graphics.shader.runtime;

/** Immutable optional developer provenance. Empty labels mean unknown, not guessed asset names.
 * A null recipe means the game must supply a stable factory and its required inputs. */
public record ShaderPreparationOrigin(String renderer, String content, String material,
        String group, ShaderPreloadRecipe recipe) {
    public static final ShaderPreparationOrigin UNKNOWN = new ShaderPreparationOrigin("", "", "", "", null);
    public ShaderPreparationOrigin {
        renderer = renderer != null ? renderer : "";
        content = content != null ? content : "";
        material = material != null ? material : "";
        group = group != null ? group : "";
    }
}
