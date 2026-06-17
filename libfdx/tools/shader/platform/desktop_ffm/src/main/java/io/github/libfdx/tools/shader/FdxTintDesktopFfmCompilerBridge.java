package io.github.libfdx.tools.shader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Desktop Java FFM Tint compiler bridge.
 *
 * @author xpenatan
 */
public final class FdxTintDesktopFfmCompilerBridge implements FdxTintCompilerBridge {
    private final Path explicitLibraryPath;
    private volatile NativeApi api;

    public FdxTintDesktopFfmCompilerBridge() {
        this(null);
    }

    public FdxTintDesktopFfmCompilerBridge(Path explicitLibraryPath) {
        this.explicitLibraryPath = explicitLibraryPath;
    }

    @Override
    public FdxTintCompilerBridgeResult compile(FdxTintCompilerBridgeRequest request) {
        try {
            NativeApi nativeApi = api();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment source = arena.allocateFrom(request.source());
                MemorySegment entry = arena.allocateFrom(request.entryPoint());
                MemorySegment glsl = arena.allocateFrom(request.glslProfile());
                MemorySegment glslEs = arena.allocateFrom(request.glslEsProfile());
                MemorySegment handle = (MemorySegment) nativeApi.compile.invoke(source, request.source().length(),
                        FdxShaderTargets.nativeTarget(request.target()), FdxShaderTargets.nativeStage(request.stage()),
                        entry, glsl, glslEs);
                int status = (int) nativeApi.status.invoke(handle);
                int kind = (int) nativeApi.kind.invoke(handle);
                int outputSize = (int) nativeApi.outputSize.invoke(handle);
                byte[] output = new byte[Math.max(0, outputSize)];
                if (outputSize > 0) {
                    MemorySegment outputPointer = (MemorySegment) nativeApi.output.invoke(handle);
                    output = outputPointer.reinterpret(outputSize).toArray(ValueLayout.JAVA_BYTE);
                }
                MemorySegment diagnosticPointer = (MemorySegment) nativeApi.diagnostics.invoke(handle);
                String diagnostics = diagnosticPointer.address() != 0L ? diagnosticPointer.reinterpret(4096).getString(0)
                        : "";
                nativeApi.free.invoke(handle);
                return FdxTintCompilerBridgeResult.of(status, FdxShaderTargets.outputKind(kind), output, diagnostics);
            }
        } catch (Throwable throwable) {
            return FdxTintCompilerBridgeResult.failure("Could not run desktop FFM shader compiler: "
                    + throwable.getMessage());
        }
    }

    private NativeApi api() throws IOException {
        NativeApi current = api;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            current = api;
            if (current == null) {
                current = NativeApi.load(resolveLibraryPath());
                api = current;
            }
            return current;
        }
    }

    private Path resolveLibraryPath() throws IOException {
        if (explicitLibraryPath != null) {
            return explicitLibraryPath;
        }
        String property = System.getProperty("libfdx.shaderc.nativeLibrary");
        if (property != null && !property.trim().isEmpty()) {
            return Path.of(property);
        }
        String resource = "libfdx/shader/native/desktop/" + hostClassifier() + "/" + nativeLibraryName();
        try (InputStream input = FdxTintDesktopFfmCompilerBridge.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Missing packaged native shader compiler resource: " + resource);
            }
            Path temp = Files.createTempFile("libfdx-shaderc-", nativeLibrarySuffix());
            Files.copy(input, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp;
        }
    }

    private static String hostClassifier() {
        String os = System.getProperty("os.name").toLowerCase();
        String arch = System.getProperty("os.arch").toLowerCase();
        String osName = os.contains("windows") ? "windows" : os.contains("mac") || os.contains("darwin") ? "macos"
                : "linux";
        String archName = arch.equals("amd64") || arch.equals("x86_64") ? "x86_64"
                : arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : arch;
        return osName + "-" + archName;
    }

    private static String nativeLibraryName() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("windows")) {
            return "fdx_shaderc.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libfdx_shaderc.dylib";
        }
        return "libfdx_shaderc.so";
    }

    private static String nativeLibrarySuffix() {
        String name = nativeLibraryName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".so";
    }

    private static final class NativeApi {
        private final Arena arena;
        private final MethodHandle compile;
        private final MethodHandle status;
        private final MethodHandle kind;
        private final MethodHandle output;
        private final MethodHandle outputSize;
        private final MethodHandle diagnostics;
        private final MethodHandle free;

        private NativeApi(Arena arena, SymbolLookup symbols) {
            Linker linker = Linker.nativeLinker();
            this.arena = arena;
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

        private static NativeApi load(Path library) {
            Arena arena = Arena.ofAuto();
            return new NativeApi(arena, SymbolLookup.libraryLookup(library, arena));
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
