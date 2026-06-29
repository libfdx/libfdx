package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;

/**
 * Describes the values used to create or identify a shader module.
 *
 * @author xpenatan
 */
public final class ShaderModuleDescriptor {
    public static final String DEFAULT_VERTEX_ENTRY_POINT = "vertexMain";
    public static final String DEFAULT_FRAGMENT_ENTRY_POINT = "fragmentMain";

    private String label = "";
    private ShaderLanguage language = ShaderLanguage.WGSL;
    private String vertexEntryPoint = DEFAULT_VERTEX_ENTRY_POINT;
    private String fragmentEntryPoint = DEFAULT_FRAGMENT_ENTRY_POINT;
    private String wgslSource;
    private String glslVertexSource;
    private String glslFragmentSource;
    private int[] spirvVertexWords;
    private int[] spirvFragmentWords;
    private String mslSource;

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
                .generatedLanguage(ShaderLanguage.WGSL)
                .source(source);
    }

    static ShaderModuleDescriptor generatedGlsl(String label, String vertexSource, String fragmentSource) {
        return new ShaderModuleDescriptor()
                .label(label)
                .generatedLanguage(ShaderLanguage.GLSL)
                .generatedGlsl(vertexSource, fragmentSource);
    }

    static ShaderModuleDescriptor generatedSpirv(String label, int[] vertexWords, int[] fragmentWords) {
        return new ShaderModuleDescriptor()
                .label(label)
                .generatedLanguage(ShaderLanguage.SPIRV)
                .generatedSpirv(vertexWords, fragmentWords);
    }

    static ShaderModuleDescriptor generatedMsl(String label, String source) {
        return new ShaderModuleDescriptor()
                .label(label)
                .generatedLanguage(ShaderLanguage.MSL)
                .generatedMsl(source);
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

    ShaderModuleDescriptor generatedLanguage(ShaderLanguage language) {
        this.language = language != null ? language : ShaderLanguage.WGSL;
        return this;
    }

    /**
     * Returns the vertex entry point.
     *
     * @return the vertex entry point
     */
    public String vertexEntryPoint() {
        return vertexEntryPoint;
    }

    /**
     * Returns the fragment entry point.
     *
     * @return the fragment entry point
     */
    public String fragmentEntryPoint() {
        return fragmentEntryPoint;
    }

    /**
     * Sets the entry points and returns this shader module descriptor.
     *
     * @param vertexEntryPoint the vertex entry point
     * @param fragmentEntryPoint the fragment entry point
     * @return this shader module descriptor for chaining
     */
    public ShaderModuleDescriptor entryPoints(String vertexEntryPoint, String fragmentEntryPoint) {
        this.vertexEntryPoint = requireEntryPoint(vertexEntryPoint, "vertex");
        this.fragmentEntryPoint = requireEntryPoint(fragmentEntryPoint, "fragment");
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

    ShaderModuleDescriptor generatedGlsl(String vertexSource, String fragmentSource) {
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

    ShaderModuleDescriptor generatedSpirv(int[] vertexWords, int[] fragmentWords) {
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

    ShaderModuleDescriptor generatedMsl(String source) {
        if (source == null || source.length() == 0) {
            throw new FdxException("MSL shader source cannot be empty");
        }
        this.mslSource = source;
        return this;
    }

    /**
     * Returns the MSL source.
     *
     * @return the MSL source
     */
    public String mslSource() {
        return mslSource;
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
        if (language == ShaderLanguage.MSL) {
            return mslSource != null && mslSource.length() > 0;
        }
        return false;
    }

    private static String requireEntryPoint(String value, String stage) {
        if (value == null || value.length() == 0) {
            throw new FdxException("Shader " + stage + " entry point cannot be empty");
        }
        return value;
    }
}
