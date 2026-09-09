package io.github.libfdx.audio.loaders;

import io.github.libfdx.core.FdxException;

/** Shared validation for whole-file and streaming PCM decoders. */
record WavFormat(int channels, int sampleRate, int bits) {
    int frameBytes() { return channels * (bits / 8); }
    static WavFormat read(byte[] bytes, int offset, int size) {
        int channels = u16(bytes, offset + 2), bits = u16(bytes, offset + 14);
        long rate = u32(bytes, offset + 4);
        if (u16(bytes, offset) != 1 || (channels != 1 && channels != 2)
                || (bits != 8 && bits != 16) || rate < 8000 || rate > 192000) {
            throw invalid("Only mono/stereo 8/16-bit PCM at 8–192 kHz is supported");
        }
        int frameBytes = channels * (bits / 8);
        if ((size != 16 && (size != 18 || u16(bytes, offset + 16) != 0))
                || u16(bytes, offset + 12) != frameBytes || u32(bytes, offset + 8) != rate * frameBytes) {
            throw invalid("Inconsistent PCM format");
        }
        return new WavFormat(channels, (int)rate, bits);
    }
    static boolean tag(byte[] bytes, int offset, String tag) {
        for (int i = 0; i < 4; i++) { if (bytes[offset + i] != tag.charAt(i)) { return false; } }
        return true;
    }
    static int u16(byte[] bytes, int offset) { return (bytes[offset] & 255) | ((bytes[offset + 1] & 255) << 8); }
    static long u32(byte[] bytes, int offset) { return u16(bytes, offset) | ((long)u16(bytes, offset + 2) << 16); }
    static FdxException invalid(String message) { return new FdxException("WAV: " + message); }
}
