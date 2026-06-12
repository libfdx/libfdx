package io.github.libfdx.input;

import io.github.libfdx.core.ProviderId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides the default implementation of a gamepads.
 *
 * @author xpenatan
 */
public final class DefaultGamepads implements Gamepads {
    private final ProviderId providerId = ProviderId.of("default_gamepads");
    private final List<Gamepad> connected = new ArrayList<Gamepad>();
    private final List<GamepadListener> listeners = new ArrayList<GamepadListener>();

    /**
     * Runs the connect step.
     *
     * @param gamepad the gamepad
     */
    public void connect(Gamepad gamepad) {
        if (gamepad == null || connected.contains(gamepad)) {
            return;
        }
        connected.add(gamepad);
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).connected(gamepad);
        }
    }

    /**
     * Runs the disconnect step.
     *
     * @param gamepad the gamepad
     */
    public void disconnect(Gamepad gamepad) {
        if (gamepad == null || !connected.remove(gamepad)) {
            return;
        }
        for (int i = 0; i < listeners.size(); i++) {
            listeners.get(i).disconnected(gamepad);
        }
    }

    /**
     * Returns the connected.
     *
     * @return the connected
     */
    @Override
    public List<Gamepad> connected() {
        return Collections.unmodifiableList(connected);
    }

    /**
     * Finds a matching value.
     *
     * @param index the index
     * @return the matching value, or null if none is available
     */
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

    /**
     * Adds the listener.
     *
     * @param listener the listener
     */
    @Override
    public void addListener(GamepadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Removes the listener.
     *
     * @param listener the listener
     */
    @Override
    public void removeListener(GamepadListener listener) {
        listeners.remove(listener);
    }

    /**
     * Returns the identifier of the provider backing this object.
     *
     * @return the provider ID
     */
    @Override
    public ProviderId providerId() {
        return providerId;
    }

    /**
     * Returns the provider-specific representation requested by the caller.
     *
     * @param <T> the value type
     * @return the as
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T as() {
        return (T) this;
    }
}
