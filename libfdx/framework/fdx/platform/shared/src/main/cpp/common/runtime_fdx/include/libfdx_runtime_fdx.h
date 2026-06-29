#ifndef LIBFDX_RUNTIME_FDX_H
#define LIBFDX_RUNTIME_FDX_H

#ifdef __cplusplus
extern "C" {
#endif

int lfdx_runtime_fdx_init(void);
void lfdx_runtime_fdx_shutdown(void);
const char* lfdx_runtime_fdx_last_error(void);

#ifdef __cplusplus
}
#endif

#endif