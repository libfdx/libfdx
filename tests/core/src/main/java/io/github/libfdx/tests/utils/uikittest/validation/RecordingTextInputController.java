package io.github.libfdx.tests.utils.uikittest.validation;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.input.TextInputController;
import io.github.libfdx.input.TextInputRequest;
import io.github.libfdx.input.TextInputType;

/**
 * Represents a recording text input controller.
 *
 * @author xpenatan
 */
public final class RecordingTextInputController implements TextInputController {
    private TextInputRequest lastRequest = TextInputRequest.builder().build();
    private int showCount;
    private int updateCount;
    private int hideCount;
    private boolean visible;

    /**
     * Runs the show text input step.
     *
     * @param request the request
     */
    @Override
    public void showTextInput(TextInputRequest request) {
        lastRequest = request != null ? request : TextInputRequest.builder().build();
        showCount++;
        visible = true;
    }

    /**
     * Runs the update text input step.
     *
     * @param request the request
     */
    @Override
    public void updateTextInput(TextInputRequest request) {
        lastRequest = request != null ? request : TextInputRequest.builder().build();
        updateCount++;
    }

    /**
     * Runs the hide text input step.
     */
    @Override
    public void hideTextInput() {
        hideCount++;
        visible = false;
    }

    public void reset() {
        lastRequest = TextInputRequest.builder().build();
        showCount = 0;
        updateCount = 0;
        hideCount = 0;
        visible = false;
    }

    public int updateCount() {
        return updateCount;
    }

    public int showCount() {
        return showCount;
    }

    public int hideCount() {
        return hideCount;
    }

    public boolean visible() {
        return visible;
    }

    public TextInputRequest lastRequest() {
        return lastRequest;
    }

    public void simulatePlatformHidden() {
        visible = false;
    }

    public void assertVisible(TextInputType expectedType, boolean expectedMultiline, String label) {
        if (!visible || showCount <= 0) {
            throw new FdxException("UiKitTest did not show text input for " + label);
        }
        if (lastRequest.type() != expectedType) {
            throw new FdxException("UiKitTest text input type mismatch for " + label + ": "
                    + lastRequest.type());
        }
        if (lastRequest.multiline() != expectedMultiline) {
            throw new FdxException("UiKitTest text input multiline mismatch for " + label + ": "
                    + lastRequest.multiline());
        }
        if (lastRequest.readOnly()) {
            throw new FdxException("UiKitTest requested a read-only platform text input for " + label);
        }
        if (!lastRequest.hasBounds() || lastRequest.boundsWidth() <= 0 || lastRequest.boundsHeight() <= 0) {
            throw new FdxException("UiKitTest text input request did not include visible bounds for " + label);
        }
    }

}
