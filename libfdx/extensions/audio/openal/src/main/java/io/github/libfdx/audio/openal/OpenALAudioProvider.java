package io.github.libfdx.audio.openal;

import io.github.libfdx.audio.Audio;
import io.github.libfdx.audio.AudioProvider;
import io.github.libfdx.core.ProviderId;

/** Desktop JVM OpenAL Soft setup. No device is opened until the backend calls create. */
public final class OpenALAudioProvider implements AudioProvider {
    private final int maxVoices;
    private final String deviceName;
    /** Selects the default output device with 32 simultaneous voices. */
    public OpenALAudioProvider() { this(32, null); }
    /** Null deviceName selects the OS default. Every logical voice uses two native sources. */
    public OpenALAudioProvider(int maxVoices, String deviceName) {
        if (maxVoices < 1 || maxVoices > 256) throw new IllegalArgumentException("Voice capacity must be 1–256");
        this.maxVoices = maxVoices; this.deviceName = deviceName;
    }
    @Override public ProviderId providerId() { return OpenALAudio.ID; }
    @Override public Audio create() { return new OpenALAudio(maxVoices, deviceName, 0); }
}
