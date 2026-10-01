package io.github.libfdx.backend.desktopc;

import io.github.libfdx.audio.Audio;
import io.github.libfdx.audio.AudioProvider;
import io.github.libfdx.core.ProviderId;

/** Native OpenAL Soft playback. The backend opens the output device during startup. */
public final class DesktopCAudioProvider implements AudioProvider {
    private final int maxVoices;

    /** Uses the default output device and 32 simultaneous sound voices. */
    public DesktopCAudioProvider() {
        this(32);
    }

    /** Each logical voice uses two native sources for independent stereo panning. */
    public DesktopCAudioProvider(int maxVoices) {
        if (maxVoices < 1 || maxVoices > 256) {
            throw new IllegalArgumentException("Voice capacity must be 1–256");
        }
        this.maxVoices = maxVoices;
    }

    @Override
    public ProviderId providerId() {
        return DesktopCAudio.ID;
    }

    @Override
    public Audio create() {
        return new DesktopCAudio(maxVoices);
    }
}
