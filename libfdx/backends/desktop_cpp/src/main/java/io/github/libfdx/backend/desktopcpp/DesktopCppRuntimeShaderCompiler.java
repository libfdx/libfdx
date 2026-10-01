package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileDiagnostic;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileOutputKind;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileRequest;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileResult;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileStage;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompileTarget;
import io.github.libfdx.runtime.core.shader.RuntimeShaderCompiler;
import io.github.libfdx.runtime.core.shader.RuntimeShaderReflection;
import io.github.libfdx.runtime.core.shader.RuntimeShaderTargetInterface;
import java.nio.charset.StandardCharsets;
import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

/**
 * Desktop C++ runtime shader compiler backed by the packaged runtime fdx library.
 *
 * @author xpenatan
 */
@NativeInclude("libfdx_jnative.hpp")
final class DesktopCppRuntimeShaderCompiler implements RuntimeShaderCompiler {
    private static final int MAX_DIAGNOSTIC_BYTES = 1024 * 1024;

    /**
     * Returns whether the packaged native shader compiler can be loaded.
     *
     * @return true when available
     */
    boolean available() {
        return fdxDesktopShadercAvailable();
    }

    @Override
    public RuntimeShaderCompileResult compile(RuntimeShaderCompileRequest request) {
        long handle = 0L;
        try {
            handle =
                    fdxDesktopShadercCompile(
                            request.source(),
                            nativeTarget(request.target()),
                            nativeStage(request.stage()),
                            request.entryPoint(),
                            request.glslProfile(),
                            request.glslEsProfile());
            if (handle == 0L) {
                return failure(nativeFailureMessage());
            }
            int status = fdxDesktopShadercResultStatus(handle);
            int kind = fdxDesktopShadercResultOutputKind(handle);
            int outputSize = fdxDesktopShadercResultOutputSize(handle);
            if (outputSize < 0) {
                return failure("Native shader compiler returned a negative output size");
            }
            long outputAddress = fdxDesktopShadercResultOutput(handle);
            byte[] output = copyBytes(outputAddress, outputSize, "output");
            if (status != 0) {
                return failure(
                        cString(fdxDesktopShadercResultDiagnostics(handle), MAX_DIAGNOSTIC_BYTES));
            }
            int reflectionSize = fdxDesktopShadercResultReflectionSize(handle);
            if (reflectionSize <= 0) {
                return failure("Native shader compiler returned no reflection");
            }
            byte[] reflectionBytes =
                    copyBytes(
                            fdxDesktopShadercResultReflection(handle),
                            reflectionSize,
                            "reflection");
            RuntimeShaderReflection reflection = RuntimeShaderReflection.fromBytes(reflectionBytes);
            int targetInterfaceSize = fdxDesktopShadercResultTargetInterfaceSize(handle);
            if (targetInterfaceSize < 0) {
                return failure("Native shader compiler returned a negative target-interface size");
            }
            RuntimeShaderTargetInterface targetInterface =
                    targetInterfaceSize > 0
                            ? RuntimeShaderTargetInterface.fromBytes(
                                    copyBytes(
                                            fdxDesktopShadercResultTargetInterface(handle),
                                            targetInterfaceSize,
                                            "target interface"))
                            : null;
            RuntimeShaderCompileOutputKind outputKind = outputKind(kind);
            if (outputKind == RuntimeShaderCompileOutputKind.TEXT) {
                return RuntimeShaderCompileResult.text(
                        new String(output, StandardCharsets.UTF_8), reflection, targetInterface);
            }
            if (outputKind == RuntimeShaderCompileOutputKind.SPIRV) {
                return RuntimeShaderCompileResult.spirv(output, reflection, targetInterface);
            }
            return failure("Native shader compiler returned no output");
        } catch (Throwable error) {
            String message = error.getMessage();
            return failure(
                    "Could not run desktop C++ runtime shader compiler"
                            + (message != null && message.length() > 0 ? ": " + message : ""));
        } finally {
            if (handle != 0L) {
                fdxDesktopShadercResultFree(handle);
            }
        }
    }

    private static String nativeFailureMessage() {
        String message = cString(fdxDesktopShadercFailureMessage(), MAX_DIAGNOSTIC_BYTES);
        return message.length() > 0
                ? message
                : "Desktop C++ runtime shader compiler is unavailable";
    }

    private static byte[] copyBytes(long address, int length, String label) {
        if (length < 0 || (address == 0L && length != 0)) {
            throw new IllegalStateException("Invalid native shader " + label);
        }
        byte[] bytes = new byte[length];
        copyNativeBytes(address, bytes);
        return bytes;
    }

    @NativeImport("fdx_cpp_copy_bytes")
    private static native void copyNativeBytes(long address, byte[] bytes);

    @NativeImport("fdx_cpp_c_string")
    private static native String cString(long address, int maximumBytes);

    private static RuntimeShaderCompileResult failure(String message) {
        return RuntimeShaderCompileResult.failure(
                new RuntimeShaderCompileDiagnostic[] {RuntimeShaderCompileDiagnostic.of(message)});
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

    @NativeImport("fdx_cpp_shaderc_available")
    private static native boolean fdxDesktopShadercAvailable();

    @NativeImport("fdx_cpp_shaderc_failure_message")
    private static native long fdxDesktopShadercFailureMessage();

    @NativeImport("fdx_cpp_shaderc_compile")
    private static native long fdxDesktopShadercCompile(
            String source,
            int target,
            int stage,
            String entryPoint,
            String glslProfile,
            String glslEsProfile);

    @NativeImport("fdx_cpp_shaderc_result_status")
    private static native int fdxDesktopShadercResultStatus(long handle);

    @NativeImport("fdx_cpp_shaderc_result_output_kind")
    private static native int fdxDesktopShadercResultOutputKind(long handle);

    @NativeImport("fdx_cpp_shaderc_result_output")
    private static native long fdxDesktopShadercResultOutput(long handle);

    @NativeImport("fdx_cpp_shaderc_result_output_size")
    private static native int fdxDesktopShadercResultOutputSize(long handle);

    @NativeImport("fdx_cpp_shaderc_result_diagnostics")
    private static native long fdxDesktopShadercResultDiagnostics(long handle);

    @NativeImport("fdx_cpp_shaderc_result_reflection")
    private static native long fdxDesktopShadercResultReflection(long handle);

    @NativeImport("fdx_cpp_shaderc_result_reflection_size")
    private static native int fdxDesktopShadercResultReflectionSize(long handle);

    @NativeImport("fdx_cpp_shaderc_result_target_interface")
    private static native long fdxDesktopShadercResultTargetInterface(long handle);

    @NativeImport("fdx_cpp_shaderc_result_target_interface_size")
    private static native int fdxDesktopShadercResultTargetInterfaceSize(long handle);

    @NativeImport("fdx_cpp_shaderc_result_free")
    private static native void fdxDesktopShadercResultFree(long handle);
}
