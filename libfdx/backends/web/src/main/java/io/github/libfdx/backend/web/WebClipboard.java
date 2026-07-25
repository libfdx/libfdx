package io.github.libfdx.backend.web;

import io.github.libfdx.input.Clipboard;
import org.teavm.jso.JSBody;

/**
 * Provides an immediate clipboard cache and mirrors writes to the browser system clipboard.
 *
 * <p>Browser clipboard reads are asynchronous and permission-gated. Native browser text editors remain the
 * authoritative path for interactive copy and paste; this bridge makes programmatic writes and synchronous
 * libFDX clipboard round trips useful without blocking the render loop.</p>
 *
 * @author xpenatan
 */
final class WebClipboard implements Clipboard {
    private String text = "";

    @Override
    public String getText() {
        String cached = cachedText();
        if (cached != null) {
            text = cached;
        }
        refreshSystemText();
        return text;
    }

    @Override
    public void setText(String text) {
        this.text = text != null ? text : "";
        writeSystemText(this.text);
    }

    @JSBody(script =
            "var value = window.__libfdxClipboardText;\n" +
            "return typeof value === 'string' ? value : null;")
    private static native String cachedText();

    @JSBody(script =
            "try {\n" +
            "  if (navigator.clipboard && navigator.clipboard.readText) {\n" +
            "    var revision = window.__libfdxClipboardRevision || 0;\n" +
            "    navigator.clipboard.readText().then(function(value) {\n" +
            "      if ((window.__libfdxClipboardRevision || 0) === revision) {\n" +
            "        window.__libfdxClipboardText = value || '';\n" +
            "      }\n" +
            "    }).catch(function() {});\n" +
            "  }\n" +
            "} catch (e) {\n" +
            "}")
    private static native void refreshSystemText();

    @JSBody(params = { "value" }, script =
            "window.__libfdxClipboardRevision = (window.__libfdxClipboardRevision || 0) + 1;\n" +
            "window.__libfdxClipboardText = value || '';\n" +
            "try {\n" +
            "  if (navigator.clipboard && navigator.clipboard.writeText) {\n" +
            "    navigator.clipboard.writeText(value || '').catch(function() {});\n" +
            "  }\n" +
            "} catch (e) {\n" +
            "}")
    private static native void writeSystemText(String value);
}
