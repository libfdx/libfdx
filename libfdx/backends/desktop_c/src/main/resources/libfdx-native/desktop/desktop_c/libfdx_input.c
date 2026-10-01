#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include "libfdx_input.h"
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

typedef struct {
    GLFWwindow* window;
    double cursorX;
    double cursorY;
    GLFWcursorposfun previousMotion;
    GLFWmousebuttonfun previousButton;
    GLFWscrollfun previousScroll;
    GLFWwindowfocusfun previousFocus;
    double (*events)[8];
    size_t capacity;
    size_t head;
    size_t count;
    int failed;
} FdxInputQueue;

static int fdxInputGrow(FdxInputQueue* queue) {
    size_t capacity;
    size_t first;
    double (*events)[8];
    if (queue->capacity > SIZE_MAX / 2 / sizeof(*events)) return 0;
    capacity = queue->capacity * 2;
    events = malloc(capacity * sizeof(*events));
    if (!events) return 0;
    first = queue->capacity - queue->head;
    if (first > queue->count) first = queue->count;
    memcpy(events, queue->events + queue->head, first * sizeof(*events));
    memcpy(events + first, queue->events, (queue->count - first) * sizeof(*events));
    free(queue->events);
    queue->events = events;
    queue->capacity = capacity;
    queue->head = 0;
    return 1;
}

static void fdxInputEvent(FdxInputQueue* queue, int kind, double value, double action, double x, double y) {
    if (!queue->failed) {
        if (queue->count == queue->capacity && !fdxInputGrow(queue)) {
            queue->failed = 1;
        } else {
            int windowX, windowY;
            double* event = queue->events[(queue->head + queue->count) % queue->capacity];
            event[0] = kind;
            event[1] = value;
            event[2] = action;
            event[3] = x;
            event[4] = y;
            glfwGetWindowPos(queue->window, &windowX, &windowY);
            event[5] = windowX + x;
            event[6] = windowY + y;
            event[7] = 0;
            queue->count++;
        }
    }
}

static void fdxInputMotion(GLFWwindow* window, double x, double y) {
    FdxInputQueue* queue = glfwGetWindowUserPointer(window);
    GLFWcursorposfun previous;
    if (!queue) return;
    previous = queue->previousMotion;
    queue->cursorX = x;
    queue->cursorY = y;
    fdxInputEvent(queue, 3, 0, 0, x, y);
    if (previous) previous(window, x, y);
}

static void fdxInputButton(GLFWwindow* window, int button, int action, int modifiers) {
    FdxInputQueue* queue = glfwGetWindowUserPointer(window);
    GLFWmousebuttonfun previous;
    if (!queue) return;
    previous = queue->previousButton;
    fdxInputEvent(queue, 4, button, action, queue->cursorX, queue->cursorY);
    if (previous) previous(window, button, action, modifiers);
}

static void fdxInputScroll(GLFWwindow* window, double scrollX, double scrollY) {
    FdxInputQueue* queue = glfwGetWindowUserPointer(window);
    GLFWscrollfun previous;
    if (!queue) return;
    previous = queue->previousScroll;
    fdxInputEvent(queue, 5, scrollX, scrollY, queue->cursorX, queue->cursorY);
    if (previous) previous(window, scrollX, scrollY);
}

static void fdxInputFocus(GLFWwindow* window, int focused) {
    FdxInputQueue* queue = glfwGetWindowUserPointer(window);
    GLFWwindowfocusfun previous;
    if (!queue) return;
    previous = queue->previousFocus;
    fdxInputEvent(queue, 6, focused, 0, queue->cursorX, queue->cursorY);
    if (previous) previous(window, focused);
}

void* fdxInputInstall(void* windowHandle) {
    GLFWwindow* window = windowHandle;
    FdxInputQueue* queue;
    if (!window || glfwGetWindowUserPointer(window)) return NULL;
    queue = calloc(1, sizeof(*queue));
    if (!queue) return NULL;
    queue->capacity = 32;
    queue->events = malloc(queue->capacity * sizeof(*queue->events));
    if (!queue->events) {
        free(queue);
        return NULL;
    }
    queue->window = window;
    // A later cursor query can already see the final OS position during queued callbacks.
    glfwGetCursorPos(window, &queue->cursorX, &queue->cursorY);
    glfwSetWindowUserPointer(window, queue);
    queue->previousMotion = glfwSetCursorPosCallback(window, fdxInputMotion);
    queue->previousButton = glfwSetMouseButtonCallback(window, fdxInputButton);
    queue->previousScroll = glfwSetScrollCallback(window, fdxInputScroll);
    queue->previousFocus = glfwSetWindowFocusCallback(window, fdxInputFocus);
    return queue;
}

int fdxInputPoll(void* handle, double* event) {
    FdxInputQueue* queue = handle;
    if (!queue) return 0;
    if (queue->failed) return -1;
    if (!queue->count) return 0;
    memcpy(event, queue->events[queue->head], sizeof(*queue->events));
    queue->head = (queue->head + 1) % queue->capacity;
    queue->count--;
    return 1;
}

void fdxInputDispose(void* handle) {
    FdxInputQueue* queue = handle;
    if (!queue) return;
    glfwSetCursorPosCallback(queue->window, queue->previousMotion);
    glfwSetMouseButtonCallback(queue->window, queue->previousButton);
    glfwSetScrollCallback(queue->window, queue->previousScroll);
    glfwSetWindowFocusCallback(queue->window, queue->previousFocus);
    glfwSetWindowUserPointer(queue->window, NULL);
    free(queue->events);
    free(queue);
}
