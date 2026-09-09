package io.github.libfdx.audio.openal;

import io.github.libfdx.audio.PcmData;
import io.github.libfdx.audio.Sound;
import io.github.libfdx.audio.VoiceState;
import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

final class OpenALAudioTest {
    @Test void nativeLoopbackRendersPanningPausePitchAndScopedLifetime() throws Exception {
        OpenALAudio audio = OpenALAudio.loopback(32, 48000);
        try {
            Sound tone = audio.createSound(tone(48000));
            long voice = audio.play(tone, 0.5f, 1, -1, true);
            short[] capture = new short[48000];
            audio.renderSamples(capture, 24000);
            assertTrue(rms(capture, 0, 2000) > 1000);
            assertTrue(rms(capture, 1, 2000) < 5, "Hard-left channel leaked");
            writeCapture(capture);
            audio.pause(voice); audio.renderSamples(capture, 24000);
            assertTrue(rms(capture, 0, 2000) < 5, "Paused voice still audible");
            audio.parameters(voice, 0.5f, 2, 1); audio.resume(voice);
            audio.renderSamples(capture, 24000);
            assertTrue(rms(capture, 0, 2000) < 5);
            assertTrue(rms(capture, 1, 2000) > 1000);
            int crossings = 0;
            for (int i = 4003; i < capture.length; i += 2) if (capture[i - 2] <= 0 && capture[i] > 0) crossings++;
            assertEquals(880 * (24000 - 2000) / 48000.0, crossings, 3);
            tone.dispose(); audio.renderSamples(capture, 24000);
            assertTrue(rms(capture, 1, 2000) < 5); assertEquals(0, audio.activeVoices());
            assertEquals(VoiceState.STOPPED, audio.state(voice));
        } finally { audio.dispose(); }
    }
    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void hardPanRemainsIsolatedWithHeadphoneVirtualization(int channels) {
        OpenALAudio audio = OpenALAudio.loopback(1, 48000);
        try {
            OpenALTestOutput.enableHeadphones();
            Sound sound = audio.createSound(tone(48000, channels));
            long voice = audio.play(sound, .5f, 1, -1, true);
            short[] output = new short[48000];
            audio.renderSamples(output, 24000);
            assertTrue(rms(output, 0, 2000) > 1000);
            assertTrue(rms(output, 1, 2000) < 5, "Hard-left headphone output leaked: " + rms(output, 1, 2000));
            audio.parameters(voice, .5f, 1, 1);
            audio.renderSamples(output, 24000);
            assertTrue(rms(output, 1, 2000) > 1000);
            assertTrue(rms(output, 0, 2000) < 5, "Hard-right headphone output leaked: " + rms(output, 0, 2000));
            sound.dispose();
        } finally { audio.dispose(); }
    }
    @Test void thirtyTwoNativeVoicesRemainBoundedAndContextsRejectForeignResources() {
        OpenALAudio audio = OpenALAudio.loopback(32, 48000), other = OpenALAudio.loopback(1, 48000);
        try {
            Sound sound = audio.createSound(tone(480));
            for (int i = 0; i < 32; i++) assertNotEquals(0, audio.play(sound, 0.01f, 1, 0, true));
            assertEquals(0, audio.play(sound));
            assertThrows(FdxException.class, () -> other.play(sound));
            short[] output = new short[96000]; audio.renderSamples(output, 48000);
            assertEquals(32, audio.activeVoices());
            audio.suspend(); audio.renderSamples(output, 48000);
            assertTrue(rms(output, 0, 2000) < 5);
            audio.resume().get(); audio.renderSamples(output, 48000);
            assertTrue(rms(output, 0, 2000) > 100);
            sound.dispose(); assertEquals(0, audio.activeVoices());
        } finally { audio.dispose(); other.dispose(); }
    }
    @Test void nativeOneShotCompletesAndWrongThreadCallsFail() throws Exception {
        OpenALAudio audio = OpenALAudio.loopback(1, 48000);
        try {
            Sound sound = audio.createSound(tone(480)); long voice = audio.play(sound);
            audio.renderSamples(new short[9600], 4800);
            assertEquals(VoiceState.STOPPED, audio.state(voice));
            Throwable[] failure = new Throwable[1];
            Thread worker = new Thread(() -> { try { audio.play(sound); } catch (Throwable e) { failure[0] = e; } });
            worker.start(); worker.join(); assertInstanceOf(FdxException.class, failure[0]);
        } finally { audio.dispose(); }
    }
    private static PcmData tone(int frames) { return tone(frames, 1); }
    private static PcmData tone(int frames, int channels) {
        short[] samples = new short[frames * channels];
        for (int i = 0; i < frames; i++) {
            for (int channel = 0; channel < channels; channel++) {
                samples[i * channels + channel] = (short) (Math.sin(i * Math.PI * 880 / 48000) * 12000);
            }
        }
        return new PcmData(channels, 48000, samples);
    }
    private static double rms(short[] pcm, int channel, int skip) {
        double sum = 0; int count = 0;
        for (int i = skip * 2 + channel; i < pcm.length; i += 2) { sum += (double) pcm[i] * pcm[i]; count++; }
        return Math.sqrt(sum / count);
    }
    private static void writeCapture(short[] samples) throws Exception {
        ByteBuffer b = ByteBuffer.allocate(44 + samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        b.put(new byte[] {'R','I','F','F'}).putInt(b.capacity() - 8).put(new byte[] {'W','A','V','E','f','m','t',' '});
        b.putInt(16).putShort((short) 1).putShort((short) 2).putInt(48000).putInt(192000).putShort((short) 4).putShort((short) 16);
        b.put(new byte[] {'d','a','t','a'}).putInt(samples.length * 2);
        for (short sample : samples) b.putShort(sample);
        Path output = Path.of("build/reports/audio-loopback.wav"); Files.createDirectories(output.getParent()); Files.write(output, b.array());
    }
}
