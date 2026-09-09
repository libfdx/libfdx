package io.github.libfdx.audio;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderHandle;

/**
 * Application-owned reusable device sound. It belongs to exactly one Audio service.
 * Disposal stops all its voices before releasing device memory and is idempotent.
 * Metadata remains readable after disposal; provider access and playback fail.
 */
public interface Sound extends Disposable, ProviderHandle {
    /** Duration at pitch 1, in seconds. */
    double durationSeconds();
    /** One (mono) or two (stereo). */
    int channels();
    /** Source sample frames per second. */
    int sampleRate();
}
