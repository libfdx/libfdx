package io.github.libfdx.graphics.shader.target;

import io.github.libfdx.graphics.shader.ShaderBundle;
import io.github.libfdx.graphics.shader.ShaderModuleDescriptor;
import io.github.libfdx.graphics.shader.ShaderStage;
import io.github.libfdx.graphics.shader.reflection.ShaderBinding;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.reflection.ShaderReflectionDecoderTest;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceUse;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.RuntimeShaderBindingRemap;
import io.github.libfdx.runtime.core.shader.RuntimeShaderBindingRemapKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderEntryPointRemap;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetBinding;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetInterface;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderCompilerRegistryTest {
    private static final String WGSL = """
            @vertex fn vs_main() -> @builtin(position) vec4f {
                return vec4f();
            }
            @fragment fn fs_main() -> @location(0) vec4f {
                return vec4f();
            }
            """;
    private static final ShaderArtifactFormat CUSTOM_FORMAT = ShaderArtifactFormat.of(
            "test-console-text", ShaderArtifactEncoding.TEXT, "text/x-test-console");
    private static final ShaderTargetId CUSTOM_TARGET = ShaderTargetId.of("test-console");
    private static final ShaderTargetEnvironment CUSTOM_ENVIRONMENT = ShaderTargetEnvironment.builder(
                    "test-console-v1", CUSTOM_TARGET, CUSTOM_FORMAT)
            .consumer("test-console", "1")
            .compiler("test-assembler", "1")
            .build();

    @Test
    void customTargetCompilerAndVerifierExtendWithoutEnumOrStandardSwitch() {
        CustomCompiler compiler = new CustomCompiler("1");
        CustomVerifier verifier = new CustomVerifier("1");
        ShaderCompilerRegistry registry = ShaderCompilerRegistry.builder()
                .compiler(compiler)
                .verifier(verifier)
                .build();

        ShaderTargetCompileResult result = registry.compile(customRequest(CUSTOM_ENVIRONMENT,
                ShaderTargetOptions.empty(), ShaderVerificationRequirement.REQUIRED));

        assertTrue(result.success());
        assertTrue(result.artifact().verified());
        assertEquals(CUSTOM_TARGET, result.artifact().target());
        assertEquals(CUSTOM_FORMAT, result.artifact().format());
        assertEquals(CustomCompiler.ID, result.artifact().compiler());
        assertEquals(CustomVerifier.ID, result.artifact().verification().verifier());
        assertEquals(2, result.artifact().translatedInterface().entryPoints().length);
        assertEquals(4, result.artifact().translatedInterface().bindings().length);

        ShaderBundle bundle = ShaderBundle.builder("custom")
                .wgsl(WGSL)
                .reflection(fixture())
                .artifact(result.artifact())
                .build();
        assertSame(result.artifact(), bundle.artifact(CUSTOM_TARGET));
        ShaderModuleDescriptor descriptor = bundle.descriptorForTarget(CUSTOM_TARGET);
        assertSame(result.artifact(), descriptor.targetArtifact());
        assertEquals(WGSL, descriptor.wgslSource());
    }

    @Test
    void duplicateDefaultsAreRejectedUnlessExplicitlyResolved() {
        CustomCompiler first = new CustomCompiler("1");
        AlternateCompiler second = new AlternateCompiler();

        assertThrows(FdxException.class, () -> ShaderCompilerRegistry.builder()
                .compiler(first)
                .compiler(second)
                .build());

        ShaderCompilerRegistry registry = ShaderCompilerRegistry.builder()
                .compiler(first)
                .compiler(second)
                .defaultCompiler(CUSTOM_TARGET, AlternateCompiler.ID)
                .verifier(new CustomVerifier("1"))
                .build();
        ShaderTargetCompileResult result = registry.compile(customRequest(CUSTOM_ENVIRONMENT,
                ShaderTargetOptions.empty(), ShaderVerificationRequirement.REQUIRED));
        assertTrue(result.success());
        assertEquals(AlternateCompiler.ID, result.artifact().compiler());
    }

    @Test
    void missingRequiredVerifierAndUnsupportedExplicitCompilerFailBeforeProviderCreation() {
        ShaderCompilerRegistry withoutVerifier = ShaderCompilerRegistry.builder()
                .compiler(new CustomCompiler("1"))
                .build();
        ShaderTargetCompileResult missing = withoutVerifier.compile(customRequest(CUSTOM_ENVIRONMENT,
                ShaderTargetOptions.empty(), ShaderVerificationRequirement.REQUIRED));
        assertFalse(missing.success());
        assertEquals("shader.target.verifier-missing", missing.diagnostics()[0].code());

        ShaderTargetCompileRequest explicitMissing = ShaderTargetCompileRequest.builder(
                        "missing", WGSL, CUSTOM_TARGET, CUSTOM_FORMAT, CUSTOM_ENVIRONMENT)
                .shaderInterface(fixture())
                .entryPoints(entries())
                .compiler(ShaderCompilerId.of("not-registered"))
                .verification(ShaderVerificationRequirement.PROVIDER_PIPELINE)
                .build();
        ShaderTargetCompileResult unsupported = withoutVerifier.compile(explicitMissing);
        assertFalse(unsupported.success());
        assertEquals("shader.target.compiler-missing", unsupported.diagnostics()[0].code());
    }

    @Test
    void providerPipelineRequirementIsExplicitAndNotReportedAsVerified() {
        ShaderCompilerRegistry registry = ShaderCompilerRegistry.builder()
                .compiler(new CustomCompiler("1"))
                .build();

        ShaderTargetCompileResult result = registry.compile(customRequest(CUSTOM_ENVIRONMENT,
                ShaderTargetOptions.empty(), ShaderVerificationRequirement.PROVIDER_PIPELINE));

        assertTrue(result.success());
        assertFalse(result.artifact().verified());
        assertEquals(ShaderTargetVerificationStatus.PROVIDER_PIPELINE_REQUIRED,
                result.artifact().verification().status());
        assertEquals(CUSTOM_ENVIRONMENT, result.artifact().verification().environment());
    }

    @Test
    void compilerVerifierOptionsAndConsumerEnvironmentInvalidateOnlyDerivedKeys() {
        ShaderTargetOptions optionA = ShaderTargetOptions.builder().option("dialect.mode", "a").build();
        ShaderTargetOptions optionB = ShaderTargetOptions.builder().option("dialect.mode", "b").build();
        ShaderTargetCompileRequest requestA = customRequest(CUSTOM_ENVIRONMENT, optionA,
                ShaderVerificationRequirement.REQUIRED);
        ShaderTargetCompileRequest requestB = customRequest(CUSTOM_ENVIRONMENT, optionB,
                ShaderVerificationRequirement.REQUIRED);
        assertNotEquals(requestA.cacheKey(), requestB.cacheKey());

        ShaderTargetEnvironment environment2 = ShaderTargetEnvironment.builder(
                        "test-console-v2", CUSTOM_TARGET, CUSTOM_FORMAT)
                .consumer("test-console", "2")
                .compiler("test-assembler", "2")
                .build();
        assertNotEquals(requestA.cacheKey(), customRequest(environment2, optionA,
                ShaderVerificationRequirement.REQUIRED).cacheKey());

        ShaderTargetArtifact compilerV1 = compile(new CustomCompiler("1"), new CustomVerifier("1"), requestA);
        ShaderTargetArtifact compilerV2 = compile(new CustomCompiler("2"), new CustomVerifier("1"), requestA);
        assertNotEquals(compilerV1.compileCacheKey(), compilerV2.compileCacheKey());

        ShaderTargetArtifact verifierV2 = compile(new CustomCompiler("1"), new CustomVerifier("2"), requestA);
        assertEquals(compilerV1.compileCacheKey(), verifierV2.compileCacheKey());
        assertNotEquals(compilerV1.cacheKey(), verifierV2.cacheKey());
    }

    @Test
    void runtimeTintAdapterReturnsCompleteArtifactsForEveryBuiltInTarget() {
        RecordingRuntimeCompiler runtime = new RecordingRuntimeCompiler();
        RuntimeShaderTargetCompiler compiler = new RuntimeShaderTargetCompiler(runtime);

        for (ShaderTarget target : ShaderTarget.values()) {
            ShaderCompilerRegistry.Builder builder = ShaderCompilerRegistry.builder().compiler(compiler);
            ShaderVerificationRequirement requirement = ShaderVerificationRequirement.PROVIDER_PIPELINE;
            if (target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL) {
                builder.verifier(new RuntimeWgslTargetVerifier(runtime));
                requirement = ShaderVerificationRequirement.REQUIRED;
            }
            ShaderTargetCompileRequest request = ShaderTargetCompileRequest.builder(
                            target.name(), WGSL, target.id(), target.format(), target.environment())
                    .shaderInterface(fixture())
                    .entryPoints(entries())
                    .verification(requirement)
                    .build();

            ShaderTargetCompileResult result = builder.build().compile(request);

            assertTrue(result.success(), target + " " + diagnostics(result));
            ShaderTargetArtifact artifact = result.artifact();
            assertEquals(target.id(), artifact.target());
            assertEquals(target.format(), artifact.format());
            assertEquals(target.environment(), artifact.environment());
            int expectedRemaps = target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL
                    ? fixture().bindingCount()
                    : fixture().requireEntryPoint(ShaderStage.VERTEX, "vs_main").resourceCount()
                    + fixture().requireEntryPoint(ShaderStage.FRAGMENT, "fs_main").resourceCount();
            assertEquals(expectedRemaps, artifact.translatedInterface().bindings().length);
            assertTrue(artifact.translatedInterface().target().complete());
            assertNotNull(artifact.verification());
            if (target == ShaderTarget.WEBGPU_WGSL || target == ShaderTarget.WGPU_WGSL) {
                assertTrue(artifact.verified());
                assertEquals(1, artifact.stages().length);
                assertEquals(ShaderArtifactStage.MODULE, artifact.stages()[0].stage());
            } else {
                assertFalse(artifact.verified());
                assertEquals(2, artifact.stages().length);
            }
        }
        assertEquals(8 + 6, runtime.requests.size());
    }

    @Test
    void exactHlslConsumerEnvironmentDistinguishesFxcFromDxc() {
        assertEquals("fxc", ShaderTargetEnvironments.D3D12_FXC_SM_5_1.compilerFamily());
        assertEquals("5.1", ShaderTargetEnvironments.D3D12_FXC_SM_5_1.shaderModel());
        assertEquals("dxc", ShaderTargetEnvironments.D3D12_DXC_SM_6_0.compilerFamily());
        assertEquals("6.0", ShaderTargetEnvironments.D3D12_DXC_SM_6_0.shaderModel());
        assertNotEquals(ShaderTargetEnvironments.D3D12_FXC_SM_5_1.cacheKey(),
                ShaderTargetEnvironments.D3D12_DXC_SM_6_0.cacheKey());
    }

    @Test
    void providerSupportRejectsUnsupportedTargetAndEnvironmentPairs() {
        ShaderTargetSupport web = ShaderTargetSupport.forProvider(
                io.github.libfdx.core.ProviderId.of("webgpu"));
        assertTrue(web.accepts(ShaderTargetEnvironments.WEBGPU_WGSL_1));
        assertFalse(web.accepts(ShaderTargetEnvironments.WGPU_WGSL_1));
        assertFalse(web.accepts(ShaderTargets.VULKAN_SPIRV, ShaderArtifactFormats.SPIRV_BINARY));
    }

    private static ShaderTargetArtifact compile(ShaderTargetCompiler compiler,
            ShaderTargetVerifier verifier, ShaderTargetCompileRequest request) {
        ShaderTargetCompileResult result = ShaderCompilerRegistry.builder()
                .compiler(compiler)
                .verifier(verifier)
                .build()
                .compile(request);
        assertTrue(result.success(), diagnostics(result));
        return result.artifact();
    }

    private static ShaderTargetCompileRequest customRequest(ShaderTargetEnvironment environment,
            ShaderTargetOptions options, ShaderVerificationRequirement verification) {
        return ShaderTargetCompileRequest.builder("custom", WGSL, CUSTOM_TARGET, CUSTOM_FORMAT, environment)
                .shaderInterface(fixture())
                .entryPoints(entries())
                .options(options)
                .verification(verification)
                .build();
    }

    private static ShaderEntryPointSelection[] entries() {
        return new ShaderEntryPointSelection[] {
                ShaderEntryPointSelection.of(ShaderArtifactStage.VERTEX, "vs_main"),
                ShaderEntryPointSelection.of(ShaderArtifactStage.FRAGMENT, "fs_main")
        };
    }

    private static ShaderReflection fixture() {
        return ShaderReflection.fromRuntime(ShaderReflectionDecoderTest.runtimeFixture());
    }

    private static String diagnostics(ShaderTargetCompileResult result) {
        StringBuilder text = new StringBuilder();
        for (ShaderTargetDiagnostic diagnostic : result.diagnostics()) {
            text.append(diagnostic.code()).append(':').append(diagnostic.message()).append('\n');
        }
        return text.toString();
    }

    private static class CustomCompiler implements ShaderTargetCompiler {
        private static final ShaderCompilerId ID = ShaderCompilerId.of("test.compiler");
        private final String version;

        private CustomCompiler(String version) {
            this.version = version;
        }

        @Override
        public ShaderCompilerId id() {
            return ID;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public ShaderTargetId[] targets() {
            return new ShaderTargetId[] { CUSTOM_TARGET };
        }

        @Override
        public boolean supports(ShaderTargetCompileRequest request) {
            return CUSTOM_TARGET.equals(request.target()) && CUSTOM_FORMAT.equals(request.format());
        }

        @Override
        public ShaderTargetCompileResult compile(ShaderTargetCompileRequest request) {
            ShaderStageArtifact[] stages = {
                    ShaderStageArtifact.text(ShaderArtifactStage.VERTEX, "vs_custom",
                            CUSTOM_FORMAT, "custom vertex"),
                    ShaderStageArtifact.text(ShaderArtifactStage.FRAGMENT, "fs_custom",
                            CUSTOM_FORMAT, "custom fragment")
            };
            ShaderEntryPointRemap[] entries = {
                    ShaderEntryPointRemap.of(ShaderArtifactStage.VERTEX, "vs_main", "vs_custom"),
                    ShaderEntryPointRemap.of(ShaderArtifactStage.FRAGMENT, "fs_main", "fs_custom")
            };
            ShaderBinding[] bindings = request.shaderInterface().bindings();
            ShaderBindingRemap[] remaps = new ShaderBindingRemap[bindings.length];
            for (int i = 0; i < bindings.length; i++) {
                remaps[i] = ShaderBindingRemap.identity(bindings[i]);
            }
            ShaderTranslatedInterface translated = ShaderTranslatedInterface.of(
                    request.shaderInterface(), request.shaderInterface(), entries, remaps);
            return ShaderTargetCompileResult.success(ShaderTargetArtifact.compiled(
                    request.target(), request.format(), request.environment(), stages, translated,
                    id(), version(), ShaderTargetCacheKeys.compilation(request, id(), version())));
        }
    }

    private static final class AlternateCompiler extends CustomCompiler {
        private static final ShaderCompilerId ID = ShaderCompilerId.of("test.alternate");

        private AlternateCompiler() {
            super("1");
        }

        @Override
        public ShaderCompilerId id() {
            return ID;
        }
    }

    private static final class CustomVerifier implements ShaderTargetVerifier {
        private static final ShaderVerifierId ID = ShaderVerifierId.of("test.verifier");
        private final String version;

        private CustomVerifier(String version) {
            this.version = version;
        }

        @Override
        public ShaderVerifierId id() {
            return ID;
        }

        @Override
        public String version() {
            return version;
        }

        @Override
        public ShaderTargetEnvironment[] environments() {
            return new ShaderTargetEnvironment[] { CUSTOM_ENVIRONMENT };
        }

        @Override
        public boolean supports(ShaderTargetVerifyRequest request) {
            return CUSTOM_ENVIRONMENT.equals(request.environment());
        }

        @Override
        public ShaderTargetVerifyResult verify(ShaderTargetVerifyRequest request) {
            return ShaderTargetVerifyResult.success(id(), version(),
                    request.artifact().translatedInterface().entryPoints());
        }
    }

    private static final class RecordingRuntimeCompiler implements RuntimeShaderCompiler {
        private final List<RuntimeShaderCompileRequest> requests = new ArrayList<>();

        @Override
        public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
            requests.add(request);
            if (request.stage() == RuntimeShaderCompileStage.MODULE) {
                return RuntimeShaderCompileResult.text(request.source(),
                        ShaderReflectionDecoderTest.runtimeFixture());
            }
            RuntimeShaderTargetInterface targetInterface = targetInterface(request);
            if (request.target() == io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget.VULKAN_SPIRV) {
                return RuntimeShaderCompileResult.spirv(new byte[] { 3, 2, 35, 7 },
                        ShaderReflectionDecoderTest.runtimeFixture(), targetInterface);
            }
            return RuntimeShaderCompileResult.text(request.target() + " " + request.stage(),
                    ShaderReflectionDecoderTest.runtimeFixture(), targetInterface);
        }

        private static RuntimeShaderTargetInterface targetInterface(RuntimeShaderCompileRequest request) {
            ShaderStage stage = request.stage() == RuntimeShaderCompileStage.VERTEX
                    ? ShaderStage.VERTEX : request.stage() == RuntimeShaderCompileStage.FRAGMENT
                    ? ShaderStage.FRAGMENT : ShaderStage.COMPUTE;
            ShaderReflection reflection = fixture();
            ShaderResourceUse[] resources = reflection.requireEntryPoint(stage,
                    request.entryPoint()).resources();
            RuntimeShaderBindingRemap[] bindings = new RuntimeShaderBindingRemap[resources.length];
            for (int i = 0; i < resources.length; i++) {
                ShaderResourceUse resource = resources[i];
                bindings[i] = RuntimeShaderBindingRemap.of(resource.group(), resource.binding(),
                        RuntimeShaderBindingRemapKind.DIRECT,
                        RuntimeShaderTargetBinding.of("group-binding", resource.group(),
                                resource.binding(), "resource",
                                reflection.requireBinding(resource.group(), resource.binding()).name()));
            }
            return RuntimeShaderTargetInterface.of(new RuntimeShaderEntryPointRemap[] {
                    RuntimeShaderEntryPointRemap.of(request.stage(),
                            request.entryPoint(), request.entryPoint())
            }, bindings);
        }
    }
}
