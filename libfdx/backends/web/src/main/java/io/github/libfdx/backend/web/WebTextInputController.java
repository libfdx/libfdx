package io.github.libfdx.backend.web;

import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.TextInputController;
import io.github.libfdx.input.TextInputRequest;
import io.github.libfdx.input.TextInputType;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.KeyboardEvent;
import org.teavm.jso.dom.events.Registration;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Represents a web text input controller.
 *
 * @author xpenatan
 */
final class WebTextInputController implements TextInputController {
    private DefaultInput input;
    private HTMLCanvasElement canvas;
    private TextInputRequest request = TextInputRequest.builder().build();
    private HTMLElement panel;
    private HTMLElement editor;
    private HTMLElement actions;
    private HTMLElement cancelButton;
    private HTMLElement acceptButton;
    private Registration editorKeyRegistration;
    private Registration cancelRegistration;
    private Registration acceptRegistration;
    private boolean active;
    private boolean multilineEditor;
    private boolean committingEditor;

    void input(DefaultInput input) {
        this.input = input;
    }

    void canvas(HTMLCanvasElement canvas) {
        this.canvas = canvas;
    }

    boolean handlesEvent(Event event) {
        return active && panel != null && event != null && eventTargetsPanel(panel, event);
    }

    /**
     * Runs the show text input step.
     *
     * @param request the request
     */
    @Override
    public void showTextInput(TextInputRequest request) {
        applyRequest(request);
        if (this.request.readOnly()) {
            hideTextInput();
            return;
        }
        ensurePanel();
        configureEditor(editor, multilineEditor, this.request.password(), this.request.readOnly(),
                inputMode(this.request.type()), this.request.text(), this.request.selectionStart(),
                this.request.selectionEnd());
        stylePanel(panel);
        styleEditor(editor, multilineEditor);
        styleActions(actions);
        styleButton(cancelButton, false);
        styleButton(acceptButton, true);
        showPanel(panel);
        installViewportHandler(panel);
        focusEditor(editor, this.request.selectionStart(), this.request.selectionEnd());
        active = true;
    }

    /**
     * Runs the update text input step.
     *
     * @param request the request
     */
    @Override
    public void updateTextInput(TextInputRequest request) {
        if (committingEditor) {
            return;
        }
        applyRequest(request);
        if (active && editor != null) {
            configureEditor(editor, multilineEditor, this.request.password(), this.request.readOnly(),
                    inputMode(this.request.type()), this.request.text(), this.request.selectionStart(),
                    this.request.selectionEnd());
            focusEditor(editor, this.request.selectionStart(), this.request.selectionEnd());
        }
    }

    /**
     * Runs the hide text input step.
     */
    @Override
    public void hideTextInput() {
        active = false;
        disposePanel();
        if (canvas != null) {
            focusCanvas(canvas);
        }
    }

    void dispose() {
        active = false;
        disposePanel();
    }

    private void ensurePanel() {
        boolean multiline = request.multiline();
        if (panel != null && editor != null && multilineEditor == multiline) {
            return;
        }
        disposePanel();
        HTMLDocument document = HTMLDocument.current();
        panel = document.createElement("div");
        editor = document.createElement(multiline ? "textarea" : "input");
        actions = document.createElement("div");
        cancelButton = document.createElement("button");
        acceptButton = document.createElement("button");
        multilineEditor = multiline;
        cancelButton.setInnerText("X");
        acceptButton.setInnerText("OK");
        prepareButton(cancelButton);
        prepareButton(acceptButton);
        appendChild(actions, cancelButton);
        appendChild(actions, acceptButton);
        appendChild(panel, editor);
        appendChild(panel, actions);
        appendChild(document.getBody(), panel);
        editorKeyRegistration = editor.onKeyDown(new EventListener<KeyboardEvent>() {
            @Override
            public void handleEvent(KeyboardEvent event) {
                handleEditorKey(event);
            }
        });
        cancelRegistration = cancelButton.onEvent("click", new EventListener<Event>() {
            @Override
            public void handleEvent(Event event) {
                event.preventDefault();
                cancelEditor();
            }
        });
        acceptRegistration = acceptButton.onEvent("click", new EventListener<Event>() {
            @Override
            public void handleEvent(Event event) {
                event.preventDefault();
                commitEditor();
            }
        });
    }

    private void handleEditorKey(KeyboardEvent event) {
        String key = event.getKey();
        if ("Escape".equals(key) || "Esc".equals(key)) {
            event.preventDefault();
            cancelEditor();
            return;
        }
        if ("Enter".equals(key) && (!request.multiline() || event.isCtrlKey() || event.isMetaKey())) {
            event.preventDefault();
            commitEditor();
        }
    }

    private void cancelEditor() {
        hideTextInput();
    }

    private void commitEditor() {
        if (input == null || editor == null) {
            hideTextInput();
            return;
        }
        String value = editorValue(editor);
        committingEditor = true;
        try {
            input.dispatchKeyDown(Key.CONTROL_LEFT);
            input.dispatchKeyDown(Key.A);
            input.dispatchKeyUp(Key.A);
            input.dispatchKeyUp(Key.CONTROL_LEFT);
            if (value != null && value.length() > 0) {
                input.dispatchTextInput(value);
            } else {
                input.dispatchKeyDown(Key.BACKSPACE);
                input.dispatchKeyUp(Key.BACKSPACE);
            }
        } finally {
            committingEditor = false;
        }
        hideTextInput();
    }

    private void disposePanel() {
        disposeRegistration(editorKeyRegistration);
        disposeRegistration(cancelRegistration);
        disposeRegistration(acceptRegistration);
        editorKeyRegistration = null;
        cancelRegistration = null;
        acceptRegistration = null;
        if (panel != null) {
            removeViewportHandler(panel);
            removeElement(panel);
        }
        panel = null;
        editor = null;
        actions = null;
        cancelButton = null;
        acceptButton = null;
    }

    private void applyRequest(TextInputRequest request) {
        this.request = request != null ? request : TextInputRequest.builder().build();
    }

    private static void disposeRegistration(Registration registration) {
        if (registration != null) {
            registration.dispose();
        }
    }

    private static String inputMode(TextInputType type) {
        if (type == TextInputType.INTEGER) {
            return "numeric";
        }
        if (type == TextInputType.DECIMAL) {
            return "decimal";
        }
        return "text";
    }

    @JSBody(params = { "parent", "child" }, script = "parent.appendChild(child);")
    private static native void appendChild(HTMLElement parent, HTMLElement child);

    @JSBody(params = { "button" }, script =
            "button.type = 'button';\n" +
            "button.tabIndex = 0;")
    private static native void prepareButton(HTMLElement button);

    @JSBody(params = { "panel" }, script =
            "panel.style.cssText = '';\n" +
            "panel.style.position = 'fixed';\n" +
            "panel.style.left = '8px';\n" +
            "panel.style.right = '8px';\n" +
            "panel.style.bottom = '8px';\n" +
            "panel.style.zIndex = '2147483647';\n" +
            "panel.style.display = 'flex';\n" +
            "panel.style.flexDirection = 'row';\n" +
            "panel.style.alignItems = 'stretch';\n" +
            "panel.style.gap = '8px';\n" +
            "panel.style.padding = '8px';\n" +
            "panel.style.boxSizing = 'border-box';\n" +
            "panel.style.background = '#f8fafc';\n" +
            "panel.style.border = '1px solid #94a3b8';\n" +
            "panel.style.borderRadius = '8px';\n" +
            "panel.style.boxShadow = '0 10px 28px rgba(15, 23, 42, 0.28)';")
    private static native void stylePanel(HTMLElement panel);

    @JSBody(params = { "editor", "multiline" }, script =
            "editor.style.cssText = '';\n" +
            "editor.style.flex = '1 1 auto';\n" +
            "editor.style.minWidth = '0';\n" +
            "editor.style.height = multiline ? '92px' : '40px';\n" +
            "editor.style.maxHeight = multiline ? '38vh' : '40px';\n" +
            "editor.style.boxSizing = 'border-box';\n" +
            "editor.style.padding = '8px 10px';\n" +
            "editor.style.border = '1px solid #cbd5e1';\n" +
            "editor.style.borderRadius = '6px';\n" +
            "editor.style.background = '#ffffff';\n" +
            "editor.style.color = '#111827';\n" +
            "editor.style.font = '16px system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif';\n" +
            "editor.style.lineHeight = '20px';\n" +
            "editor.style.outline = 'none';\n" +
            "editor.style.resize = 'none';\n" +
            "editor.style.whiteSpace = multiline ? 'pre-wrap' : 'nowrap';")
    private static native void styleEditor(HTMLElement editor, boolean multiline);

    @JSBody(params = { "actions" }, script =
            "actions.style.cssText = '';\n" +
            "actions.style.display = 'flex';\n" +
            "actions.style.flex = '0 0 auto';\n" +
            "actions.style.flexDirection = 'column';\n" +
            "actions.style.gap = '6px';")
    private static native void styleActions(HTMLElement actions);

    @JSBody(params = { "button", "primary" }, script =
            "button.style.cssText = '';\n" +
            "button.style.width = '48px';\n" +
            "button.style.height = '34px';\n" +
            "button.style.boxSizing = 'border-box';\n" +
            "button.style.borderRadius = '6px';\n" +
            "button.style.border = primary ? '1px solid #111827' : '1px solid #cbd5e1';\n" +
            "button.style.background = primary ? '#111827' : '#e5e7eb';\n" +
            "button.style.color = primary ? '#ffffff' : '#111827';\n" +
            "button.style.font = '600 14px system-ui, -apple-system, BlinkMacSystemFont, Segoe UI, sans-serif';\n" +
            "button.style.padding = '0';\n" +
            "button.style.cursor = 'pointer';")
    private static native void styleButton(HTMLElement button, boolean primary);

    @JSBody(params = { "panel" }, script = "panel.style.display = 'flex';")
    private static native void showPanel(HTMLElement panel);

    @JSBody(params = { "editor", "multiline", "password", "readOnly", "inputMode", "value", "selectionStart",
            "selectionEnd" }, script =
            "if (!multiline) {\n" +
            "  editor.type = password ? 'password' : 'text';\n" +
            "} else {\n" +
            "  editor.rows = 3;\n" +
            "  editor.wrap = 'soft';\n" +
            "}\n" +
            "editor.inputMode = inputMode || 'text';\n" +
            "editor.autocapitalize = 'off';\n" +
            "editor.autocomplete = 'off';\n" +
            "editor.spellcheck = false;\n" +
            "editor.readOnly = !!readOnly;\n" +
            "editor.value = value || '';\n" +
            "if (editor.setSelectionRange) {\n" +
            "  editor.setSelectionRange(selectionStart, selectionEnd);\n" +
            "}")
    private static native void configureEditor(HTMLElement editor, boolean multiline, boolean password,
            boolean readOnly, String inputMode, String value, int selectionStart, int selectionEnd);

    @JSBody(params = { "editor", "selectionStart", "selectionEnd" }, script =
            "editor.focus();\n" +
            "if (editor.setSelectionRange) {\n" +
            "  editor.setSelectionRange(selectionStart, selectionEnd);\n" +
            "}")
    private static native void focusEditor(HTMLElement editor, int selectionStart, int selectionEnd);

    @JSBody(params = { "editor" }, script = "return editor.value || '';")
    private static native String editorValue(HTMLElement editor);

    @JSBody(params = { "panel", "event" }, script =
            "var target = event.target;\n" +
            "return !!(target && (target === panel || (panel.contains && panel.contains(target))));")
    private static native boolean eventTargetsPanel(HTMLElement panel, Event event);

    @JSBody(params = { "panel" }, script =
            "if (panel.__libfdxViewportUpdate) {\n" +
            "  panel.__libfdxViewportUpdate();\n" +
            "  return;\n" +
            "}\n" +
            "var update = function() {\n" +
            "  var bottom = 8;\n" +
            "  if (window.visualViewport) {\n" +
            "    bottom = Math.max(8, window.innerHeight - window.visualViewport.height - window.visualViewport.offsetTop + 8);\n" +
            "  }\n" +
            "  panel.style.bottom = bottom + 'px';\n" +
            "};\n" +
            "panel.__libfdxViewportUpdate = update;\n" +
            "window.addEventListener('resize', update);\n" +
            "if (window.visualViewport) {\n" +
            "  window.visualViewport.addEventListener('resize', update);\n" +
            "  window.visualViewport.addEventListener('scroll', update);\n" +
            "}\n" +
            "update();")
    private static native void installViewportHandler(HTMLElement panel);

    @JSBody(params = { "panel" }, script =
            "var update = panel.__libfdxViewportUpdate;\n" +
            "if (!update) return;\n" +
            "window.removeEventListener('resize', update);\n" +
            "if (window.visualViewport) {\n" +
            "  window.visualViewport.removeEventListener('resize', update);\n" +
            "  window.visualViewport.removeEventListener('scroll', update);\n" +
            "}\n" +
            "delete panel.__libfdxViewportUpdate;")
    private static native void removeViewportHandler(HTMLElement panel);

    @JSBody(params = { "element" }, script =
            "if (element && element.parentNode) {\n" +
            "  element.parentNode.removeChild(element);\n" +
            "}")
    private static native void removeElement(HTMLElement element);

    @JSBody(params = { "canvas" }, script = "canvas.focus();")
    private static native void focusCanvas(HTMLCanvasElement canvas);
}
