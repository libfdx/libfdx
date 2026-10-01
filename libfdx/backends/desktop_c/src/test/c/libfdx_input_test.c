/* Compile with the desktop_c native resource and bundled GLFW include directories.
 * GLFW stubs exercise the production shim without a display or window system.
 */
#define GLFW_INCLUDE_NONE
#include <GLFW/glfw3.h>
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>

struct GLFWwindow {
    void* user;
    GLFWscrollfun scroll;
    GLFWcursorposfun motion;
    GLFWmousebuttonfun button;
    GLFWwindowfocusfun focus;
    double cursorX, cursorY;
    int x, y;
};

void* glfwGetWindowUserPointer(GLFWwindow* window) { return window->user; }
void glfwSetWindowUserPointer(GLFWwindow* window, void* user) { window->user = user; }
GLFWscrollfun glfwSetScrollCallback(GLFWwindow* window, GLFWscrollfun callback) {
    GLFWscrollfun previous = window->scroll;
    window->scroll = callback;
    return previous;
}
GLFWcursorposfun glfwSetCursorPosCallback(GLFWwindow* window, GLFWcursorposfun callback) {
    GLFWcursorposfun previous = window->motion;
    window->motion = callback;
    return previous;
}
GLFWmousebuttonfun glfwSetMouseButtonCallback(GLFWwindow* window, GLFWmousebuttonfun callback) {
    GLFWmousebuttonfun previous = window->button;
    window->button = callback;
    return previous;
}
GLFWwindowfocusfun glfwSetWindowFocusCallback(GLFWwindow* window, GLFWwindowfocusfun callback) {
    GLFWwindowfocusfun previous = window->focus;
    window->focus = callback;
    return previous;
}
static int cursorQueries;
void glfwGetCursorPos(GLFWwindow* window, double* x, double* y) {
    cursorQueries++;
    *x = window->cursorX;
    *y = window->cursorY;
}
void glfwGetWindowPos(GLFWwindow* window, int* x, int* y) { *x = window->x; *y = window->y; }

static int failAfter = -1;
static int liveAllocations;
static int allocationCalls;
static int shouldFail(void) {
    allocationCalls++;
    if (failAfter < 0) return 0;
    if (failAfter-- == 0) { failAfter = -1; return 1; }
    return 0;
}
static void* testMalloc(size_t size) {
    void* memory;
    if (shouldFail()) return NULL;
    memory = malloc(size);
    if (memory) liveAllocations++;
    return memory;
}
static void* testCalloc(size_t count, size_t size) {
    void* memory;
    if (shouldFail()) return NULL;
    memory = calloc(count, size);
    if (memory) liveAllocations++;
    return memory;
}
static void testFree(void* memory) {
    if (memory) liveAllocations--;
    free(memory);
}

#define malloc testMalloc
#define calloc testCalloc
#define free testFree
#include "libfdx_input.c"
#undef malloc
#undef calloc
#undef free

static int previousCalls;
static int motionCalls, buttonCalls, focusCalls;
static void previousScroll(GLFWwindow* window, double x, double y) {
    (void) window; (void) x; (void) y;
    previousCalls++;
}
static void previousMotion(GLFWwindow* window, double x, double y) {
    (void) window; (void) x; (void) y;
    motionCalls++;
}
static void previousButton(GLFWwindow* window, int button, int action, int modifiers) {
    (void) window; (void) button; (void) action;
    assert(modifiers == 4);
    buttonCalls++;
}
static void previousFocus(GLFWwindow* window, int focused) {
    (void) window; (void) focused;
    focusCalls++;
}
static void send(GLFWwindow* window, int index) {
    window->cursorX = index + 0.5;
    window->cursorY = index + 0.75;
    window->x = -300 - index;
    window->y = 400 + index;
    window->scroll(window, index * 0.25, -index * 0.5);
}
static void expect(void* queue, int index) {
    double event[8];
    assert(fdxInputPoll(queue, event) == 1);
    assert(event[0] == 5);
    assert(event[1] == index * 0.25);
    assert(event[2] == -index * 0.5);
    /* The getter changes without a motion callback: cached initial coordinates remain authoritative. */
    assert(event[3] == 0);
    assert(event[4] == 0);
    assert(event[5] == -300 - index);
    assert(event[6] == 400 + index);
    assert(event[7] == 0);
}

static void expectPointer(void* queue, int kind, double value, double action, double x, double y) {
    double event[8];
    assert(fdxInputPoll(queue, event) == 1);
    assert(event[0] == kind && event[1] == value && event[2] == action);
    assert(event[3] == x && event[4] == y);
    assert(event[5] == -300 + x && event[6] == 400 + y);
    assert(event[7] == 0);
}

int main(void) {
    GLFWwindow first = {0}, second = {0};
    void* queue;
    void* other;
    double event[8];
    int index, before;
    first.scroll = previousScroll;
    first.motion = previousMotion;
    first.button = previousButton;
    first.focus = previousFocus;

    /* Refuse occupied user pointer, and leave callbacks/state untouched. */
    first.user = &second;
    assert(fdxInputInstall(&first) == NULL);
    assert(first.user == &second && first.scroll == previousScroll);
    first.user = NULL;
    assert(fdxInputInstall(NULL) == NULL);

    /* Both installation allocations fail safely. */
    for (index = 0; index < 2; index++) {
        failAfter = index;
        assert(fdxInputInstall(&first) == NULL);
        assert(first.user == NULL && first.scroll == previousScroll);
        assert(liveAllocations == 0);
    }

    queue = fdxInputInstall(&first);
    other = fdxInputInstall(&second);
    assert(queue && other);
    assert(fdxInputInstall(&first) == NULL);
    for (index = 0; index < 32; index++) send(&first, index);
    for (index = 0; index < 20; index++) expect(queue, index);
    /* Growth after wrap must retain FIFO and event-time coordinates. */
    for (index = 32; index < 76; index++) send(&first, index);
    send(&second, 999);
    for (index = 20; index < 76; index++) expect(queue, index);
    assert(fdxInputPoll(queue, event) == 0);
    expect(other, 999);
    assert(previousCalls == 76);

    /* Repeated drains/scrolls reuse allocated storage. */
    before = allocationCalls;
    for (index = 0; index < 1000; index++) { send(&first, index); expect(queue, index); }
    assert(allocationCalls == before);
    fdxInputDispose(queue);
    fdxInputDispose(other);
    fdxInputDispose(NULL);
    assert(first.user == NULL && first.scroll == previousScroll);
    assert(first.motion == previousMotion && first.button == previousButton && first.focus == previousFocus);
    assert(second.user == NULL && second.scroll == NULL);
    assert(second.motion == NULL && second.button == NULL && second.focus == NULL);
    assert(liveAllocations == 0);

    /* A failed burst allocation is sticky and reported outside callback. */
    queue = fdxInputInstall(&first);
    for (index = 0; index < 32; index++) send(&first, index);
    failAfter = 0;
    send(&first, 32);
    assert(fdxInputPoll(queue, event) == -1);
    before = allocationCalls;
    send(&first, 33);
    assert(fdxInputPoll(queue, event) == -1);
    assert(allocationCalls == before);
    assert(previousCalls == 1110);
    fdxInputDispose(queue);
    assert(first.user == NULL && first.scroll == previousScroll);
    assert(liveAllocations == 0);

    /* All pointer callbacks share one ordered queue and capture their own coordinates. */
    first.x = -300; first.y = 400;
    first.cursorX = 10; first.cursorY = 20;
    queue = fdxInputInstall(&first);
    before = cursorQueries;
    /* Simulate all queued callbacks being dispatched after the OS cursor reached its final position. */
    first.cursorX = 500; first.cursorY = 600;
    first.button(&first, 0, 1, 4);
    /* Motion must use its callback arguments, even if queried cursor state differs. */
    first.motion(&first, 15.5, 28.75);
    first.motion(&first, 19, 35);
    first.scroll(&first, 0.25, -0.5);
    first.button(&first, 0, 0, 4);
    first.focus(&first, 0);
    first.motion(&first, 25, 40);
    assert(cursorQueries == before);
    /* Movement after callbacks must not change queued event coordinates. */
    first.x = 900; first.y = 1000;
    first.cursorX = 500; first.cursorY = 600;
    expectPointer(queue, 4, 0, 1, 10, 20);
    expectPointer(queue, 3, 0, 0, 15.5, 28.75);
    expectPointer(queue, 3, 0, 0, 19, 35);
    expectPointer(queue, 5, 0.25, -0.5, 19, 35);
    expectPointer(queue, 4, 0, 0, 19, 35);
    expectPointer(queue, 6, 0, 0, 19, 35);
    expectPointer(queue, 3, 0, 0, 25, 40);
    assert(fdxInputPoll(queue, event) == 0);
    assert(motionCalls == 3 && buttonCalls == 2 && focusCalls == 1);
    fdxInputDispose(queue);
    assert(first.user == NULL && first.scroll == previousScroll);
    assert(first.motion == previousMotion && first.button == previousButton && first.focus == previousFocus);
    assert(liveAllocations == 0);
    puts("libfdx_input_test: pointer order, coordinates, growth, failures and lifecycle passed");
    return 0;
}
