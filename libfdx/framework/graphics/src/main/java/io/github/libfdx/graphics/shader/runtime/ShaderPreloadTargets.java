package io.github.libfdx.graphics.shader.runtime;

import io.github.libfdx.graphics.RenderTargetLayout;
import java.util.function.Function;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Application-thread logical target roles for recipe capture/import. Values describe formats
 * and sample counts, never attachment views. Register current layouts at configuration changes. */
public final class ShaderPreloadTargets implements Function<String, RenderTargetLayout> {
    private final Map<String, RenderTargetLayout> targets = new LinkedHashMap<>();
    public ShaderPreloadTargets register(String role, RenderTargetLayout target) {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("Empty target role");
        targets.put(role, Objects.requireNonNull(target)); return this;
    }
    public String role(RenderTargetLayout target) {
        for (var entry : targets.entrySet()) if (entry.getValue().equals(target)) return entry.getKey();
        return null;
    }
    @Override public RenderTargetLayout apply(String role) { return targets.get(role); }
}
