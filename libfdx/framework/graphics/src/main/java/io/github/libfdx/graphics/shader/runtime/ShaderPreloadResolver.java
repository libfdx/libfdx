package io.github.libfdx.graphics.shader.runtime;

import java.util.Objects;

/** Game-owned mapping from portable recipes to loaded content and current target roles.
 * Runs during explicit import on the application thread. Must not compile shaders, perform
 * hidden asset loading or construct native batches. Check content/schema/configuration identity. */
@FunctionalInterface
public interface ShaderPreloadResolver {
    Resolution resolve(ShaderPreloadRecipe recipe);

    enum Status { RESOLVED, REQUIRES_INPUT, STALE, UNSUPPORTED, FAILED }

    record Resolution(Status status, ShaderProvider provider, ShaderRequest request, String message) {
        public Resolution {
            Objects.requireNonNull(status);
            message = message != null ? message : "";
            if (status == Status.RESOLVED) { Objects.requireNonNull(provider); Objects.requireNonNull(request); }
        }
        public static Resolution resolved(ShaderProvider provider, ShaderRequest request) {
            return new Resolution(Status.RESOLVED, provider, request, "");
        }
        public static Resolution requiresInput(String message) { return new Resolution(Status.REQUIRES_INPUT, null, null, message); }
        public static Resolution stale(String message) { return new Resolution(Status.STALE, null, null, message); }
        public static Resolution unsupported(String message) { return new Resolution(Status.UNSUPPORTED, null, null, message); }
    }
}
