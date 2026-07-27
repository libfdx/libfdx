package io.github.libfdx.graphics.shadergraph.compiler;

import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBarrierScope;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphStageSemantic;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeCompileResult;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphComputeProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphComputeProgramCompiler;
import io.github.libfdx.graphics.shadergraph.technique.ShaderGraphProgram;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompileResult;
import io.github.libfdx.graphics.shadergraph.compiler.ShaderGraphProgramCompiler;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shader.reflection.ShaderResourceAccess;
import io.github.libfdx.graphics.shader.reflection.ShaderStorageTextureFormat;
import io.github.libfdx.graphics.shader.reflection.ShaderValueType;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Emits the canonical graph fixture and validates it with the native Tint
 * reflection CLI supplied by the build.
 */
public final class ShaderGraphTintTool {
    private ShaderGraphTintTool() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException(
                    "Usage: ShaderGraphTintTool <fdx_shaderc_reflect>");
        }
        ShaderGraphType vec3 = ShaderGraphType.vector(ShaderScalarType.F32, 3);
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "tint_fixture", ShaderGraphKind.SURFACE);
        builder.parameter(ShaderGraphParameter.of("tint", vec3,
                ShaderGraphParameterKind.MATERIAL,
                ShaderGraphLiteral.composite(vec3,
                        ShaderGraphLiteral.f32(1),
                        ShaderGraphLiteral.f32(0.5f),
                        ShaderGraphLiteral.f32(0.25f))));
        ShaderExpression tint = builder.parameter("tint_node", "tint");
        ShaderExpression gain = builder.constant("gain", ShaderGraphLiteral.f32(0.8f));
        ShaderExpression gain3 = builder.construct("gain3", vec3,
                gain, gain, gain);
        builder.output("base_color", "baseColor",
                builder.multiply("color", tint, gain3));
        builder.output("alpha", "alpha",
                builder.constant("alpha", ShaderGraphLiteral.f32(1)));
        ShaderGraphCompileResult result = new ShaderGraphCompiler().compile(
                builder.build(), ShaderGraphCompileOptions.builder().build());
        if (!result.success()) {
            throw new IllegalStateException("Canonical shader graph fixture did not compile");
        }

        Path directory = Files.createTempDirectory("libfdx-shader-graph-tint-");
        try {
            validate(arguments[0], directory, "surface", result.wgsl());
            validate(arguments[0], directory, "program",
                    completeProgramFixture());
            validate(arguments[0], directory, "compute-buffer",
                    computeBufferFixture());
            validate(arguments[0], directory, "compute-texture",
                    computeTextureFixture());
        } finally {
            Files.deleteIfExists(directory.resolve("surface.fdxi"));
            Files.deleteIfExists(directory.resolve("surface.wgsl"));
            Files.deleteIfExists(directory.resolve("program.fdxi"));
            Files.deleteIfExists(directory.resolve("program.wgsl"));
            Files.deleteIfExists(directory.resolve("compute-buffer.fdxi"));
            Files.deleteIfExists(directory.resolve("compute-buffer.wgsl"));
            Files.deleteIfExists(directory.resolve("compute-texture.fdxi"));
            Files.deleteIfExists(directory.resolve("compute-texture.wgsl"));
            Files.deleteIfExists(directory);
        }
    }

    private static String computeBufferFixture() {
        ShaderGraphType u32 = ShaderGraphType.scalar(
                ShaderScalarType.U32);
        ShaderGraphType u32x3 = ShaderGraphType.vector(
                ShaderScalarType.U32, 3);
        ShaderGraphType atomic = ShaderGraphType.value(
                ShaderValueType.atomic(ShaderScalarType.U32));
        ShaderGraphType buffer = ShaderGraphType.storageBuffer(
                u32, ShaderResourceAccess.READ_WRITE);
        ShaderGraphType atomicBuffer = ShaderGraphType.storageBuffer(
                atomic, ShaderResourceAccess.READ_WRITE);
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "tint_compute_buffer", ShaderGraphKind.COMPUTE);
        builder.parameter(ShaderGraphParameter.semantic(
                "global_id", u32x3,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.GLOBAL_INVOCATION_ID));
        builder.parameter(ShaderGraphParameter.semantic(
                "local_index", u32,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.LOCAL_INVOCATION_INDEX));
        builder.resource(ShaderGraphResource.of(
                "data", buffer, 0, 0));
        builder.resource(ShaderGraphResource.of(
                "counter", atomicBuffer, 0, 1));
        builder.resource(ShaderGraphResource.workgroup(
                "shared", ShaderGraphType.workgroupArray(u32, 4)));
        ShaderExpression data = builder.resource("data_node", "data");
        ShaderExpression counter = builder.resource(
                "counter_node", "counter");
        ShaderExpression shared = builder.resource(
                "shared_node", "shared");
        ShaderExpression global = builder.member("global_index",
                builder.parameter("global_node", "global_id"),
                "x", u32);
        ShaderExpression local = builder.parameter(
                "local_node", "local_index");
        ShaderExpression loaded = builder.storageLoad(
                "load_data", data, global);
        ShaderExpression staged = builder.storageStore(
                "store_shared", shared, local, loaded);
        ShaderExpression synchronizedValue = builder.barrier(
                "barrier", ShaderGraphBarrierScope.WORKGROUP, staged);
        ShaderExpression sharedValue = builder.storageLoad(
                "load_shared", shared, local, synchronizedValue);
        ShaderExpression prior = builder.atomicAdd(
                "atomic_add", counter,
                builder.constant("zero", ShaderGraphLiteral.u32(0)),
                builder.constant("one", ShaderGraphLiteral.u32(1)));
        builder.output("stored", builder.storageStore(
                "store_data", data, global, sharedValue, prior));
        ShaderGraphComputeCompileResult result =
                new ShaderGraphComputeProgramCompiler().compile(
                        ShaderGraphComputeProgram.builder(
                                        "tint_compute_buffer_program",
                                        builder.build())
                                .workgroupSize(4, 1, 1)
                                .build(),
                        ShaderGraphCompileOptions.builder().build());
        if (!result.success()) {
            throw new IllegalStateException(
                    "Compute buffer graph fixture did not compile");
        }
        return result.wgsl();
    }

    private static String computeTextureFixture() {
        ShaderGraphType u32x3 = ShaderGraphType.vector(
                ShaderScalarType.U32, 3);
        ShaderGraphType i32x2 = ShaderGraphType.vector(
                ShaderScalarType.I32, 2);
        ShaderGraphType f32x4 = ShaderGraphType.vector(
                ShaderScalarType.F32, 4);
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "tint_compute_texture", ShaderGraphKind.COMPUTE);
        builder.parameter(ShaderGraphParameter.semantic(
                "global_id", u32x3,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.GLOBAL_INVOCATION_ID));
        builder.resource(ShaderGraphResource.of("output",
                ShaderGraphType.storageTexture2D(
                        ShaderStorageTextureFormat.RGBA8_UNORM,
                        ShaderResourceAccess.WRITE),
                0, 0));
        ShaderExpression resource = builder.resource(
                "output_node", "output");
        ShaderExpression global = builder.parameter(
                "global_node", "global_id");
        ShaderExpression coordinates = builder.customWgsl(
                "coordinates", i32x2, "vec2<i32>($0.xy)", global);
        ShaderExpression color = builder.constant("color",
                ShaderGraphLiteral.composite(f32x4,
                        ShaderGraphLiteral.f32(0.1f),
                        ShaderGraphLiteral.f32(0.8f),
                        ShaderGraphLiteral.f32(0.2f),
                        ShaderGraphLiteral.f32(1)));
        builder.output("stored", builder.storageStore(
                "store", resource, coordinates, color));
        ShaderGraphComputeCompileResult result =
                new ShaderGraphComputeProgramCompiler().compile(
                        ShaderGraphComputeProgram.builder(
                                        "tint_compute_texture_program",
                                        builder.build())
                                .workgroupSize(8, 8, 1)
                                .build(),
                        ShaderGraphCompileOptions.builder().build());
        if (!result.success()) {
            throw new IllegalStateException(
                    "Compute texture graph fixture did not compile");
        }
        return result.wgsl();
    }

    private static String completeProgramFixture() {
        ShaderGraphType f32 = ShaderGraphType.scalar(ShaderScalarType.F32);
        ShaderGraphType bool = ShaderGraphType.scalar(ShaderScalarType.BOOL);
        ShaderGraphType vec2 = ShaderGraphType.vector(ShaderScalarType.F32, 2);
        ShaderGraphType vec4 = ShaderGraphType.vector(ShaderScalarType.F32, 4);

        ShaderGraphBuilder vertex = new ShaderGraphBuilder(
                "tint_program_vertex", ShaderGraphKind.VERTEX);
        vertex.parameter(ShaderGraphParameter.semantic("position", vec2,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.location(0)));
        vertex.parameter(ShaderGraphParameter.semantic("uv", vec2,
                ShaderGraphParameterKind.STAGE_INPUT, null,
                ShaderGraphStageSemantic.location(1)));
        ShaderExpression position = vertex.parameter("position_node", "position");
        vertex.output("position", ShaderGraphStageSemantic.POSITION,
                vertex.construct("clip", vec4, position,
                        vertex.floatValue(0), vertex.floatValue(1)));
        vertex.output("uv", "uv0", vertex.parameter("uv_node", "uv"));

        ShaderGraphBuilder fragment = new ShaderGraphBuilder(
                "tint_program_fragment", ShaderGraphKind.FRAGMENT);
        fragment.parameter(ShaderGraphParameter.semantic("uv", vec2,
                ShaderGraphParameterKind.STAGE_INPUT, null, "uv0"));
        ShaderExpression uv = fragment.parameter("uv_node", "uv");
        ShaderExpression uvDerivative = fragment.derivativeX("uv_dx", uv);
        ShaderExpression color = fragment.customWgsl("color_node", vec4,
                "vec4<f32>($0, length($1), 1.0)", uv, uvDerivative);
        ShaderExpression condition = fragment.customWgsl(
                "discard_condition", bool, "$0.x < -1.0", uv);
        ShaderExpression discarded = fragment.discardIf("discard", condition);
        color = fragment.branch("after_discard", discarded, color, color);
        fragment.output("color0", ShaderGraphStageSemantic.location(0), color);
        fragment.output("color1", ShaderGraphStageSemantic.location(1),
                fragment.customWgsl("color1_node", vec4,
                        "vec4<f32>($0.bgr, $0.a)", color));
        fragment.output("depth", ShaderGraphStageSemantic.FRAGMENT_DEPTH,
                fragment.constant("depth_value", ShaderGraphLiteral.f32(0.5f)));

        ShaderGraphProgramCompileResult result =
                new ShaderGraphProgramCompiler().compile(
                        ShaderGraphProgram.builder("tint_program",
                                        vertex.build(), fragment.build())
                                .entryPoints("customVertex", "customFragment")
                                .build(),
                        ShaderGraphCompileOptions.builder().build());
        if (!result.success()) {
            throw new IllegalStateException(
                    "Complete shader graph program fixture did not compile");
        }
        return result.wgsl();
    }

    private static void validate(String executable, Path directory,
            String name, String wgsl) throws Exception {
        Path source = directory.resolve(name + ".wgsl");
        Path reflection = directory.resolve(name + ".fdxi");
        Files.writeString(source, wgsl, StandardCharsets.UTF_8);
        Process process = new ProcessBuilder(executable,
                source.toString(), reflection.toString())
                .inheritIO().start();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !Files.isRegularFile(reflection)
                || Files.size(reflection) == 0) {
            throw new IllegalStateException(
                    "Tint rejected " + name
                            + " graph WGSL with exit code " + exitCode);
        }
    }
}
