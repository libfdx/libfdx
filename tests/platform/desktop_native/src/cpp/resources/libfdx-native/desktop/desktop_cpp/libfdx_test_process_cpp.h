#ifndef LIBFDX_TEST_PROCESS_CPP_H
#define LIBFDX_TEST_PROCESS_CPP_H

#include "libfdx_jnative.hpp"

extern "C" {
int64_t fdx_cpp_test_process_create();
int32_t fdx_cpp_test_process_launch(int64_t state, jn_handle arguments, int32_t length);
int32_t fdx_cpp_test_process_poll(int64_t state);
void fdx_cpp_test_process_dispose(int64_t state);
}

#endif
