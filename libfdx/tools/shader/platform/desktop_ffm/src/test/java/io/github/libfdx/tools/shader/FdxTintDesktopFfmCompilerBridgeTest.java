package io.github.libfdx.tools.shader;

import io.github.libfdx.graphics.ShaderTarget;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the desktop FFM bridge test.
 *
 * @author xpenatan
 */
final class FdxTintDesktopFfmCompilerBridgeTest {
    @Test
    void reportsDiagnosticWhenNativeLibraryIsMissing() {
        FdxTintDesktopFfmCompilerBridge bridge = new FdxTintDesktopFfmCompilerBridge(Path.of("missing-fdx-shaderc"));
        FdxTintCompilerBridgeResult result = bridge.compile(FdxTintCompilerBridgeRequest.of("""
                @fragment
                fn fs_main() -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0);
                }
                """, ShaderTarget.METAL_MSL, FdxTintShaderStage.FRAGMENT, "fs_main", "330", "300"));

        assertTrue(result.diagnostics().contains("Could not run desktop FFM shader compiler"));
    }

    @Test
    void compilesWithNativeLibraryWhenConfigured() {
        String library = System.getProperty("libfdx.shaderc.nativeLibrary");
        if (library == null || library.isBlank() || !Files.isRegularFile(Path.of(library))) {
            return;
        }
        FdxTintDesktopFfmCompilerBridge bridge = new FdxTintDesktopFfmCompilerBridge(Path.of(library));
        FdxTintCompilerBridgeResult result = bridge.compile(FdxTintCompilerBridgeRequest.of("""
                @fragment
                fn fs_main() -> @location(0) vec4<f32> {
                    return vec4<f32>(1.0);
                }
                """, ShaderTarget.METAL_MSL, FdxTintShaderStage.FRAGMENT, "fs_main", "330", "300"));

        assertTrue(result.success(), result.diagnostics());
    }
}
