#ifndef LIBFDX_TEST_PROCESS_H
#define LIBFDX_TEST_PROCESS_H

/* Test-owned same-executable spawning. All operations run on the chooser thread.
 * Arguments are length-delimited NUL-separated UTF-8 tokens, never a shell command.
 * Launch returns zero or an OS error; poll is nonblocking and reaps completed children.
 * Dispose releases tracking only; children continue after the chooser exits.
 */
#include <errno.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <wchar.h>
typedef HANDLE FdxTestChildHandle;
#else
#include <spawn.h>
#include <sys/wait.h>
#include <unistd.h>
#ifdef __APPLE__
#include <mach-o/dyld.h>
#endif
extern char** environ;
typedef pid_t FdxTestChildHandle;
#endif

typedef struct FdxTestChild {
    FdxTestChildHandle handle;
    struct FdxTestChild* next;
} FdxTestChild;
typedef struct { FdxTestChild* children; } FdxTestProcesses;

static inline void* fdxTestProcessCreate(void) { return calloc(1, sizeof(FdxTestProcesses)); }

#ifdef _WIN32
static inline wchar_t* fdxTestProcessUtf16(const char* value) {
    int count = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value, -1, NULL, 0);
    wchar_t* result;
    if (!count) return NULL;
    result = (wchar_t*) malloc((size_t) count * sizeof(wchar_t));
    if (!result) { SetLastError(ERROR_NOT_ENOUGH_MEMORY); return NULL; }
    if (!MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, value, -1, result, count)) {
        DWORD error = GetLastError(); free(result); SetLastError(error); return NULL;
    }
    return result;
}

/* Quote every token with the Windows CRT backslash/quote rules. */
static inline wchar_t* fdxTestProcessQuote(wchar_t* output, const wchar_t* value) {
    *output++ = L'"';
    while (*value) {
        size_t slashes = 0, i;
        while (*value == L'\\') { slashes++; value++; }
        if (*value == L'"') {
            for (i = 0; i < slashes * 2 + 1; i++) *output++ = L'\\';
            *output++ = *value++;
        } else if (!*value) {
            for (i = 0; i < slashes * 2; i++) *output++ = L'\\';
        } else {
            for (i = 0; i < slashes; i++) *output++ = L'\\';
            *output++ = *value++;
        }
    }
    *output++ = L'"';
    return output;
}

static inline HANDLE fdxTestProcessStandardHandle(DWORD kind) {
    HANDLE original = GetStdHandle(kind), inherited;
    if (!original || original == INVALID_HANDLE_VALUE) return NULL;
    if (!DuplicateHandle(GetCurrentProcess(), original, GetCurrentProcess(), &inherited,
            0, TRUE, DUPLICATE_SAME_ACCESS)) return INVALID_HANDLE_VALUE;
    return inherited;
}
#endif

static inline int fdxTestProcessLaunch(void* tracker, const unsigned char* packed, int length) {
    FdxTestProcesses* state = (FdxTestProcesses*) tracker;
    FdxTestChild* child;
    int count = 0, offset;
    if (!state || !packed || length <= 0 || packed[length - 1] != 0) return EINVAL;
    for (offset = 0; offset < length; offset++) if (!packed[offset]) count++;
    child = (FdxTestChild*) malloc(sizeof(*child));
    if (!child) return ENOMEM;
#ifdef _WIN32
    {
        wchar_t* executable = (wchar_t*) malloc(32768 * sizeof(wchar_t));
        wchar_t** arguments = (wchar_t**) calloc((size_t) count, sizeof(wchar_t*));
        wchar_t* command = NULL;
        wchar_t* cursor;
        size_t capacity;
        DWORD executableLength, error = 0;
        int index;
        STARTUPINFOW startup;
        PROCESS_INFORMATION process;
        HANDLE input = NULL, output = NULL, errors = NULL;
        if (!executable || !arguments) { error = ERROR_NOT_ENOUGH_MEMORY; goto windows_done; }
        executableLength = GetModuleFileNameW(NULL, executable, 32768);
        if (!executableLength || executableLength >= 32768) {
            error = executableLength >= 32768 ? ERROR_INSUFFICIENT_BUFFER : GetLastError();
            goto windows_done;
        }
        capacity = (size_t) executableLength * 2 + 4;
        offset = 0;
        for (index = 0; index < count; index++) {
            arguments[index] = fdxTestProcessUtf16((const char*) packed + offset);
            if (!arguments[index]) { error = GetLastError(); goto windows_done; }
            capacity += wcslen(arguments[index]) * 2 + 4;
            offset += (int) strlen((const char*) packed + offset) + 1;
        }
        if (capacity > 32767) { error = ERROR_BUFFER_OVERFLOW; goto windows_done; }
        command = (wchar_t*) malloc(capacity * sizeof(wchar_t));
        if (!command) { error = ERROR_NOT_ENOUGH_MEMORY; goto windows_done; }
        cursor = fdxTestProcessQuote(command, executable);
        for (index = 0; index < count; index++) {
            *cursor++ = L' ';
            cursor = fdxTestProcessQuote(cursor, arguments[index]);
        }
        *cursor = 0;
        memset(&startup, 0, sizeof(startup));
        memset(&process, 0, sizeof(process));
        startup.cb = sizeof(startup);
        input = fdxTestProcessStandardHandle(STD_INPUT_HANDLE);
        if (input == INVALID_HANDLE_VALUE) { error = GetLastError(); goto windows_done; }
        output = fdxTestProcessStandardHandle(STD_OUTPUT_HANDLE);
        if (output == INVALID_HANDLE_VALUE) { error = GetLastError(); goto windows_done; }
        errors = fdxTestProcessStandardHandle(STD_ERROR_HANDLE);
        if (errors == INVALID_HANDLE_VALUE) { error = GetLastError(); goto windows_done; }
        startup.dwFlags = STARTF_USESTDHANDLES;
        startup.hStdInput = input;
        startup.hStdOutput = output;
        startup.hStdError = errors;
        /* Inherit the existing cwd, environment and standard handles; create no console or pipes. */
        if (!CreateProcessW(executable, command, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &startup, &process)) {
            error = GetLastError(); goto windows_done;
        }
        CloseHandle(process.hThread);
        child->handle = process.hProcess;
windows_done:
        if (input && input != INVALID_HANDLE_VALUE) CloseHandle(input);
        if (output && output != INVALID_HANDLE_VALUE) CloseHandle(output);
        if (errors && errors != INVALID_HANDLE_VALUE) CloseHandle(errors);
        if (arguments) {
            for (index = 0; index < count; index++) free(arguments[index]);
        }
        free(arguments); free(command); free(executable);
        if (error) { free(child); return (int) error; }
    }
#else
    {
        char* executable = NULL;
        char** arguments = (char**) calloc((size_t) count + 2, sizeof(char*));
        int index, error = 0;
#ifdef __APPLE__
        uint32_t size = 0;
        char* unresolved;
        _NSGetExecutablePath(NULL, &size);
        unresolved = (char*) malloc(size);
        if (unresolved && !_NSGetExecutablePath(unresolved, &size)) executable = realpath(unresolved, NULL);
        free(unresolved);
#else
        size_t capacity = 256;
        for (;;) {
            ssize_t size;
            char* resized = (char*) realloc(executable, capacity);
            if (!resized) { error = ENOMEM; break; }
            executable = resized;
            size = readlink("/proc/self/exe", executable, capacity - 1);
            if (size < 0) { error = errno; break; }
            if ((size_t) size < capacity - 1) { executable[size] = 0; break; }
            if (capacity > SIZE_MAX / 2) { error = ENOMEM; break; }
            capacity *= 2;
        }
#endif
        if (!arguments || !executable) error = error ? error : ENOMEM;
        if (!error) {
            arguments[0] = executable;
            offset = 0;
            for (index = 0; index < count; index++) {
                arguments[index + 1] = (char*) packed + offset;
                offset += (int) strlen((const char*) packed + offset) + 1;
            }
            error = posix_spawn(&child->handle, executable, NULL, NULL, arguments, environ);
        }
        free(arguments); free(executable);
        if (error) { free(child); return error; }
    }
#endif
    child->next = state->children;
    state->children = child;
    return 0;
}

static inline int fdxTestProcessPoll(void* tracker) {
    FdxTestProcesses* state = (FdxTestProcesses*) tracker;
    FdxTestChild** slot;
    int active = 0;
    if (!state) return 0;
    slot = &state->children;
    while (*slot) {
        FdxTestChild* child = *slot;
#ifdef _WIN32
        DWORD status = WaitForSingleObject(child->handle, 0);
        if (status == WAIT_FAILED) return -(int) GetLastError();
        if (status == WAIT_TIMEOUT) { active = 1; slot = &child->next; continue; }
        CloseHandle(child->handle);
#else
        int status;
        pid_t result = waitpid(child->handle, &status, WNOHANG);
        if (result < 0 && errno == EINTR) { active = 1; slot = &child->next; continue; }
        if (result < 0 && errno != ECHILD) return -errno;
        if (!result) { active = 1; slot = &child->next; continue; }
#endif
        *slot = child->next;
        free(child);
    }
    return active;
}

static inline void fdxTestProcessDispose(void* tracker) {
    FdxTestProcesses* state = (FdxTestProcesses*) tracker;
    FdxTestChild* child;
    if (!state) return;
    fdxTestProcessPoll(state);
    child = state->children;
    while (child) {
        FdxTestChild* next = child->next;
#ifdef _WIN32
        CloseHandle(child->handle);
#endif
        free(child);
        child = next;
    }
    free(state);
}
#endif
