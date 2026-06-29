package io.github.libfdx.input;

import io.github.libfdx.core.ProviderHandle;
import java.util.List;

/**
 * Defines the contract for gamepads implementations.
 *
 * @author xpenatan
 */
public interface Gamepads extends ProviderHandle {
    /**
     * Returns the connected.
     *
     * @return the connected
     */
    List<Gamepad> connected();

    /**
     * Finds a matching value.
     *
     * @param index the index
     * @return the matching value, or null if none is available
     */
    Gamepad find(int index);

    /**
     * Adds the listener.
     *
     * @param listener the listener
     */
    void addListener(GamepadListener listener);

    /**
     * Removes the listener.
     *
     * @param listener the listener
     */
    void removeListener(GamepadListener listener);
}
