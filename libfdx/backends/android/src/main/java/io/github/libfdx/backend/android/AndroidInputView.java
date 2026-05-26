package io.github.libfdx.backend.android;

import android.content.Context;
import android.graphics.Rect;
import android.view.SurfaceView;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

final class AndroidInputView extends SurfaceView {
    private final AndroidTextInputController textInputController;

    AndroidInputView(Context context, AndroidTextInputController textInputController) {
        super(context);
        this.textInputController = textInputController;
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return textInputController != null && textInputController.active();
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        return textInputController != null ? textInputController.createInputConnection(this, outAttrs) : null;
    }

    @Override
    public void getFocusedRect(Rect r) {
        if (textInputController != null && textInputController.focusedBounds(r)) {
            return;
        }
        super.getFocusedRect(r);
    }
}
