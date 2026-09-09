package io.github.libfdx.audio.loaders;

import io.github.libfdx.audio.PcmData;
import static io.github.libfdx.audio.loaders.WavFormat.*;

/** Strict RIFF/WAVE PCM decoder. Supports unsigned 8-bit or signed 16-bit mono/stereo. */
public final class WavDecoder {
    private WavDecoder() { }

    /**
     * Decodes at most maxFrames sample frames. Unknown RIFF chunks are skipped with
     * padding; duplicate format/data chunks, incomplete frames, inconsistent rates,
     * compressed/extensible formats and malformed lengths fail explicitly. Cue/smpl
     * metadata is ignored: sound playback loops the entire data chunk.
     */
    public static PcmData decode(byte[] bytes, int maxFrames) {
        if (maxFrames < 1) throw new IllegalArgumentException("maxFrames must be positive");
        if (bytes == null || bytes.length < 12 || !tag(bytes, 0, "RIFF") || !tag(bytes, 8, "WAVE")
                || u32(bytes, 4) + 8 != bytes.length) throw invalid("Expected a complete RIFF/WAVE file");
        int format = -1, data = -1, dataSize = 0, formatSize = 0;
        for (int offset = 12; offset < bytes.length;) {
            if ((long) offset + 8 > bytes.length) throw invalid("Truncated chunk header");
            long size = u32(bytes, offset + 4);
            long next = offset + 8L + size + (size & 1);
            if (next > bytes.length) throw invalid("Chunk exceeds RIFF length");
            if (tag(bytes, offset, "fmt ")) {
                if (format >= 0 || size < 16) throw invalid("Duplicate or truncated format");
                format = offset + 8; formatSize = (int) size;
            } else if (tag(bytes, offset, "data")) {
                if (data >= 0) throw invalid("Duplicate data chunk");
                data = offset + 8; dataSize = (int) size;
            }
            offset = (int) next;
        }
        if (format < 0 || data < 0) throw invalid("Missing format/data chunk");
        WavFormat metadata = WavFormat.read(bytes, format, formatSize);
        int channels = metadata.channels(), bits = metadata.bits(), sampleRate = metadata.sampleRate();
        int frameBytes = metadata.frameBytes();
        if (dataSize == 0 || dataSize % frameBytes != 0) throw invalid("Inconsistent PCM format/data");
        int frames = dataSize / frameBytes;
        if (frames > maxFrames) throw invalid("Decoded frame limit exceeded; use streaming for long tracks");
        short[] samples = new short[frames * channels];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = bits == 8 ? (short) (((bytes[data + i] & 255) - 128) << 8)
                    : (short) u16(bytes, data + i * 2);
        }
        return new PcmData(channels, sampleRate, samples);
    }
}
