#pragma once

#include "PSPInclude.h"
#include "PSPMemory.h"
#include <stdio.h>

extern int64_t teavm_memory_heap_used_bytes(void);
extern int64_t teavm_memory_heap_free_bytes(void);
extern int64_t teavm_memory_heap_committed_bytes(void);
extern int64_t teavm_memory_heap_max_bytes(void);
extern int64_t teavm_memory_direct_buffer_live_bytes(void);
extern int32_t teavm_memory_direct_buffer_count(void);

int32_t libfdx_psp_debug_heap_log(int32_t frame) {
    char line[256];
    int32_t length = 0;
    SceUID file = -1;
    sceIoMkdir("ms0:/PSP/SYSTEM/DUMP", 0777);
    file = sceIoOpen("ms0:/PSP/SYSTEM/DUMP/libfdx-app.log",
            PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    if (file < 0) {
        file = sceIoOpen("libfdx-app.log", PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    }
    if (file < 0) {
        return 0;
    }
    length = snprintf(line, sizeof(line),
            "[heap] frame=%d used=%lld free=%lld committed=%lld max=%lld directBytes=%lld directCount=%d\n",
            (int) frame,
            (long long) teavm_memory_heap_used_bytes(),
            (long long) teavm_memory_heap_free_bytes(),
            (long long) teavm_memory_heap_committed_bytes(),
            (long long) teavm_memory_heap_max_bytes(),
            (long long) teavm_memory_direct_buffer_live_bytes(),
            (int) teavm_memory_direct_buffer_count());
    if (length > 0) {
        if (length > (int32_t) sizeof(line)) {
            length = (int32_t) sizeof(line);
        }
        sceIoWrite(file, line, length);
    }
    sceIoClose(file);
    return 1;
}

int32_t libfdx_psp_debug_loop_log(
        int32_t frame,
        int32_t stage,
        int32_t java_running,
        int32_t native_running,
        int32_t close_requested) {
    char line[160];
    int32_t length = 0;
    SceUID file = -1;
    sceIoMkdir("ms0:/PSP/SYSTEM/DUMP", 0777);
    file = sceIoOpen("ms0:/PSP/SYSTEM/DUMP/libfdx-app.log",
            PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    if (file < 0) {
        file = sceIoOpen("libfdx-app.log", PSP_O_WRONLY | PSP_O_CREAT | PSP_O_APPEND, 0777);
    }
    if (file < 0) {
        return 0;
    }
    length = snprintf(line, sizeof(line),
            "[loop] frame=%d stage=%d java=%d native=%d close=%d\n",
            (int) frame,
            (int) stage,
            (int) java_running,
            (int) native_running,
            (int) close_requested);
    if (length > 0) {
        if (length > (int32_t) sizeof(line)) {
            length = (int32_t) sizeof(line);
        }
        sceIoWrite(file, line, length);
    }
    sceIoClose(file);
    return 1;
}
