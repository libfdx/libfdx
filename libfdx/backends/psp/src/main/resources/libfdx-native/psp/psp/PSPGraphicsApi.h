#pragma once

#include "PSPInclude.h"
#include <string.h>
#include <vramalloc.h>

static unsigned int __attribute__((aligned(16))) list[1 * 1024 * 1024];

#define BUFFER_WIDTH 512
#define BUFFER_HEIGHT 272
#define SCREEN_WIDTH 480
#define SCREEN_HEIGHT BUFFER_HEIGHT

void libfdx_psp_dcache_writeback_invalidate(void* data, int size) {
    if (data != NULL && size > 0) {
        sceKernelDcacheWritebackInvalidateRange(data, (unsigned int) size);
    }
}

void libfdx_psp_copy_texture_data(void* target, const void* source, int size) {
    if (target != NULL && source != NULL && size > 0) {
        memcpy(target, source, (size_t) size);
        sceKernelDcacheWritebackInvalidateRange(target, (unsigned int) size);
    }
}

void initGraphics() {
    void* fbp0 = guGetStaticVramBuffer(BUFFER_WIDTH, BUFFER_HEIGHT, GU_PSM_8888);
    void* fbp1 = guGetStaticVramBuffer(BUFFER_WIDTH, BUFFER_HEIGHT, GU_PSM_8888);
    void* zbp = guGetStaticVramBuffer(BUFFER_WIDTH, BUFFER_HEIGHT, GU_PSM_4444);

    sceGuInit();

    sceGuStart(GU_DIRECT, list);
    sceGuDrawBuffer(GU_PSM_8888, fbp0, BUFFER_WIDTH);
    sceGuDispBuffer(SCREEN_WIDTH, SCREEN_HEIGHT, fbp1, BUFFER_WIDTH);
    sceGuDepthBuffer(zbp, BUFFER_WIDTH);
    sceGuOffset(2048 - (SCREEN_WIDTH / 2), 2048 - (SCREEN_HEIGHT / 2));
    sceGuViewport(2048, 2048, SCREEN_WIDTH, SCREEN_HEIGHT);
    sceGuDepthRange(65535, 0);
    sceGuScissor(0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    sceGuEnable(GU_SCISSOR_TEST);
    sceGuDepthFunc(GU_GEQUAL);
    sceGuEnable(GU_DEPTH_TEST);
    sceGuFrontFace(GU_CCW);
    sceGuShadeModel(GU_SMOOTH);
    sceGuEnable(GU_CULL_FACE);
    sceGuFinish();
    sceGuSync(0, 0);

    sceDisplayWaitVblankStart();
    sceGuDisplay(GU_TRUE);
}

void beginFrame(int dialog) {
    sceGuStart(GU_DIRECT, list);

    if (dialog) {
        sceGuFinish();
        sceGuSync(0, 0);
    }
}

void endFrame(int vsync, int dialog) {
    if (!dialog) {
        sceGuFinish();
        sceGuSync(GU_SYNC_FINISH, GU_SYNC_WHAT_DONE);
    }

    if (vsync) {
        sceDisplayWaitVblankStart();
    }

    sceGuSwapBuffers();
}
