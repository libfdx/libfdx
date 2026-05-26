package io.github.libfdx.ui;

public final class UiFocusScope {
    private final String id;
    private final boolean wraps;
    private final boolean restoresFocus;

    private UiFocusScope(String id, boolean wraps, boolean restoresFocus) {
        this.id = id;
        this.wraps = wraps;
        this.restoresFocus = restoresFocus;
    }

    public static UiFocusScope scope(String id) {
        return new UiFocusScope(id, true, true);
    }

    public UiFocusScope wraps(boolean wraps) {
        return new UiFocusScope(id, wraps, restoresFocus);
    }

    public UiFocusScope restoresFocus(boolean restoresFocus) {
        return new UiFocusScope(id, wraps, restoresFocus);
    }

    public String id() {
        return id;
    }

    public boolean wraps() {
        return wraps;
    }

    public boolean restoresFocus() {
        return restoresFocus;
    }
}
