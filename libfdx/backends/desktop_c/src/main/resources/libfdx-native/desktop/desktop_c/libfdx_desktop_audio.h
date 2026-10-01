#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
int64_t fdx_c_al_open();
int64_t fdx_c_al_context(int64_t device);
int32_t fdx_c_al_current(int64_t context);
void fdx_c_al_destroy_context(int64_t context);
void fdx_c_al_close(int64_t device);
int32_t fdx_c_al_gen_source();
void fdx_c_al_delete_source(int32_t value);
int32_t fdx_c_al_gen_buffer();
void fdx_c_al_delete_buffer(int32_t value);
void fdx_c_al_source_i(int32_t source, int32_t property, int32_t value);
void fdx_c_al_source_f(int32_t source, int32_t property, float value);
int32_t fdx_c_al_get_source(int32_t source, int32_t property);
int32_t fdx_c_al_error();
void fdx_c_al_buffer_data(int32_t buffer, int32_t format, void* pcm_data, int32_t pcm_length, int32_t bytes, int32_t rate);
void fdx_c_al_play(void* values_data, int32_t values_length);
void fdx_c_al_pause(void* values_data, int32_t values_length);
void fdx_c_al_stop(void* values_data, int32_t values_length);
void fdx_c_al_rewind(void* values_data, int32_t values_length);
void fdx_c_al_stop_one(int32_t source);
void fdx_c_al_rewind_one(int32_t source);
void fdx_c_al_queue(int32_t source, int32_t buffer);
int32_t fdx_c_al_unqueue(int32_t source);
#ifdef __cplusplus
}
#endif
