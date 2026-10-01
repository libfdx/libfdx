#pragma once
#include <cstdint>
#include <climits>
#include <stdexcept>
#include <string>
namespace libfdx_c {
// TeaVM's native call is synchronous. The generated caller retains the Java
// array/string/buffer and passes its storage and checked length for this call.
struct FdxCSpan { void* data; int32_t length; };
inline std::uint8_t* direct_bytes(FdxCSpan value, int64_t count, bool) {
    if (count < 0 || count > value.length || (!value.data && count))
        throw std::invalid_argument("Native buffer size is out of range");
    return static_cast<std::uint8_t*>(value.data);
}
inline std::string java_text(FdxCSpan value) {
    if (!value.data) throw std::invalid_argument("Null native text");
    return std::string(static_cast<const char*>(value.data), value.length);
}
template<class T, int InlineCapacity = 32> class NativeArray {
    FdxCSpan value_;
public:
    explicit NativeArray(FdxCSpan value) : value_(value) {}
    T* data() const { return static_cast<T*>(value_.data); }
    int32_t size() const { return value_.length; }
    void require(int64_t count) const {
        if (count < 0 || count > value_.length || (!value_.data && count))
            throw std::invalid_argument("Native array is too short");
    }
    void write() {}
};
}