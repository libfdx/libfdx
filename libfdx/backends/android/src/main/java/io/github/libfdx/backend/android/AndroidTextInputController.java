package io.github.libfdx.backend.android;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.TextInputController;
import io.github.libfdx.input.TextInputRequest;
import io.github.libfdx.input.TextInputType;

final class AndroidTextInputController implements TextInputController {
    private final Activity activity;
    private final AndroidApplicationBackend backend;
    private final SpannableStringBuilder editable = new SpannableStringBuilder();
    private final Rect focusedBounds = new Rect();
    private final Rect visibleFrame = new Rect();
    private FrameLayout container;
    private LinearLayout editorPanel;
    private EditText editor;
    private Button editorCancel;
    private Button editorOk;
    private AndroidInputView view;
    private DefaultInput input;
    private TextInputRequest request = TextInputRequest.builder().build();
    private boolean active;
    private boolean committingNativeEditor;
    private boolean keyboardLayoutListenerInstalled;

    AndroidTextInputController(Activity activity, AndroidApplicationBackend backend) {
        this.activity = activity;
        this.backend = backend;
    }

    void view(AndroidInputView view) {
        this.view = view;
    }

    void container(FrameLayout container) {
        this.container = container;
        installKeyboardLayoutListener();
    }

    void input(DefaultInput input) {
        this.input = input;
    }

    boolean active() {
        return active;
    }

    @Override
    public void showTextInput(TextInputRequest request) {
        applyRequest(request);
        if (this.request.readOnly()) {
            hideTextInput();
            return;
        }
        active = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (view == null || !active) {
                    return;
                }
                showNativeEditor();
                InputMethodManager inputMethodManager = inputMethodManager();
                if (inputMethodManager != null && !nativeEditorVisible()) {
                    inputMethodManager.restartInput(view);
                    requestFocusedBoundsOnScreen();
                    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
                    requestFocusedBoundsAfterKeyboard();
                }
            }
        });
    }

    @Override
    public void updateTextInput(TextInputRequest request) {
        if (!active) {
            return;
        }
        if (committingNativeEditor) {
            return;
        }
        applyRequest(request);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (view == null || !active) {
                    return;
                }
                InputMethodManager inputMethodManager = inputMethodManager();
                if (inputMethodManager != null && editorPanel == null) {
                    inputMethodManager.updateSelection(view, AndroidTextInputController.this.request.selectionStart(),
                            AndroidTextInputController.this.request.selectionEnd(), -1, -1);
                }
                requestFocusedBoundsOnScreen();
            }
        });
    }

    @Override
    public void hideTextInput() {
        active = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideNativeEditor();
                InputMethodManager inputMethodManager = inputMethodManager();
                if (inputMethodManager != null) {
                    View target = editor != null ? editor : view;
                    if (target != null) {
                        inputMethodManager.hideSoftInputFromWindow(target.getWindowToken(), 0);
                    }
                }
                if (view != null) {
                    view.requestFocus();
                }
            }
        });
    }

    private void showNativeEditor() {
        ensureNativeEditor();
        if (editorPanel == null || editor == null) {
            if (view != null) {
                view.requestFocus();
            }
            return;
        }
        configureNativeEditor();
        editorPanel.setVisibility(View.VISIBLE);
        updateEditorPanelInsets();
        focusNativeEditor();
        editor.post(new Runnable() {
            @Override
            public void run() {
                if (!active || editor == null) {
                    return;
                }
                focusNativeEditor();
                InputMethodManager inputMethodManager = inputMethodManager();
                if (inputMethodManager != null) {
                    inputMethodManager.restartInput(editor);
                    inputMethodManager.showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT);
                }
                updateEditorPanelInsets();
                requestFocusedBoundsAfterKeyboard();
            }
        });
    }

    private void hideNativeEditor() {
        if (editorPanel != null) {
            editorPanel.setVisibility(View.GONE);
        }
    }

    private void ensureNativeEditor() {
        if (editorPanel != null || container == null || activity == null) {
            return;
        }
        AndroidTextEditorStyle style = nativeTextEditorStyle();
        int margin = dp(style.panelMarginDp());
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(style.panelPaddingHorizontalDp()), dp(style.panelPaddingVerticalDp()),
                dp(style.panelPaddingHorizontalDp()), dp(style.panelPaddingVerticalDp()));
        panel.setBackground(background(style.panelBackgroundColor(), style.panelBorderColor(),
                style.panelCornerRadiusDp()));
        panel.setVisibility(View.GONE);

        EditText textEditor = new EditText(activity);
        textEditor.setTextColor(style.editorTextColor());
        textEditor.setHintTextColor(style.editorHintTextColor());
        textEditor.setTextSize(style.editorTextSizeSp());
        textEditor.setFocusable(true);
        textEditor.setFocusableInTouchMode(true);
        textEditor.setSelectAllOnFocus(false);
        textEditor.setPadding(dp(style.editorPaddingHorizontalDp()), dp(style.editorPaddingVerticalDp()),
                dp(style.editorPaddingHorizontalDp()), dp(style.editorPaddingVerticalDp()));
        textEditor.setBackground(background(style.editorBackgroundColor(), style.editorBorderColor(),
                style.editorCornerRadiusDp()));
        textEditor.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
                boolean enter = event != null
                        && event.getAction() == KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;
                if (actionId == EditorInfo.IME_ACTION_DONE
                        || actionId == EditorInfo.IME_ACTION_GO
                        || actionId == EditorInfo.IME_ACTION_SEND
                        || actionId == EditorInfo.IME_ACTION_SEARCH
                        || (!request.multiline() && enter)) {
                    commitNativeEditor();
                    return true;
                }
                return false;
            }
        });

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        actions.setGravity(Gravity.CENTER);

        Button cancel = new Button(activity);
        configureActionButton(cancel, style.cancelText(), style.cancelButtonTextColor(),
                style.cancelButtonBackgroundColor(), style.cancelButtonBorderColor(), style);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                cancelNativeEditor();
            }
        });

        Button ok = new Button(activity);
        configureActionButton(ok, style.acceptText(), style.acceptButtonTextColor(),
                style.acceptButtonBackgroundColor(), style.acceptButtonBackgroundColor(), style);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clicked) {
                commitNativeEditor();
            }
        });

        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        panel.addView(textEditor, editorParams);

        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(dp(style.actionButtonWidthDp()),
                dp(style.actionButtonHeightDp()));
        actions.addView(cancel, cancelParams);
        LinearLayout.LayoutParams okParams = new LinearLayout.LayoutParams(dp(style.actionButtonWidthDp()),
                dp(style.actionButtonHeightDp()));
        okParams.setMargins(0, dp(style.actionSpacingDp()), 0, 0);
        actions.addView(ok, okParams);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.setMargins(dp(style.actionSpacingDp()), 0, 0, 0);
        panel.addView(actions, actionParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        panelParams.setMargins(margin, 0, margin, margin);
        container.addView(panel, panelParams);
        editorPanel = panel;
        editor = textEditor;
        editorCancel = cancel;
        editorOk = ok;
        installKeyboardLayoutListener();
    }

    private void configureActionButton(Button button, String text, int textColor, int backgroundColor,
            int borderColor, AndroidTextEditorStyle style) {
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(style.actionTextSizeSp());
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setIncludeFontPadding(false);
        button.setBackground(background(backgroundColor, borderColor, style.actionCornerRadiusDp()));
    }

    private void configureNativeEditor() {
        if (editor == null) {
            return;
        }
        boolean multiline = request.multiline();
        editor.setGravity(multiline ? Gravity.START | Gravity.TOP : Gravity.START | Gravity.CENTER_VERTICAL);
        editor.setSingleLine(!multiline);
        editor.setMinLines(multiline ? 2 : 1);
        editor.setMaxLines(multiline ? 4 : 1);
        editor.setInputType(inputType(request));
        editor.setImeOptions((multiline ? EditorInfo.IME_ACTION_NONE : EditorInfo.IME_ACTION_DONE)
                | EditorInfo.IME_FLAG_NO_EXTRACT_UI
                | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        editor.setText(request.text());
        int selectionStart = Math.max(0, Math.min(request.selectionStart(), editor.length()));
        int selectionEnd = Math.max(0, Math.min(request.selectionEnd(), editor.length()));
        editor.setSelection(selectionStart, selectionEnd);
        editor.setEnabled(!request.readOnly());
        if (editorCancel != null) {
            editorCancel.setEnabled(true);
        }
        if (editorOk != null) {
            editorOk.setEnabled(!request.readOnly());
        }
    }

    private void cancelNativeEditor() {
        active = false;
        hideNativeEditor();
        InputMethodManager inputMethodManager = inputMethodManager();
        if (inputMethodManager != null && editor != null) {
            inputMethodManager.hideSoftInputFromWindow(editor.getWindowToken(), 0);
        }
        if (view != null) {
            view.requestFocus();
        }
    }

    private void commitNativeEditor() {
        if (committingNativeEditor || editor == null || input == null) {
            return;
        }
        committingNativeEditor = true;
        try {
            String text = editor.getText() != null ? editor.getText().toString() : "";
            active = false;
            hideNativeEditor();
            InputMethodManager inputMethodManager = inputMethodManager();
            if (inputMethodManager != null) {
                inputMethodManager.hideSoftInputFromWindow(editor.getWindowToken(), 0);
            }
            if (view != null) {
                view.requestFocus();
            }
            replaceFocusedText(text);
        } finally {
            committingNativeEditor = false;
        }
    }

    private void replaceFocusedText(String text) {
        if (input == null) {
            return;
        }
        input.dispatchKeyDown(Key.CONTROL_LEFT);
        input.dispatchKeyDown(Key.A);
        input.dispatchKeyUp(Key.A);
        input.dispatchKeyUp(Key.CONTROL_LEFT);
        if (text != null && text.length() > 0) {
            input.dispatchTextInput(text);
        } else {
            dispatchKey(Key.BACKSPACE);
        }
    }

    private void installKeyboardLayoutListener() {
        if (container == null || keyboardLayoutListenerInstalled) {
            return;
        }
        keyboardLayoutListenerInstalled = true;
        container.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                updateEditorPanelInsets();
            }
        });
    }

    private void updateEditorPanelInsets() {
        if (container == null || editorPanel == null || editorPanel.getVisibility() != View.VISIBLE) {
            return;
        }
        AndroidTextEditorStyle style = nativeTextEditorStyle();
        container.getWindowVisibleDisplayFrame(visibleFrame);
        int rootHeight = container.getRootView() != null ? container.getRootView().getHeight() : container.getHeight();
        int keyboardHeight = Math.max(0, rootHeight - visibleFrame.bottom);
        int baseMargin = dp(style.panelMarginDp());
        int bottomMargin = keyboardHeight > dp(80.0f) ? keyboardHeight + baseMargin : baseMargin;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) editorPanel.getLayoutParams();
        if (params.leftMargin != baseMargin || params.rightMargin != baseMargin || params.bottomMargin != bottomMargin) {
            params.setMargins(baseMargin, 0, baseMargin, bottomMargin);
            editorPanel.setLayoutParams(params);
        }
    }

    private AndroidTextEditorStyle nativeTextEditorStyle() {
        return backend != null ? backend.nativeTextEditorStyle() : new AndroidTextEditorStyle();
    }

    private GradientDrawable background(int color, int borderColor, float cornerRadiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setStroke(Math.max(1, dp(1.0f)), borderColor);
        drawable.setCornerRadius(dp(cornerRadiusDp));
        return drawable;
    }

    InputConnection createInputConnection(View target, EditorInfo outAttrs) {
        TextInputRequest current = request;
        if (outAttrs != null) {
            outAttrs.inputType = inputType(current);
            int action = current.multiline()
                    ? EditorInfo.IME_FLAG_NO_ENTER_ACTION
                    : EditorInfo.IME_ACTION_DONE;
            outAttrs.imeOptions = action | EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN;
            outAttrs.initialSelStart = current.selectionStart();
            outAttrs.initialSelEnd = current.selectionEnd();
        }
        return new AndroidTextInputConnection(target, this);
    }

    boolean focusedBounds(Rect outBounds) {
        if (outBounds == null || request == null || !request.hasBounds()) {
            return false;
        }
        int margin = Math.max(8, Math.round(12.0f * activity.getResources().getDisplayMetrics().density));
        outBounds.set(
                Math.max(0, request.boundsX() - margin),
                Math.max(0, request.boundsY() - margin),
                request.boundsX() + request.boundsWidth() + margin,
                request.boundsY() + request.boundsHeight() + margin);
        return true;
    }

    Editable editable() {
        return editable;
    }

    boolean commitText(CharSequence text) {
        if (!active || input == null || text == null || text.length() == 0) {
            return true;
        }
        if (nativeEditorVisible()) {
            commitTextToNativeEditor(text);
            return true;
        }
        input.dispatchTextInput(text.toString());
        return true;
    }

    boolean deleteSurroundingText(int beforeLength, int afterLength) {
        if (!active || input == null) {
            return true;
        }
        if (nativeEditorVisible()) {
            deleteSurroundingTextFromNativeEditor(beforeLength, afterLength);
            return true;
        }
        for (int i = 0; i < Math.max(0, beforeLength); i++) {
            dispatchKey(Key.BACKSPACE);
        }
        for (int i = 0; i < Math.max(0, afterLength); i++) {
            dispatchKey(Key.DELETE);
        }
        return true;
    }

    boolean sendKeyEvent(KeyEvent event) {
        if (nativeEditorVisible()) {
            return sendKeyEventToNativeEditor(event);
        }
        return view != null && event != null && backend.onKey(view, event.getKeyCode(), event);
    }

    boolean performEditorAction(int actionCode) {
        if (actionCode == EditorInfo.IME_ACTION_DONE
                || actionCode == EditorInfo.IME_ACTION_GO
                || actionCode == EditorInfo.IME_ACTION_NEXT
                || actionCode == EditorInfo.IME_ACTION_SEND
                || actionCode == EditorInfo.IME_ACTION_SEARCH) {
            if (editorPanel != null && editorPanel.getVisibility() == View.VISIBLE) {
                commitNativeEditor();
                return true;
            }
            dispatchKey(Key.ENTER);
            if (!request.multiline()) {
                hideTextInput();
            }
            return true;
        }
        return false;
    }

    private void applyRequest(TextInputRequest request) {
        TextInputRequest next = request != null ? request : TextInputRequest.builder().build();
        this.request = next;
        editable.replace(0, editable.length(), next.text());
        Selection.setSelection(editable, next.selectionStart(), next.selectionEnd());
    }

    private void requestFocusedBoundsAfterKeyboard() {
        if (view == null) {
            return;
        }
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                requestFocusedBoundsOnScreen();
            }
        }, 240L);
    }

    private void requestFocusedBoundsOnScreen() {
        if (view == null || !focusedBounds(focusedBounds)) {
            return;
        }
        view.requestRectangleOnScreen(focusedBounds, true);
    }

    private boolean nativeEditorVisible() {
        return active && editorPanel != null && editorPanel.getVisibility() == View.VISIBLE && editor != null;
    }

    private void focusNativeEditor() {
        if (editor == null) {
            return;
        }
        if (view != null) {
            view.clearFocus();
        }
        editor.setFocusable(true);
        editor.setFocusableInTouchMode(true);
        editor.requestFocus();
        editor.requestFocusFromTouch();
    }

    private void commitTextToNativeEditor(final CharSequence text) {
        if (text == null || text.length() == 0) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            insertNativeEditorText(text.toString());
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    insertNativeEditorText(text.toString());
                }
            });
        }
    }

    private void deleteSurroundingTextFromNativeEditor(final int beforeLength, final int afterLength) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            deleteNativeEditorText(beforeLength, afterLength);
        } else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    deleteNativeEditorText(beforeLength, afterLength);
                }
            });
        }
    }

    private boolean sendKeyEventToNativeEditor(final KeyEvent event) {
        if (event == null) {
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN) {
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_DEL) {
            deleteSurroundingTextFromNativeEditor(1, 0);
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_FORWARD_DEL) {
            deleteSurroundingTextFromNativeEditor(0, 1);
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            if (request.multiline()) {
                commitTextToNativeEditor("\n");
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        commitNativeEditor();
                    }
                });
            }
            return true;
        }
        int unicode = event.getUnicodeChar();
        if (unicode >= 32 && unicode != 127 && Character.isValidCodePoint(unicode)) {
            commitTextToNativeEditor(new String(Character.toChars(unicode)));
        }
        return true;
    }

    private void insertNativeEditorText(String text) {
        if (!nativeEditorVisible() || text == null || text.length() == 0) {
            return;
        }
        Editable target = editor.getText();
        if (target == null) {
            return;
        }
        int start = normalizedSelectionStart(target.length());
        int end = normalizedSelectionEnd(target.length());
        int from = Math.min(start, end);
        int to = Math.max(start, end);
        target.replace(from, to, text);
        Selection.setSelection(target, from + text.length());
    }

    private void deleteNativeEditorText(int beforeLength, int afterLength) {
        if (!nativeEditorVisible()) {
            return;
        }
        Editable target = editor.getText();
        if (target == null || target.length() == 0) {
            return;
        }
        int start = normalizedSelectionStart(target.length());
        int end = normalizedSelectionEnd(target.length());
        int from = Math.min(start, end);
        int to = Math.max(start, end);
        if (from == to) {
            from = Math.max(0, from - Math.max(0, beforeLength));
            to = Math.min(target.length(), to + Math.max(0, afterLength));
        }
        if (from < to) {
            target.delete(from, to);
            Selection.setSelection(target, from);
        }
    }

    private int normalizedSelectionStart(int length) {
        return normalizedSelection(editor != null ? editor.getSelectionStart() : -1, length);
    }

    private int normalizedSelectionEnd(int length) {
        return normalizedSelection(editor != null ? editor.getSelectionEnd() : -1, length);
    }

    private static int normalizedSelection(int selection, int length) {
        if (selection < 0) {
            return Math.max(0, length);
        }
        return Math.max(0, Math.min(selection, Math.max(0, length)));
    }

    private void dispatchKey(Key key) {
        if (input != null && key != null) {
            input.dispatchKeyDown(key);
            input.dispatchKeyUp(key);
        }
    }

    private int inputType(TextInputRequest request) {
        if (request.readOnly()) {
            return InputType.TYPE_NULL;
        }
        if (request.type() == TextInputType.INTEGER) {
            return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED;
        }
        if (request.type() == TextInputType.DECIMAL) {
            return InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL;
        }
        int type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if (request.password()) {
            type = InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        }
        if (request.multiline()) {
            type |= InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        }
        return type;
    }

    private int dp(float value) {
        if (value <= 0.0f) {
            return 0;
        }
        float density = activity != null ? activity.getResources().getDisplayMetrics().density : 1.0f;
        return Math.max(1, Math.round(value * Math.max(0.25f, density)));
    }

    private InputMethodManager inputMethodManager() {
        return activity != null
                ? (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)
                : null;
    }

    private void runOnUiThread(Runnable runnable) {
        if (activity == null || runnable == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            activity.runOnUiThread(runnable);
        }
    }

    private static final class AndroidTextInputConnection extends BaseInputConnection {
        private final AndroidTextInputController controller;

        AndroidTextInputConnection(View targetView, AndroidTextInputController controller) {
            super(targetView, true);
            this.controller = controller;
        }

        @Override
        public Editable getEditable() {
            return controller.editable();
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            return controller.commitText(text);
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            return controller.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean deleteSurroundingTextInCodePoints(int beforeLength, int afterLength) {
            return controller.deleteSurroundingText(beforeLength, afterLength);
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            return controller.sendKeyEvent(event);
        }

        @Override
        public boolean performEditorAction(int actionCode) {
            return controller.performEditorAction(actionCode);
        }
    }
}
