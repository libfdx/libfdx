#include "libfdx_jnative.hpp"
#include <GL/glew.h>
#include <cstdio>
#include <cstring>

using namespace libfdx_cpp;

namespace {
void enable(GLenum capability, bool value) {
    if (value) glEnable(capability); else glDisable(capability);
}
GLenum factor(int value) {
    static const GLenum values[] = {GL_ZERO, GL_ONE, GL_SRC_COLOR, GL_ONE_MINUS_SRC_COLOR,
        GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_DST_COLOR, GL_ONE_MINUS_DST_COLOR,
        GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA, GL_SRC_ALPHA_SATURATE, GL_CONSTANT_COLOR, GL_ONE_MINUS_CONSTANT_COLOR};
    if (value < 0 || value >= 13) throw std::invalid_argument("Invalid blend factor");
    return values[value];
}
GLenum operation(int value) {
    static const GLenum values[] = {GL_FUNC_ADD, GL_FUNC_SUBTRACT, GL_FUNC_REVERSE_SUBTRACT, GL_MIN, GL_MAX};
    if (value < 0 || value >= 5) throw std::invalid_argument("Invalid blend operation");
    return values[value];
}
GLenum stencil(int value) {
    static const GLenum values[] = {GL_KEEP, GL_ZERO, GL_REPLACE, GL_INVERT, GL_INCR, GL_DECR, GL_INCR_WRAP, GL_DECR_WRAP};
    if (value < 0 || value >= 8) throw std::invalid_argument("Invalid stencil operation");
    return values[value];
}
}

extern "C" int32_t fdx_jn_gl_features_supported(int32_t feature) {
    if (feature == 1) {
        GLint colors = 0, depth = 0;
        glGetIntegerv(GL_MAX_COLOR_TEXTURE_SAMPLES, &colors);
        glGetIntegerv(GL_MAX_DEPTH_TEXTURE_SAMPLES, &depth);
        return (GLEW_VERSION_4_0 || GLEW_ARB_draw_buffers_blend) && colors >= 4 && depth >= 4;
    }
    if (feature == 2) return GLEW_VERSION_4_6 || GLEW_ARB_polygon_offset_clamp || GLEW_EXT_polygon_offset_clamp;
    if (!(GLEW_VERSION_4_3 || (GLEW_ARB_compute_shader && GLEW_ARB_shader_storage_buffer_object
            && GLEW_ARB_shader_image_load_store))) return 0;
    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    const char* source = "#version 460\nlayout(local_size_x=1) in; void main() {}\n";
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint status = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    glDeleteShader(shader);
    return status != 0;
}

extern "C" int32_t fdx_jn_gl_compute_program(jn_handle sourceHandle) {
    std::string text = java_text(sourceHandle);
    const char* source = text.c_str();
    GLuint shader = glCreateShader(GL_COMPUTE_SHADER);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint status = 0;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &status);
    if (!status) {
        char message[8192]{};
        glGetShaderInfoLog(shader, sizeof(message), nullptr, message);
        std::fprintf(stderr, "OpenGL compute compilation: %s\n", message);
        glDeleteShader(shader);
        return 0;
    }
    GLuint program = glCreateProgram();
    glAttachShader(program, shader);
    glLinkProgram(program);
    glDeleteShader(shader);
    glGetProgramiv(program, GL_LINK_STATUS, &status);
    if (!status) {
        char message[8192]{};
        glGetProgramInfoLog(program, sizeof(message), nullptr, message);
        std::fprintf(stderr, "OpenGL compute linking: %s\n", message);
        glDeleteProgram(program);
        return 0;
    }
    return program;
}

extern "C" void fdx_jn_gl_compute_buffer(int32_t slot, int32_t buffer, int32_t offset, int32_t size, int32_t uniform) {
    glBindBufferRange(uniform ? GL_UNIFORM_BUFFER : GL_SHADER_STORAGE_BUFFER, slot, buffer, offset, size);
}
extern "C" void fdx_jn_gl_storage_image(int32_t slot, int32_t texture, int32_t format) {
    glBindImageTexture(slot, texture, 0, GL_FALSE, 0, GL_READ_WRITE, format);
}
extern "C" void fdx_jn_gl_dispatch_compute(int32_t x, int32_t y, int32_t z) { glDispatchCompute(x, y, z); }
extern "C" void fdx_jn_gl_compute_barrier() { glMemoryBarrier(GL_ALL_BARRIER_BITS); }
extern "C" void fdx_jn_gl_copy_buffer(int32_t source, int32_t sourceOffset, int32_t destination, int32_t destinationOffset, int32_t size) {
    glBindBuffer(GL_COPY_READ_BUFFER, source);
    glBindBuffer(GL_COPY_WRITE_BUFFER, destination);
    glCopyBufferSubData(GL_COPY_READ_BUFFER, GL_COPY_WRITE_BUFFER, sourceOffset, destinationOffset, size);
    glBindBuffer(GL_COPY_READ_BUFFER, 0);
    glBindBuffer(GL_COPY_WRITE_BUFFER, 0);
}
extern "C" void fdx_jn_gl_read_buffer(int32_t source, int32_t offset, jn_handle output, int32_t size) {
    glBindBuffer(GL_COPY_READ_BUFFER, source);
    glGetBufferSubData(GL_COPY_READ_BUFFER, offset, size, direct_bytes(output, size, true));
    glBindBuffer(GL_COPY_READ_BUFFER, 0);
}
extern "C" void fdx_jn_gl_texture_multisample(int32_t texture, int32_t format, int32_t width, int32_t height, int32_t samples) {
    glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, texture);
    glTexImage2DMultisample(GL_TEXTURE_2D_MULTISAMPLE, samples, format, width, height, GL_TRUE);
    glBindTexture(GL_TEXTURE_2D_MULTISAMPLE, 0);
}
extern "C" void fdx_jn_gl_framebuffer_texture(int32_t index, int32_t texture, int32_t level, int32_t samples) {
    glFramebufferTexture2D(GL_FRAMEBUFFER, index < 0 ? GL_DEPTH_ATTACHMENT : GL_COLOR_ATTACHMENT0 + index,
            samples > 1 ? GL_TEXTURE_2D_MULTISAMPLE : GL_TEXTURE_2D, texture, level);
}
extern "C" void fdx_jn_gl_draw_buffers(int32_t count) {
    if (count < 0 || count > 8) throw std::invalid_argument("Invalid draw buffer count");
    GLenum buffers[8];
    for (int i = 0; i < count; ++i) buffers[i] = GL_COLOR_ATTACHMENT0 + i;
    glDrawBuffers(count, buffers);
}
extern "C" void fdx_jn_gl_clear_attachment(int32_t index, float red, float green, float blue, float alpha) {
    const GLfloat color[] = {red, green, blue, alpha};
    glClearBufferfv(GL_COLOR, index, color);
}
extern "C" void fdx_jn_gl_depth_multisample(int32_t width, int32_t height, int32_t samples) {
    glRenderbufferStorageMultisample(GL_RENDERBUFFER, samples, GL_DEPTH_COMPONENT32F, width, height);
}
extern "C" void fdx_jn_gl_resolve_framebuffer(int32_t source, int32_t index, int32_t destination, int32_t width, int32_t height) {
    glBindFramebuffer(GL_READ_FRAMEBUFFER, source);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, destination);
    glReadBuffer(GL_COLOR_ATTACHMENT0 + index);
    glDrawBuffer(GL_COLOR_ATTACHMENT0);
    glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST);
}
extern "C" void fdx_jn_gl_reset_masks() {
    glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
    glDepthMask(GL_TRUE);
    glStencilMask(~0u);
}
extern "C" void fdx_jn_gl_pipeline_state(jn_handle packed) {
    NativeArray<int32_t> values(packed);
    values.require(21);
    const int32_t* p = values.data();
    enable(GL_CULL_FACE, p[0] != 0);
    glCullFace(p[0] == 1 ? GL_FRONT : GL_BACK);
    glFrontFace(p[1] == 0 ? GL_CCW : GL_CW);
    enable(GL_SAMPLE_MASK, p[2] != -1);
    glSampleMaski(0, static_cast<GLuint>(p[2]));
    enable(GL_SAMPLE_ALPHA_TO_COVERAGE, p[3] != 0);
    enable(GL_DEPTH_TEST, p[4] != 0);
    glDepthMask(p[5] != 0);
    if (p[4]) glDepthFunc(GL_NEVER + p[6]);
    enable(GL_STENCIL_TEST, p[7] != 0);
    if (p[7]) {
        glStencilMask(p[9]);
        for (int i = 0; i < 2; ++i) {
            GLenum face = i == 0 ? GL_FRONT : GL_BACK;
            int offset = 13 + i * 4;
            glStencilFuncSeparate(face, GL_NEVER + p[offset], 0, p[8]);
            glStencilOpSeparate(face, stencil(p[offset + 1]), stencil(p[offset + 2]), stencil(p[offset + 3]));
        }
    }
    float slope, clamp;
    std::memcpy(&slope, p + 11, sizeof(float));
    std::memcpy(&clamp, p + 12, sizeof(float));
    bool bias = p[10] != 0 || slope != 0;
    enable(GL_POLYGON_OFFSET_FILL, bias);
    if (bias) {
        if (GLEW_VERSION_4_6 || GLEW_ARB_polygon_offset_clamp) glPolygonOffsetClamp(slope, static_cast<float>(p[10]), clamp);
        else if (GLEW_EXT_polygon_offset_clamp) glPolygonOffsetClampEXT(slope, static_cast<float>(p[10]), clamp);
        else if (clamp == 0) glPolygonOffset(slope, static_cast<float>(p[10]));
        else throw std::runtime_error("OpenGL polygon offset clamp is unavailable");
    }
}
extern "C" void fdx_jn_gl_color_targets(jn_handle packed, int32_t count) {
    NativeArray<int32_t, 64> values(packed);
    values.require(count * 8);
    for (int i = 0; i < count; ++i) {
        const int32_t* p = values.data() + i * 8;
        glColorMaski(i, (p[1] & 1) != 0, (p[1] & 2) != 0, (p[1] & 4) != 0, (p[1] & 8) != 0);
        if (!p[0]) glDisablei(GL_BLEND, i);
        else {
            glEnablei(GL_BLEND, i);
            if (GLEW_VERSION_4_0) {
                glBlendFuncSeparatei(i, factor(p[2]), factor(p[3]), factor(p[5]), factor(p[6]));
                glBlendEquationSeparatei(i, operation(p[4]), operation(p[7]));
            } else if (GLEW_ARB_draw_buffers_blend) {
                glBlendFuncSeparateiARB(i, factor(p[2]), factor(p[3]), factor(p[5]), factor(p[6]));
                glBlendEquationSeparateiARB(i, operation(p[4]), operation(p[7]));
            } else if (count == 1) {
                glBlendFuncSeparate(factor(p[2]), factor(p[3]), factor(p[5]), factor(p[6]));
                glBlendEquationSeparate(operation(p[4]), operation(p[7]));
            } else {
                throw std::runtime_error("OpenGL independent target blending is unavailable");
            }
        }
    }
    glBlendColor(0, 0, 0, 0);
}
