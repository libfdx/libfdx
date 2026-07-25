package io.github.libfdx.backend.android;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import io.github.libfdx.input.Clipboard;

/**
 * Bridges libFDX clipboard operations to Android's system clipboard.
 *
 * @author xpenatan
 */
final class AndroidClipboard implements Clipboard {
    private final Activity activity;
    private String cachedText = "";

    AndroidClipboard(Activity activity) {
        this.activity = activity;
    }

    @Override
    public String getText() {
        try {
            ClipboardManager clipboard = manager();
            ClipData clip = clipboard != null && clipboard.hasPrimaryClip() ? clipboard.getPrimaryClip() : null;
            if (clip == null || clip.getItemCount() == 0) {
                return cachedText;
            }
            CharSequence value = clip.getItemAt(0).coerceToText(activity);
            cachedText = value != null ? value.toString() : "";
        } catch (RuntimeException ignored) {
            // Android can deny clipboard reads while the activity is not eligible; keep local copy/paste usable.
        }
        return cachedText;
    }

    @Override
    public void setText(String text) {
        cachedText = text != null ? text : "";
        try {
            ClipboardManager clipboard = manager();
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("libFDX text", cachedText));
            }
        } catch (RuntimeException ignored) {
            // Preserve the synchronous in-process value if platform access is unavailable.
        }
    }

    private ClipboardManager manager() {
        Object service = activity.getSystemService(Context.CLIPBOARD_SERVICE);
        return service instanceof ClipboardManager ? (ClipboardManager) service : null;
    }
}
