#include "libfdx_desktop_values.hpp"
#include <mutex>
#include <stdexcept>
#if defined(_WIN32)
#define NOMINMAX
#include <windows.h>
#elif defined(__APPLE__)
#include <dlfcn.h>
#include <mach-o/dyld.h>
#else
#include <dlfcn.h>
#include <unistd.h>
#endif

using namespace libfdx_c;

namespace {
#if defined(_WIN32)
HMODULE audioLibrary;
#else
void* audioLibrary;
#endif
std::once_flag audioLoaded;
void loadAudio() {
    std::call_once(audioLoaded, [] {
#if defined(_WIN32)
        wchar_t path[32768];
        DWORD length = GetModuleFileNameW(nullptr, path, 32768);
        if (!length || length == 32768) throw std::runtime_error("Cannot locate audio runtime directory");
        std::wstring name(path, length);
        name = name.substr(0, name.find_last_of(L"/\\") + 1) + L"OpenAL.dll";
        audioLibrary = LoadLibraryW(name.c_str());
#else
        char path[4096];
#if defined(__APPLE__)
        uint32_t length = sizeof(path);
        if (_NSGetExecutablePath(path, &length)) throw std::runtime_error("Cannot locate audio runtime directory");
        const char* libraryName = "libopenal.dylib";
#else
        ssize_t length = readlink("/proc/self/exe", path, sizeof(path) - 1);
        if (length <= 0) throw std::runtime_error("Cannot locate audio runtime directory");
        path[length] = 0;
        const char* libraryName = "libopenal.so";
#endif
        std::string name(path);
        name = name.substr(0, name.find_last_of('/') + 1) + libraryName;
        audioLibrary = dlopen(name.c_str(), RTLD_NOW | RTLD_LOCAL);
#endif
        if (!audioLibrary) throw std::runtime_error("Cannot load the packaged OpenAL Soft runtime");
    });
}
template<class T> T symbol(const char* name) {
    loadAudio();
#if defined(_WIN32)
    auto value = GetProcAddress(audioLibrary, name);
#else
    auto value = dlsym(audioLibrary, name);
#endif
    if (!value) throw std::runtime_error(std::string("Missing OpenAL entry point: ") + name);
    return reinterpret_cast<T>(value);
}
void* pointer(int64_t value) { return reinterpret_cast<void*>(static_cast<intptr_t>(value)); }
int64_t address(void* value) { return static_cast<int64_t>(reinterpret_cast<intptr_t>(value)); }
typedef void (*SourceVector)(int, const unsigned*);
void sources(FdxCSpan values, SourceVector call) {
    NativeArray<int32_t> array(values);
    call(array.size(), reinterpret_cast<const unsigned*>(array.data()));
}
}

extern "C" int64_t fdx_c_al_open() {

    static auto call = symbol<void* (*)(const char*)>("alcOpenDevice");
    return address(call(nullptr));
}
extern "C" int64_t fdx_c_al_context(int64_t device) {

    static auto call = symbol<void* (*)(void*, const int*)>("alcCreateContext");
    return address(call(pointer(device), nullptr));
}
extern "C" int32_t fdx_c_al_current(int64_t context) {

    static auto call = symbol<char (*)(void*)>("alcSetThreadContext");
    if (!call(pointer(context))) return false;
    if (context) {
        static auto supported = symbol<char (*)(const char*)>("alIsExtensionPresent");
        if (!supported("AL_SOFT_direct_channels")) {
            throw std::runtime_error("OpenAL Soft lacks AL_SOFT_direct_channels");
        }
    }
    return true;
}
extern "C" void fdx_c_al_destroy_context(int64_t context) {

    static auto call = symbol<void (*)(void*)>("alcDestroyContext");
    call(pointer(context));
}
extern "C" void fdx_c_al_close(int64_t device) {

    static auto call = symbol<char (*)(void*)>("alcCloseDevice");
    call(pointer(device));
}
extern "C" int32_t fdx_c_al_gen_source() {

    static auto call = symbol<void (*)(int, unsigned*)>("alGenSources");
    unsigned value = 0;
    call(1, &value);
    return value;
}
extern "C" void fdx_c_al_delete_source(int32_t value) {

    static auto call = symbol<void (*)(int, const unsigned*)>("alDeleteSources");
    unsigned source = value;
    call(1, &source);
}
extern "C" int32_t fdx_c_al_gen_buffer() {

    static auto call = symbol<void (*)(int, unsigned*)>("alGenBuffers");
    unsigned value = 0;
    call(1, &value);
    return value;
}
extern "C" void fdx_c_al_delete_buffer(int32_t value) {

    static auto call = symbol<void (*)(int, const unsigned*)>("alDeleteBuffers");
    unsigned buffer = value;
    call(1, &buffer);
}
extern "C" void fdx_c_al_source_i(int32_t source, int32_t property, int32_t value) {

    static auto call = symbol<void (*)(unsigned, int, int)>("alSourcei");
    call(source, property, value);
}
extern "C" void fdx_c_al_source_f(int32_t source, int32_t property, float value) {

    static auto call = symbol<void (*)(unsigned, int, float)>("alSourcef");
    call(source, property, value);
}
extern "C" int32_t fdx_c_al_get_source(int32_t source, int32_t property) {

    static auto call = symbol<void (*)(unsigned, int, int*)>("alGetSourcei");
    int value = 0;
    call(source, property, &value);
    return value;
}
extern "C" int32_t fdx_c_al_error() {

    static auto call = symbol<int (*)()>("alGetError");
    return call();
}
extern "C" void fdx_c_al_buffer_data(int32_t buffer, int32_t format, void* pcm_data, int32_t pcm_length, int32_t bytes, int32_t rate) {
    FdxCSpan pcm{pcm_data, pcm_length};
    static auto call = symbol<void (*)(unsigned, int, const void*, int, int)>("alBufferData");
    call(buffer, format, direct_bytes(pcm, bytes, false), bytes, rate);
}
extern "C" void fdx_c_al_play(void* values_data, int32_t values_length) {
    FdxCSpan values{values_data, values_length};
    static auto call = symbol<SourceVector>("alSourcePlayv");
    sources(values, call);
}
extern "C" void fdx_c_al_pause(void* values_data, int32_t values_length) {
    FdxCSpan values{values_data, values_length};
    static auto call = symbol<SourceVector>("alSourcePausev");
    sources(values, call);
}
extern "C" void fdx_c_al_stop(void* values_data, int32_t values_length) {
    FdxCSpan values{values_data, values_length};
    static auto call = symbol<SourceVector>("alSourceStopv");
    sources(values, call);
}
extern "C" void fdx_c_al_rewind(void* values_data, int32_t values_length) {
    FdxCSpan values{values_data, values_length};
    static auto call = symbol<SourceVector>("alSourceRewindv");
    sources(values, call);
}
extern "C" void fdx_c_al_stop_one(int32_t source) {

    static auto call = symbol<void (*)(unsigned)>("alSourceStop");
    call(source);
}
extern "C" void fdx_c_al_rewind_one(int32_t source) {

    static auto call = symbol<void (*)(unsigned)>("alSourceRewind");
    call(source);
}
extern "C" void fdx_c_al_queue(int32_t source, int32_t buffer) {

    static auto call = symbol<void (*)(unsigned, int, const unsigned*)>("alSourceQueueBuffers");
    unsigned value = buffer;
    call(source, 1, &value);
}
extern "C" int32_t fdx_c_al_unqueue(int32_t source) {

    static auto call = symbol<void (*)(unsigned, int, unsigned*)>("alSourceUnqueueBuffers");
    unsigned value = 0;
    call(source, 1, &value);
    return value;
}
