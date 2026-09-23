package io.github.libfdx.graphics.shader.internal;

import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.internal.NativeRuntimeShaderResultEnvelope;
import java.util.function.Consumer;

/** Compiler result persistence. Waiting for storage never occupies a provider compiler worker. */
public final class ShaderTranslationCache {
    private final RuntimeShaderCompiler compiler;
    private final ShaderArtifactCache cache;

    public ShaderTranslationCache(RuntimeShaderCompiler compiler, ShaderArtifactCache cache) {
        this.compiler = compiler; this.cache = cache;
    }

    /** Cache decoding uses the preparation executor; native misses may use a platform worker. */
    public FdxFuture<RuntimeShaderCompileResult> compileAsync(RuntimeShaderCompileRequest request,
            Consumer<Runnable> execute) {
        String identity = cache != null && cache.enabled() ? compiler.cacheIdentity() : null;
        if (identity == null || identity.isBlank()) return compiler.compileAsync(request, execute);
        ShaderCacheLayer layer = request.target() == RuntimeShaderCompileTarget.VULKAN_SPIRV ? ShaderCacheLayer.SPIRV
                : request.target() == RuntimeShaderCompileTarget.WGPU_WGSL
                || request.target() == RuntimeShaderCompileTarget.WEBGPU_WGSL ? ShaderCacheLayer.SOURCE : ShaderCacheLayer.TRANSLATION;
        ShaderCacheKey key = ShaderCacheKey.of(layer, "runtime-result-fdxr2-v1", identity,
                request.source(), request.target().name(), request.stage().name(), request.entryPoint(),
                request.glslProfile(), request.glslEsProfile());
        return ShaderCompilationTasks.then(cache.readAsync(key), execute, bytes -> {
            if (bytes != null) {
                RuntimeShaderCompileResult restored = NativeRuntimeShaderResultEnvelope.decode(bytes);
                if (valid(request, restored)) return FdxFuture.completed(restored);
                cache.rejected(key);
            }
            cache.compilerInvoked(key);
            return ShaderCompilationTasks.then(compiler.compileAsync(request, execute), execute, compiled -> {
                if (valid(request, compiled)) {
                    byte[] encoded = NativeRuntimeShaderResultEnvelope.encode(compiled);
                    if (encoded.length <= ShaderArtifactCache.MAX_PAYLOAD_BYTES) cache.writeAsync(key, encoded);
                }
                return FdxFuture.completed(compiled);
            });
        });
    }

    private static boolean valid(RuntimeShaderCompileRequest request, RuntimeShaderCompileResult result) {
        if (result == null || !result.success() || !result.hasReflection()) return false;
        try {
            ShaderReflection.fromRuntime(result.reflection());
            if (request.target() == RuntimeShaderCompileTarget.VULKAN_SPIRV) {
                if (result.outputKind() != RuntimeShaderCompileOutputKind.SPIRV) return false;
                byte[] spirv = result.output();
                if (spirv.length < 20 || spirv.length % 4 != 0 || spirv[0] != 3 || spirv[1] != 2
                        || spirv[2] != 35 || spirv[3] != 7) return false;
            } else if (result.outputKind() != RuntimeShaderCompileOutputKind.TEXT || result.output().length == 0) return false;
            if (request.stage() == RuntimeShaderCompileStage.MODULE) return result.outputText().equals(request.source());
            if (!result.hasTargetInterface()) return false;
            var entries = result.targetInterface().entryPoints();
            return entries.length == 1 && entries[0].stage() == request.stage()
                    && entries[0].sourceName().equals(request.entryPoint());
        } catch (RuntimeException invalid) { return false; }
    }
}
