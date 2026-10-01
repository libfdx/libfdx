#include "libfdx_jnative.hpp"
#include <GLFW/glfw3.h>
#include <deque>
#include <memory>

namespace {
struct Input {
    std::deque<std::array<double, 8> > events;
    double cursorX = 0;
    double cursorY = 0;
    GLFWcursor* cursors[8] = {};
    ~Input() { for (auto cursor : cursors) if (cursor) glfwDestroyCursor(cursor); }
};
GLFWwindow* window(int64_t value) { return reinterpret_cast<GLFWwindow*>(static_cast<intptr_t>(value)); }
Input* input(GLFWwindow* value) { return static_cast<Input*>(glfwGetWindowUserPointer(value)); }
void event(GLFWwindow* value, int kind, double a, double b) {
    int left, top;
    auto state = input(value);
    double x = state->cursorX, y = state->cursorY;
    glfwGetWindowPos(value, &left, &top);
    input(value)->events.push_back({{static_cast<double>(kind), a, b, x, y, x + left, y + top, 0}});
}
void key(GLFWwindow* value, int key_code, int, int action, int) { event(value, 1, key_code, action); }
void text(GLFWwindow* value, unsigned codepoint) { event(value, 2, codepoint, 0); }
void motion(GLFWwindow* value, double x, double y) {
    auto state = input(value);
    state->cursorX = x;
    state->cursorY = y;
    event(value, 3, 0, 0);
}
void button(GLFWwindow* value, int button_code, int action, int) { event(value, 4, button_code, action); }
void scroll(GLFWwindow* value, double x, double y) { event(value, 5, x, y); }
void focus(GLFWwindow* value, int focused) { event(value, 6, focused, 0); }
void display_event(GLFWwindow* value, int kind, double x, double y) {
    input(value)->events.push_back({{static_cast<double>(kind), x, y, 0, 0, 0, 0, 0}});
}
void window_size(GLFWwindow* value, int width, int height) { display_event(value, 7, width, height); }
void framebuffer_size(GLFWwindow* value, int width, int height) { display_event(value, 8, width, height); }
void content_scale(GLFWwindow* value, float x, float y) { display_event(value, 9, x, y); }
}

extern "C" void fdx_cpp_input_install(int64_t handle) {
    auto value = window(handle);
    if (glfwGetWindowUserPointer(value)) throw std::logic_error("Native window input already installed");
    std::unique_ptr<Input> state(new Input());
    // Seed once; later OS queries may see a position beyond the current queued callback.
    glfwGetCursorPos(value, &state->cursorX, &state->cursorY);
    glfwSetWindowUserPointer(value, state.release());
    glfwSetKeyCallback(value, key);
    glfwSetCharCallback(value, text);
    glfwSetCursorPosCallback(value, motion);
    glfwSetMouseButtonCallback(value, button);
    glfwSetScrollCallback(value, scroll);
    glfwSetWindowFocusCallback(value, focus);
    glfwSetWindowSizeCallback(value, window_size);
    glfwSetFramebufferSizeCallback(value, framebuffer_size);
    glfwSetWindowContentScaleCallback(value, content_scale);
}

// The caller owns a live window and drains this queue on the GLFW application thread.
extern "C" int32_t fdx_cpp_input_pending(int64_t handle) {
    auto state = input(window(handle));
    return state && !state->events.empty();
}

extern "C" int32_t fdx_cpp_input_next(int64_t handle, jn_handle target) {
    auto state = input(window(handle));
    if (!state || state->events.empty()) return 0;
    libfdx_cpp::NativeArray<double> data(target);
    data.require(8);
    for (int i = 0; i < 8; ++i) data.data()[i] = state->events.front()[i];
    data.write();
    state->events.pop_front();
    return 1;
}

extern "C" void fdx_cpp_input_cursor(int64_t handle, int32_t mode, int32_t shape) {
    if (shape < 0 || shape >= 8 || mode < 0 || mode > 2) throw std::invalid_argument("Invalid cursor");
    auto value = window(handle);
    auto state = input(value);
    if (!state) throw std::logic_error("Native window input is not installed");
    const int shapes[] = {GLFW_ARROW_CURSOR, GLFW_POINTING_HAND_CURSOR, GLFW_IBEAM_CURSOR,
        GLFW_CROSSHAIR_CURSOR, GLFW_RESIZE_ALL_CURSOR, GLFW_HRESIZE_CURSOR, GLFW_VRESIZE_CURSOR,
        GLFW_NOT_ALLOWED_CURSOR};
    if (!state->cursors[shape]) state->cursors[shape] = glfwCreateStandardCursor(shapes[shape]);
    glfwSetCursor(value, state->cursors[shape]);
    glfwSetInputMode(value, GLFW_CURSOR, mode == 2 ? GLFW_CURSOR_DISABLED : mode == 1 ? GLFW_CURSOR_HIDDEN : GLFW_CURSOR_NORMAL);
}

extern "C" void fdx_cpp_input_uninstall(int64_t handle) {
    auto value = window(handle);
    glfwSetKeyCallback(value, nullptr);
    glfwSetCharCallback(value, nullptr);
    glfwSetCursorPosCallback(value, nullptr);
    glfwSetMouseButtonCallback(value, nullptr);
    glfwSetScrollCallback(value, nullptr);
    glfwSetWindowFocusCallback(value, nullptr);
    glfwSetWindowSizeCallback(value, nullptr);
    glfwSetFramebufferSizeCallback(value, nullptr);
    glfwSetWindowContentScaleCallback(value, nullptr);
    glfwSetCursor(value, nullptr);
    delete input(value);
    glfwSetWindowUserPointer(value, nullptr);
}
