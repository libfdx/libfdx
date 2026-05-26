package io.github.libfdx.ui;

import java.util.LinkedHashSet;
import java.util.Set;

abstract class UiObservableState {
    private final Set<UiStateListener> listeners = new LinkedHashSet<UiStateListener>();
    private Object[] notifySnapshot = new Object[0];

    final void observeRead() {
        UiRoot root = UiComposition.CURRENT_ROOT.get();
        if (root != null) {
            root.observe(this);
        }
    }

    final void addListener(UiStateListener listener) {
        listeners.add(listener);
    }

    final void removeListener(UiStateListener listener) {
        listeners.remove(listener);
    }

    final void notifyListeners() {
        int listenerCount = listeners.size();
        if (notifySnapshot.length < listenerCount) {
            notifySnapshot = new Object[listenerCount];
        }
        int index = 0;
        for (UiStateListener listener : listeners) {
            notifySnapshot[index++] = listener;
        }
        for (int i = 0; i < listenerCount; i++) {
            ((UiStateListener) notifySnapshot[i]).stateChanged(this);
            notifySnapshot[i] = null;
        }
    }
}
