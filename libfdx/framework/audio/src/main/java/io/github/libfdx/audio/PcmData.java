package io.github.libfdx.audio;

/** Immutable interleaved signed 16-bit PCM, independent of any device. */
public final class PcmData {
    private final short[] samples;
    private final int channels;
    private final int sampleRate;

    /** Copies samples. Supports mono/stereo, 8–192 kHz, and at least one complete frame. */
    public PcmData(int channels, int sampleRate, short[] samples) {
        if ((channels != 1 && channels != 2) || sampleRate < 8000 || sampleRate > 192000
                || samples == null || samples.length == 0 || samples.length % channels != 0) {
            throw new IllegalArgumentException("Expected mono/stereo PCM, 8–192 kHz, with complete frames");
        }
        this.channels = channels;
        this.sampleRate = sampleRate;
        this.samples = samples.clone();
    }
    /** Number of source channels. */
    public int channels() { return channels; }
    /** Source frames per second. */
    public int sampleRate() { return sampleRate; }
    /** Number of sample frames (not interleaved sample values). */
    public int frames() { return samples.length / channels; }
    /** Duration at the original rate. */
    public double durationSeconds() { return (double) frames() / sampleRate; }
    /** Reads a sample without exposing mutable storage. Intended for setup/upload. */
    public short sample(int frame, int channel) {
        if (frame < 0 || frame >= frames() || channel < 0 || channel >= channels) {
            throw new IndexOutOfBoundsException();
        }
        return samples[frame * channels + channel];
    }
}
