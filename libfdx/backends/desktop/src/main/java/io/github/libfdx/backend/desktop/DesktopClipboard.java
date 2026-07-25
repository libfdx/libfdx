package io.github.libfdx.backend.desktop;

import io.github.libfdx.input.Clipboard;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.locks.LockSupport;
import org.lwjgl.glfw.GLFW;

/**
 * Bridges libFDX clipboard operations to the GLFW desktop system clipboard.
 *
 * @author xpenatan
 */
final class DesktopClipboard implements Clipboard {
    private static final int ACCESS_ATTEMPTS = 50;
    private static final long RETRY_NANOS = 10_000_000L;
    private final long windowHandle;
    private String cachedText = "";

    DesktopClipboard(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    @Override
    public String getText() {
        for (int attempt = 0; attempt < ACCESS_ATTEMPTS; attempt++) {
            String value = getTextOnce();
            if (value != null) {
                cachedText = value;
                return cachedText;
            }
            waitForClipboard();
        }
        return cachedText;
    }

    @Override
    public void setText(String text) {
        String value = text != null ? text : "";
        cachedText = value;
        for (int attempt = 0; attempt < ACCESS_ATTEMPTS; attempt++) {
            setTextOnce(value);
            String stored = getTextOnce();
            if (value.equals(stored)) {
                return;
            }
            waitForClipboard();
        }
    }

    private void waitForClipboard() {
        LockSupport.parkNanos(RETRY_NANOS);
    }

    private String getTextOnce() {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (contents == null) {
                    return "";
                }
                if (contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                    Object value = contents.getTransferData(DataFlavor.stringFlavor);
                    return value != null ? String.valueOf(value) : "";
                }
                return "";
            } catch (Exception error) {
                return null;
            }
        }
        return GLFW.glfwGetClipboardString(windowHandle);
    }

    private void setTextOnce(String value) {
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(value), null);
            } catch (IllegalStateException error) {
                // Another process owns the clipboard briefly. The caller retries.
            }
            return;
        }
        GLFW.glfwSetClipboardString(windowHandle, value);
    }
}
