package io.github.libfdx.audio;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.ProviderId;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PooledAudioTest {
    private static final PcmData PCM = new PcmData(1, 8000, new short[] {0, 100, -100});

    @Test void saturationAndRecyclingNeverLetOldOrForeignHandlesAffectNewVoices() {
        FakeAudio audio = new FakeAudio(2), other = new FakeAudio(2);
        Sound sound = audio.createSound(PCM), foreign = other.createSound(PCM);
        long first = audio.play(sound), second = audio.play(sound);
        assertEquals(0, audio.play(sound));
        assertTrue(audio.stop(first));
        long replacement = audio.play(sound);
        assertNotEquals(first, replacement);
        assertFalse(audio.stop(first));
        assertFalse(other.stop(replacement));
        assertThrows(FdxException.class, () -> audio.play(foreign));
        assertEquals(VoiceState.PLAYING, audio.state(second));
        assertEquals(2, audio.activeVoices());
        audio.dispose(); other.dispose();
    }
    @Test void suspensionPreservesIndividualPauseStateIncludingNewVoices() {
        FakeAudio audio = new FakeAudio(3);
        Sound sound = audio.createSound(PCM);
        long paused = audio.play(sound), running = audio.play(sound);
        audio.pause(paused);
        audio.suspend();
        long createdSuspended = audio.play(sound);
        assertEquals(VoiceState.PAUSED, audio.state(createdSuspended));
        audio.resume().get();
        assertEquals(VoiceState.PAUSED, audio.state(paused));
        assertEquals(VoiceState.PLAYING, audio.state(running));
        assertEquals(VoiceState.PLAYING, audio.state(createdSuspended));
        assertTrue(audio.resume(paused));
        audio.dispose();
    }
    @Test void naturalCompletionFreesSlotAndSoundDisposalStopsBeforeRelease() {
        FakeAudio audio = new FakeAudio(2);
        Sound sound = audio.createSound(PCM);
        long done = audio.play(sound), playing = audio.play(sound);
        audio.ended[0] = true;
        assertEquals(VoiceState.STOPPED, audio.state(done));
        sound.dispose(); sound.dispose();
        assertEquals(0, audio.activeVoices());
        assertEquals(VoiceState.STOPPED, audio.state(playing));
        assertEquals(1, audio.releases);
        assertThrows(FdxException.class, () -> audio.play(sound));
        assertThrows(FdxException.class, sound::as);
        audio.dispose(); audio.dispose();
        assertEquals(1, audio.closes);
    }
    @Test void shutdownInvalidatesAllSoundsEvenWhenOneReleaseFails() {
        FakeAudio audio = new FakeAudio(2);
        Sound a = audio.createSound(PCM), b = audio.createSound(PCM);
        audio.play(a); audio.play(b); audio.failRelease = true;
        assertThrows(FdxException.class, audio::dispose);
        assertTrue(audio.isDisposed());
        assertTrue(a.isDisposed()); assertTrue(b.isDisposed());
        assertEquals(2, audio.releases); assertEquals(1, audio.closes);
        assertThrows(FdxException.class, () -> audio.play(a));
    }
    @Test void pendingActivationIsSupersededBySuspendAndDispose() {
        FakeAudio audio = new FakeAudio(1);
        audio.activation = FdxFuture.pending();
        FdxFuture<Void> result = audio.resume();
        audio.suspend(); audio.activation.complete(null);
        assertTrue(result.isFailed()); assertTrue(audio.isSuspended());
        audio.activation = FdxFuture.pending();
        result = audio.resume(); audio.dispose(); audio.activation.complete(null);
        assertTrue(result.isFailed());
    }
    @Test void failedStartDoesNotOccupySlotAndInvalidParametersDoNotReachDriver() {
        FakeAudio audio = new FakeAudio(1);
        Sound sound = audio.createSound(PCM);
        assertThrows(IllegalArgumentException.class, () -> audio.play(sound, Float.NaN, 1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> audio.play(sound, 1, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> audio.play(sound, 1, 1, 2, false));
        audio.failStart = true;
        assertThrows(FdxException.class, () -> audio.play(sound));
        assertEquals(0, audio.activeVoices());
        audio.failStart = false;
        assertNotEquals(0, audio.play(sound));
        audio.dispose();
    }
    private static final class FakeAudio extends PooledAudio {
        final Object[] slots;
        final boolean[] ended;
        int releases, closes;
        boolean failRelease, failStart;
        FdxFuture<Void> activation;
        FakeAudio(int max) { super(max); slots = new Object[max]; ended = new boolean[max]; }
        @Override public ProviderId providerId() { return ProviderId.of("test_audio"); }
        @SuppressWarnings("unchecked") @Override public <T> T as() { return (T) this; }
        @Override protected void checkDevice() { }
        @Override protected Object upload(PcmData pcm) { return new Object(); }
        @Override protected void release(Object resource) {
            for (Object slot : slots) assertNotSame(resource, slot, "Must detach before releasing");
            releases++; if (failRelease) throw new FdxException("test release failure");
        }
        @Override protected void start(int slot, Object resource, float gain, float pitch, float pan, boolean loop, boolean paused) {
            if (failStart) throw new FdxException("test start failure");
            slots[slot] = resource; ended[slot] = false;
        }
        @Override protected void stopSlot(int slot) { slots[slot] = null; }
        @Override protected void pauseSlot(int slot) { }
        @Override protected void resumeSlot(int slot) { }
        @Override protected void parametersSlot(int slot, float gain, float pitch, float pan) { }
        @Override protected boolean finished(int slot) { return ended[slot]; }
        @Override protected FdxFuture<Void> activate() { return activation == null ? FdxFuture.completed(null) : activation; }
        @Override protected boolean platformSuspended() { return false; }
        @Override protected void closeDevice() { closes++; }
    }
}
