#pragma once

#include "PSPInclude.h"

#include <stdint.h>
#include <string.h>

static SceCtrlData libfdx_psp_pad;

void initInput() {
    memset(&libfdx_psp_pad, 0, sizeof(libfdx_psp_pad));
    libfdx_psp_pad.Lx = 128;
    libfdx_psp_pad.Ly = 128;
    sceCtrlSetSamplingCycle(0);
    sceCtrlSetSamplingMode(PSP_CTRL_MODE_ANALOG);
}

int32_t pollInput() {
    sceCtrlPeekBufferPositive(&libfdx_psp_pad, 1);
    return (int32_t) libfdx_psp_pad.Buttons;
}

int32_t analogX() {
    return (int32_t) libfdx_psp_pad.Lx;
}

int32_t analogY() {
    return (int32_t) libfdx_psp_pad.Ly;
}
