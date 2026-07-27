package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.core.FdxException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable explicitly composed target compiler and verifier registry.
 *
 * @author xpenatan
 */
public final class ShaderCompilerRegistry {
    private final Map<ShaderCompilerId, ShaderTargetCompiler> compilers;
    private final Map<ShaderTargetId, ShaderCompilerId> defaultCompilers;
    private final Map<ShaderVerifierId, ShaderTargetVerifier> verifiers;
    private final Map<String, ShaderVerifierId> defaultVerifiers;

    private ShaderCompilerRegistry(Builder builder) {
        compilers = Collections.unmodifiableMap(new TreeMap<>(builder.compilers));
        verifiers = Collections.unmodifiableMap(new TreeMap<>(builder.verifiers));
        defaultCompilers = Collections.unmodifiableMap(resolveCompilerDefaults(builder));
        defaultVerifiers = Collections.unmodifiableMap(resolveVerifierDefaults(builder));
    }

    /**
     * Creates a registry builder.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves a compiler using an explicit request selection or the target default.
     *
     * @param request the request
     * @return the compiler
     */
    public ShaderTargetCompiler compiler(ShaderTargetCompileRequest request) {
        if (request == null) {
            throw new FdxException("Shader target compile request cannot be null");
        }
        ShaderCompilerId id = request.compiler();
        if (id == null) {
            id = defaultCompilers.get(request.target());
        }
        ShaderTargetCompiler compiler = id != null ? compilers.get(id) : null;
        if (compiler == null) {
            throw new FdxException("No shader target compiler is registered for target " + request.target());
        }
        if (!compiler.supports(request)) {
            throw new FdxException("Shader target compiler " + compiler.id()
                    + " does not support target " + request.target() + ", format " + request.format()
                    + ", and environment " + request.environment());
        }
        return compiler;
    }

    /**
     * Compiles and verifies a target artifact using this immutable registry.
     *
     * @param request the request
     * @return the result
     */
    public ShaderTargetCompileResult compile(ShaderTargetCompileRequest request) {
        ShaderTargetCompiler compiler;
        try {
            compiler = compiler(request);
        } catch (FdxException error) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.compiler-missing", error.getMessage()));
        }

        ShaderTargetCompileResult compiled;
        try {
            compiled = compiler.compile(request);
        } catch (RuntimeException error) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.compiler-exception",
                    "Shader compiler " + compiler.id() + " failed: " + message(error)));
        }
        if (compiled == null || !compiled.success()) {
            return compiled != null ? compiled : ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.compiler-null", "Shader compiler " + compiler.id() + " returned no result"));
        }
        ShaderTargetArtifact artifact = compiled.artifact();
        String invalid = validateCompilerArtifact(request, compiler, artifact);
        if (invalid != null) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.artifact-invalid", invalid));
        }

        ShaderTargetVerifier verifier;
        try {
            verifier = verifier(request, artifact);
        } catch (FdxException error) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.verifier-missing", error.getMessage()));
        }
        if (verifier == null) {
            ShaderEntryPointRemap[] entries = artifact.translatedInterface().entryPoints();
            return ShaderTargetCompileResult.success(artifact.withVerification(
                    ShaderTargetVerification.providerPipeline(request.environment(), entries,
                            artifact.compileCacheKey())), compiled.diagnostics());
        }

        ShaderTargetVerifyResult verified;
        try {
            ShaderTargetVerifyRequest verifyRequest = ShaderTargetVerifyRequest.of(request, artifact);
            if (!verifier.supports(verifyRequest)) {
                return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                        "shader.target.verifier-unsupported",
                        "Shader verifier " + verifier.id() + " does not support environment "
                                + request.environment()));
            }
            verified = verifier.verify(verifyRequest);
        } catch (RuntimeException error) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.verifier-exception",
                    "Shader verifier " + verifier.id() + " failed: " + message(error)));
        }
        if (verified == null || !verified.success()) {
            return verified != null ? ShaderTargetCompileResult.failure(verified.diagnostics())
                    : ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                            "shader.target.verifier-null",
                            "Shader verifier " + verifier.id() + " returned no result"));
        }
        if (!verifier.id().equals(verified.verifier())
                || !verifier.version().equals(verified.verifierVersion())) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.verifier-identity",
                    "Shader verifier result identity/version does not match the selected verifier"));
        }
        if (!sameEntries(artifact.translatedInterface().entryPoints(), verified.verifiedEntryPoints())) {
            return ShaderTargetCompileResult.failure(ShaderTargetDiagnostic.error(
                    "shader.target.verifier-entry-points",
                    "Shader verifier did not verify the complete translated entry-point set"));
        }
        ShaderTargetVerification verification = ShaderTargetVerification.verified(
                verified, request.environment(), artifact.compileCacheKey());
        return ShaderTargetCompileResult.success(artifact.withVerification(verification), compiled.diagnostics());
    }

    /**
     * Returns a registered compiler by ID.
     *
     * @param id the ID
     * @return the compiler, or null
     */
    public ShaderTargetCompiler findCompiler(ShaderCompilerId id) {
        return id != null ? compilers.get(id) : null;
    }

    /**
     * Returns a registered verifier by ID.
     *
     * @param id the ID
     * @return the verifier, or null
     */
    public ShaderTargetVerifier findVerifier(ShaderVerifierId id) {
        return id != null ? verifiers.get(id) : null;
    }

    private ShaderTargetVerifier verifier(ShaderTargetCompileRequest request, ShaderTargetArtifact artifact) {
        ShaderVerifierId id = request.verifier();
        if (id == null) {
            id = defaultVerifiers.get(request.environment().id());
        }
        if (id == null) {
            if (request.verificationRequirement() == ShaderVerificationRequirement.REQUIRED) {
                throw new FdxException("No shader target verifier is registered for environment "
                        + request.environment());
            }
            return null;
        }
        ShaderTargetVerifier verifier = verifiers.get(id);
        if (verifier == null) {
            throw new FdxException("Shader target verifier is not registered: " + id);
        }
        return verifier;
    }

    private static String validateCompilerArtifact(ShaderTargetCompileRequest request,
            ShaderTargetCompiler compiler, ShaderTargetArtifact artifact) {
        if (artifact == null) {
            return "Shader compiler returned no artifact";
        }
        if (!request.target().equals(artifact.target())
                || !request.format().equals(artifact.format())
                || !request.environment().equals(artifact.environment())) {
            return "Shader compiler returned an artifact for a different target, format, or environment";
        }
        if (!compiler.id().equals(artifact.compiler())
                || !compiler.version().equals(artifact.compilerVersion())) {
            return "Shader compiler artifact identity/version does not match the selected compiler";
        }
        ShaderTranslatedInterface translated = artifact.translatedInterface();
        ShaderReflection requestedInterface = request.shaderInterface();
        if (requestedInterface.complete()
                && (!translated.canonical().complete()
                || !requestedInterface.physicallyEquivalent(translated.canonical()))) {
            return "Shader compiler artifact canonical interface does not match the requested interface";
        }
        if (!sameSelections(request.entryPoints(), translated.entryPoints())) {
            return "Shader compiler artifact entry points do not match the requested entry-point set";
        }
        if (!ShaderArtifactFormats.WGSL_TEXT.equals(artifact.format())) {
            for (ShaderEntryPointRemap entry : translated.entryPoints()) {
                if (artifact.find(entry.stage(), entry.targetName()) == null) {
                    return "Shader compiler artifact is missing translated stage "
                            + entry.stage() + " " + entry.targetName();
                }
            }
        }
        String expectedCacheKey = ShaderTargetCacheKeys.compilation(request, compiler.id(), compiler.version());
        if (!expectedCacheKey.equals(artifact.compileCacheKey())) {
            return "Shader compiler artifact cache key omits or changes required inputs";
        }
        return null;
    }

    private static boolean sameSelections(ShaderEntryPointSelection[] selections,
            ShaderEntryPointRemap[] entries) {
        if (selections.length != entries.length) {
            return false;
        }
        for (int i = 0; i < selections.length; i++) {
            if (selections[i].stage() != entries[i].stage()
                    || !selections[i].entryPoint().equals(entries[i].sourceName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameEntries(ShaderEntryPointRemap[] first, ShaderEntryPointRemap[] second) {
        if (first.length != second.length) {
            return false;
        }
        for (int i = 0; i < first.length; i++) {
            ShaderEntryPointRemap left = first[i];
            ShaderEntryPointRemap right = second[i];
            if (left.stage() != right.stage()
                    || !left.sourceName().equals(right.sourceName())
                    || !left.targetName().equals(right.targetName())) {
                return false;
            }
        }
        return true;
    }

    private static Map<ShaderTargetId, ShaderCompilerId> resolveCompilerDefaults(Builder builder) {
        TreeMap<ShaderTargetId, List<ShaderCompilerId>> candidates = new TreeMap<>();
        for (ShaderTargetCompiler compiler : builder.compilers.values()) {
            ShaderTargetId[] targets = compiler.targets();
            if (targets == null || targets.length == 0) {
                throw new FdxException("Shader compiler " + compiler.id() + " declares no targets");
            }
            for (ShaderTargetId target : targets) {
                if (target == null) {
                    throw new FdxException("Shader compiler " + compiler.id() + " declares a null target");
                }
                candidates.computeIfAbsent(target, ignored -> new ArrayList<>()).add(compiler.id());
            }
        }
        TreeMap<ShaderTargetId, ShaderCompilerId> defaults = new TreeMap<>();
        for (Map.Entry<ShaderTargetId, List<ShaderCompilerId>> entry : candidates.entrySet()) {
            ShaderCompilerId selected = builder.explicitCompilerDefaults.get(entry.getKey());
            if (selected == null) {
                if (entry.getValue().size() != 1) {
                    throw new FdxException("Ambiguous shader compiler default for target " + entry.getKey());
                }
                selected = entry.getValue().get(0);
            }
            if (!entry.getValue().contains(selected)) {
                throw new FdxException("Default shader compiler " + selected
                        + " does not declare target " + entry.getKey());
            }
            defaults.put(entry.getKey(), selected);
        }
        for (ShaderTargetId explicit : builder.explicitCompilerDefaults.keySet()) {
            if (!defaults.containsKey(explicit)) {
                throw new FdxException("Default shader compiler target has no registered compiler: " + explicit);
            }
        }
        return defaults;
    }

    private static Map<String, ShaderVerifierId> resolveVerifierDefaults(Builder builder) {
        TreeMap<String, List<ShaderVerifierId>> candidates = new TreeMap<>();
        TreeMap<String, ShaderTargetEnvironment> environments = new TreeMap<>();
        for (ShaderTargetVerifier verifier : builder.verifiers.values()) {
            ShaderTargetEnvironment[] supported = verifier.environments();
            if (supported == null || supported.length == 0) {
                throw new FdxException("Shader verifier " + verifier.id() + " declares no environments");
            }
            for (ShaderTargetEnvironment environment : supported) {
                if (environment == null) {
                    throw new FdxException("Shader verifier " + verifier.id() + " declares a null environment");
                }
                ShaderTargetEnvironment previous = environments.put(environment.id(), environment);
                if (previous != null && !previous.equals(environment)) {
                    throw new FdxException("Shader environment ID has conflicting definitions: "
                            + environment.id());
                }
                candidates.computeIfAbsent(environment.id(), ignored -> new ArrayList<>()).add(verifier.id());
            }
        }
        TreeMap<String, ShaderVerifierId> defaults = new TreeMap<>();
        for (Map.Entry<String, List<ShaderVerifierId>> entry : candidates.entrySet()) {
            ShaderVerifierId selected = builder.explicitVerifierDefaults.get(entry.getKey());
            if (selected == null) {
                if (entry.getValue().size() != 1) {
                    throw new FdxException("Ambiguous shader verifier default for environment " + entry.getKey());
                }
                selected = entry.getValue().get(0);
            }
            if (!entry.getValue().contains(selected)) {
                throw new FdxException("Default shader verifier " + selected
                        + " does not declare environment " + entry.getKey());
            }
            defaults.put(entry.getKey(), selected);
        }
        for (String explicit : builder.explicitVerifierDefaults.keySet()) {
            if (!defaults.containsKey(explicit)) {
                throw new FdxException("Default shader verifier environment has no registered verifier: "
                        + explicit);
            }
        }
        return defaults;
    }

    private static String message(Throwable error) {
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    /**
     * Builds immutable compiler registries.
     *
     * @author xpenatan
     */
    public static final class Builder {
        private final TreeMap<ShaderCompilerId, ShaderTargetCompiler> compilers = new TreeMap<>();
        private final TreeMap<ShaderVerifierId, ShaderTargetVerifier> verifiers = new TreeMap<>();
        private final TreeMap<ShaderTargetId, ShaderCompilerId> explicitCompilerDefaults = new TreeMap<>();
        private final TreeMap<String, ShaderVerifierId> explicitVerifierDefaults = new TreeMap<>();

        private Builder() {
        }

        public Builder compiler(ShaderTargetCompiler compiler) {
            if (compiler == null || compiler.id() == null
                    || compiler.version() == null || compiler.version().trim().length() == 0) {
                throw new FdxException("Shader target compiler identity/version cannot be empty");
            }
            if (compilers.put(compiler.id(), compiler) != null) {
                throw new FdxException("Duplicate shader target compiler ID: " + compiler.id());
            }
            return this;
        }

        public Builder defaultCompiler(ShaderTargetId target, ShaderCompilerId compiler) {
            if (target == null || compiler == null) {
                throw new FdxException("Default shader compiler target and ID cannot be null");
            }
            if (explicitCompilerDefaults.put(target, compiler) != null) {
                throw new FdxException("Duplicate explicit shader compiler default for " + target);
            }
            return this;
        }

        public Builder verifier(ShaderTargetVerifier verifier) {
            if (verifier == null || verifier.id() == null
                    || verifier.version() == null || verifier.version().trim().length() == 0) {
                throw new FdxException("Shader target verifier identity/version cannot be empty");
            }
            if (verifiers.put(verifier.id(), verifier) != null) {
                throw new FdxException("Duplicate shader target verifier ID: " + verifier.id());
            }
            return this;
        }

        public Builder defaultVerifier(ShaderTargetEnvironment environment, ShaderVerifierId verifier) {
            if (environment == null || verifier == null) {
                throw new FdxException("Default shader verifier environment and ID cannot be null");
            }
            if (explicitVerifierDefaults.put(environment.id(), verifier) != null) {
                throw new FdxException("Duplicate explicit shader verifier default for " + environment);
            }
            return this;
        }

        public ShaderCompilerRegistry build() {
            return new ShaderCompilerRegistry(this);
        }
    }
}
