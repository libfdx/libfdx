package io.github.libfdx.graphics.shadergraph.cache;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.internal.PortableSha256;
import io.github.libfdx.graphics.shader.ShaderStage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShaderGraphCompiledCacheCodecTest {
    @Test
    void interfaceRejectsConflictingPhysicalIdentities() {
        assertThrows(FdxException.class,
                () -> ShaderGraphCompiledInterface.of("abi-1",
                        new ShaderGraphCompiledInterface.EntryPoint[0],
                        new ShaderGraphCompiledInterface.Binding[] {
                                ShaderGraphCompiledInterface.Binding.of(
                                        0, 1, "first", "buffer",
                                        "f32", ""),
                                ShaderGraphCompiledInterface.Binding.of(
                                        0, 1, "second", "texture",
                                        "texture_2d", "")
                        },
                        new ShaderGraphCompiledInterface.Parameter[0]));
        assertThrows(FdxException.class,
                () -> ShaderGraphCompiledInterface.of("abi-1",
                        new ShaderGraphCompiledInterface.EntryPoint[0],
                        new ShaderGraphCompiledInterface.Binding[0],
                        new ShaderGraphCompiledInterface.Parameter[] {
                                ShaderGraphCompiledInterface.Parameter.of(
                                        "tint", "uniform", "f32",
                                        "", -1, -1),
                                ShaderGraphCompiledInterface.Parameter.of(
                                        "tint", "uniform", "vec4<f32>",
                                        "", -1, -1)
                        }));
    }

    @Test
    void roundTripsDeterministicTextAndBinaryEntries() {
        String semanticHash = hash("semantic");
        ShaderGraphCompiledInterface shaderInterface = shaderInterface();
        ShaderGraphCompiledCacheEntry text = entry(
                key(semanticHash, shaderInterface)
                        .compilationUnit("render-program")
                        .build(),
                ShaderGraphCompiledArtifact.text("wgsl",
                        "@fragment fn fragmentMain() {}"),
                shaderInterface);
        ShaderGraphCacheKey binaryKey =
                key(semanticHash, shaderInterface)
                        .target("vulkan-spirv", "spirv",
                                "vulkan-1.3")
                        .compilationUnit("compute-program")
                        .pass("update")
                        .variant("fast")
                        .build();
        ShaderGraphCompiledCacheEntry binary = entry(binaryKey,
                ShaderGraphCompiledArtifact.binary(
                        "spirv", new byte[] { 3, 2, 35, 7 }),
                shaderInterface);
        ShaderGraphCompiledCache cache =
                ShaderGraphCompiledCache.of(binary, text);

        String encoded = ShaderGraphCompiledCacheCodec.write(cache);
        ShaderGraphCompiledCacheCodec.DecodeResult decoded =
                ShaderGraphCompiledCacheCodec.read(
                        encoded, semanticHash);

        assertTrue(decoded.acceptedAll());
        assertEquals(cache, decoded.cache());
        assertEquals(encoded, ShaderGraphCompiledCacheCodec.write(
                decoded.cache()));
        assertArrayEquals(new byte[] { 3, 2, 35, 7 },
                decoded.cache().lookup(binaryKey)
                        .entry().artifact().bytes());
    }

    @Test
    void everyDeclaredKeyContributionParticipatesInLookup() {
        ShaderGraphCompiledInterface shaderInterface = shaderInterface();
        ShaderGraphCacheKey expected =
                key(hash("semantic"), shaderInterface).build();
        ShaderGraphCompiledCache cache =
                ShaderGraphCompiledCache.of(entry(expected,
                        ShaderGraphCompiledArtifact.text(
                                "wgsl", "fn cached() {}"),
                        shaderInterface));
        assertTrue(cache.lookup(expected).hit());

        List<ShaderGraphCacheKey> mismatches = List.of(
                expected.toBuilder().documentFormatVersion(3).build(),
                expected.toBuilder().semanticHash(hash("semantic-2")).build(),
                expected.toBuilder().dependencyHash(hash("dependency-2")).build(),
                expected.toBuilder().compiler("other", expected.compilerVersion()).build(),
                expected.toBuilder().compiler(expected.compilerId(), "2").build(),
                expected.toBuilder().libraries("2", expected.standardLibraryVersion()).build(),
                expected.toBuilder().libraries(expected.nodeLibraryVersion(), "2").build(),
                expected.toBuilder().profile("fdx-native", expected.capabilitiesHash()).build(),
                expected.toBuilder().profile(expected.profileId(), hash("capabilities-2")).build(),
                expected.toBuilder().target("other-target", expected.artifactFormat(),
                        expected.consumerEnvironment()).build(),
                expected.toBuilder().target(expected.targetId(), "spirv",
                        expected.consumerEnvironment()).build(),
                expected.toBuilder().target(expected.targetId(), expected.artifactFormat(),
                        "other-environment").build(),
                expected.toBuilder().verifier("tint", expected.verifierVersion()).build(),
                expected.toBuilder().verifier(expected.verifierId(), "2").build(),
                expected.toBuilder().optionsHash(hash("options-2")).build(),
                expected.toBuilder().interfaceAbiVersion("abi-2").build(),
                expected.toBuilder().compilationUnit("other-unit").build(),
                expected.toBuilder().pass("shadow").build(),
                expected.toBuilder().variant("skinned").build(),
                expected.toBuilder().entryPointsHash(hash("entries-2")).build());

        for (ShaderGraphCacheKey mismatch : mismatches) {
            ShaderGraphCompiledCache.Lookup lookup =
                    cache.lookup(mismatch);
            assertFalse(lookup.hit(), mismatch.hash());
            assertNull(lookup.entry(), mismatch.hash());
            assertEquals(
                    ShaderGraphCompiledCache.MissReason.NO_EXACT_MATCH,
                    lookup.missReason(), mismatch.hash());
        }
    }

    @Test
    void rejectsCorruptAndStaleEntriesWithoutFailingSemanticLoad() {
        String semanticHash = hash("semantic");
        ShaderGraphCompiledInterface shaderInterface = shaderInterface();
        ShaderGraphCompiledCache cache = ShaderGraphCompiledCache.of(
                entry(key(semanticHash, shaderInterface).build(),
                        ShaderGraphCompiledArtifact.text(
                                "wgsl", "fn original() {}"),
                        shaderInterface));
        String encoded = ShaderGraphCompiledCacheCodec.write(cache);
        String corrupt = encoded.replace(
                "fn original() {}", "fn changed() {}");

        ShaderGraphCompiledCacheCodec.DecodeResult corruptResult =
                ShaderGraphCompiledCacheCodec.read(
                        corrupt, semanticHash);
        assertEquals(0, corruptResult.cache().size());
        assertFalse(corruptResult.acceptedAll());
        assertEquals("FDXG_CACHE_ENTRY_INVALID",
                corruptResult.rejections()[0].code());

        ShaderGraphCompiledCacheCodec.DecodeResult stale =
                ShaderGraphCompiledCacheCodec.read(
                        encoded, hash("new-semantic"));
        assertEquals(0, stale.cache().size());
        assertEquals("FDXG_CACHE_SEMANTIC_MISMATCH",
                stale.rejections()[0].code());

        ShaderGraphCompiledCacheCodec.DecodeResult invalidBlock =
                ShaderGraphCompiledCacheCodec.read("42", semanticHash);
        assertEquals(0, invalidBlock.cache().size());
        assertEquals("FDXG_CACHE_BLOCK_INVALID",
                invalidBlock.rejections()[0].code());
    }

    private static ShaderGraphCacheKey.Builder key(
            String semanticHash,
            ShaderGraphCompiledInterface shaderInterface) {
        return ShaderGraphCacheKey.builder(semanticHash)
                .dependencyHash(hash("dependencies"))
                .compiler("libfdx-shader-graph", "1")
                .libraries("1", "1")
                .profile("fdx-wgsl-webgpu",
                        hash("capabilities"))
                .target("wgpu-wgsl", "wgsl", "wgpu")
                .verifier("libfdx-wgsl-validator", "1")
                .optionsHash(hash("options"))
                .interfaceAbiVersion(
                        shaderInterface.abiVersion())
                .compilationUnit("graph-library")
                .pass("")
                .variant("")
                .entryPointsHash(
                        shaderInterface.entryPointsHash());
    }

    private static ShaderGraphCompiledCacheEntry entry(
            ShaderGraphCacheKey key,
            ShaderGraphCompiledArtifact artifact,
            ShaderGraphCompiledInterface shaderInterface) {
        return ShaderGraphCompiledCacheEntry.of(
                key, artifact, shaderInterface);
    }

    private static ShaderGraphCompiledInterface shaderInterface() {
        return ShaderGraphCompiledInterface.of("abi-1",
                new ShaderGraphCompiledInterface.EntryPoint[] {
                        ShaderGraphCompiledInterface.EntryPoint.of(
                                ShaderStage.FRAGMENT, "fragmentMain"),
                        ShaderGraphCompiledInterface.EntryPoint.of(
                                ShaderStage.VERTEX, "vertexMain")
                },
                new ShaderGraphCompiledInterface.Binding[] {
                        ShaderGraphCompiledInterface.Binding.of(
                                1, 0, "material", "uniform",
                                "Material", "read")
                },
                new ShaderGraphCompiledInterface.Parameter[] {
                        ShaderGraphCompiledInterface.Parameter.of(
                                "tint", "material", "vec4<f32>",
                                "", 0, 16)
                });
    }

    private static String hash(String value) {
        return PortableSha256.hashUtf8(value);
    }
}
