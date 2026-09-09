package io.github.libfdx.audio.openal;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.EXTThreadLocalContext.alcGetThreadContext;
import static org.lwjgl.openal.SOFTHRTF.*;
import static org.lwjgl.openal.SOFTLoopback.*;

/** Enables real HRTF mixing in the current offline device, without changing OS settings. */
final class OpenALTestOutput {
    private OpenALTestOutput() { }
    static void enableHeadphones() {
        long device = alcGetContextsDevice(alcGetThreadContext());
        assertTrue(alcResetDeviceSOFT(device, new int[] {
                ALC_FREQUENCY, 48000,
                ALC_FORMAT_CHANNELS_SOFT, ALC_STEREO_SOFT,
                ALC_FORMAT_TYPE_SOFT, ALC_SHORT_SOFT,
                ALC_HRTF_SOFT, ALC_TRUE, 0 }), "Could not configure headphone test output");
        assertEquals(ALC_TRUE, alcGetInteger(device, ALC_HRTF_SOFT), "Headphone processing must actually be active");
    }
}
