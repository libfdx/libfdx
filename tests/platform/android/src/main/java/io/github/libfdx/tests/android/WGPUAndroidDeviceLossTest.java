package io.github.libfdx.tests.android;

import android.os.Handler;
import android.os.Looper;
import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.android.AndroidApplicationBackend;
import io.github.libfdx.graphics.wgpu.WGPUAndroidDeviceLossChecks;

/** Native wgpu Destroyed notifications; selects a case with libfdx.test.wgpuLoss. */
public final class WGPUAndroidDeviceLossTest extends ApplicationAdapter {
    @Override public void create(Fdx fdx) {
        // Finish initial surface sizing before pausing the backend and controlling test frames.
        new Handler(Looper.getMainLooper()).post(() -> {
            AndroidApplicationBackend backend = fdx.app().as();
            backend.pause();
            try {
                new WGPUAndroidDeviceLossChecks(fdx.graphics().main().as()).validate();
            } finally {
                fdx.app().requestExit();
            }
        });
    }
}
