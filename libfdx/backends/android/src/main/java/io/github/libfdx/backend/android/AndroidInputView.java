package io.github.libfdx.backend.android;

import android.content.Context;
import android.graphics.Rect;
import android.view.SurfaceView;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * Represents an android input view.
 *
 * @author xpenatan
 */
final class AndroidInputView extends SurfaceView {
    private final AndroidTextInputController textInputController;

    AndroidInputView(Context context, AndroidTextInputController textInputController) {
        super(context);
        this.textInputController = textInputController;
    }

    /**
     * Returns the on check is text editor.
     *
     * @return true if on check is text editor succeeds or is active; false otherwise
     */
    @Override
    public boolean onCheckIsTextEditor() {
        return textInputController != null && textInputController.active();
    }

    /**
     * Handles the create input connection event.
     *
     * @param outAttrs the out attrs
     * @return the on create input connection
     */
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return textInputController != null ? textInputController.createInputConnection(this, outAttrs) : null;
    }

    /**
     * Returns the focused rect.
     *
     * @param r the r
     */
    @Override
    public void getFocusedRect(Rect r) {
        if (textInputController != null && textInputController.focusedBounds(r)) {
            return;
        }
        super.getFocusedRect(r);
    }
}
