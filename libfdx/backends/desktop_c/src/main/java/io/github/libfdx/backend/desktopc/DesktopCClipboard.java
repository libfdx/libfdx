package io.github.libfdx.backend.desktopc;

import io.github.libfdx.input.Clipboard;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.teavm.interop.Address;

/**
 * Bridges libFDX clipboard operations to GLFW's desktop system clipboard.
 *
 * @author xpenatan
 */
final class DesktopCClipboard implements Clipboard {
    private static final int ACCESS_ATTEMPTS = 5;
    private static final int GLFW_PLATFORM_ERROR = 0x00010008;
    private static final int MAXIMUM_TEXT_BYTES = 16 * 1024 * 1024;
    private final long windowHandle;
    private String cachedText = "";

    DesktopCClipboard(long windowHandle) {
        this.windowHandle = windowHandle;
    }

    @Override
    public String getText() {
        for (int attempt = 0; attempt < ACCESS_ATTEMPTS; attempt++) {
            DesktopCGLFW.getError();
            Address address = DesktopCGLFW.getClipboardString(windowHandle);
            int error = DesktopCGLFW.getError();
            if (error == 0) {
                cachedText = decode(address);
                return cachedText;
            }
            if (error != GLFW_PLATFORM_ERROR) {
                return cachedText;
            }
        }
        return cachedText;
    }

    private static String decode(Address address) {
        if (address == null || address.toLong() == 0L) {
            return "";
        }
        int length = 0;
        while (length < MAXIMUM_TEXT_BYTES && address.add(length).getByte() != 0) {
            length++;
        }
        if (length == MAXIMUM_TEXT_BYTES) {
            return "";
        }
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = address.add(i).getByte();
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void setText(String text) {
        cachedText = text != null ? text : "";
        byte[] utf8 = cachedText.getBytes(StandardCharsets.UTF_8);
        byte[] terminatedUtf8 = Arrays.copyOf(utf8, utf8.length + 1);
        for (int attempt = 0; attempt < ACCESS_ATTEMPTS; attempt++) {
            DesktopCGLFW.getError();
            DesktopCGLFW.setClipboardString(windowHandle, Address.ofData(terminatedUtf8));
            int error = DesktopCGLFW.getError();
            if (error == 0) {
                return;
            }
            if (error != GLFW_PLATFORM_ERROR) {
                return;
            }
        }
    }
}
