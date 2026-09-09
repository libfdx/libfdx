#include <jni.h>
#include <cstdint>
#include <cstring>
#include <EGL/egl.h>
#include <GLES3/gl3.h>

namespace {
bool hasExtension(const char* extensions, const char* name) {
    if (!extensions) return false;
    const size_t length = std::strlen(name);
    const char* match = extensions;
    while ((match = std::strstr(match, name))) {
        if ((match == extensions || match[-1] == ' ')
                && (match[length] == '\0' || match[length] == ' ')) return true;
        match += length;
    }
    return false;
}
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_libfdx_backend_android_AndroidGlesApi_findResetStatusQuery(JNIEnv*, jclass) {
    GLint major = 0, minor = 0;
    glGetIntegerv(GL_MAJOR_VERSION, &major);
    glGetIntegerv(GL_MINOR_VERSION, &minor);
    const char* extensions = reinterpret_cast<const char*>(glGetString(GL_EXTENSIONS));
    const char* name = nullptr;
    if (major > 3 || (major == 3 && minor >= 2)) name = "glGetGraphicsResetStatus";
    else if (hasExtension(extensions, "GL_KHR_robustness")) name = "glGetGraphicsResetStatusKHR";
    else if (hasExtension(extensions, "GL_EXT_robustness")) name = "glGetGraphicsResetStatusEXT";
    if (!name) return 0; // No supported reset-status API on this context.
    auto query = eglGetProcAddress(name);
    return query ? static_cast<jlong>(reinterpret_cast<intptr_t>(query)) : -1;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_libfdx_backend_android_AndroidGlesApi_graphicsResetStatus(JNIEnv*, jclass, jlong address) {
    using ResetStatus = GLenum (GL_APIENTRY*)();
    auto query = reinterpret_cast<ResetStatus>(static_cast<intptr_t>(address));
    return static_cast<jint>(query());
}
