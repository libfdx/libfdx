#include "libfdx_jnative.hpp"
#include <GL/glew.h>

using namespace libfdx_cpp;

extern "C" int32_t fdx_cpp_gl_parallel_shader_compilation(int32_t workers) {
    if (GLEW_KHR_parallel_shader_compile) {
        glMaxShaderCompilerThreadsKHR(workers);
        return 1;
    }
    if (GLEW_ARB_parallel_shader_compile) {
        glMaxShaderCompilerThreadsARB(workers);
        return 1;
    }
    return 0;
}

namespace {
int32_t pixel_transfer_size(int32_t width, int32_t height, int32_t format, int32_t type) {
    int components = format == GL_RGBA ? 4 : format == GL_RED ? 1 : 0;
    int component_size = type == GL_UNSIGNED_BYTE ? 1 : type == GL_HALF_FLOAT ? 2 : type == GL_FLOAT ? 4 : 0;
    if (!components || !component_size) throw std::invalid_argument("Unsupported pixel transfer format");
    int pixel_size = components * component_size;
    if (width < 0 || height < 0 || int64_t(width) * height > INT32_MAX / pixel_size)
        throw std::invalid_argument("Pixel dimensions are out of range");
    return width * height * pixel_size;
}
}

extern "C" int32_t fdx_jn_glewInit() {
    return glewInit();
}

extern "C" int32_t fdx_jn_glCreateProgram() {
    return glCreateProgram();
}

extern "C" int32_t fdx_jn_glCreateShader(int32_t type) {
    return glCreateShader(type);
}

extern "C" void fdx_jn_glShaderSource(int32_t shader, jn_handle source) {
    auto contents = java_text(source);
    const char* pointer = contents.c_str();
    glShaderSource(shader, 1, &pointer, nullptr);
}

extern "C" void fdx_jn_glCompileShader(int32_t shader) {
    glCompileShader(shader);
}

extern "C" void fdx_jn_glGetShaderiv(int32_t shader, int32_t name, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(1);
    glGetShaderiv(shader, name, values_array.data());
    values_array.write();
}

extern "C" void fdx_jn_glGetShaderInfoLog(int32_t shader, int32_t maxLength, jn_handle length, jn_handle infoLog) {
    NativeArray<int32_t> length_array(length);
    NativeArray<int8_t> infoLog_array(infoLog);
    length_array.require(1);
    infoLog_array.require(maxLength);
    glGetShaderInfoLog(shader, maxLength, length_array.data(), reinterpret_cast<char*>(infoLog_array.data()));
    length_array.write();
    infoLog_array.write();
}

extern "C" void fdx_jn_glDeleteShader(int32_t shader) {
    glDeleteShader(shader);
}

extern "C" void fdx_jn_glAttachShader(int32_t program, int32_t shader) {
    glAttachShader(program, shader);
}

extern "C" void fdx_jn_glLinkProgram(int32_t program) {
    glLinkProgram(program);
}

extern "C" void fdx_jn_glGetProgramiv(int32_t program, int32_t name, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(1);
    glGetProgramiv(program, name, values_array.data());
    values_array.write();
}

extern "C" void fdx_jn_glGetProgramInfoLog(int32_t program, int32_t maxLength, jn_handle length, jn_handle infoLog) {
    NativeArray<int32_t> length_array(length);
    NativeArray<int8_t> infoLog_array(infoLog);
    length_array.require(1);
    infoLog_array.require(maxLength);
    glGetProgramInfoLog(program, maxLength, length_array.data(), reinterpret_cast<char*>(infoLog_array.data()));
    length_array.write();
    infoLog_array.write();
}

extern "C" void fdx_jn_glDeleteProgram(int32_t program) {
    glDeleteProgram(program);
}

extern "C" void fdx_jn_glUseProgram(int32_t program) {
    glUseProgram(program);
}

extern "C" void fdx_jn_glGenVertexArrays(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glGenVertexArrays(count, reinterpret_cast<GLuint*>(values_array.data()));
    values_array.write();
}

extern "C" void fdx_jn_glBindVertexArray(int32_t vertexArray) {
    glBindVertexArray(vertexArray);
}

extern "C" void fdx_jn_glDeleteVertexArrays(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glDeleteVertexArrays(count, reinterpret_cast<GLuint*>(values_array.data()));
}

extern "C" void fdx_jn_glGenBuffers(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glGenBuffers(count, reinterpret_cast<GLuint*>(values_array.data()));
    values_array.write();
}

extern "C" void fdx_jn_glBindBuffer(int32_t target, int32_t buffer) {
    glBindBuffer(target, buffer);
}

extern "C" void fdx_jn_glBufferData(int32_t target, int32_t size, int64_t data, int32_t usage) {
    glBufferData(target, size, reinterpret_cast<const void*>(static_cast<intptr_t>(data)), usage);
}

extern "C" void fdx_jn_glBindBufferBase(int32_t target, int32_t index, int32_t buffer) {
    glBindBufferBase(target, index, buffer);
}

extern "C" void fdx_jn_glDeleteBuffers(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glDeleteBuffers(count, reinterpret_cast<GLuint*>(values_array.data()));
}

extern "C" void fdx_jn_glGenTextures(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glGenTextures(count, reinterpret_cast<GLuint*>(values_array.data()));
    values_array.write();
}

extern "C" void fdx_jn_glBindTexture(int32_t target, int32_t texture) {
    glBindTexture(target, texture);
}

extern "C" void fdx_jn_glTexParameteri(int32_t target, int32_t name, int32_t value) {
    glTexParameteri(target, name, value);
}

extern "C" void fdx_jn_glTexImage2D(int32_t target, int32_t level, int32_t internalFormat, int32_t width, int32_t height, int32_t border, int32_t format, int32_t type, int64_t data) {
    glTexImage2D(target, level, internalFormat, width, height, border, format, type, reinterpret_cast<const void*>(static_cast<intptr_t>(data)));
}

extern "C" void fdx_jn_glDeleteTextures(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glDeleteTextures(count, reinterpret_cast<GLuint*>(values_array.data()));
}

extern "C" void fdx_jn_glGenFramebuffers(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glGenFramebuffers(count, reinterpret_cast<GLuint*>(values_array.data()));
    values_array.write();
}

extern "C" void fdx_jn_glBindFramebuffer(int32_t target, int32_t framebuffer) {
    glBindFramebuffer(target, framebuffer);
}

extern "C" void fdx_jn_glFramebufferTexture2D(int32_t target, int32_t attachment, int32_t textureTarget, int32_t texture, int32_t level) {
    glFramebufferTexture2D(target, attachment, textureTarget, texture, level);
}

extern "C" int32_t fdx_jn_glCheckFramebufferStatus(int32_t target) {
    return glCheckFramebufferStatus(target);
}

extern "C" void fdx_jn_glDeleteFramebuffers(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glDeleteFramebuffers(count, reinterpret_cast<GLuint*>(values_array.data()));
}

extern "C" void fdx_jn_glGenRenderbuffers(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glGenRenderbuffers(count, reinterpret_cast<GLuint*>(values_array.data()));
    values_array.write();
}

extern "C" void fdx_jn_glBindRenderbuffer(int32_t target, int32_t renderbuffer) {
    glBindRenderbuffer(target, renderbuffer);
}

extern "C" void fdx_jn_glRenderbufferStorage(int32_t target, int32_t internalFormat, int32_t width, int32_t height) {
    glRenderbufferStorage(target, internalFormat, width, height);
}

extern "C" void fdx_jn_glFramebufferRenderbuffer(int32_t target, int32_t attachment, int32_t renderbufferTarget, int32_t renderbuffer) {
    glFramebufferRenderbuffer(target, attachment, renderbufferTarget, renderbuffer);
}

extern "C" void fdx_jn_glDeleteRenderbuffers(int32_t count, jn_handle values) {
    NativeArray<int32_t> values_array(values);
    values_array.require(count);
    glDeleteRenderbuffers(count, reinterpret_cast<GLuint*>(values_array.data()));
}

extern "C" void fdx_jn_glActiveTexture(int32_t texture) {
    glActiveTexture(texture);
}

extern "C" int32_t fdx_jn_glGetUniformLocation(int32_t program, jn_handle name) {
    auto name_text = java_text(name);
    return glGetUniformLocation(program, name_text.c_str());
}

extern "C" void fdx_jn_glUniform1i(int32_t location, int32_t value) {
    glUniform1i(location, value);
}

extern "C" int32_t fdx_jn_glGetUniformBlockIndex(int32_t program, jn_handle uniformBlockName) {
    auto uniformBlockName_text = java_text(uniformBlockName);
    return glGetUniformBlockIndex(program, uniformBlockName_text.c_str());
}

extern "C" void fdx_jn_glUniformBlockBinding(int32_t program, int32_t uniformBlockIndex, int32_t uniformBlockBinding) {
    glUniformBlockBinding(program, uniformBlockIndex, uniformBlockBinding);
}

extern "C" void fdx_jn_glUniform1f(int32_t location, float value) {
    glUniform1f(location, value);
}

extern "C" void fdx_jn_glUniform3f(int32_t location, float x, float y, float z) {
    glUniform3f(location, x, y, z);
}

extern "C" void fdx_jn_glUniform4f(int32_t location, float x, float y, float z, float w) {
    glUniform4f(location, x, y, z, w);
}

extern "C" void fdx_jn_glUniformMatrix4fv(int32_t location, int32_t count, int32_t transpose, jn_handle values) {
    NativeArray<float> values_array(values);
    values_array.require(int64_t(count) * 16);
    glUniformMatrix4fv(location, count, transpose, values_array.data());
}

extern "C" void fdx_jn_glEnable(int32_t value) {
    glEnable(value);
}

extern "C" void fdx_jn_glDisable(int32_t value) {
    glDisable(value);
}

extern "C" void fdx_jn_glDepthMask(int32_t enabled) {
    glDepthMask(enabled);
}

extern "C" void fdx_jn_glDepthFunc(int32_t func) {
    glDepthFunc(func);
}

extern "C" void fdx_jn_glBlendFuncSeparate(int32_t sourceRgb, int32_t destinationRgb, int32_t sourceAlpha, int32_t destinationAlpha) {
    glBlendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
}

extern "C" void fdx_jn_glEnableVertexAttribArray(int32_t index) {
    glEnableVertexAttribArray(index);
}

extern "C" void fdx_jn_glDisableVertexAttribArray(int32_t index) {
    glDisableVertexAttribArray(index);
}

extern "C" void fdx_jn_glVertexAttribPointer(int32_t index, int32_t size, int32_t type, int32_t normalized, int32_t stride, int64_t offset) {
    glVertexAttribPointer(index, size, type, normalized, stride, reinterpret_cast<const void*>(static_cast<intptr_t>(offset)));
}

extern "C" void fdx_jn_glVertexAttribDivisor(int32_t index, int32_t divisor) {
    glVertexAttribDivisor(index, divisor);
}

extern "C" void fdx_jn_glViewport(int32_t x, int32_t y, int32_t width, int32_t height) {
    glViewport(x, y, width, height);
}

extern "C" void fdx_jn_glScissor(int32_t x, int32_t y, int32_t width, int32_t height) {
    glScissor(x, y, width, height);
}

extern "C" void fdx_jn_glClearColor(float red, float green, float blue, float alpha) {
    glClearColor(red, green, blue, alpha);
}

extern "C" void fdx_jn_glClearDepth(double depth) {
    glClearDepth(depth);
}

extern "C" void fdx_jn_glClear(int32_t mask) {
    glClear(mask);
}

extern "C" void fdx_jn_glPixelStorei(int32_t name, int32_t value) {
    glPixelStorei(name, value);
}

extern "C" void fdx_jn_glReadBuffer(int32_t source) {
    glReadBuffer(source);
}

extern "C" void fdx_jn_glDrawArrays(int32_t mode, int32_t first, int32_t count) {
    glDrawArrays(mode, first, count);
}

extern "C" void fdx_jn_glDrawArraysInstanced(int32_t mode, int32_t first, int32_t count, int32_t instanceCount) {
    glDrawArraysInstanced(mode, first, count, instanceCount);
}

extern "C" void fdx_jn_glDrawElements(int32_t mode, int32_t count, int32_t type, int64_t indices) {
    glDrawElements(mode, count, type, reinterpret_cast<const void*>(static_cast<intptr_t>(indices)));
}

extern "C" void fdx_jn_glDrawElementsBaseVertex(int32_t mode, int32_t count, int32_t type, int64_t indices, int32_t baseVertex) {
    glDrawElementsBaseVertex(mode, count, type, reinterpret_cast<const void*>(static_cast<intptr_t>(indices)), baseVertex);
}

extern "C" void fdx_jn_glDrawElementsInstanced(int32_t mode, int32_t count, int32_t type, int64_t indices, int32_t instanceCount) {
    glDrawElementsInstanced(mode, count, type, reinterpret_cast<const void*>(static_cast<intptr_t>(indices)), instanceCount);
}

extern "C" void fdx_jn_glDrawElementsInstancedBaseVertex(int32_t mode, int32_t count, int32_t type, int64_t indices, int32_t instanceCount, int32_t baseVertex) {
    glDrawElementsInstancedBaseVertex(mode, count, type, reinterpret_cast<const void*>(static_cast<intptr_t>(indices)), instanceCount, baseVertex);
}

extern "C" void fdx_jn_glBufferSubData(int32_t target, int32_t offset, int32_t size, jn_handle data) {
    glBufferSubData(target, offset, size, direct_bytes(data, size, false));
}

extern "C" void fdx_jn_glTexSubImage2D(int32_t target, int32_t level, int32_t x, int32_t y,
        int32_t width, int32_t height, int32_t format, int32_t type, jn_handle data) {
    glTexSubImage2D(target, level, x, y, width, height, format, type, direct_bytes(data, pixel_transfer_size(width, height, format, type), false));
}

extern "C" void fdx_jn_glReadPixels(int32_t x, int32_t y, int32_t width, int32_t height,
        int32_t format, int32_t type, jn_handle pixels) {
    glReadPixels(x, y, width, height, format, type, direct_bytes(pixels, pixel_transfer_size(width, height, format, type), true));
}
