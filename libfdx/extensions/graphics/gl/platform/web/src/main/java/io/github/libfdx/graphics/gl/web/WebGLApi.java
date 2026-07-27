package io.github.libfdx.graphics.gl.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
import io.github.libfdx.graphics.TextureFilter;
import io.github.libfdx.graphics.TextureWrap;
import io.github.libfdx.graphics.VertexFormat;
import io.github.libfdx.graphics.gl.GLApi;
import io.github.libfdx.graphics.gl.GLShaderType;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.ArrayBufferView;
import org.teavm.jso.typedarrays.Uint8Array;
import org.teavm.jso.webgl.WebGLBuffer;
import org.teavm.jso.webgl.WebGLProgram;
import org.teavm.jso.webgl.WebGLRenderingContext;
import org.teavm.jso.webgl.WebGLShader;
import org.teavm.jso.webgl.WebGLTexture;
import org.teavm.jso.webgl.WebGLUniformLocation;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Exposes API access for web GL.
 *
 * @author xpenatan
 */
final class WebGLApi implements GLApi {
    private static final int ARRAY_BUFFER = 0x8892;
    private static final int ELEMENT_ARRAY_BUFFER = 0x8893;
    private static final int UNIFORM_BUFFER = 0x8A11;
    private static final int DYNAMIC_DRAW = 0x88E8;
    private static final int STATIC_DRAW = 0x88E4;
    private static final int VERTEX_SHADER = 0x8B31;
    private static final int FRAGMENT_SHADER = 0x8B30;
    private static final int COMPILE_STATUS = 0x8B81;
    private static final int LINK_STATUS = 0x8B82;
    private static final int TEXTURE_2D = 0x0DE1;
    private static final int FRAMEBUFFER = 0x8D40;
    private static final int RENDERBUFFER = 0x8D41;
    private static final int COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int DEPTH_ATTACHMENT = 0x8D00;
    private static final int FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int DEPTH_COMPONENT16 = 0x81A5;
    private static final int TEXTURE_MIN_FILTER = 0x2801;
    private static final int TEXTURE_MAG_FILTER = 0x2800;
    private static final int TEXTURE_WRAP_S = 0x2802;
    private static final int TEXTURE_WRAP_T = 0x2803;
    private static final int NEAREST = 0x2600;
    private static final int LINEAR = 0x2601;
    private static final int REPEAT = 0x2901;
    private static final int CLAMP_TO_EDGE = 0x812F;
    private static final int MIRRORED_REPEAT = 0x8370;
    private static final int RGBA = 0x1908;
    private static final int UNSIGNED_BYTE = 0x1401;
    private static final int UNSIGNED_SHORT = 0x1403;
    private static final int TEXTURE0 = 0x84C0;
    private static final int BLEND = 0x0BE2;
    private static final int ONE = 1;
    private static final int SRC_ALPHA = 0x0302;
    private static final int ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int FLOAT = 0x1406;
    private static final int COLOR_BUFFER_BIT = 0x4000;
    private static final int DEPTH_BUFFER_BIT = 0x0100;
    private static final int DEPTH_TEST = 0x0B71;
    private static final int SCISSOR_TEST = 0x0C11;
    private static final int LEQUAL = 0x0203;
    private static final int LINES = 0x0001;
    private static final int TRIANGLES = 0x0004;
    private static final int TRIANGLE_STRIP = 0x0005;
    private static final int UNPACK_ALIGNMENT = 0x0CF5;
    private static final int UNPACK_PREMULTIPLY_ALPHA_WEBGL = 0x9241;

    /**
     * Represents a handle map.
     *
     * @param <T> the value type
     *
     * @author xpenatan
     */
    @JSClass(transparent = true)
    static class HandleMap<T extends JSObject> implements JSObject {
        @JSBody(script = "return [undefined];")
        static native <T extends JSObject> HandleMap<T> create();

        @JSBody(params = { "key" }, script = "if (this[key] === undefined) return null; return this[key];")
        native T get(int key);

        @JSBody(params = { "key", "value" }, script = "this[key] = value;")
        native void put(int key, T value);

        @JSBody(params = { "value" }, script = "this.push(value); return this.length - 1;")
        native int add(T value);

        @JSBody(params = { "key" }, script = "var value = this[key]; delete this[key]; return value;")
        native T remove(int key);
    }

    private final WebGLRenderingContext gl;
    private final HandleMap<WebGLProgram> programs = HandleMap.create();
    private final HandleMap<WebGLShader> shaders = HandleMap.create();
    private final HandleMap<WebGLBuffer> buffers = HandleMap.create();
    private final HandleMap<WebGLTexture> textures = HandleMap.create();
    private final HandleMap<JSObject> framebuffers = HandleMap.create();
    private final HandleMap<JSObject> renderbuffers = HandleMap.create();
    private final HandleMap<HandleMap<WebGLUniformLocation>> uniforms = HandleMap.create();
    private final Map<Integer, GLShaderType> shaderTypes = new HashMap<Integer, GLShaderType>();
    private int currentProgram;

    WebGLApi(WebGLRenderingContext gl) {
        this.gl = gl;
        this.gl.pixelStorei(UNPACK_ALIGNMENT, 1);
        this.gl.pixelStorei(UNPACK_PREMULTIPLY_ALPHA_WEBGL, 0);
    }

    /**
     * Returns the create program.
     *
     * @return the created value
     */
    @Override
    public int createProgram() {
        return programs.add(gl.createProgram());
    }

    /**
     * Creates a shader.
     *
     * @param type the expected Java type
     * @return the created value
     */
    @Override
    public int createShader(GLShaderType type) {
        int nativeType;
        if (type == GLShaderType.VERTEX) {
            nativeType = VERTEX_SHADER;
        } else if (type == GLShaderType.FRAGMENT) {
            nativeType = FRAGMENT_SHADER;
        } else {
            throw new FdxException("Unsupported WebGL shader type: " + type);
        }
        int shader = shaders.add(gl.createShader(nativeType));
        shaderTypes.put(shader, type);
        return shader;
    }

    /**
     * Runs the shader source step.
     *
     * @param shader the shader
     * @param source the source value
     */
    @Override
    public void shaderSource(int shader, String source) {
        gl.shaderSource(shaders.get(shader), toGlesSource(shaderTypes.get(shader), source));
    }

    /**
     * Runs the compile shader step.
     *
     * @param shader the shader
     */
    @Override
    public void compileShader(int shader) {
        gl.compileShader(shaders.get(shader));
    }

    /**
     * Runs the shader compile status step.
     *
     * @param shader the shader
     * @return true if shader compile status succeeds or is active; false otherwise
     */
    @Override
    public boolean shaderCompileStatus(int shader) {
        return gl.getShaderParameterb(shaders.get(shader), COMPILE_STATUS);
    }

    /**
     * Runs the shader info log step.
     *
     * @param shader the shader
     * @return the shader info log
     */
    @Override
    public String shaderInfoLog(int shader) {
        return gl.getShaderInfoLog(shaders.get(shader));
    }

    /**
     * Runs the delete shader step.
     *
     * @param shader the shader
     */
    @Override
    public void deleteShader(int shader) {
        shaderTypes.remove(shader);
        gl.deleteShader(shaders.remove(shader));
    }

    /**
     * Runs the attach shader step.
     *
     * @param program the program
     * @param shader the shader
     */
    @Override
    public void attachShader(int program, int shader) {
        gl.attachShader(programs.get(program), shaders.get(shader));
    }

    /**
     * Runs the link program step.
     *
     * @param program the program
     */
    @Override
    public void linkProgram(int program) {
        gl.linkProgram(programs.get(program));
    }

    /**
     * Runs the program link status step.
     *
     * @param program the program
     * @return true if program link status succeeds or is active; false otherwise
     */
    @Override
    public boolean programLinkStatus(int program) {
        return gl.getProgramParameterb(programs.get(program), LINK_STATUS);
    }

    /**
     * Runs the program info log step.
     *
     * @param program the program
     * @return the program info log
     */
    @Override
    public String programInfoLog(int program) {
        return gl.getProgramInfoLog(programs.get(program));
    }

    /**
     * Runs the delete program step.
     *
     * @param program the program
     */
    @Override
    public void deleteProgram(int program) {
        uniforms.remove(program);
        gl.deleteProgram(programs.remove(program));
    }

    /**
     * Runs the use program step.
     *
     * @param program the program
     */
    @Override
    public void useProgram(int program) {
        currentProgram = program;
        gl.useProgram(programs.get(program));
    }

    /**
     * Returns the gen vertex array.
     *
     * @return the gen vertex array
     */
    @Override
    public int genVertexArray() {
        return 1;
    }

    /**
     * Runs the bind vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void bindVertexArray(int vertexArray) {
    }

    /**
     * Runs the delete vertex array step.
     *
     * @param vertexArray the vertex array
     */
    @Override
    public void deleteVertexArray(int vertexArray) {
    }

    /**
     * Returns the gen buffer.
     *
     * @return the gen buffer
     */
    @Override
    public int genBuffer() {
        return buffers.add(gl.createBuffer());
    }

    /**
     * Runs the bind array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindArrayBuffer(int buffer) {
        gl.bindBuffer(ARRAY_BUFFER, buffers.get(buffer));
    }

    /**
     * Runs the bind element array buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindElementArrayBuffer(int buffer) {
        gl.bindBuffer(ELEMENT_ARRAY_BUFFER, buffers.get(buffer));
    }

    /**
     * Runs the buffer data step.
     *
     * @param size the size
     */
    @Override
    public void bufferData(int size) {
        gl.bufferData(ARRAY_BUFFER, size, DYNAMIC_DRAW);
    }

    /**
     * Runs the element buffer data step.
     *
     * @param size the size
     */
    @Override
    public void elementBufferData(int size) {
        gl.bufferData(ELEMENT_ARRAY_BUFFER, size, STATIC_DRAW);
    }

    /**
     * Runs the buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void bufferSubData(ByteBuffer data) {
        gl.bufferSubData(ARRAY_BUFFER, 0, activeBytes(data));
    }

    /**
     * Runs the bind uniform buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBuffer(int buffer) {
        gl.bindBuffer(UNIFORM_BUFFER, buffers.get(buffer));
    }

    /**
     * Runs the uniform buffer data step.
     *
     * @param size the size
     */
    @Override
    public void uniformBufferData(int size) {
        gl.bufferData(UNIFORM_BUFFER, size, DYNAMIC_DRAW);
    }

    /**
     * Runs the uniform buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void uniformBufferSubData(ByteBuffer data) {
        gl.bufferSubData(UNIFORM_BUFFER, 0, activeBytes(data));
    }

    /**
     * Runs the bind uniform buffer base step.
     *
     * @param binding the binding
     * @param buffer the buffer
     */
    @Override
    public void bindUniformBufferBase(int binding, int buffer) {
        bindBufferBase(gl, UNIFORM_BUFFER, binding, buffers.get(buffer));
    }

    /**
     * Runs the element buffer sub data step.
     *
     * @param data the data
     */
    @Override
    public void elementBufferSubData(ByteBuffer data) {
        gl.bufferSubData(ELEMENT_ARRAY_BUFFER, 0, activeBytes(data));
    }

    /**
     * Runs the delete buffer step.
     *
     * @param buffer the buffer
     */
    @Override
    public void deleteBuffer(int buffer) {
        gl.deleteBuffer(buffers.remove(buffer));
    }

    /**
     * Returns the gen texture.
     *
     * @return the gen texture
     */
    @Override
    public int genTexture() {
        return textures.add(gl.createTexture());
    }

    /**
     * Runs the bind texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void bindTexture2D(int texture) {
        gl.bindTexture(TEXTURE_2D, textures.get(texture));
    }

    /**
     * Runs the tex image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    @Override
    public void texImage2D(int width, int height, ByteBuffer data) {
        gl.texParameterf(TEXTURE_2D, TEXTURE_MIN_FILTER, LINEAR);
        gl.texParameterf(TEXTURE_2D, TEXTURE_MAG_FILTER, LINEAR);
        gl.texParameterf(TEXTURE_2D, TEXTURE_WRAP_S, CLAMP_TO_EDGE);
        gl.texParameterf(TEXTURE_2D, TEXTURE_WRAP_T, CLAMP_TO_EDGE);
        gl.texImage2D(TEXTURE_2D, 0, RGBA, width, height, 0, RGBA, UNSIGNED_BYTE,
                data != null ? activeBytes(data) : (ArrayBufferView) null);
    }

    /**
     * Runs the tex sub image2 d step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param data the data
     */
    @Override
    public void texSubImage2D(int width, int height, ByteBuffer data) {
        gl.texSubImage2D(TEXTURE_2D, 0, 0, 0, width, height, RGBA, UNSIGNED_BYTE,
                activeBytes(data));
    }

    /**
     * Runs the texture wrap2 d step.
     *
     * @param wrapS the horizontal wrap mode
     * @param wrapT the vertical wrap mode
     */
    @Override
    public void textureWrap2D(TextureWrap wrapS, TextureWrap wrapT) {
        gl.texParameterf(TEXTURE_2D, TEXTURE_WRAP_S, toNative(wrapS));
        gl.texParameterf(TEXTURE_2D, TEXTURE_WRAP_T, toNative(wrapT));
    }

    /**
     * Runs the texture filter2 d step.
     *
     * @param filter the sampled texture filter
     */
    @Override
    public void textureFilter2D(TextureFilter filter) {
        int nativeFilter = toNative(filter);
        gl.texParameterf(TEXTURE_2D, TEXTURE_MIN_FILTER, nativeFilter);
        gl.texParameterf(TEXTURE_2D, TEXTURE_MAG_FILTER, nativeFilter);
    }

    /**
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        gl.deleteTexture(textures.remove(texture));
    }

    /**
     * Returns the gen framebuffer.
     *
     * @return the gen framebuffer
     */
    @Override
    public int genFramebuffer() {
        return framebuffers.add(createFramebuffer(gl));
    }

    /**
     * Runs the bind framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void bindFramebuffer(int framebuffer) {
        bindFramebuffer(gl, FRAMEBUFFER, framebuffer == 0 ? null : framebuffers.get(framebuffer));
    }

    /**
     * Runs the framebuffer texture2 d step.
     *
     * @param texture the texture
     */
    @Override
    public void framebufferTexture2D(int texture) {
        framebufferTexture2D(gl, FRAMEBUFFER, COLOR_ATTACHMENT0, TEXTURE_2D, textures.get(texture), 0);
    }

    /**
     * Returns whether the currently bound framebuffer is complete.
     *
     * @return true if complete
     */
    @Override
    public boolean framebufferComplete() {
        return checkFramebufferStatus(gl, FRAMEBUFFER) == FRAMEBUFFER_COMPLETE;
    }

    /**
     * Runs the delete framebuffer step.
     *
     * @param framebuffer the framebuffer
     */
    @Override
    public void deleteFramebuffer(int framebuffer) {
        if (framebuffer != 0) {
            deleteFramebuffer(gl, framebuffers.remove(framebuffer));
        }
    }

    /**
     * Returns the gen renderbuffer.
     *
     * @return the gen renderbuffer
     */
    @Override
    public int genRenderbuffer() {
        return renderbuffers.add(createRenderbuffer(gl));
    }

    /**
     * Runs the bind renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void bindRenderbuffer(int renderbuffer) {
        bindRenderbuffer(gl, RENDERBUFFER, renderbuffer == 0 ? null : renderbuffers.get(renderbuffer));
    }

    /**
     * Runs the renderbuffer depth storage step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void renderbufferStorageDepth(int width, int height) {
        renderbufferStorage(gl, RENDERBUFFER, DEPTH_COMPONENT16, width, height);
    }

    /**
     * Runs the framebuffer depth renderbuffer attachment step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void framebufferRenderbufferDepth(int renderbuffer) {
        framebufferRenderbuffer(gl, FRAMEBUFFER, DEPTH_ATTACHMENT, RENDERBUFFER, renderbuffers.get(renderbuffer));
    }

    /**
     * Runs the delete renderbuffer step.
     *
     * @param renderbuffer the renderbuffer
     */
    @Override
    public void deleteRenderbuffer(int renderbuffer) {
        if (renderbuffer != 0) {
            deleteRenderbuffer(gl, renderbuffers.remove(renderbuffer));
        }
    }

    /**
     * Runs the active texture step.
     *
     * @param slot the slot
     */
    @Override
    public void activeTexture(int slot) {
        gl.activeTexture(TEXTURE0 + slot);
    }

    /**
     * Runs the uniform location step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform location
     */
    @Override
    public int uniformLocation(int program, String name) {
        WebGLUniformLocation location = gl.getUniformLocation(programs.get(program), name);
        if (location == null) {
            return -1;
        }
        HandleMap<WebGLUniformLocation> programUniforms = uniforms.get(program);
        if (programUniforms == null) {
            programUniforms = HandleMap.create();
            uniforms.put(program, programUniforms);
        }
        return programUniforms.add(location);
    }

    /**
     * Runs the uniform1i step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1i(int location, int value) {
        if (location < 0 || currentProgram == 0) {
            return;
        }
        HandleMap<WebGLUniformLocation> programUniforms = uniforms.get(currentProgram);
        if (programUniforms != null) {
            gl.uniform1i(programUniforms.get(location), value);
        }
    }

    /**
     * Runs the uniform block index step.
     *
     * @param program the program
     * @param name the name
     * @return the uniform block index, or -1 when absent
     */
    @Override
    public int uniformBlockIndex(int program, String name) {
        return uniformBlockIndex(gl, programs.get(program), name);
    }

    /**
     * Runs the uniform block binding step.
     *
     * @param program the program
     * @param blockIndex the block index
     * @param binding the binding
     */
    @Override
    public void uniformBlockBinding(int program, int blockIndex, int binding) {
        uniformBlockBinding(gl, programs.get(program), blockIndex, binding);
    }

    /**
     * Runs the uniform1f step.
     *
     * @param location the location
     * @param value the value
     */
    @Override
    public void uniform1f(int location, float value) {
        if (location < 0 || currentProgram == 0) {
            return;
        }
        HandleMap<WebGLUniformLocation> programUniforms = uniforms.get(currentProgram);
        if (programUniforms != null) {
            gl.uniform1f(programUniforms.get(location), value);
        }
    }

    /**
     * Runs the uniform3f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     */
    @Override
    public void uniform3f(int location, float x, float y, float z) {
        if (location < 0 || currentProgram == 0) {
            return;
        }
        HandleMap<WebGLUniformLocation> programUniforms = uniforms.get(currentProgram);
        if (programUniforms != null) {
            gl.uniform3f(programUniforms.get(location), x, y, z);
        }
    }

    /**
     * Runs the uniform4f step.
     *
     * @param location the location
     * @param x the x coordinate
     * @param y the y coordinate
     * @param z the z coordinate
     * @param w the w
     */
    @Override
    public void uniform4f(int location, float x, float y, float z, float w) {
        if (location < 0 || currentProgram == 0) {
            return;
        }
        HandleMap<WebGLUniformLocation> programUniforms = uniforms.get(currentProgram);
        if (programUniforms != null) {
            gl.uniform4f(programUniforms.get(location), x, y, z, w);
        }
    }

    /**
     * Runs the uniform matrix4fv step.
     *
     * @param location the location
     * @param transpose the transpose
     * @param values the values
     */
    @Override
    public void uniformMatrix4fv(int location, boolean transpose, float[] values) {
        if (location < 0 || currentProgram == 0) {
            return;
        }
        HandleMap<WebGLUniformLocation> programUniforms = uniforms.get(currentProgram);
        if (programUniforms != null) {
            uniformMatrix4fv(gl, programUniforms.get(location), transpose, values);
        }
    }

    /**
     * Runs the enable alpha blending step.
     */
    @Override
    public void enableAlphaBlending() {
        gl.enable(BLEND);
        blendFuncSeparate(gl, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
    }

    /**
     * Runs the enable depth test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableDepthTest(boolean enabled) {
        if (enabled) {
            gl.enable(DEPTH_TEST);
        } else {
            gl.disable(DEPTH_TEST);
        }
    }

    /**
     * Runs the depth mask step.
     *
     * @param enabled the enabled
     */
    @Override
    public void depthMask(boolean enabled) {
        gl.depthMask(enabled);
    }

    /**
     * Runs the depth func less equal step.
     */
    @Override
    public void depthFuncLessEqual() {
        gl.depthFunc(LEQUAL);
    }

    /**
     * Runs the enable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void enableVertexAttribArray(int index) {
        gl.enableVertexAttribArray(index);
    }

    /**
     * Runs the disable vertex attrib array step.
     *
     * @param index the index
     */
    @Override
    public void disableVertexAttribArray(int index) {
        gl.disableVertexAttribArray(index);
    }

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param size the size
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, int size, int stride, int offset) {
        gl.vertexAttribPointer(index, size, FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib pointer step.
     *
     * @param index the index
     * @param format the format
     * @param stride the stride
     * @param offset the offset
     */
    @Override
    public void vertexAttribPointer(int index, VertexFormat format, int stride, int offset) {
        if (format == VertexFormat.UNORM8X4) {
            gl.vertexAttribPointer(index, format.componentCount(), UNSIGNED_BYTE, true, stride, offset);
            return;
        }
        gl.vertexAttribPointer(index, format.componentCount(), FLOAT, false, stride, offset);
    }

    /**
     * Runs the vertex attrib divisor step.
     *
     * @param index the index
     * @param divisor the divisor
     */
    @Override
    public void vertexAttribDivisor(int index, int divisor) {
        vertexAttribDivisor(gl, index, divisor);
    }

    /**
     * Runs the viewport step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void viewport(int x, int y, int width, int height) {
        gl.viewport(x, y, width, height);
    }

    /**
     * Runs the enable scissor test step.
     *
     * @param enabled the enabled
     */
    @Override
    public void enableScissorTest(boolean enabled) {
        if (enabled) {
            gl.enable(SCISSOR_TEST);
        } else {
            gl.disable(SCISSOR_TEST);
        }
    }

    /**
     * Runs the scissor step.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void scissor(int x, int y, int width, int height) {
        gl.scissor(x, y, width, height);
    }

    /**
     * Runs the clear color step.
     *
     * @param red the red
     * @param green the green
     * @param blue the blue
     * @param alpha the alpha
     */
    @Override
    public void clearColor(float red, float green, float blue, float alpha) {
        gl.clearColor(red, green, blue, alpha);
    }

    /**
     * Runs the clear color buffer step.
     */
    @Override
    public void clearColorBuffer() {
        gl.clear(COLOR_BUFFER_BIT);
    }

    /**
     * Runs the clear depth step.
     *
     * @param depth the depth
     */
    @Override
    public void clearDepth(float depth) {
        gl.clearDepth(depth);
    }

    /**
     * Runs the clear depth buffer step.
     */
    @Override
    public void clearDepthBuffer() {
        gl.clear(DEPTH_BUFFER_BIT);
    }

    /**
     * Runs the read pixels RGBA8 step.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return the read pixels RGBA8
     */
    @Override
    public ByteBuffer readPixelsRgba8(int width, int height) {
        if (width <= 0 || height <= 0) {
            return ByteBuffer.allocateDirect(0);
        }
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        readPixelsRgba8(gl, width, height, Uint8Array.fromJavaBuffer(pixels));
        pixels.position(0);
        return pixels;
    }

    /**
     * Draws arrays.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     */
    @Override
    public void drawArrays(PrimitiveTopology topology, int firstVertex, int vertexCount) {
        gl.drawArrays(toNative(topology), firstVertex, vertexCount);
    }

    /**
     * Draws arrays instanced.
     *
     * @param topology the topology
     * @param firstVertex the first vertex
     * @param vertexCount the vertex count
     * @param instanceCount the instance count
     */
    @Override
    public void drawArraysInstanced(PrimitiveTopology topology, int firstVertex, int vertexCount, int instanceCount) {
        drawArraysInstanced(gl, toNative(topology), firstVertex, vertexCount, instanceCount);
    }

    /**
     * Draws elements.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     */
    @Override
    public void drawElements(PrimitiveTopology topology, int indexCount, int offsetBytes) {
        gl.drawElements(toNative(topology), indexCount, UNSIGNED_SHORT, offsetBytes);
    }

    /**
     * Draws elements instanced.
     *
     * @param topology the topology
     * @param indexCount the index count
     * @param offsetBytes the offset bytes
     * @param instanceCount the instance count
     */
    @Override
    public void drawElementsInstanced(PrimitiveTopology topology, int indexCount, int offsetBytes, int instanceCount) {
        drawElementsInstanced(gl, toNative(topology), indexCount, UNSIGNED_SHORT, offsetBytes, instanceCount);
    }

    private int toNative(PrimitiveTopology topology) {
        if (topology == PrimitiveTopology.LINE_LIST) {
            return LINES;
        }
        if (topology == PrimitiveTopology.TRIANGLE_STRIP) {
            return TRIANGLE_STRIP;
        }
        return TRIANGLES;
    }

    private int toNative(TextureWrap wrap) {
        if (wrap == TextureWrap.REPEAT) {
            return REPEAT;
        }
        if (wrap == TextureWrap.MIRRORED_REPEAT) {
            return MIRRORED_REPEAT;
        }
        return CLAMP_TO_EDGE;
    }

    private int toNative(TextureFilter filter) {
        return filter == TextureFilter.NEAREST ? NEAREST : LINEAR;
    }

    private Uint8Array activeBytes(ByteBuffer data) {
        Uint8Array bytes = Uint8Array.fromJavaBuffer(data);
        int position = data.position();
        int remaining = data.remaining();
        if (position == 0 && remaining == bytes.getByteLength()) {
            return bytes;
        }
        return Uint8Array.create(bytes.getBuffer(), bytes.getByteOffset() + position, remaining);
    }

    private String toGlesSource(GLShaderType type, String source) {
        String actualSource = source != null ? source : "";
        if (actualSource.startsWith("#version 330 core")) {
            actualSource = "#version 300 es" + actualSource.substring("#version 330 core".length());
        } else if (actualSource.startsWith("#version 330")) {
            actualSource = "#version 300 es" + actualSource.substring("#version 330".length());
        } else if (!actualSource.startsWith("#version 300 es")) {
            actualSource = "#version 300 es\n" + actualSource;
        }
        if (type == GLShaderType.FRAGMENT && actualSource.indexOf("precision ") < 0) {
            int lineEnd = actualSource.indexOf('\n');
            if (lineEnd >= 0) {
                actualSource = actualSource.substring(0, lineEnd + 1)
                        + "precision highp float;\n"
                        + actualSource.substring(lineEnd + 1);
            } else {
                actualSource = actualSource + "\nprecision highp float;\n";
            }
        }
        return actualSource;
    }

    @JSBody(params = { "gl", "mode", "first", "count", "instances" }, script =
            "if (gl.drawArraysInstanced) {\n" +
            "  gl.drawArraysInstanced(mode, first, count, instances);\n" +
            "} else {\n" +
            "  for (var i = 0; i < instances; i++) gl.drawArrays(mode, first, count);\n" +
            "}")
    private static native void drawArraysInstanced(WebGLRenderingContext gl, int mode, int first, int count,
            int instances);

    @JSBody(params = { "gl", "mode", "count", "type", "offset", "instances" }, script =
            "if (gl.drawElementsInstanced) {\n" +
            "  gl.drawElementsInstanced(mode, count, type, offset, instances);\n" +
            "} else {\n" +
            "  for (var i = 0; i < instances; i++) gl.drawElements(mode, count, type, offset);\n" +
            "}")
    private static native void drawElementsInstanced(WebGLRenderingContext gl, int mode, int count, int type,
            int offset, int instances);

    @JSBody(params = { "gl", "index", "divisor" }, script =
            "if (gl.vertexAttribDivisor) {\n" +
            "  gl.vertexAttribDivisor(index, divisor);\n" +
            "} else {\n" +
            "  var ext = gl.getExtension('ANGLE_instanced_arrays');\n" +
            "  if (ext && ext.vertexAttribDivisorANGLE) {\n" +
            "    ext.vertexAttribDivisorANGLE(index, divisor);\n" +
            "  } else if (divisor !== 0) {\n" +
            "    throw 'Instanced vertex attributes are not supported';\n" +
            "  }\n" +
            "}")
    private static native void vertexAttribDivisor(WebGLRenderingContext gl, int index, int divisor);

    @JSBody(params = { "gl", "location", "transpose", "values" }, script =
            "gl.uniformMatrix4fv(location, transpose, values);")
    private static native void uniformMatrix4fv(WebGLRenderingContext gl, WebGLUniformLocation location,
            boolean transpose, float[] values);

    @JSBody(params = { "gl", "target", "binding", "buffer" }, script =
            "if (!gl.bindBufferBase) throw 'Uniform buffers are not supported';\n" +
            "gl.bindBufferBase(target, binding, buffer);")
    private static native void bindBufferBase(WebGLRenderingContext gl, int target, int binding, WebGLBuffer buffer);

    @JSBody(params = { "gl", "program", "name" }, script =
            "if (!gl.getUniformBlockIndex) return -1;\n" +
            "var index = gl.getUniformBlockIndex(program, name);\n" +
            "return index === 0xFFFFFFFF ? -1 : index;")
    private static native int uniformBlockIndex(WebGLRenderingContext gl, WebGLProgram program, String name);

    @JSBody(params = { "gl", "program", "blockIndex", "binding" }, script =
            "if (!gl.uniformBlockBinding) throw 'Uniform buffers are not supported';\n" +
            "gl.uniformBlockBinding(program, blockIndex, binding);")
    private static native void uniformBlockBinding(WebGLRenderingContext gl, WebGLProgram program, int blockIndex,
            int binding);

    @JSBody(params = { "gl" }, script = "return gl.createFramebuffer();")
    private static native JSObject createFramebuffer(WebGLRenderingContext gl);

    @JSBody(params = { "gl", "target", "framebuffer" }, script = "gl.bindFramebuffer(target, framebuffer);")
    private static native void bindFramebuffer(WebGLRenderingContext gl, int target, JSObject framebuffer);

    @JSBody(params = { "gl", "target", "attachment", "textarget", "texture", "level" }, script =
            "gl.framebufferTexture2D(target, attachment, textarget, texture, level);")
    private static native void framebufferTexture2D(WebGLRenderingContext gl, int target, int attachment,
            int textarget, WebGLTexture texture, int level);

    @JSBody(params = { "gl", "target" }, script = "return gl.checkFramebufferStatus(target);")
    private static native int checkFramebufferStatus(WebGLRenderingContext gl, int target);

    @JSBody(params = { "gl", "framebuffer" }, script = "gl.deleteFramebuffer(framebuffer);")
    private static native void deleteFramebuffer(WebGLRenderingContext gl, JSObject framebuffer);

    @JSBody(params = { "gl", "sourceRgb", "destinationRgb", "sourceAlpha", "destinationAlpha" }, script =
            "gl.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);")
    private static native void blendFuncSeparate(WebGLRenderingContext gl, int sourceRgb, int destinationRgb,
            int sourceAlpha, int destinationAlpha);

    @JSBody(params = { "gl" }, script = "return gl.createRenderbuffer();")
    private static native JSObject createRenderbuffer(WebGLRenderingContext gl);

    @JSBody(params = { "gl", "target", "renderbuffer" }, script = "gl.bindRenderbuffer(target, renderbuffer);")
    private static native void bindRenderbuffer(WebGLRenderingContext gl, int target, JSObject renderbuffer);

    @JSBody(params = { "gl", "target", "internalformat", "width", "height" }, script =
            "gl.renderbufferStorage(target, internalformat, width, height);")
    private static native void renderbufferStorage(WebGLRenderingContext gl, int target, int internalformat,
            int width, int height);

    @JSBody(params = { "gl", "target", "attachment", "renderbuffertarget", "renderbuffer" }, script =
            "gl.framebufferRenderbuffer(target, attachment, renderbuffertarget, renderbuffer);")
    private static native void framebufferRenderbuffer(WebGLRenderingContext gl, int target, int attachment,
            int renderbuffertarget, JSObject renderbuffer);

    @JSBody(params = { "gl", "renderbuffer" }, script = "gl.deleteRenderbuffer(renderbuffer);")
    private static native void deleteRenderbuffer(WebGLRenderingContext gl, JSObject renderbuffer);

    @JSBody(params = { "gl", "width", "height", "target" }, script =
            "gl.readPixels(0, 0, width, height, 0x1908, 0x1401, target);")
    private static native void readPixelsRgba8(WebGLRenderingContext gl, int width, int height,
            ArrayBufferView target);
}
