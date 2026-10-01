#ifndef LIBFDX_JNATIVE_HPP
#define LIBFDX_JNATIVE_HPP

#include "jn_abi.hpp"
#include "jn_native_storage.hpp"
#include <array>
#include <vector>
#include <string>

namespace libfdx_cpp {
inline std::uint8_t* direct_bytes(jn_handle handle, int64_t count, bool write) {
    if (count < 0 || count > INT32_MAX) throw std::invalid_argument("Native buffer size is out of range");
    try {
        return jnative::native_buffer_storage(handle, static_cast<int32_t>(count), write);
    } catch (const std::invalid_argument&) {
        // Preserve the Java NIO exception on the uncommon invalid-input path.
        jnative::ManagedAccess managed;
        return jnative::byte_buffer_data(jnative::Heap::instance().resolve(handle), static_cast<int32_t>(count), write);
    }
}
inline std::string java_text(jn_handle handle) {
    try {
        return jnative::utf8(jnative::native_string_storage(handle));
    } catch (const std::invalid_argument&) {
        jnative::ManagedAccess managed;
        return jnative::utf8(jnative::as_string(jnative::Heap::instance().resolve(handle))->value);
    }
}

inline jn_handle string_handle(const char* value) {
    if (!value) return 0;
    jnative::ManagedEntry managed;
    return jnative::Heap::instance().retain(jnative::allocate<jnative::String>(jnative::utf16(value)), true);
}
template<class T, int InlineCapacity = 32> class NativeArray {
    jn_handle handle_;
    int32_t length_;
    std::array<T, InlineCapacity> small_;
    std::vector<T> large_;
public:
    explicit NativeArray(jn_handle handle) : handle_(handle), length_(0) {
        if (!handle) return;
        jnative::NativeArrayStorage<T> array(handle);
        length_ = array.size();
        if (length_ > InlineCapacity) large_.resize(length_);
        array.copy_to(data(), 0, length_);
    }
    T* data() { return handle_ ? (length_ > InlineCapacity ? large_.data() : small_.data()) : nullptr; }
    int32_t size() const { return length_; }
    void require(int64_t count) {
        if (count < 0 || count > length_) throw std::invalid_argument("Native array is too short");
    }
    void write() {
        if (!handle_) return;
        jnative::NativeArrayStorage<T>(handle_).copy_from(data(), 0, length_);
    }
};
}

#endif
