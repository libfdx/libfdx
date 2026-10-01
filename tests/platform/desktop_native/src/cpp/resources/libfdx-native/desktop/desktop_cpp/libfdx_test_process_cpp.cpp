#include "libfdx_test_process_cpp.h"
#include "libfdx_test_process.h"

extern "C" int64_t fdx_cpp_test_process_create() {
    return reinterpret_cast<intptr_t>(fdxTestProcessCreate());
}
extern "C" int32_t fdx_cpp_test_process_launch(int64_t state, jn_handle arguments, int32_t length) {
    libfdx_cpp::NativeArray<std::int8_t> bytes(arguments);
    bytes.require(length);
    return fdxTestProcessLaunch(reinterpret_cast<void*>(static_cast<intptr_t>(state)),
            reinterpret_cast<const unsigned char*>(bytes.data()), length);
}
extern "C" int32_t fdx_cpp_test_process_poll(int64_t state) {
    return fdxTestProcessPoll(reinterpret_cast<void*>(static_cast<intptr_t>(state)));
}
extern "C" void fdx_cpp_test_process_dispose(int64_t state) {
    fdxTestProcessDispose(reinterpret_cast<void*>(static_cast<intptr_t>(state)));
}
