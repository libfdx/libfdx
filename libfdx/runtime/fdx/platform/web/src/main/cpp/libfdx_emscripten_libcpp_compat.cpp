#include <cstddef>
#include <cstdint>

#if defined(__EMSCRIPTEN__)
namespace std {
inline namespace __2 {

__attribute__((weak)) size_t __hash_memory(const void* data, size_t size) noexcept {
    const auto* bytes = static_cast<const uint8_t*>(data);
    if constexpr (sizeof(size_t) == 8) {
        size_t hash = 1469598103934665603ull;
        for (size_t index = 0; index < size; ++index) {
            hash ^= static_cast<size_t>(bytes[index]);
            hash *= 1099511628211ull;
        }
        return hash;
    } else {
        size_t hash = 2166136261u;
        for (size_t index = 0; index < size; ++index) {
            hash ^= static_cast<size_t>(bytes[index]);
            hash *= 16777619u;
        }
        return hash;
    }
}

} // namespace __2
} // namespace std
#endif
