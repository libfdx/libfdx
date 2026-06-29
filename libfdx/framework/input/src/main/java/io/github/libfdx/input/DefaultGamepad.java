package io.github.libfdx.input;

import io.github.libfdx.core.ProviderId;

/**
 * Provides the default implementation of a gamepad.
 *
 * @author xpenatan
 */
public final class DefaultGamepad implements Gamepad {
    private final ProviderId providerId;
    private final String id;
    private final String name;
    private final int index;
    private final GamepadMapping mapping;
    private final GamepadState state = new GamepadState();
    private boolean connected = true;

    /**
     * Creates a default gamepad.
     *
     * @param id the identifier
     * @param name the name
     * @param index the index
     * @param mapping the mapping
     */
    public DefaultGamepad(String id, String name, int index, GamepadMapping mapping) {
        this.providerId = ProviderId.of("default_gamepad");
        this.id = id;
        this.name = name;
        this.index = index;
        this.mapping = mapping != null ? mapping : GamepadMapping.UNKNOWN;
    }

    /**
     * Returns the ID.
     *
     * @return the ID
     */
    @Override
    public String id() {
        return id;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * Returns the index.
     *
     * @return the index
     */
    @Override
    public int index() {
        return index;
    }

    /**
     * Returns whether connected is enabled or true.
     *
     * @return true if connected is enabled or true; false otherwise
     */
    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * Runs the connected step.
     *
     * @param connected the connected
     */
    public void connected(boolean connected) {
        this.connected = connected;
    }

    /**
     * Returns the mapping.
     *
     * @return the mapping
     */
    @Override
    public GamepadMapping mapping() {
        return mapping;
    }

    /**
     * Returns the state.
     *
     * @return the state
     */
    @Override
    public GamepadState state() {
        return state;
    }

    /**
     * Runs the axis step.
     *
     * @param axis the axis
     * @return the axis
     */
    @Override
    public float axis(GamepadAxis axis) {
        return state.axis(axis);
    }

    /**
     * Runs the pressed step.
     *
     * @param button the button
     * @return true if pressed succeeds or is active; false otherwise
     */
    @Override
    public boolean pressed(GamepadButton button) {
        return state.pressed(button);
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
