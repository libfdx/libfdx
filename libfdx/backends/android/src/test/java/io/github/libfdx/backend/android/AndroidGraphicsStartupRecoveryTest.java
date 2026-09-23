package io.github.libfdx.backend.android;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AndroidGraphicsStartupRecoveryTest {
    private static final class MemoryStore implements AndroidGraphicsStartupRecovery.Store {
        AndroidGraphicsStartupRecovery.State state = AndroidGraphicsStartupRecovery.State.empty();
        @Override
        public AndroidGraphicsStartupRecovery.State read() { return state; }
        @Override
        public void write(AndroidGraphicsStartupRecovery.State value) { state = value; }
    }

    @Test
    void nativeStartupCrashAdvancesOnceAndRemembersSuccessfulFallback() {
        MemoryStore store = new MemoryStore();
        AndroidGraphicsStartupRecovery first = recovery(store, false, 10);
        first.begin(0, 100);
        AndroidGraphicsStartupRecovery next = recovery(store, true, 11);
        assertEquals(1, next.firstAttempt());
        next.begin(1, 200);
        next.firstFrame();
        assertEquals(-1, store.state.pending());
        assertEquals(1, recovery(store, false, 12).firstAttempt());
    }

    @Test
    void ordinaryTerminationAndMissingEvidenceDoNotDisablePrimary() {
        MemoryStore store = new MemoryStore();
        recovery(store, false, 10).begin(0, 100);
        assertEquals(0, recovery(store, false, 11).firstAttempt());
        assertEquals(-1, store.state.pending());
    }

    @Test
    void completedStartupDoesNotAttributeLaterCrashToInitialization() {
        MemoryStore store = new MemoryStore();
        AndroidGraphicsStartupRecovery first = recovery(store, false, 10);
        first.begin(0, 100);
        first.firstFrame();
        assertEquals(0, recovery(store, true, 11).firstAttempt());
    }

    @Test
    void orderlyPauseOrTeardownClearsPendingMarker() {
        MemoryStore store = new MemoryStore();
        AndroidGraphicsStartupRecovery first = recovery(store, false, 10);
        first.begin(0, 100);
        first.cancel();
        assertEquals(0, recovery(store, true, 11).firstAttempt());
    }

    @Test
    void failedFallbackExhaustsInsteadOfCyclingBackToVulkan() {
        MemoryStore store = new MemoryStore();
        recovery(store, false, 10).begin(0, 100);
        recovery(store, true, 11).begin(1, 200);
        assertEquals(2, recovery(store, true, 12).firstAttempt());
        assertEquals(2, recovery(store, true, 13).firstAttempt());
    }

    @Test
    void sameProcessActivityRecreationIsNotACrash() {
        MemoryStore store = new MemoryStore();
        recovery(store, false, 10).begin(0, 100);
        assertEquals(0, recovery(store, true, 10).firstAttempt());
    }

    @Test
    void errorRetryIsRememberedOnlyAfterSuccessfulFrame() {
        MemoryStore store = new MemoryStore();
        AndroidGraphicsStartupRecovery first = recovery(store, false, 10);
        first.begin(0, 100);
        first.cancel();
        first.begin(1, 200);
        assertEquals(0, store.state.selected());
        first.firstFrame();
        assertEquals(1, store.state.selected());
    }

    @Test
    void passesExactAttemptPidAndTimeToExitEvidenceLookup() {
        MemoryStore store = new MemoryStore();
        recovery(store, false, 10).begin(0, 100);
        new AndroidGraphicsStartupRecovery(store, (pid, started, now) -> {
            assertEquals(10, pid);
            assertEquals(100, started);
            assertEquals(300, now);
            return false;
        }, 11, 300, 2);
    }

    @Test
    void persistenceFailureStopsBeforeEnteringNativeStartup() {
        MemoryStore store = new MemoryStore();
        AndroidGraphicsStartupRecovery recovery = recovery(store, false, 10);
        assertThrows(IllegalArgumentException.class, () -> recovery.begin(2, 100));
        assertEquals(-1, store.state.pending());
        assertThrows(IllegalStateException.class, () -> new AndroidGraphicsStartupRecovery(
                new AndroidGraphicsStartupRecovery.Store() {
                    @Override
                    public AndroidGraphicsStartupRecovery.State read() { return store.state; }
                    @Override
                    public void write(AndroidGraphicsStartupRecovery.State state) {
                        throw new IllegalStateException("Disk full");
                    }
                }, (pid, start, end) -> false, 11, 300, 2));
    }

    private AndroidGraphicsStartupRecovery recovery(MemoryStore store, boolean crash, int pid) {
        return new AndroidGraphicsStartupRecovery(store, (oldPid, started, now) -> crash, pid, 300, 2);
    }
}
