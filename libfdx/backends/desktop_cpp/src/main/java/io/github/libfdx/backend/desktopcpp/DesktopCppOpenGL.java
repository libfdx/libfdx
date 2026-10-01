package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;

import java.nio.ByteBuffer;

/**
 * Represents a jNative open GL.
 *
 * @author xpenatan
 */
@NativeInclude("libfdx_jnative.hpp")
final class DesktopCppOpenGL {
    @NativeImport("fdx_cpp_gl_parallel_shader_compilation")
    static native boolean enableParallelShaderCompilation(int workers);

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

    private DesktopCppOpenGL() {}

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

    @NativeImport("fdx_jn_glewInit")
    static native int glewInit();

    @NativeImport("fdx_jn_glCreateProgram")
    static native int glCreateProgram();

    @NativeImport("fdx_jn_glCreateShader")
    static native int glCreateShader(int type);

    @NativeImport("fdx_jn_glShaderSource")
    static native void glShaderSource(int shader, String source);

    @NativeImport("fdx_jn_glCompileShader")
    static native void glCompileShader(int shader);

    @NativeImport("fdx_jn_glGetShaderiv")
    private static native void glGetShaderiv(int shader, int name, int[] values);

    @NativeImport("fdx_jn_glGetShaderInfoLog")
    private static native void glGetShaderInfoLog(
            int shader, int maxLength, int[] length, byte[] infoLog);

    @NativeImport("fdx_jn_glDeleteShader")
    static native void glDeleteShader(int shader);

    @NativeImport("fdx_jn_glAttachShader")
    static native void glAttachShader(int program, int shader);

    @NativeImport("fdx_jn_glLinkProgram")
    static native void glLinkProgram(int program);

    @NativeImport("fdx_jn_glGetProgramiv")
    private static native void glGetProgramiv(int program, int name, int[] values);

    @NativeImport("fdx_jn_glGetProgramInfoLog")
    private static native void glGetProgramInfoLog(
            int program, int maxLength, int[] length, byte[] infoLog);

    @NativeImport("fdx_jn_glDeleteProgram")
    static native void glDeleteProgram(int program);

    @NativeImport("fdx_jn_glUseProgram")
    static native void glUseProgram(int program);

    @NativeImport("fdx_jn_glGenVertexArrays")
    private static native void glGenVertexArrays(int count, int[] values);

    @NativeImport("fdx_jn_glBindVertexArray")
    static native void glBindVertexArray(int vertexArray);

    @NativeImport("fdx_jn_glDeleteVertexArrays")
    private static native void glDeleteVertexArrays(int count, int[] values);

    @NativeImport("fdx_jn_glGenBuffers")
    private static native void glGenBuffers(int count, int[] values);

    @NativeImport("fdx_jn_glBindBuffer")
    static native void glBindBuffer(int target, int buffer);

    @NativeImport("fdx_jn_glBufferData")
    static native void glBufferData(int target, int size, long data, int usage);

    @NativeImport("fdx_jn_glBufferSubData")
    static native void glBufferSubData(int target, int offset, int size, ByteBuffer data);

    @NativeImport("fdx_jn_glBindBufferBase")
    static native void glBindBufferBase(int target, int index, int buffer);

    @NativeImport("fdx_jn_glDeleteBuffers")
    private static native void glDeleteBuffers(int count, int[] values);

    @NativeImport("fdx_jn_glGenTextures")
    private static native void glGenTextures(int count, int[] values);

    @NativeImport("fdx_jn_glBindTexture")
    static native void glBindTexture(int target, int texture);

    @NativeImport("fdx_jn_glTexParameteri")
    static native void glTexParameteri(int target, int name, int value);

    @NativeImport("fdx_jn_glTexImage2D")
    static native void glTexImage2D(
            int target,
            int level,
            int internalFormat,
            int width,
            int height,
            int border,
            int format,
            int type,
            long data);

    @NativeImport("fdx_jn_glTexSubImage2D")
    static native void glTexSubImage2D(
            int target,
            int level,
            int xOffset,
            int yOffset,
            int width,
            int height,
            int format,
            int type,
            ByteBuffer data);

    @NativeImport("fdx_jn_glDeleteTextures")
    private static native void glDeleteTextures(int count, int[] values);

    @NativeImport("fdx_jn_glGenFramebuffers")
    private static native void glGenFramebuffers(int count, int[] values);

    @NativeImport("fdx_jn_glBindFramebuffer")
    static native void glBindFramebuffer(int target, int framebuffer);

    @NativeImport("fdx_jn_glFramebufferTexture2D")
    static native void glFramebufferTexture2D(
            int target, int attachment, int textureTarget, int texture, int level);

    @NativeImport("fdx_jn_glCheckFramebufferStatus")
    static native int glCheckFramebufferStatus(int target);

    @NativeImport("fdx_jn_glDeleteFramebuffers")
    private static native void glDeleteFramebuffers(int count, int[] values);

    @NativeImport("fdx_jn_glGenRenderbuffers")
    private static native void glGenRenderbuffers(int count, int[] values);

    @NativeImport("fdx_jn_glBindRenderbuffer")
    static native void glBindRenderbuffer(int target, int renderbuffer);

    @NativeImport("fdx_jn_glRenderbufferStorage")
    static native void glRenderbufferStorage(int target, int internalFormat, int width, int height);

    @NativeImport("fdx_jn_glFramebufferRenderbuffer")
    static native void glFramebufferRenderbuffer(
            int target, int attachment, int renderbufferTarget, int renderbuffer);

    @NativeImport("fdx_jn_glDeleteRenderbuffers")
    private static native void glDeleteRenderbuffers(int count, int[] values);

    @NativeImport("fdx_jn_glActiveTexture")
    static native void glActiveTexture(int texture);

    @NativeImport("fdx_jn_glGetUniformLocation")
    static native int glGetUniformLocation(int program, String name);

    @NativeImport("fdx_jn_glUniform1i")
    static native void glUniform1i(int location, int value);

    @NativeImport("fdx_jn_glGetUniformBlockIndex")
    static native int glGetUniformBlockIndex(int program, String uniformBlockName);

    @NativeImport("fdx_jn_glUniformBlockBinding")
    static native void glUniformBlockBinding(
            int program, int uniformBlockIndex, int uniformBlockBinding);

    @NativeImport("fdx_jn_glUniform1f")
    static native void glUniform1f(int location, float value);

    @NativeImport("fdx_jn_glUniform3f")
    static native void glUniform3f(int location, float x, float y, float z);

    @NativeImport("fdx_jn_glUniform4f")
    static native void glUniform4f(int location, float x, float y, float z, float w);

    @NativeImport("fdx_jn_glUniformMatrix4fv")
    static native void glUniformMatrix4fv(
            int location, int count, boolean transpose, float[] values);

    @NativeImport("fdx_jn_glEnable")
    static native void glEnable(int value);

    @NativeImport("fdx_jn_glDisable")
    static native void glDisable(int value);

    @NativeImport("fdx_jn_glDepthMask")
    static native void glDepthMask(boolean enabled);

    @NativeImport("fdx_jn_glDepthFunc")
    static native void glDepthFunc(int func);

    @NativeImport("fdx_jn_glBlendFuncSeparate")
    static native void glBlendFuncSeparate(
            int sourceRgb, int destinationRgb, int sourceAlpha, int destinationAlpha);

    @NativeImport("fdx_jn_glEnableVertexAttribArray")
    static native void glEnableVertexAttribArray(int index);

    @NativeImport("fdx_jn_glDisableVertexAttribArray")
    static native void glDisableVertexAttribArray(int index);

    @NativeImport("fdx_jn_glVertexAttribPointer")
    static native void glVertexAttribPointer(
            int index, int size, int type, boolean normalized, int stride, long offset);

    @NativeImport("fdx_jn_glVertexAttribDivisor")
    static native void glVertexAttribDivisor(int index, int divisor);

    @NativeImport("fdx_jn_glViewport")
    static native void glViewport(int x, int y, int width, int height);

    @NativeImport("fdx_jn_glScissor")
    static native void glScissor(int x, int y, int width, int height);

    @NativeImport("fdx_jn_glClearColor")
    static native void glClearColor(float red, float green, float blue, float alpha);

    @NativeImport("fdx_jn_glClearDepth")
    static native void glClearDepth(double depth);

    @NativeImport("fdx_jn_glClear")
    static native void glClear(int mask);

    @NativeImport("fdx_jn_glPixelStorei")
    static native void glPixelStorei(int name, int value);

    @NativeImport("fdx_jn_glReadBuffer")
    static native void glReadBuffer(int source);

    @NativeImport("fdx_jn_glReadPixels")
    static native void glReadPixels(
            int x, int y, int width, int height, int format, int type, ByteBuffer pixels);

    @NativeImport("fdx_jn_glDrawArrays")
    static native void glDrawArrays(int mode, int first, int count);

    @NativeImport("fdx_jn_glDrawArraysInstanced")
    static native void glDrawArraysInstanced(int mode, int first, int count, int instanceCount);

    @NativeImport("fdx_jn_glDrawElements")
    static native void glDrawElements(int mode, int count, int type, long indices);

    @NativeImport("fdx_jn_glDrawElementsBaseVertex")
    static native void glDrawElementsBaseVertex(
            int mode, int count, int type, long indices, int baseVertex);

    @NativeImport("fdx_jn_glDrawElementsInstanced")
    static native void glDrawElementsInstanced(
            int mode, int count, int type, long indices, int instanceCount);

    @NativeImport("fdx_jn_glDrawElementsInstancedBaseVertex")
    static native void glDrawElementsInstancedBaseVertex(
            int mode, int count, int type, long indices, int instanceCount, int baseVertex);
}
