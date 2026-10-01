#include "libfdx_jnative.hpp"
#include <GLFW/glfw3.h>

using namespace libfdx_cpp;

extern "C" int32_t fdx_jn_glfwInit() {
    return glfwInit();
}

extern "C" void fdx_jn_glfwTerminate() {
    glfwTerminate();
}

extern "C" void fdx_jn_glfwDefaultWindowHints() {
    glfwDefaultWindowHints();
}

extern "C" void fdx_jn_glfwWindowHint(int32_t hint, int32_t value) {
    glfwWindowHint(hint, value);
}

extern "C" int64_t fdx_jn_glfwCreateWindow(int32_t width, int32_t height, jn_handle title, int64_t monitor, int64_t share) {
    auto title_text = java_text(title);
    return static_cast<int64_t>(reinterpret_cast<intptr_t>(glfwCreateWindow(width, height, title_text.c_str(), reinterpret_cast<GLFWmonitor*>(static_cast<intptr_t>(monitor)), reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(share)))));
}

extern "C" void fdx_jn_glfwDestroyWindow(int64_t window) {
    glfwDestroyWindow(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)));
}

extern "C" void fdx_jn_glfwShowWindow(int64_t window) {
    glfwShowWindow(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)));
}

extern "C" void fdx_jn_glfwSetWindowTitle(int64_t window, jn_handle title) {
    auto title_text = java_text(title);
    glfwSetWindowTitle(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)), title_text.c_str());
}

extern "C" jn_handle fdx_jn_glfwGetClipboardString(int64_t window) {
    return string_handle(glfwGetClipboardString(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window))));
}

extern "C" void fdx_jn_glfwSetClipboardString(int64_t window, jn_handle text) {
    auto text_text = java_text(text);
    glfwSetClipboardString(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)), text_text.c_str());
}

extern "C" int32_t fdx_jn_glfwGetError(int64_t description) {
    return glfwGetError(reinterpret_cast<const char**>(static_cast<intptr_t>(description)));
}

extern "C" int32_t fdx_jn_glfwWindowShouldClose(int64_t window) {
    return glfwWindowShouldClose(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)));
}

extern "C" void fdx_jn_glfwSetWindowShouldClose(int64_t window, int32_t shouldClose) {
    glfwSetWindowShouldClose(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)), shouldClose);
}

extern "C" void fdx_jn_glfwPollEvents() {
    glfwPollEvents();
}

extern "C" void fdx_jn_glfwWaitEventsTimeout(double timeoutSeconds) {
    glfwWaitEventsTimeout(timeoutSeconds);
}

extern "C" void fdx_jn_glfwMakeContextCurrent(int64_t window) {
    glfwMakeContextCurrent(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)));
}

extern "C" void fdx_jn_glfwSwapInterval(int32_t interval) {
    glfwSwapInterval(interval);
}

extern "C" void fdx_jn_glfwSwapBuffers(int64_t window) {
    glfwSwapBuffers(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)));
}

extern "C" void fdx_jn_glfwGetWindowSize(int64_t window, jn_handle width, jn_handle height) {
    NativeArray<int32_t> width_array(width);
    width_array.require(1);
    NativeArray<int32_t> height_array(height);
    height_array.require(1);
    glfwGetWindowSize(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)), width_array.data(), height_array.data());
    width_array.write();
    height_array.write();
}

extern "C" void fdx_jn_glfwGetFramebufferSize(int64_t window, jn_handle width, jn_handle height) {
    NativeArray<int32_t> width_array(width);
    width_array.require(1);
    NativeArray<int32_t> height_array(height);
    height_array.require(1);
    glfwGetFramebufferSize(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)), width_array.data(), height_array.data());
    width_array.write();
    height_array.write();
}

extern "C" void fdx_jn_glfwGetWindowContentScale(int64_t window, jn_handle scaleX, jn_handle scaleY) {
    NativeArray<float> scaleX_array(scaleX);
    scaleX_array.require(1);
    NativeArray<float> scaleY_array(scaleY);
    scaleY_array.require(1);
    glfwGetWindowContentScale(reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(window)), scaleX_array.data(), scaleY_array.data());
    scaleX_array.write();
    scaleY_array.write();
}

extern "C" int32_t fdx_jn_glfwVulkanSupported() {
    return glfwVulkanSupported();
}

extern "C" int32_t fdx_jn_glfwGetRequiredInstanceExtensions() {
    uint32_t count = 0;
    return glfwGetRequiredInstanceExtensions(&count) ? static_cast<int32_t>(count) : 0;
}
