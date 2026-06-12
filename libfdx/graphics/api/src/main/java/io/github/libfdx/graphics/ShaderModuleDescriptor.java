package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Describes the values used to create or identify a shader module.
 *
 * @author xpenatan
 */
public final class ShaderModuleDescriptor {
    private String label = "";
    private ShaderLanguage language = ShaderLanguage.WGSL;
    private String wgslSource;
    private String glslVertexSource;
    private String glslFragmentSource;
    private int[] spirvVertexWords;
    private int[] spirvFragmentWords;

    /**
     * Creates a shader module descriptor.
     *
     * @param label the debug label
     * @param source the source value
     * @return a new shader module descriptor
     */
    public static ShaderModuleDescriptor wgsl(String label, String source) {
        return new ShaderModuleDescriptor()
                .label(label)
                .language(ShaderLanguage.WGSL)
                .source(source);
    }

    /**
     * Creates a shader module descriptor.
     *
     * @param label the debug label
     * @param vertexSource the vertex source
     * @param fragmentSource the fragment source
     * @return a new shader module descriptor
     */
    public static ShaderModuleDescriptor glsl(String label, String vertexSource, String fragmentSource) {
        return new ShaderModuleDescriptor()
                .label(label)
                .language(ShaderLanguage.GLSL)
                .glsl(vertexSource, fragmentSource);
    }

    /**
     * Creates a shader module descriptor.
     *
     * @param label the debug label
     * @param vertexWords the vertex words
     * @param fragmentWords the fragment words
     * @return a new shader module descriptor
     */
    public static ShaderModuleDescriptor spirv(String label, int[] vertexWords, int[] fragmentWords) {
        return new ShaderModuleDescriptor()
                .label(label)
                .language(ShaderLanguage.SPIRV)
                .spirv(vertexWords, fragmentWords);
    }

    /**
     * Returns the label.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Sets the label and returns this shader module descriptor.
     *
     * @param label the debug label
     * @return this shader module descriptor for chaining
     */
    public ShaderModuleDescriptor label(String label) {
        this.label = label != null ? label : "";
        return this;
    }

    /**
     * Returns the language.
     *
     * @return the language
     */
    public ShaderLanguage language() {
        return language;
    }

    /**
     * Sets the language and returns this shader module descriptor.
     *
     * @param language the language
     * @return this shader module descriptor for chaining
     */
    public ShaderModuleDescriptor language(ShaderLanguage language) {
        this.language = language != null ? language : ShaderLanguage.WGSL;
        return this;
    }

    /**
     * Returns the source.
     *
     * @return the source
     */
    public String source() {
        return wgslSource;
    }

    /**
     * Sets the source and returns this shader module descriptor.
     *
     * @param source the source value
     * @return this shader module descriptor for chaining
     */
    public ShaderModuleDescriptor source(String source) {
        if (source == null || source.length() == 0) {
            throw new FdxException("Shader source cannot be empty");
        }
        this.wgslSource = source;
        return this;
    }

    /**
     * Sets the wgsl and returns this shader module descriptor.
     *
     * @param source the source value
     * @return this shader module descriptor for chaining
     */
    public ShaderModuleDescriptor wgsl(String source) {
        return source(source);
    }

    /**
     * Returns the wgsl source.
     *
     * @return the wgsl source
     */
    public String wgslSource() {
        return wgslSource;
    }

    /**
     * Sets the glsl and returns this shader module descriptor.
     *
     * @param vertexSource the vertex source
     * @param fragmentSource the fragment source
     * @return this shader module descriptor for chaining
     */
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

    /**
     * Returns the glsl vertex source.
     *
     * @return the glsl vertex source
     */
    public String glslVertexSource() {
        return glslVertexSource;
    }

    /**
     * Returns the glsl fragment source.
     *
     * @return the glsl fragment source
     */
    public String glslFragmentSource() {
        return glslFragmentSource;
    }

    /**
     * Sets the SPIR-V and returns this shader module descriptor.
     *
     * @param vertexWords the vertex words
     * @param fragmentWords the fragment words
     * @return this shader module descriptor for chaining
     */
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

    /**
     * Returns the SPIR-V vertex words.
     *
     * @return the SPIR-V vertex words
     */
    public int[] spirvVertexWords() {
        return spirvVertexWords != null ? spirvVertexWords.clone() : null;
    }

    /**
     * Returns the SPIR-V fragment words.
     *
     * @return the SPIR-V fragment words
     */
    public int[] spirvFragmentWords() {
        return spirvFragmentWords != null ? spirvFragmentWords.clone() : null;
    }

    /**
     * Returns whether this instance has source.
     *
     * @param language the language
     * @return true if this instance has source; false otherwise
     */
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
