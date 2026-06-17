package io.github.libfdx.backend.desktop;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Optional;

/**
 * Desktop runtime shader compiler backed by the loaded fdx native library.
 *
 * @author xpenatan
 */
final class DesktopRuntimeShaderCompiler implements RuntimeShaderCompiler {
    private volatile NativeApi api;
    private volatile String failureMessage;

    @Override
    public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
        try {
            NativeApi nativeApi = api();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment source = arena.allocateFrom(request.source());
                MemorySegment entry = arena.allocateFrom(request.entryPoint());
                MemorySegment glsl = arena.allocateFrom(request.glslProfile());
                MemorySegment glslEs = arena.allocateFrom(request.glslEsProfile());
                MemorySegment handle = (MemorySegment)nativeApi.compile.invoke(source, request.source().length(),
                        nativeTarget(request.target()), nativeStage(request.stage()), entry, glsl, glslEs);
                int status = (int)nativeApi.status.invoke(handle);
                int kind = (int)nativeApi.kind.invoke(handle);
                int outputSize = (int)nativeApi.outputSize.invoke(handle);
                byte[] output = new byte[Math.max(0, outputSize)];
                if (outputSize > 0) {
                    MemorySegment outputPointer = (MemorySegment)nativeApi.output.invoke(handle);
                    output = outputPointer.reinterpret(outputSize).toArray(ValueLayout.JAVA_BYTE);
                }
                MemorySegment diagnosticPointer = (MemorySegment)nativeApi.diagnostics.invoke(handle);
                String diagnostics = diagnosticPointer.address() != 0L ? diagnosticPointer.reinterpret(4096)
                        .getString(0) : "";
                nativeApi.free.invoke(handle);
                if (status != 0) {
                    return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                            RuntimeShaderCompileDiagnostic.of(diagnostics)
                    });
                }
                RuntimeShaderCompileOutputKind outputKind = outputKind(kind);
                if (outputKind == RuntimeShaderCompileOutputKind.TEXT) {
                    return RuntimeShaderCompileResult.text(new String(output, java.nio.charset.StandardCharsets.UTF_8));
                }
                if (outputKind == RuntimeShaderCompileOutputKind.SPIRV) {
                    return RuntimeShaderCompileResult.spirv(output);
                }
                return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                        RuntimeShaderCompileDiagnostic.of("Native shader compiler returned no output")
                });
            }
        } catch (Throwable throwable) {
            return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                    RuntimeShaderCompileDiagnostic.of("Could not run desktop runtime shader compiler: "
                            + throwable.getMessage())
            });
        }
    }

    boolean available() {
        try {
            api();
            return true;
        } catch (Throwable throwable) {
            failureMessage = throwable.getMessage();
            return false;
        }
    }

    String failureMessage() {
        return failureMessage;
    }

    private NativeApi api() {
        NativeApi current = api;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = api;
            if (current == null) {
                if (!DesktopRuntimeCoreNative.load()) {
                    throw new IllegalStateException(DesktopRuntimeCoreNative.failureMessage());
                }
                current = NativeApi.load();
                api = current;
            }
            return current;
        }
    }

    private static int nativeTarget(RuntimeShaderCompileTarget target) {
        switch (target) {
            case WEBGPU_WGSL:
                return 0;
            case WGPU_WGSL:
                return 1;
            case WEBGL_GLSL_ES:
                return 2;
            case GLES_GLSL_ES:
                return 3;
            case OPENGL_GLSL:
                return 4;
            case VULKAN_SPIRV:
                return 5;
            case METAL_MSL:
                return 6;
            case DIRECTX_HLSL:
                return 7;
            default:
                return 0;
        }
    }

    private static int nativeStage(RuntimeShaderCompileStage stage) {
        if (stage == RuntimeShaderCompileStage.VERTEX) {
            return 1;
        }
        if (stage == RuntimeShaderCompileStage.FRAGMENT) {
            return 2;
        }
        return 0;
    }

    private static RuntimeShaderCompileOutputKind outputKind(int value) {
        if (value == 1) {
            return RuntimeShaderCompileOutputKind.TEXT;
        }
        if (value == 2) {
            return RuntimeShaderCompileOutputKind.SPIRV;
        }
        return RuntimeShaderCompileOutputKind.NONE;
    }

    private static final class NativeApi {
        private final MethodHandle compile;
        private final MethodHandle status;
        private final MethodHandle kind;
        private final MethodHandle output;
        private final MethodHandle outputSize;
        private final MethodHandle diagnostics;
        private final MethodHandle free;

        private NativeApi(SymbolLookup symbols) {
            Linker linker = Linker.nativeLinker();
            compile = downcall(linker, symbols, "fdx_shaderc_compile_wgsl_handle",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS));
            status = downcall(linker, symbols, "fdx_shaderc_result_status",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            kind = downcall(linker, symbols, "fdx_shaderc_result_output_kind",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            output = downcall(linker, symbols, "fdx_shaderc_result_output",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            outputSize = downcall(linker, symbols, "fdx_shaderc_result_output_size",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            diagnostics = downcall(linker, symbols, "fdx_shaderc_result_diagnostics",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            free = downcall(linker, symbols, "fdx_shaderc_result_free",
                    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));
        }

        private static NativeApi load() {
            return new NativeApi(SymbolLookup.loaderLookup());
        }

        private static MethodHandle downcall(Linker linker, SymbolLookup symbols, String name,
                FunctionDescriptor descriptor) {
            Optional<MemorySegment> symbol = symbols.find(name);
            if (symbol.isEmpty()) {
                throw new IllegalStateException("Missing native symbol " + name);
            }
            return linker.downcallHandle(symbol.get(), descriptor);
        }
    }
}
