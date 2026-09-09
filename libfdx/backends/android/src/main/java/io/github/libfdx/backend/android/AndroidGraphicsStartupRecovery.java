package io.github.libfdx.backend.android;

/** Startup-only recovery policy; persistence and process-exit evidence are supplied by Android. */
final class AndroidGraphicsStartupRecovery {
    record State(int selected, int pending, int pid, long started) {
        static State empty() { return new State(0, -1, 0, 0); }
    }

    interface Store {
        State read();
        void write(State state);
    }

    interface ExitHistory {
        boolean nativeCrash(int pid, long started, long now);
    }

    private final Store store;
    private final int pid;
    private final int count;
    private State state;

    AndroidGraphicsStartupRecovery(Store store, ExitHistory history, int pid, long now, int count) {
        this.store = store;
        this.pid = pid;
        this.count = count;
        State previous = store.read();
        int selected = previous.selected();
        if(selected < 0 || selected > count) selected = 0;
        if(previous.pending() >= 0 && previous.pending() < count && previous.pid() != pid
                && previous.started() > 0 && previous.started() <= now
                && history.nativeCrash(previous.pid(), previous.started(), now)) {
            selected = Math.max(selected, previous.pending() + 1);
        }
        save(new State(selected, -1, 0, 0));
    }

    int firstAttempt() { return state.selected(); }

    void begin(int attempt, long now) {
        if(attempt < 0 || attempt >= count) throw new IllegalArgumentException("Invalid startup attempt");
        save(new State(state.selected(), attempt, pid, now));
    }

    void firstFrame() {
        if(state.pending() >= 0) save(new State(state.pending(), -1, 0, 0));
    }

    void cancel() {
        if(state.pending() >= 0) save(new State(state.selected(), -1, 0, 0));
    }

    private void save(State state) {
        store.write(state); // Must finish before native initialization starts.
        this.state = state;
    }
}
