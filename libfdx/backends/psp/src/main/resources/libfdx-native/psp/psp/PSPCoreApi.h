#pragma once

#include "PSPInclude.h"

volatile bool libfdx_psp_core_running;

int exit_callback(int arg1, int arg2, void *common) {
    libfdx_psp_core_running = false;
    return 0;
}

int callback_thread(SceSize args, void *argp) {
    int cbid = sceKernelCreateCallback("Exit Callback", exit_callback, NULL);
    sceKernelRegisterExitCallback(cbid);
    sceKernelSleepThreadCB();
    return 0;
}

int setupCallbacks() {
    libfdx_psp_core_running = true;
    int thid = sceKernelCreateThread("update_thread", callback_thread, 0x11, 0xFA0, 0, 0);
    if(thid >= 0) {
        sceKernelStartThread(thid, 0, 0);
    }
    return thid;
}

bool isRunning() {
    return libfdx_psp_core_running;
}

void libfdx_psp_delay_micros(int micros) {
    if (micros > 0) {
        sceKernelDelayThread(micros);
    }
}
