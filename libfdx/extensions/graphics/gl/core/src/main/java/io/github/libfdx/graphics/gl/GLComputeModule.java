package io.github.libfdx.graphics.gl;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.graphics.shader.*;
import io.github.libfdx.graphics.shader.reflection.ShaderReflection;
import io.github.libfdx.graphics.shader.target.*;
import io.github.libfdx.runtime.core.RuntimeCore;

/** Individually linked compute entry points with the same retained-program lifetime as render modules. */
final class GLComputeModule implements ShaderModule {
    private static final ShaderTargetEnvironment ENVIRONMENT = ShaderTargetEnvironment.builder(
                    "opengl-4.6-glsl-460-compute", ShaderTargets.OPENGL_GLSL, ShaderArtifactFormats.GLSL_TEXT)
            .consumer("opengl", "4.6").compiler("opengl-driver", "glsl-460")
            .options(ShaderTargetOptions.builder().option("glsl.version", "460").build()).build();
    final GLResourceDomain domain;
    private final ProviderId provider;
    private final ShaderReflection reflection;
    private final String[] entryPoints;
    private final GLShaderModuleHandle[] programs;
    private boolean disposed;

    GLComputeModule(ProviderId provider, GLApi gl, GLResourceDomain domain, ShaderModuleDescriptor descriptor) {
        this.provider = provider;
        this.domain = domain;
        var translated = ShaderModuleDescriptors.requireTarget(descriptor, ShaderTargets.OPENGL_GLSL,
                ShaderArtifactFormats.GLSL_TEXT, ENVIRONMENT,
                ShaderCompilerRegistry.builder().compiler(new RuntimeShaderTargetCompiler(RuntimeCore.shaderCompiler())).build(),
                ShaderVerificationRequirement.PROVIDER_PIPELINE, "GL compute");
        var artifact = translated.targetArtifact();
        reflection = translated.reflection();
        var remaps = artifact.translatedInterface().entryPoints();
        entryPoints = new String[remaps.length];
        programs = new GLShaderModuleHandle[remaps.length];
        try {
            for (int i = 0; i < remaps.length; i++) {
                var remap = remaps[i];
                var stage = artifact.find(ShaderArtifactStage.COMPUTE, remap.targetName());
                if (stage == null) throw new FdxException("Missing GL compute stage " + remap.sourceName());
                int program = gl.createComputeProgram(stage.text());
                entryPoints[i] = remap.sourceName();
                programs[i] = new GLShaderModuleHandle(provider, gl, domain, program, reflection,
                        artifact.translatedInterface(), remap.sourceName(), "");
            }
        } catch (RuntimeException | Error failure) {
            dispose();
            throw failure;
        }
    }

    GLShaderModuleHandle program(String entry) {
        if (disposed) throw new FdxException("GL compute module is disposed");
        for (int i = 0; i < entryPoints.length; i++) if (entryPoints[i].equals(entry)) return programs[i];
        throw new FdxException("Unknown GL compute entry point " + entry);
    }

    @Override
    public ShaderLanguage language() { return ShaderLanguage.GLSL; }
    @Override
    public ShaderReflection reflection() { return reflection; }
    @Override
    public ProviderId providerId() { return provider; }
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() { return (T) this; }
    @Override
    public boolean isDisposed() { return disposed; }
    @Override
    public void dispose() {
        if (disposed) return;
        disposed = true;
        for (var program : programs) if (program != null) program.dispose();
    }
}
