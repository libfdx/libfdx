#ifndef LIBFDX_RUNTIME_CORE_H
#define LIBFDX_RUNTIME_CORE_H

#ifdef __cplusplus
extern "C" {
#endif

int lfdx_runtime_core_init(void);
void lfdx_runtime_core_shutdown(void);
const char* lfdx_runtime_core_last_error(void);

#ifdef __cplusplus
}
#endif

#endif