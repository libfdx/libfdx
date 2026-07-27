package io.github.libfdx.backend.desktop;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetInterface;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
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
                int sourceSize = Math.toIntExact(source.byteSize() - 1L);
                MemorySegment handle = MemorySegment.NULL;
                try {
                    handle = (MemorySegment)nativeApi.compile.invoke(source, sourceSize,
                            nativeTarget(request.target()), nativeStage(request.stage()), entry, glsl, glslEs);
                    if (handle.address() == 0L) {
                        return failure("Native shader compiler returned no result handle");
                    }
                    int status = (int)nativeApi.status.invoke(handle);
                    if (status != 0) {
                        MemorySegment diagnosticPointer = (MemorySegment)nativeApi.diagnostics.invoke(handle);
                        String diagnostics = diagnosticPointer.address() != 0L ? diagnosticPointer.reinterpret(4096)
                                .getString(0) : "";
                        return failure(diagnostics);
                    }

                    int outputSize = (int)nativeApi.outputSize.invoke(handle);
                    if (outputSize < 0) {
                        return failure("Native shader compiler returned a negative output size");
                    }
                    byte[] output = copyBytes(nativeApi.output, handle, outputSize, "output");

                    int reflectionSize = (int)nativeApi.reflectionSize.invoke(handle);
                    if (reflectionSize <= 0) {
                        return failure("Native shader compiler returned no reflection");
                    }
                    byte[] reflectionBytes = copyBytes(nativeApi.reflection, handle, reflectionSize, "reflection");
                    RuntimeShaderReflection reflection = RuntimeShaderReflection.fromBytes(reflectionBytes);
                    int targetInterfaceSize = (int)nativeApi.targetInterfaceSize.invoke(handle);
                    if (targetInterfaceSize < 0) {
                        return failure("Native shader compiler returned a negative target-interface size");
                    }
                    RuntimeShaderTargetInterface targetInterface = targetInterfaceSize > 0
                            ? RuntimeShaderTargetInterface.fromBytes(copyBytes(nativeApi.targetInterface,
                                    handle, targetInterfaceSize, "target interface"))
                            : null;

                    RuntimeShaderCompileOutputKind outputKind =
                            outputKind((int)nativeApi.kind.invoke(handle));
                    if (outputKind == RuntimeShaderCompileOutputKind.TEXT) {
                        return RuntimeShaderCompileResult.text(new String(output, StandardCharsets.UTF_8),
                                reflection, targetInterface);
                    }
                    if (outputKind == RuntimeShaderCompileOutputKind.SPIRV) {
                        return RuntimeShaderCompileResult.spirv(output, reflection, targetInterface);
                    }
                    return failure("Native shader compiler returned no output");
                } finally {
                    if (handle.address() != 0L) {
                        nativeApi.free.invoke(handle);
                    }
                }
            }
        } catch (Throwable throwable) {
            return failure("Could not run desktop runtime shader compiler: " + throwable.getMessage());
        }
    }

    private static byte[] copyBytes(MethodHandle pointerAccessor, MemorySegment handle, int size, String label)
            throws Throwable {
        if (size == 0) {
            return new byte[0];
        }
        MemorySegment pointer = (MemorySegment)pointerAccessor.invoke(handle);
        if (pointer.address() == 0L) {
            throw new IllegalStateException("Native shader compiler returned a null " + label + " pointer");
        }
        return pointer.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
    }

    private static RuntimeShaderCompileResult failure(String message) {
        return RuntimeShaderCompileResult.failure(new RuntimeShaderCompileDiagnostic[] {
                RuntimeShaderCompileDiagnostic.of(message)
        });
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
        if (stage == RuntimeShaderCompileStage.COMPUTE) {
            return 3;
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
        private final MethodHandle reflection;
        private final MethodHandle reflectionSize;
        private final MethodHandle targetInterface;
        private final MethodHandle targetInterfaceSize;
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
            reflection = downcall(linker, symbols, "fdx_shaderc_result_reflection",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            reflectionSize = downcall(linker, symbols, "fdx_shaderc_result_reflection_size",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
            targetInterface = downcall(linker, symbols, "fdx_shaderc_result_target_interface",
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            targetInterfaceSize = downcall(linker, symbols, "fdx_shaderc_result_target_interface_size",
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
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
