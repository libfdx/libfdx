package io.github.libfdx.input;

import io.github.libfdx.core.ProviderId;

public final class DefaultGamepad implements Gamepad {
    private final ProviderId providerId;
    private final String id;
    private final String name;
    private final int index;
    private final GamepadMapping mapping;
    private final GamepadState state = new GamepadState();
    private boolean connected = true;

    public DefaultGamepad(String id, String name, int index, GamepadMapping mapping) {
        this.providerId = ProviderId.of("default_gamepad");
        this.id = id;
        this.name = name;
        this.index = index;
        this.mapping = mapping != null ? mapping : GamepadMapping.UNKNOWN;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    public void connected(boolean connected) {
        this.connected = connected;
    }

    @Override
    public GamepadMapping mapping() {
        return mapping;
    }

    @Override
    public GamepadState state() {
        return state;
    }

    @Override
    public float axis(GamepadAxis axis) {
        return state.axis(axis);
    }

    @Override
    public boolean pressed(GamepadButton button) {
        return state.pressed(button);
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
