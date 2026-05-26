package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

public final class ShaderModuleDescriptor {
    private String label = "";
    private ShaderLanguage language = ShaderLanguage.WGSL;
    private String wgslSource;
    private String glslVertexSource;
    private String glslFragmentSource;
    private int[] spirvVertexWords;
    private int[] spirvFragmentWords;

    public static ShaderModuleDescriptor wgsl(String label, String source) {
        return new ShaderModuleDescriptor()
                .label(label)
                .language(ShaderLanguage.WGSL)
                .source(source);
    }

    public static ShaderModuleDescriptor glsl(String label, String vertexSource, String fragmentSource) {
        return new ShaderModuleDescriptor()
                .label(label)
                .language(ShaderLanguage.GLSL)
                .glsl(vertexSource, fragmentSource);
    }

    public static ShaderModuleDescriptor spirv(String label, int[] vertexWords, int[] fragmentWords) {
        return new ShaderModuleDescriptor()
                .label(label)
                .language(ShaderLanguage.SPIRV)
                .spirv(vertexWords, fragmentWords);
    }

    public String label() {
        return label;
    }

    public ShaderModuleDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    public ShaderLanguage language() {
        return language;
    }

    public ShaderModuleDescriptor language(ShaderLanguage language) {
        this.language = language != null ? language : ShaderLanguage.WGSL;
        return this;
    }

    public String source() {
        return wgslSource;
    }

    public ShaderModuleDescriptor source(String source) {
        if (source == null || source.length() == 0) {
            throw new FdxException("Shader source cannot be empty");
        }
        this.wgslSource = source;
        return this;
    }

    public ShaderModuleDescriptor wgsl(String source) {
        return source(source);
    }

    public String wgslSource() {
        return wgslSource;
    }

    public ShaderModuleDescriptor glsl(String vertexSource, String fragmentSource) {
        if (vertexSource == null || vertexSource.length() == 0) {
            throw new FdxException("GLSL vertex shader source cannot be empty");
        }
        if (fragmentSource == null || fragmentSource.length() == 0) {
            throw new FdxException("GLSL fragment shader source cannot be empty");
        }
        this.glslVertexSource = vertexSource;
        this.glslFragmentSource = fragmentSource;
        return this;
    }

    public String glslVertexSource() {
        return glslVertexSource;
    }

    public String glslFragmentSource() {
        return glslFragmentSource;
    }

    public ShaderModuleDescriptor spirv(int[] vertexWords, int[] fragmentWords) {
        if (vertexWords == null || vertexWords.length == 0) {
            throw new FdxException("SPIR-V vertex shader words cannot be empty");
        }
        if (fragmentWords == null || fragmentWords.length == 0) {
            throw new FdxException("SPIR-V fragment shader words cannot be empty");
        }
        this.spirvVertexWords = vertexWords.clone();
        this.spirvFragmentWords = fragmentWords.clone();
        return this;
    }

    public int[] spirvVertexWords() {
        return spirvVertexWords != null ? spirvVertexWords.clone() : null;
    }

    public int[] spirvFragmentWords() {
        return spirvFragmentWords != null ? spirvFragmentWords.clone() : null;
    }

    public boolean hasSource(ShaderLanguage language) {
        if (language == ShaderLanguage.WGSL) {
            return wgslSource != null && wgslSource.length() > 0;
        }
        if (language == ShaderLanguage.GLSL) {
            return glslVertexSource != null && glslVertexSource.length() > 0
                    && glslFragmentSource != null && glslFragmentSource.length() > 0;
        }
        if (language == ShaderLanguage.SPIRV) {
            return spirvVertexWords != null && spirvVertexWords.length > 0
                    && spirvFragmentWords != null && spirvFragmentWords.length > 0;
        }
        return false;
    }
}
