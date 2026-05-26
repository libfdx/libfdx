package io.github.libfdx.backend.desktopnative;

import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

import java.nio.ByteBuffer;

@Include("GL/glew.h")
final class DesktopNativeOpenGL {
    static final int FALSE = 0;
    static final int COLOR_BUFFER_BIT = 0x00004000;
    static final int DEPTH_BUFFER_BIT = 0x00000100;
    static final int LINES = 0x0001;
    static final int TRIANGLES = 0x0004;
    static final int TRIANGLE_STRIP = 0x0005;
    static final int DEPTH_TEST = 0x0B71;
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
    static final int DYNAMIC_DRAW = 0x88E8;
    static final int STATIC_DRAW = 0x88E4;
    static final int FRAGMENT_SHADER = 0x8B30;
    static final int VERTEX_SHADER = 0x8B31;
    static final int COMPILE_STATUS = 0x8B81;
    static final int LINK_STATUS = 0x8B82;
    static final int INFO_LOG_LENGTH = 0x8B84;
    static final int TEXTURE_2D = 0x0DE1;
    static final int BLEND = 0x0BE2;
    static final int SRC_ALPHA = 0x0302;
    static final int ONE_MINUS_SRC_ALPHA = 0x0303;
    static final int RGBA8 = 0x8058;

    private DesktopNativeOpenGL() {
    }

    static int genBuffer() {
        int[] values = new int[1];
        glGenBuffers(1, Address.ofData(values));
        return values[0];
    }

    static void deleteBuffer(int buffer) {
        int[] values = new int[] { buffer };
        glDeleteBuffers(1, Address.ofData(values));
    }

    static int genTexture() {
        int[] values = new int[1];
        glGenTextures(1, Address.ofData(values));
        return values[0];
    }

    static void deleteTexture(int texture) {
        int[] values = new int[] { texture };
        glDeleteTextures(1, Address.ofData(values));
    }

    static int genVertexArray() {
        int[] values = new int[1];
        glGenVertexArrays(1, Address.ofData(values));
        return values[0];
    }

    static void deleteVertexArray(int vertexArray) {
        int[] values = new int[] { vertexArray };
        glDeleteVertexArrays(1, Address.ofData(values));
    }

    static int getShaderInt(int shader, int name) {
        int[] values = new int[1];
        glGetShaderiv(shader, name, Address.ofData(values));
        return values[0];
    }

    static int getProgramInt(int program, int name) {
        int[] values = new int[1];
        glGetProgramiv(program, name, Address.ofData(values));
        return values[0];
    }

    static String getShaderInfoLog(int shader) {
        int length = Math.max(1, getShaderInt(shader, INFO_LOG_LENGTH));
        byte[] bytes = new byte[length];
        int[] written = new int[1];
        glGetShaderInfoLog(shader, length, Address.ofData(written), bytes);
        return toString(bytes, written[0]);
    }

    static String getProgramInfoLog(int program) {
        int length = Math.max(1, getProgramInt(program, INFO_LOG_LENGTH));
        byte[] bytes = new byte[length];
        int[] written = new int[1];
        glGetProgramInfoLog(program, length, Address.ofData(written), bytes);
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

    @Import(name = "glewInit")
    static native int glewInit();

    @Import(name = "glCreateProgram")
    static native int glCreateProgram();

    @Import(name = "glCreateShader")
    static native int glCreateShader(int type);

    @Import(name = "glShaderSource")
    static native void glShaderSource(int shader, int count, Address strings, Address length);

    @Import(name = "glCompileShader")
    static native void glCompileShader(int shader);

    @Import(name = "glGetShaderiv")
    private static native void glGetShaderiv(int shader, int name, Address values);

    @Import(name = "glGetShaderInfoLog")
    private static native void glGetShaderInfoLog(int shader, int maxLength, Address length, byte[] infoLog);

    @Import(name = "glDeleteShader")
    static native void glDeleteShader(int shader);

    @Import(name = "glAttachShader")
    static native void glAttachShader(int program, int shader);

    @Import(name = "glLinkProgram")
    static native void glLinkProgram(int program);

    @Import(name = "glGetProgramiv")
    private static native void glGetProgramiv(int program, int name, Address values);

    @Import(name = "glGetProgramInfoLog")
    private static native void glGetProgramInfoLog(int program, int maxLength, Address length, byte[] infoLog);

    @Import(name = "glDeleteProgram")
    static native void glDeleteProgram(int program);

    @Import(name = "glUseProgram")
    static native void glUseProgram(int program);

    @Import(name = "glGenVertexArrays")
    private static native void glGenVertexArrays(int count, Address values);

    @Import(name = "glBindVertexArray")
    static native void glBindVertexArray(int vertexArray);

    @Import(name = "glDeleteVertexArrays")
    private static native void glDeleteVertexArrays(int count, Address values);

    @Import(name = "glGenBuffers")
    private static native void glGenBuffers(int count, Address values);

    @Import(name = "glBindBuffer")
    static native void glBindBuffer(int target, int buffer);

    @Import(name = "glBufferData")
    static native void glBufferData(int target, int size, Address data, int usage);

    @Import(name = "glBufferSubData")
    static native void glBufferSubData(int target, int offset, int size, ByteBuffer data);

    @Import(name = "glDeleteBuffers")
    private static native void glDeleteBuffers(int count, Address values);

    @Import(name = "glGenTextures")
    private static native void glGenTextures(int count, Address values);

    @Import(name = "glBindTexture")
    static native void glBindTexture(int target, int texture);

    @Import(name = "glTexParameteri")
    static native void glTexParameteri(int target, int name, int value);

    @Import(name = "glTexImage2D")
    static native void glTexImage2D(int target, int level, int internalFormat, int width, int height, int border,
            int format, int type, Address data);

    @Import(name = "glTexSubImage2D")
    static native void glTexSubImage2D(int target, int level, int xOffset, int yOffset, int width, int height,
            int format, int type, ByteBuffer data);

    @Import(name = "glDeleteTextures")
    private static native void glDeleteTextures(int count, Address values);

    @Import(name = "glActiveTexture")
    static native void glActiveTexture(int texture);

    @Import(name = "glGetUniformLocation")
    static native int glGetUniformLocation(int program, String name);

    @Import(name = "glUniform1i")
    static native void glUniform1i(int location, int value);

    @Import(name = "glUniform1f")
    static native void glUniform1f(int location, float value);

    @Import(name = "glUniform3f")
    static native void glUniform3f(int location, float x, float y, float z);

    @Import(name = "glUniform4f")
    static native void glUniform4f(int location, float x, float y, float z, float w);

    @Import(name = "glUniformMatrix4fv")
    static native void glUniformMatrix4fv(int location, int count, boolean transpose, float[] values);

    @Import(name = "glEnable")
    static native void glEnable(int value);

    @Import(name = "glDisable")
    static native void glDisable(int value);

    @Import(name = "glDepthMask")
    static native void glDepthMask(boolean enabled);

    @Import(name = "glDepthFunc")
    static native void glDepthFunc(int func);

    @Import(name = "glBlendFunc")
    static native void glBlendFunc(int source, int destination);

    @Import(name = "glEnableVertexAttribArray")
    static native void glEnableVertexAttribArray(int index);

    @Import(name = "glVertexAttribPointer")
    static native void glVertexAttribPointer(int index, int size, int type, boolean normalized, int stride, int offset);

    @Import(name = "glVertexAttribDivisor")
    static native void glVertexAttribDivisor(int index, int divisor);

    @Import(name = "glViewport")
    static native void glViewport(int x, int y, int width, int height);

    @Import(name = "glClearColor")
    static native void glClearColor(float red, float green, float blue, float alpha);

    @Import(name = "glClearDepth")
    static native void glClearDepth(double depth);

    @Import(name = "glClear")
    static native void glClear(int mask);

    @Import(name = "glDrawArrays")
    static native void glDrawArrays(int mode, int first, int count);

    @Import(name = "glDrawArraysInstanced")
    static native void glDrawArraysInstanced(int mode, int first, int count, int instanceCount);

    @Import(name = "glDrawElements")
    static native void glDrawElements(int mode, int count, int type, Address indices);

    @Import(name = "glDrawElementsInstanced")
    static native void glDrawElementsInstanced(int mode, int count, int type, Address indices, int instanceCount);
}
