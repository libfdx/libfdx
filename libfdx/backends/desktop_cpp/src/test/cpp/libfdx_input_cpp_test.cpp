/* Compile with desktop_cpp, cpp_shared/cpp and bundled GLFW include directories.
 * GLFW/managed-array stubs exercise the production native event queue without a display.
 */
#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <array>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <stdexcept>

/* Skip the managed runtime header; only its output-array boundary is stubbed. */
#define LIBFDX_JNATIVE_HPP
using jn_handle = std::int64_t;
namespace libfdx_cpp {
template<class T> class NativeArray {
    T* values;
public:
    explicit NativeArray(jn_handle handle) : values(reinterpret_cast<T*>(static_cast<intptr_t>(handle))) {}
    T* data() { return values; }
    void require(int count) { assert(count == 8); }
    void write() {}
};
}

struct GLFWwindow {
    void* user = nullptr;
    double cursorX = 10, cursorY = 20;
    int left = -300, top = 400;
    GLFWkeyfun key = nullptr;
    GLFWcharfun text = nullptr;
    GLFWcursorposfun motion = nullptr;
    GLFWmousebuttonfun button = nullptr;
    GLFWscrollfun scroll = nullptr;
    GLFWwindowfocusfun focus = nullptr;
    GLFWwindowsizefun size = nullptr;
    GLFWframebuffersizefun framebuffer = nullptr;
    GLFWwindowcontentscalefun scale = nullptr;
};
static int cursorQueries;
void* glfwGetWindowUserPointer(GLFWwindow* window) { return window->user; }
void glfwSetWindowUserPointer(GLFWwindow* window, void* user) { window->user = user; }
void glfwGetCursorPos(GLFWwindow* window, double* x, double* y) {
    cursorQueries++;
    *x = window->cursorX; *y = window->cursorY;
}
void glfwGetWindowPos(GLFWwindow* window, int* x, int* y) { *x = window->left; *y = window->top; }
#define CALLBACK_SETTER(name, type, member) \
type name(GLFWwindow* window, type callback) { \
    type previous = window->member; window->member = callback; return previous; \
}
CALLBACK_SETTER(glfwSetKeyCallback, GLFWkeyfun, key)
CALLBACK_SETTER(glfwSetCharCallback, GLFWcharfun, text)
CALLBACK_SETTER(glfwSetCursorPosCallback, GLFWcursorposfun, motion)
CALLBACK_SETTER(glfwSetMouseButtonCallback, GLFWmousebuttonfun, button)
CALLBACK_SETTER(glfwSetScrollCallback, GLFWscrollfun, scroll)
CALLBACK_SETTER(glfwSetWindowFocusCallback, GLFWwindowfocusfun, focus)
CALLBACK_SETTER(glfwSetWindowSizeCallback, GLFWwindowsizefun, size)
CALLBACK_SETTER(glfwSetFramebufferSizeCallback, GLFWframebuffersizefun, framebuffer)
CALLBACK_SETTER(glfwSetWindowContentScaleCallback, GLFWwindowcontentscalefun, scale)
#undef CALLBACK_SETTER
void glfwDestroyCursor(GLFWcursor*) {}
GLFWcursor* glfwCreateStandardCursor(int) { return nullptr; }
void glfwSetCursor(GLFWwindow*, GLFWcursor*) {}
void glfwSetInputMode(GLFWwindow*, int, int) {}

#include "libfdx_input_cpp.cpp"

static void expectEvent(int64_t handle, int kind, double value, double action, double x, double y) {
    double values[8] = {};
    assert(fdx_cpp_input_pending(handle));
    assert(fdx_cpp_input_next(handle, reinterpret_cast<intptr_t>(values)));
    assert(values[0] == kind && values[1] == value && values[2] == action);
    assert(values[3] == x && values[4] == y);
    assert(values[5] == x - 300 && values[6] == y + 400);
}

int main() {
    GLFWwindow window;
    int64_t handle = reinterpret_cast<intptr_t>(&window);
    fdx_cpp_input_install(handle);
    assert(cursorQueries == 1);
    /* OS getter sees the final position throughout callback dispatch. */
    window.cursorX = 25; window.cursorY = 40;
    window.button(&window, 0, GLFW_PRESS, 0);
    window.motion(&window, 15.5, 28.75);
    window.scroll(&window, 0.25, -0.5);
    window.motion(&window, 19, 35);
    window.button(&window, 0, GLFW_RELEASE, 0);
    window.focus(&window, GLFW_FALSE);
    window.motion(&window, 25, 40);
    assert(cursorQueries == 1);
    expectEvent(handle, 4, 0, GLFW_PRESS, 10, 20);
    expectEvent(handle, 3, 0, 0, 15.5, 28.75);
    expectEvent(handle, 5, 0.25, -0.5, 15.5, 28.75);
    expectEvent(handle, 3, 0, 0, 19, 35);
    expectEvent(handle, 4, 0, GLFW_RELEASE, 19, 35);
    expectEvent(handle, 6, GLFW_FALSE, 0, 19, 35);
    expectEvent(handle, 3, 0, 0, 25, 40);
    assert(!fdx_cpp_input_pending(handle));
    fdx_cpp_input_uninstall(handle);
    assert(!window.user && !window.motion && !window.button && !window.scroll && !window.focus);
    puts("libfdx_input_cpp_test: queued pointer coordinates, ordering and teardown passed");
}
