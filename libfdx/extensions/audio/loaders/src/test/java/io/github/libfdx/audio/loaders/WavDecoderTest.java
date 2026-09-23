package io.github.libfdx.audio.loaders;

import io.github.libfdx.audio.PcmData;
import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class WavDecoderTest {
    @Test
    void decodesSignedStereoAndPreservesIndependentChannels() {
        PcmData pcm = WavDecoder.decode(wav(2, 16, new byte[] {0, -128, -1, 127, 0, 0, 0, 64}), 2);
        assertEquals(2, pcm.channels()); assertEquals(2, pcm.frames());
        assertEquals(-32768, pcm.sample(0, 0)); assertEquals(32767, pcm.sample(0, 1));
        assertEquals(16384, pcm.sample(1, 1)); assertEquals(8000, pcm.sampleRate());
    }
    @Test
    void decodesUnsignedEightBitAndSkipsOddPaddedChunks() {
        byte[] basic = wav(1, 8, new byte[] {0, -128, -1});
        byte[] padded = new byte[basic.length + 10];
        System.arraycopy(basic, 0, padded, 0, 12);
        ByteBuffer b = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(4, padded.length - 8); b.position(12);
        b.put(new byte[] {'J','U','N','K'}).putInt(1).put((byte) 7).put((byte) 0);
        b.put(basic, 12, basic.length - 12);
        PcmData pcm = WavDecoder.decode(padded, 3);
        assertEquals(-32768, pcm.sample(0, 0)); assertEquals(0, pcm.sample(1, 0));
        assertEquals(32512, pcm.sample(2, 0));
    }
    @Test
    void rejectsMalformedOrUnsupportedDataBeforeDecodedAllocation() {
        byte[] wav = wav(1, 16, new byte[] {0, 0, 0, 0});
        assertThrows(FdxException.class, () -> WavDecoder.decode(wav, 1));
        byte[] overflow = wav.clone(); ByteBuffer.wrap(overflow).order(ByteOrder.LITTLE_ENDIAN).putInt(40, -1);
        assertThrows(FdxException.class, () -> WavDecoder.decode(overflow, 100));
        byte[] compressed = wav.clone(); compressed[20] = 3;
        assertThrows(FdxException.class, () -> WavDecoder.decode(compressed, 100));
        byte[] truncated = java.util.Arrays.copyOf(wav, wav.length - 1);
        assertThrows(FdxException.class, () -> WavDecoder.decode(truncated, 100));
        byte[] rate = wav.clone(); rate[28] = 0;
        assertThrows(FdxException.class, () -> WavDecoder.decode(rate, 100));
    }
    @Test
    void pcmCopiesCallerStorageAndRejectsPartialFrames() {
        short[] source = {1, 2}; PcmData pcm = new PcmData(2, 8000, source); source[0] = 10;
        assertEquals(1, pcm.sample(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PcmData(2, 8000, new short[] {1}));
    }
    static byte[] wav(int channels, int bits, byte[] samples) {
        ByteBuffer b = ByteBuffer.allocate(44 + samples.length + (samples.length & 1)).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[] {'R','I','F','F'}).putInt(b.capacity() - 8).put(new byte[] {'W','A','V','E','f','m','t',' '});
        b.putInt(16).putShort((short) 1).putShort((short) channels).putInt(8000);
        b.putInt(8000 * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits);
        b.put(new byte[] {'d','a','t','a'}).putInt(samples.length).put(samples);
        return b.array();
    }
}
