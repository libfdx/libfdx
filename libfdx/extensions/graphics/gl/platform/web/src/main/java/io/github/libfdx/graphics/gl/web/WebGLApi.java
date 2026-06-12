package io.github.libfdx.graphics.gl.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.PrimitiveTopology;
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
    private static final int DYNAMIC_DRAW = 0x88E8;
    private static final int STATIC_DRAW = 0x88E4;
    private static final int VERTEX_SHADER = 0x8B31;
    private static final int FRAGMENT_SHADER = 0x8B30;
    private static final int COMPILE_STATUS = 0x8B81;
    private static final int LINK_STATUS = 0x8B82;
    private static final int TEXTURE_2D = 0x0DE1;
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
     * Runs the delete texture step.
     *
     * @param texture the texture
     */
    @Override
    public void deleteTexture(int texture) {
        gl.deleteTexture(textures.remove(texture));
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
        gl.blendFunc(SRC_ALPHA, ONE_MINUS_SRC_ALPHA);
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
}
