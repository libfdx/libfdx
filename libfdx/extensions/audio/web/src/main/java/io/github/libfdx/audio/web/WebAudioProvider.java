package io.github.libfdx.audio.web;

import io.github.libfdx.audio.Audio;
import io.github.libfdx.audio.AudioProvider;
import io.github.libfdx.core.ProviderId;

/** Direct Web Audio setup for TeaVM. Startup does not request user activation. */
public final class WebAudioProvider implements AudioProvider {
    private final int maxVoices;
    /** Configures 32 simultaneous voices. */
    public WebAudioProvider() { this(32); }
    /** Sets the bounded logical voice count, 1–256. */
    public WebAudioProvider(int maxVoices) {
        if (maxVoices < 1 || maxVoices > 256) throw new IllegalArgumentException("Voice capacity must be 1–256");
        this.maxVoices = maxVoices;
    }
    @Override
    public ProviderId providerId() { return WebAudio.ID; }
    @Override
    public Audio create() { return new WebAudio(maxVoices); }
}
