#ifndef LIBFDX_INPUT_H
#define LIBFDX_INPUT_H

#ifdef __cplusplus
extern "C" {
#endif

/* Application-thread-only. Install fails without altering an occupied user pointer.
 * Poll writes eight doubles: kind, value, action, cursor X/Y in window,
 * screen X/Y, reserved. Kinds: move=3, button=4, scroll=5, focus=6.
 * Scroll value/action are raw X/Y deltas. Result: 1=event, 0=empty,
 * -1=allocation failure (sticky, surfaced outside the GLFW callback).
 * Dispose must precede window destruction; a null handle is harmless.
 */
void* fdxInputInstall(void* window);
int fdxInputPoll(void* handle, double* event);
void fdxInputDispose(void* handle);

#ifdef __cplusplus
}
#endif
#endif
