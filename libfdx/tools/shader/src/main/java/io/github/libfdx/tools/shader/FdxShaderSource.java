package io.github.libfdx.tools.shader;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.ShaderProfile;

import java.nio.file.Path;

public final class FdxShaderSource {
    private final Path path;
    private final ShaderProfile profile;
    private final String source;

    private FdxShaderSource(Path path, ShaderProfile profile, String source) {
        if (path == null) {
            throw new FdxException("Shader source path cannot be null");
        }
        if (source == null || source.length() == 0) {
            throw new FdxException("Shader source cannot be empty");
        }
        this.path = path;
        this.profile = profile != null ? profile : ShaderProfile.PORTABLE_WEBGPU;
        this.source = source;
    }

    public static FdxShaderSource of(Path path, ShaderProfile profile, String source) {
        return new FdxShaderSource(path, profile, source);
    }

    public Path path() {
        return path;
    }

    public ShaderProfile profile() {
        return profile;
    }

    public String source() {
        return source;
    }
}
