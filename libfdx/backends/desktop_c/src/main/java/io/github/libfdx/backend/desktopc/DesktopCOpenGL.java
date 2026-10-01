package io.github.libfdx.backend.desktopc;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import java.nio.ByteBuffer;

/**
 * Represents a desktop C open GL.
 *
 * @author xpenatan
 */
@Include("libfdx_desktop_bridge.h")
final class DesktopCOpenGL {
    static boolean enableParallelShaderCompilation(int workers) {

        return native_enableParallelShaderCompilation(workers);
    }

    @Import(name = "fdx_c_gl_parallel_shader_compilation")
    private static native boolean native_enableParallelShaderCompilation(int workers);

    static final int FALSE = 0;
    static final int COLOR_BUFFER_BIT = 0x00004000;
    static final int DEPTH_BUFFER_BIT = 0x00000100;
    static final int LINES = 0x0001;
    static final int TRIANGLES = 0x0004;
    static final int TRIANGLE_STRIP = 0x0005;
    static final int DEPTH_TEST = 0x0B71;
    static final int SCISSOR_TEST = 0x0C11;
    static final int LEQUAL = 0x0203;
    static final int UNSIGNED_BYTE = 0x1401;
    static final int UNSIGNED_SHORT = 0x1403;
    static final int FLOAT = 0x1406;
    static final int RGBA = 0x1908;
    static final int NEAREST = 0x2600;
    static final int LINEAR = 0x2601;
    static final int TEXTURE_MAG_FILTER = 0x2800;
    static final int TEXTURE_MIN_FILTER = 0x2801;
    static final int TEXTURE_WRAP_S = 0x2802;
    static final int TEXTURE_WRAP_T = 0x2803;
    static final int REPEAT = 0x2901;
    static final int CLAMP_TO_EDGE = 0x812F;
    static final int MIRRORED_REPEAT = 0x8370;
    static final int TEXTURE0 = 0x84C0;
    static final int ARRAY_BUFFER = 0x8892;
    static final int ELEMENT_ARRAY_BUFFER = 0x8893;
    static final int UNIFORM_BUFFER = 0x8A11;
    static final int DYNAMIC_DRAW = 0x88E8;
    static final int STATIC_DRAW = 0x88E4;
    static final int FRAGMENT_SHADER = 0x8B30;
    static final int VERTEX_SHADER = 0x8B31;
    static final int COMPILE_STATUS = 0x8B81;
    static final int LINK_STATUS = 0x8B82;
    static final int INFO_LOG_LENGTH = 0x8B84;
    static final int TEXTURE_2D = 0x0DE1;
    static final int BLEND = 0x0BE2;
    static final int ONE = 1;
    static final int SRC_ALPHA = 0x0302;
    static final int ONE_MINUS_SRC_ALPHA = 0x0303;
    static final int RGBA8 = 0x8058;
    static final int BACK = 0x0405;
    static final int PACK_ALIGNMENT = 0x0D05;
    static final int DEPTH_COMPONENT24 = 0x81A6;
    static final int FRAMEBUFFER = 0x8D40;
    static final int RENDERBUFFER = 0x8D41;
    static final int COLOR_ATTACHMENT0 = 0x8CE0;
    static final int DEPTH_ATTACHMENT = 0x8D00;
    static final int FRAMEBUFFER_COMPLETE = 0x8CD5;

    private DesktopCOpenGL() {}

    static int genBuffer() {
        int[] values = new int[1];
        glGenBuffers(1, values);
        return values[0];
    }

    static void deleteBuffer(int buffer) {
        int[] values = new int[] {buffer};
        glDeleteBuffers(1, values);
    }

    static int genTexture() {
        int[] values = new int[1];
        glGenTextures(1, values);
        return values[0];
    }

    static void deleteTexture(int texture) {
        int[] values = new int[] {texture};
        glDeleteTextures(1, values);
    }

    static int genFramebuffer() {
        int[] values = new int[1];
        glGenFramebuffers(1, values);
        return values[0];
    }

    static void deleteFramebuffer(int framebuffer) {
        int[] values = new int[] {framebuffer};
        glDeleteFramebuffers(1, values);
    }

    static int genRenderbuffer() {
        int[] values = new int[1];
        glGenRenderbuffers(1, values);
        return values[0];
    }

    static void deleteRenderbuffer(int renderbuffer) {
        int[] values = new int[] {renderbuffer};
        glDeleteRenderbuffers(1, values);
    }

    static int genVertexArray() {
        int[] values = new int[1];
        glGenVertexArrays(1, values);
        return values[0];
    }

    static void deleteVertexArray(int vertexArray) {
        int[] values = new int[] {vertexArray};
        glDeleteVertexArrays(1, values);
    }

    static int getShaderInt(int shader, int name) {
        int[] values = new int[1];
        glGetShaderiv(shader, name, values);
        return values[0];
    }

    static int getProgramInt(int program, int name) {
        int[] values = new int[1];
        glGetProgramiv(program, name, values);
        return values[0];
    }

    static String getShaderInfoLog(int shader) {
        int length = Math.max(1, getShaderInt(shader, INFO_LOG_LENGTH));
        byte[] bytes = new byte[length];
        int[] written = new int[1];
        glGetShaderInfoLog(shader, length, written, bytes);
        return toString(bytes, written[0]);
    }

    static String getProgramInfoLog(int program) {
        int length = Math.max(1, getProgramInt(program, INFO_LOG_LENGTH));
        byte[] bytes = new byte[length];
        int[] written = new int[1];
        glGetProgramInfoLog(program, length, written, bytes);
        return toString(bytes, written[0]);
    }

    private static String toString(byte[] bytes, int length) {
        int actualLength = Math.max(0, Math.min(length, bytes.length));
        while (actualLength > 0 && bytes[actualLength - 1] == 0) {
            actualLength--;
        }
        if (actualLength == 0) {
            return "";
        }
        return new String(bytes, 0, actualLength);
    }

    static int glewInit() {

        return native_glewInit();
    }

    @Import(name = "fdx_c_glewInit")
    private static native int native_glewInit();

    static int glCreateProgram() {

        return native_glCreateProgram();
    }

    @Import(name = "fdx_c_glCreateProgram")
    private static native int native_glCreateProgram();

    static int glCreateShader(int type) {

        return native_glCreateShader(type);
    }

    @Import(name = "fdx_c_glCreateShader")
    private static native int native_glCreateShader(int type);

    static void glShaderSource(int shader, String source) {
        byte[] sourceBytes = source.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        native_glShaderSource(shader, Address.ofData(sourceBytes), sourceBytes.length);
    }

    @Import(name = "fdx_c_glShaderSource")
    private static native void native_glShaderSource(int shader, Address sourceData, int sourceLength);

    static void glCompileShader(int shader) {

        native_glCompileShader(shader);
    }

    @Import(name = "fdx_c_glCompileShader")
    private static native void native_glCompileShader(int shader);

    private static void glGetShaderiv(int shader, int name, int[] values) {

        native_glGetShaderiv(shader, name, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGetShaderiv")
    private static native void native_glGetShaderiv(int shader, int name, Address valuesData, int valuesLength);

    private static void glGetShaderInfoLog(
            int shader, int maxLength, int[] length, byte[] infoLog) {

        native_glGetShaderInfoLog(shader, maxLength, length == null ? Address.fromLong(0) : Address.ofData(length), length == null ? 0 : length.length, infoLog == null ? Address.fromLong(0) : Address.ofData(infoLog), infoLog == null ? 0 : infoLog.length);
    }

    @Import(name = "fdx_c_glGetShaderInfoLog")
    private static native void native_glGetShaderInfoLog(int shader, int maxLength, Address lengthData, int lengthLength, Address infoLogData, int infoLogLength);

    static void glDeleteShader(int shader) {

        native_glDeleteShader(shader);
    }

    @Import(name = "fdx_c_glDeleteShader")
    private static native void native_glDeleteShader(int shader);

    static void glAttachShader(int program, int shader) {

        native_glAttachShader(program, shader);
    }

    @Import(name = "fdx_c_glAttachShader")
    private static native void native_glAttachShader(int program, int shader);

    static void glLinkProgram(int program) {

        native_glLinkProgram(program);
    }

    @Import(name = "fdx_c_glLinkProgram")
    private static native void native_glLinkProgram(int program);

    private static void glGetProgramiv(int program, int name, int[] values) {

        native_glGetProgramiv(program, name, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGetProgramiv")
    private static native void native_glGetProgramiv(int program, int name, Address valuesData, int valuesLength);

    private static void glGetProgramInfoLog(
            int program, int maxLength, int[] length, byte[] infoLog) {

        native_glGetProgramInfoLog(program, maxLength, length == null ? Address.fromLong(0) : Address.ofData(length), length == null ? 0 : length.length, infoLog == null ? Address.fromLong(0) : Address.ofData(infoLog), infoLog == null ? 0 : infoLog.length);
    }

    @Import(name = "fdx_c_glGetProgramInfoLog")
    private static native void native_glGetProgramInfoLog(int program, int maxLength, Address lengthData, int lengthLength, Address infoLogData, int infoLogLength);

    static void glDeleteProgram(int program) {

        native_glDeleteProgram(program);
    }

    @Import(name = "fdx_c_glDeleteProgram")
    private static native void native_glDeleteProgram(int program);

    static void glUseProgram(int program) {

        native_glUseProgram(program);
    }

    @Import(name = "fdx_c_glUseProgram")
    private static native void native_glUseProgram(int program);

    private static void glGenVertexArrays(int count, int[] values) {

        native_glGenVertexArrays(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGenVertexArrays")
    private static native void native_glGenVertexArrays(int count, Address valuesData, int valuesLength);

    static void glBindVertexArray(int vertexArray) {

        native_glBindVertexArray(vertexArray);
    }

    @Import(name = "fdx_c_glBindVertexArray")
    private static native void native_glBindVertexArray(int vertexArray);

    private static void glDeleteVertexArrays(int count, int[] values) {

        native_glDeleteVertexArrays(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glDeleteVertexArrays")
    private static native void native_glDeleteVertexArrays(int count, Address valuesData, int valuesLength);

    private static void glGenBuffers(int count, int[] values) {

        native_glGenBuffers(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGenBuffers")
    private static native void native_glGenBuffers(int count, Address valuesData, int valuesLength);

    static void glBindBuffer(int target, int buffer) {

        native_glBindBuffer(target, buffer);
    }

    @Import(name = "fdx_c_glBindBuffer")
    private static native void native_glBindBuffer(int target, int buffer);

    static void glBufferData(int target, int size, long data, int usage) {

        native_glBufferData(target, size, data, usage);
    }

    @Import(name = "fdx_c_glBufferData")
    private static native void native_glBufferData(int target, int size, long data, int usage);

    static void glBufferSubData(int target, int offset, int size, ByteBuffer data) {
        // The C interop conversion addresses buffer index zero, not its position.
        if (data != null && data.position() != 0) data = data.slice();
        native_glBufferSubData(target, offset, size, data, data == null ? 0 : data.remaining());
    }

    @Import(name = "fdx_c_glBufferSubData")
    private static native void native_glBufferSubData(int target, int offset, int size, ByteBuffer data, int dataLength);

    static void glBindBufferBase(int target, int index, int buffer) {

        native_glBindBufferBase(target, index, buffer);
    }

    @Import(name = "fdx_c_glBindBufferBase")
    private static native void native_glBindBufferBase(int target, int index, int buffer);

    private static void glDeleteBuffers(int count, int[] values) {

        native_glDeleteBuffers(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glDeleteBuffers")
    private static native void native_glDeleteBuffers(int count, Address valuesData, int valuesLength);

    private static void glGenTextures(int count, int[] values) {

        native_glGenTextures(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGenTextures")
    private static native void native_glGenTextures(int count, Address valuesData, int valuesLength);

    static void glBindTexture(int target, int texture) {

        native_glBindTexture(target, texture);
    }

    @Import(name = "fdx_c_glBindTexture")
    private static native void native_glBindTexture(int target, int texture);

    static void glTexParameteri(int target, int name, int value) {

        native_glTexParameteri(target, name, value);
    }

    @Import(name = "fdx_c_glTexParameteri")
    private static native void native_glTexParameteri(int target, int name, int value);

    static void glTexImage2D(
            int target,
            int level,
            int internalFormat,
            int width,
            int height,
            int border,
            int format,
            int type,
            long data) {

        native_glTexImage2D(target, level, internalFormat, width, height, border, format, type, data);
    }

    @Import(name = "fdx_c_glTexImage2D")
    private static native void native_glTexImage2D(int target, int level, int internalFormat, int width, int height, int border, int format, int type, long data);

    static void glTexSubImage2D(
            int target,
            int level,
            int xOffset,
            int yOffset,
            int width,
            int height,
            int format,
            int type,
            ByteBuffer data) {
        if (data != null && data.position() != 0) data = data.slice();
        native_glTexSubImage2D(target, level, xOffset, yOffset, width, height, format, type, data, data == null ? 0 : data.remaining());
    }

    @Import(name = "fdx_c_glTexSubImage2D")
    private static native void native_glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height, int format, int type, ByteBuffer data, int dataLength);

    private static void glDeleteTextures(int count, int[] values) {

        native_glDeleteTextures(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glDeleteTextures")
    private static native void native_glDeleteTextures(int count, Address valuesData, int valuesLength);

    private static void glGenFramebuffers(int count, int[] values) {

        native_glGenFramebuffers(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGenFramebuffers")
    private static native void native_glGenFramebuffers(int count, Address valuesData, int valuesLength);

    static void glBindFramebuffer(int target, int framebuffer) {

        native_glBindFramebuffer(target, framebuffer);
    }

    @Import(name = "fdx_c_glBindFramebuffer")
    private static native void native_glBindFramebuffer(int target, int framebuffer);

    static void glFramebufferTexture2D(
            int target, int attachment, int textureTarget, int texture, int level) {

        native_glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
    }

    @Import(name = "fdx_c_glFramebufferTexture2D")
    private static native void native_glFramebufferTexture2D(int target, int attachment, int textureTarget, int texture, int level);

    static int glCheckFramebufferStatus(int target) {

        return native_glCheckFramebufferStatus(target);
    }

    @Import(name = "fdx_c_glCheckFramebufferStatus")
    private static native int native_glCheckFramebufferStatus(int target);

    private static void glDeleteFramebuffers(int count, int[] values) {

        native_glDeleteFramebuffers(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glDeleteFramebuffers")
    private static native void native_glDeleteFramebuffers(int count, Address valuesData, int valuesLength);

    private static void glGenRenderbuffers(int count, int[] values) {

        native_glGenRenderbuffers(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glGenRenderbuffers")
    private static native void native_glGenRenderbuffers(int count, Address valuesData, int valuesLength);

    static void glBindRenderbuffer(int target, int renderbuffer) {

        native_glBindRenderbuffer(target, renderbuffer);
    }

    @Import(name = "fdx_c_glBindRenderbuffer")
    private static native void native_glBindRenderbuffer(int target, int renderbuffer);

    static void glRenderbufferStorage(int target, int internalFormat, int width, int height) {

        native_glRenderbufferStorage(target, internalFormat, width, height);
    }

    @Import(name = "fdx_c_glRenderbufferStorage")
    private static native void native_glRenderbufferStorage(int target, int internalFormat, int width, int height);

    static void glFramebufferRenderbuffer(
            int target, int attachment, int renderbufferTarget, int renderbuffer) {

        native_glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
    }

    @Import(name = "fdx_c_glFramebufferRenderbuffer")
    private static native void native_glFramebufferRenderbuffer(int target, int attachment, int renderbufferTarget, int renderbuffer);

    private static void glDeleteRenderbuffers(int count, int[] values) {

        native_glDeleteRenderbuffers(count, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glDeleteRenderbuffers")
    private static native void native_glDeleteRenderbuffers(int count, Address valuesData, int valuesLength);

    static void glActiveTexture(int texture) {

        native_glActiveTexture(texture);
    }

    @Import(name = "fdx_c_glActiveTexture")
    private static native void native_glActiveTexture(int texture);

    static int glGetUniformLocation(int program, String name) {
        byte[] nameBytes = name.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return native_glGetUniformLocation(program, Address.ofData(nameBytes), nameBytes.length);
    }

    @Import(name = "fdx_c_glGetUniformLocation")
    private static native int native_glGetUniformLocation(int program, Address nameData, int nameLength);

    static void glUniform1i(int location, int value) {

        native_glUniform1i(location, value);
    }

    @Import(name = "fdx_c_glUniform1i")
    private static native void native_glUniform1i(int location, int value);

    static int glGetUniformBlockIndex(int program, String uniformBlockName) {
        byte[] uniformBlockNameBytes = uniformBlockName.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return native_glGetUniformBlockIndex(program, Address.ofData(uniformBlockNameBytes), uniformBlockNameBytes.length);
    }

    @Import(name = "fdx_c_glGetUniformBlockIndex")
    private static native int native_glGetUniformBlockIndex(int program, Address uniformBlockNameData, int uniformBlockNameLength);

    static void glUniformBlockBinding(
            int program, int uniformBlockIndex, int uniformBlockBinding) {

        native_glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
    }

    @Import(name = "fdx_c_glUniformBlockBinding")
    private static native void native_glUniformBlockBinding(int program, int uniformBlockIndex, int uniformBlockBinding);

    static void glUniform1f(int location, float value) {

        native_glUniform1f(location, value);
    }

    @Import(name = "fdx_c_glUniform1f")
    private static native void native_glUniform1f(int location, float value);

    static void glUniform3f(int location, float x, float y, float z) {

        native_glUniform3f(location, x, y, z);
    }

    @Import(name = "fdx_c_glUniform3f")
    private static native void native_glUniform3f(int location, float x, float y, float z);

    static void glUniform4f(int location, float x, float y, float z, float w) {

        native_glUniform4f(location, x, y, z, w);
    }

    @Import(name = "fdx_c_glUniform4f")
    private static native void native_glUniform4f(int location, float x, float y, float z, float w);

    static void glUniformMatrix4fv(
            int location, int count, boolean transpose, float[] values) {

        native_glUniformMatrix4fv(location, count, transpose, values == null ? Address.fromLong(0) : Address.ofData(values), values == null ? 0 : values.length);
    }

    @Import(name = "fdx_c_glUniformMatrix4fv")
    private static native void native_glUniformMatrix4fv(int location, int count, boolean transpose, Address valuesData, int valuesLength);

    static void glEnable(int value) {

        native_glEnable(value);
    }

    @Import(name = "fdx_c_glEnable")
    private static native void native_glEnable(int value);

    static void glDisable(int value) {

        native_glDisable(value);
    }

    @Import(name = "fdx_c_glDisable")
    private static native void native_glDisable(int value);

    static void glDepthMask(boolean enabled) {

        native_glDepthMask(enabled);
    }

    @Import(name = "fdx_c_glDepthMask")
    private static native void native_glDepthMask(boolean enabled);

    static void glDepthFunc(int func) {

        native_glDepthFunc(func);
    }

    @Import(name = "fdx_c_glDepthFunc")
    private static native void native_glDepthFunc(int func);

    static void glBlendFuncSeparate(
            int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha) {

        native_glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
    }

    @Import(name = "fdx_c_glBlendFuncSeparate")
    private static native void native_glBlendFuncSeparate(int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha);

    static void glEnableVertexAttribArray(int index) {

        native_glEnableVertexAttribArray(index);
    }

    @Import(name = "fdx_c_glEnableVertexAttribArray")
    private static native void native_glEnableVertexAttribArray(int index);

    static void glDisableVertexAttribArray(int index) {

        native_glDisableVertexAttribArray(index);
    }

    @Import(name = "fdx_c_glDisableVertexAttribArray")
    private static native void native_glDisableVertexAttribArray(int index);

    static void glVertexAttribPointer(
            int index, int size, int type, boolean normalized, int stride, long offset) {

        native_glVertexAttribPointer(index, size, type, normalized, stride, offset);
    }

    @Import(name = "fdx_c_glVertexAttribPointer")
    private static native void native_glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long offset);

    static void glVertexAttribDivisor(int index, int divisor) {

        native_glVertexAttribDivisor(index, divisor);
    }

    @Import(name = "fdx_c_glVertexAttribDivisor")
    private static native void native_glVertexAttribDivisor(int index, int divisor);

    static void glViewport(int x, int y, int width, int height) {

        native_glViewport(x, y, width, height);
    }

    @Import(name = "fdx_c_glViewport")
    private static native void native_glViewport(int x, int y, int width, int height);

    static void glScissor(int x, int y, int width, int height) {

        native_glScissor(x, y, width, height);
    }

    @Import(name = "fdx_c_glScissor")
    private static native void native_glScissor(int x, int y, int width, int height);

    static void glClearColor(float red, float green, float blue, float alpha) {

        native_glClearColor(red, green, blue, alpha);
    }

    @Import(name = "fdx_c_glClearColor")
    private static native void native_glClearColor(float red, float green, float blue, float alpha);

    static void glClearDepth(double depth) {

        native_glClearDepth(depth);
    }

    @Import(name = "fdx_c_glClearDepth")
    private static native void native_glClearDepth(double depth);

    static void glClear(int mask) {

        native_glClear(mask);
    }

    @Import(name = "fdx_c_glClear")
    private static native void native_glClear(int mask);

    static void glPixelStorei(int name, int value) {

        native_glPixelStorei(name, value);
    }

    @Import(name = "fdx_c_glPixelStorei")
    private static native void native_glPixelStorei(int name, int value);

    static void glReadBuffer(int source) {

        native_glReadBuffer(source);
    }

    @Import(name = "fdx_c_glReadBuffer")
    private static native void native_glReadBuffer(int source);

    static void glReadPixels(
            int x, int y, int width, int height, int format, int type, ByteBuffer pixels) {
        if (pixels != null && pixels.position() != 0) pixels = pixels.slice();
        native_glReadPixels(x, y, width, height, format, type, pixels, pixels == null ? 0 : pixels.remaining());
    }

    @Import(name = "fdx_c_glReadPixels")
    private static native void native_glReadPixels(int x, int y, int width, int height, int format, int type, ByteBuffer pixels, int pixelsLength);

    static void glDrawArrays(int mode, int first, int count) {

        native_glDrawArrays(mode, first, count);
    }

    @Import(name = "fdx_c_glDrawArrays")
    private static native void native_glDrawArrays(int mode, int first, int count);

    static void glDrawArraysInstanced(int mode, int first, int count, int instanceCount) {

        native_glDrawArraysInstanced(mode, first, count, instanceCount);
    }

    @Import(name = "fdx_c_glDrawArraysInstanced")
    private static native void native_glDrawArraysInstanced(int mode, int first, int count, int instanceCount);

    static void glDrawElements(int mode, int count, int type, long indices) {

        native_glDrawElements(mode, count, type, indices);
    }

    @Import(name = "fdx_c_glDrawElements")
    private static native void native_glDrawElements(int mode, int count, int type, long indices);

    static void glDrawElementsBaseVertex(
            int mode, int count, int type, long indices, int baseVertex) {

        native_glDrawElementsBaseVertex(mode, count, type, indices, baseVertex);
    }

    @Import(name = "fdx_c_glDrawElementsBaseVertex")
    private static native void native_glDrawElementsBaseVertex(int mode, int count, int type, long indices, int baseVertex);

    static void glDrawElementsInstanced(
            int mode, int count, int type, long indices, int instanceCount) {

        native_glDrawElementsInstanced(mode, count, type, indices, instanceCount);
    }

    @Import(name = "fdx_c_glDrawElementsInstanced")
    private static native void native_glDrawElementsInstanced(int mode, int count, int type, long indices, int instanceCount);

    static void glDrawElementsInstancedBaseVertex(
            int mode, int count, int type, long indices, int instanceCount, int baseVertex) {

        native_glDrawElementsInstancedBaseVertex(mode, count, type, indices, instanceCount, baseVertex);
    }

    @Import(name = "fdx_c_glDrawElementsInstancedBaseVertex")
    private static native void native_glDrawElementsInstancedBaseVertex(int mode, int count, int type, long indices, int instanceCount, int baseVertex);
}
