package io.github.libfdx.backend.desktopcpp;

import io.github.libfdx.input.Clipboard;

/** GLFW clipboard with managed UTF-8 conversion at the native boundary. */
final class DesktopCppClipboard implements Clipboard {
    private final long window;

    DesktopCppClipboard(long window) {
        this.window = window;
    }

    @Override
    public String getText() {
        String text = DesktopCppGLFW.getClipboardString(window);
        return text == null ? "" : text;
    }

    @Override
    public void setText(String text) {
        DesktopCppGLFW.setClipboardString(window, text == null ? "" : text);
    }
}
