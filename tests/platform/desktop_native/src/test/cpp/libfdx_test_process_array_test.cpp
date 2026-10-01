/* Link against the real jNative runtime/platform libraries, not an array stub.
 * Include the production wrapper so using the boolean-array element kind for Java byte[] fails here.
 */
#include "libfdx_test_process_cpp.cpp"
#include <cassert>
#include <iostream>
#ifdef NDEBUG
#error This regression requires assertions enabled.
#endif

int main() {
    try {
        jnative::ManagedEntry managed;
        for (int size : {7, 128}) {
            auto array = jnative::allocate<jnative::PrimitiveArray<std::int8_t>>(size, "[B");
            jnative::BorrowedHandle bytes(array);
            for (int i = 0; i < size; i++) array->elements[i].set(static_cast<std::int8_t>(i + 128));
            {
                jnative::NativeRegion native;
                libfdx_cpp::NativeArray<std::int8_t> copied(bytes.id());
                assert(copied.size() == size);
                for (int i = 0; i < size; i++) {
                    assert(static_cast<unsigned char>(copied.data()[i]) == static_cast<unsigned char>(i + 128));
                }
                /* A null tracker intentionally prevents spawning, after the real managed-array read. */
                assert(fdx_cpp_test_process_launch(0, bytes.id(), size) == EINVAL);
                bool rejected = false;
                try { libfdx_cpp::NativeArray<std::uint8_t> incorrect(bytes.id()); }
                catch (const std::invalid_argument&) { rejected = true; }
                assert(rejected);
            }
        }
        std::cout << "libfdx_test_process_array_test: real Java byte-array boundary passed\n";
    } catch (const std::exception& failure) {
        std::cerr << failure.what() << '\n';
        return 1;
    }
}
