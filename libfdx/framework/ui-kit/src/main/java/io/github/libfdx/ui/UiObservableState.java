package io.github.libfdx.ui;

import io.github.libfdx.collections.Array;

/**
 * Represents an ui observable state.
 *
 * @author xpenatan
 */
abstract class UiObservableState {
    private final Array<UiStateListener> listeners = new Array<UiStateListener>();
    private Object[] notifySnapshot = new Object[0];

    final void observeRead() {
        UiRoot root = UiComposition.CURRENT_ROOT.get();
        if (root != null) {
            root.observe(this);
        }
    }

    final void addListener(UiStateListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    final void removeListener(UiStateListener listener) {
        listeners.removeValue(listener, true);
    }

    final void notifyListeners() {
        int listenerCount = listeners.size();
        if (notifySnapshot.length < listenerCount) {
            notifySnapshot = new Object[listenerCount];
        }
        for (int i = 0; i < listenerCount; i++) {
            notifySnapshot[i] = listeners.get(i);
        }
        for (int i = 0; i < listenerCount; i++) {
            ((UiStateListener) notifySnapshot[i]).stateChanged(this);
            notifySnapshot[i] = null;
        }
    }
}
