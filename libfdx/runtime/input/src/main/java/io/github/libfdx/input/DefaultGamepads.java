package io.github.libfdx.input;

import io.github.libfdx.core.ProviderId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DefaultGamepads implements Gamepads {
    private final ProviderId providerId = ProviderId.of("default_gamepads");
    private final List<Gamepad> connected = new ArrayList<Gamepad>();
    private final List<GamepadListener> listeners = new ArrayList<GamepadListener>();

    public void connect(Gamepad gamepad) {
        if (gamepad == null || connected.contains(gamepad)) {
            return;
        }
        connected.add(gamepad);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).connected(gamepad);
        }
    }

    public void disconnect(Gamepad gamepad) {
        if (gamepad == null || !connected.remove(gamepad)) {
            return;
        }
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).disconnected(gamepad);
        }
    }

    @Override
    public List<Gamepad> connected() {
        return Collections.unmodifiableList(connected);
    }

    @Override
    public Gamepad find(int index) {
        for (int i = 0; i < connected.size(); i++) {
            Gamepad gamepad = connected.get(i);
            if (gamepad.index() == index) {
                return gamepad;
            }
        }
        return null;
    }

    @Override
    public void addListener(GamepadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(GamepadListener listener) {
        listeners.remove(listener);
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
