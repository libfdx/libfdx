#include "libfdx_desktop_shaderc.h"

#include <cstring>
#include <mutex>
#include <string>

#if defined(_WIN32)
#include <windows.h>
#elif defined(__APPLE__)
#include <dlfcn.h>
#include <mach-o/dyld.h>
#include <vector>
#else
#include <dlfcn.h>
#include <limits.h>
#include <unistd.h>
#endif

namespace {

using CompileFn = void* (*)(const char*, int32_t, int32_t, int32_t, const char*, const char*, const char*);
using IntResultFn = int32_t (*)(void*);
using OutputFn = const uint8_t* (*)(void*);
using DiagnosticFn = const char* (*)(void*);
using FreeFn = void (*)(void*);

std::once_flag load_once;
std::string failure_message;
CompileFn compile_fn = nullptr;
IntResultFn status_fn = nullptr;
IntResultFn output_kind_fn = nullptr;
OutputFn output_fn = nullptr;
IntResultFn output_size_fn = nullptr;
DiagnosticFn diagnostics_fn = nullptr;
FreeFn free_fn = nullptr;

#if defined(_WIN32)
HMODULE library_handle = nullptr;

void* load_symbol(const char* name) {
    return reinterpret_cast<void*>(GetProcAddress(library_handle, name));
}

void load_library() {
    library_handle = LoadLibraryW(L"fdx.dll");
    if (library_handle == nullptr) {
        failure_message = "Could not load fdx.dll (Windows error " + std::to_string(GetLastError()) + ")";
    }
}
#else
void* library_handle = nullptr;

std::string sibling_library_path(const char* library_name) {
#if defined(__APPLE__)
    uint32_t size = 0;
    _NSGetExecutablePath(nullptr, &size);
    std::vector<char> path(size + 1u, 0);
    if (_NSGetExecutablePath(path.data(), &size) != 0) {
        return library_name;
    }
    std::string executable(path.data());
#else
    char path[PATH_MAX + 1] = {};
    ssize_t length = readlink("/proc/self/exe", path, PATH_MAX);
    if (length <= 0) {
        return library_name;
    }
    path[length] = '\0';
    std::string executable(path);
#endif
    std::string::size_type separator = executable.find_last_of("/\\");
    if (separator == std::string::npos) {
        return library_name;
    }
    return executable.substr(0, separator + 1) + library_name;
}

void* load_symbol(const char* name) {
    return dlsym(library_handle, name);
}

void load_library() {
#if defined(__APPLE__)
    const char* library_name = "libfdx.dylib";
#else
    const char* library_name = "libfdx.so";
#endif
    std::string path = sibling_library_path(library_name);
    library_handle = dlopen(path.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (library_handle == nullptr) {
        library_handle = dlopen(library_name, RTLD_NOW | RTLD_LOCAL);
    }
    if (library_handle == nullptr) {
        const char* error = dlerror();
        failure_message = "Could not load " + std::string(library_name)
                + (error != nullptr ? ": " + std::string(error) : "");
    }
}
#endif

template <typename T>
bool require_symbol(T& target, const char* name) {
    target = reinterpret_cast<T>(load_symbol(name));
    if (target != nullptr) {
        return true;
    }
    failure_message = "Runtime fdx library does not export " + std::string(name);
    return false;
}

void load_api() {
    load_library();
    if (library_handle == nullptr) {
        return;
    }
    if (!require_symbol(compile_fn, "fdx_shaderc_compile_wgsl_handle")
            || !require_symbol(status_fn, "fdx_shaderc_result_status")
            || !require_symbol(output_kind_fn, "fdx_shaderc_result_output_kind")
            || !require_symbol(output_fn, "fdx_shaderc_result_output")
            || !require_symbol(output_size_fn, "fdx_shaderc_result_output_size")
            || !require_symbol(diagnostics_fn, "fdx_shaderc_result_diagnostics")
            || !require_symbol(free_fn, "fdx_shaderc_result_free")) {
        return;
    }
    failure_message.clear();
}

bool ensure_api() {
    std::call_once(load_once, load_api);
    return compile_fn != nullptr && status_fn != nullptr && output_kind_fn != nullptr
            && output_fn != nullptr && output_size_fn != nullptr && diagnostics_fn != nullptr
            && free_fn != nullptr;
}

}  // namespace

extern "C" int32_t fdx_desktop_shaderc_available(void) {
    return ensure_api() ? 1 : 0;
}

extern "C" char* fdx_desktop_shaderc_failure_message(void) {
    ensure_api();
    return const_cast<char*>(failure_message.c_str());
}

extern "C" void* fdx_desktop_shaderc_compile(const char* source,
                                               int32_t target,
                                               int32_t stage,
                                               const char* entry_point,
                                               const char* glsl_profile,
                                               const char* glsl_es_profile) {
    if (!ensure_api() || source == nullptr) {
        return nullptr;
    }
    return compile_fn(source, static_cast<int32_t>(std::strlen(source)), target, stage,
            entry_point != nullptr ? entry_point : "", glsl_profile != nullptr ? glsl_profile : "330",
            glsl_es_profile != nullptr ? glsl_es_profile : "300");
}

extern "C" int32_t fdx_desktop_shaderc_result_status(void* handle) {
    return ensure_api() && handle != nullptr ? status_fn(handle) : 1;
}

extern "C" int32_t fdx_desktop_shaderc_result_output_kind(void* handle) {
    return ensure_api() && handle != nullptr ? output_kind_fn(handle) : 0;
}

extern "C" uint8_t* fdx_desktop_shaderc_result_output(void* handle) {
    return ensure_api() && handle != nullptr ? const_cast<uint8_t*>(output_fn(handle)) : nullptr;
}

extern "C" int32_t fdx_desktop_shaderc_result_output_size(void* handle) {
    return ensure_api() && handle != nullptr ? output_size_fn(handle) : 0;
}

extern "C" char* fdx_desktop_shaderc_result_diagnostics(void* handle) {
    return ensure_api() && handle != nullptr ? const_cast<char*>(diagnostics_fn(handle))
            : const_cast<char*>(failure_message.c_str());
}

extern "C" void fdx_desktop_shaderc_result_free(void* handle) {
    if (ensure_api() && handle != nullptr) {
        free_fn(handle);
    }
}
