package io.github.libfdx.backend.web;

import java.util.function.Consumer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Window;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.workers.Worker;

/** Application-thread worker lifecycle; each preparation owner retains its own connection. */
final class WebWorkerConnection {
    private final Worker worker;
    private String objectUrl;
    private int startupTimer;
    private boolean disposed;

    private WebWorkerConnection(Worker worker, String objectUrl) {
        this.worker = worker;
        this.objectUrl = objectUrl;
    }

    private void listen(int timeout, Consumer<JSObject> receive, Runnable failed) {
        startupTimer = Window.setTimeout(() -> { if (!disposed) failed.run(); }, timeout);
        worker.onMessage(event -> {
            if (disposed) return;
            Window.clearTimeout(startupTimer);
            releaseUrl();
            receive.accept(event.getData());
        });
        worker.onError(event -> {
            event.preventDefault();
            if (!disposed) { Window.clearTimeout(startupTimer); failed.run(); }
        });
        worker.onEvent("messageerror", event -> {
            if (!disposed) { Window.clearTimeout(startupTimer); failed.run(); }
        });
    }

    static WebWorkerConnection open(String url, int timeout, Consumer<JSObject> receive, Runnable failed) {
        return open(url, null, timeout, receive, failed);
    }

    static WebWorkerConnection bundled(String role, int timeout, Consumer<JSObject> receive, Runnable failed) {
        if (!available()) return null;
        String url;
        try { url = createUrl(WebWorkerSource.source(), role); }
        catch (RuntimeException | Error failure) { return null; }
        return open(url + "#libfdx-" + role, url, timeout, receive, failed);
    }

    private static WebWorkerConnection open(String url, String objectUrl, int timeout,
            Consumer<JSObject> receive, Runnable failed) {
        if (!available() || url == null || url.isEmpty()) {
            if (objectUrl != null) revokeUrl(objectUrl);
            return null;
        }
        Worker worker = null;
        WebWorkerConnection connection = null;
        try {
            worker = new Worker(url);
            connection = new WebWorkerConnection(worker, objectUrl);
            connection.listen(timeout, receive, failed);
            return connection;
        } catch (RuntimeException | Error failure) {
            if (connection != null) connection.terminate();
            else {
                if (worker != null) worker.terminate();
                if (objectUrl != null) revokeUrl(objectUrl);
            }
            return null;
        }
    }

    void post(JSObject message) { worker.postMessage(message); }
    void post(JSObject message, ArrayBuffer transfer) { transfer(worker, message, transfer); }
    void terminate() {
        if (disposed) return;
        disposed = true;
        Window.clearTimeout(startupTimer);
        releaseUrl();
        worker.terminate();
    }

    private void releaseUrl() {
        if (objectUrl == null) return;
        revokeUrl(objectUrl);
        objectUrl = null;
    }

    @JSBody(script = "return typeof Worker === 'function';")
    private static native boolean available();
    @JSBody(params = {"source", "role"}, script = """
            var base = globalThis.libfdxRuntimeBaseUrl || new URL('scripts/', document.baseURI).href;
            var prefix = 'self.libfdxRuntimeBaseUrl=' + JSON.stringify(base) + ';\\n';
            var start = '\\nmain([' + JSON.stringify('libfdx-' + role) + ']);\\n';
            return URL.createObjectURL(new Blob([prefix, source, start], {type:'text/javascript'}));
            """)
    private static native String createUrl(String source, String role);
    @JSBody(params = "url", script = "URL.revokeObjectURL(url);")
    private static native void revokeUrl(String url);
    // TeaVM 0.16's Worker binding does not expose the transfer-list overload.
    @JSBody(params = {"worker", "message", "buffer"}, script = "worker.postMessage(message, [buffer]);")
    private static native void transfer(Worker worker, JSObject message, ArrayBuffer buffer);
}
