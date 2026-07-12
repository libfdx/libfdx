#ifndef LIBFDX_DESKTOP_SHADERC_H_
#define LIBFDX_DESKTOP_SHADERC_H_

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t fdx_desktop_shaderc_available(void);

char* fdx_desktop_shaderc_failure_message(void);

void* fdx_desktop_shaderc_compile(const char* source,
                                  int32_t target,
                                  int32_t stage,
                                  const char* entry_point,
                                  const char* glsl_profile,
                                  const char* glsl_es_profile);

int32_t fdx_desktop_shaderc_result_status(void* handle);

int32_t fdx_desktop_shaderc_result_output_kind(void* handle);

uint8_t* fdx_desktop_shaderc_result_output(void* handle);

int32_t fdx_desktop_shaderc_result_output_size(void* handle);

char* fdx_desktop_shaderc_result_diagnostics(void* handle);

void fdx_desktop_shaderc_result_free(void* handle);

#ifdef __cplusplus
}
#endif

#endif  // LIBFDX_DESKTOP_SHADERC_H_
