package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.g3d.StandardPbrSourcePreparer;
import io.github.libfdx.graphics.g3d.StandardPbrSources;
import io.github.libfdx.graphics.shader.ShaderProfile;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Standard PBR graph worker, automatically owned by default model plans on web. Applications
 * may also create and share one explicitly. Requires the G3D artifact. Call only
 * from the browser application thread; dispose after model plans and preparation scopes. One
 * worker caches source per shader profile, with at most 32 waiting callers. Results complete on
 * the browser event loop; no loading executor is held while awaiting worker input. Unsupported
 * workers, startup failure or a crash use each caller's own loading executor as fallback (which
 * can block). Disposal cancels waiting callers and ignores late replies. Arbitrary custom graphs
 * and Java source generators are not part of this explicit versioned recipe.
 */
public final class WebPbrSourcePreparation implements StandardPbrSourcePreparer.Owned {
    private final ArrayList<Request> pending = new ArrayList<>();
    private final StandardPbrSources[] cache = new StandardPbrSources[ShaderProfile.values().length];
    private WebWorkerConnection worker;
    private ShaderProfile active;
    private int id, completed, fallbacks;
    private boolean ready, fallback, disposed;

    public WebPbrSourcePreparation() { this(null, true); }
    /** Custom script URL relative to the document; its lifetime remains the caller's responsibility. */
    public WebPbrSourcePreparation(String url) {
        this(url, false);
    }
    private WebPbrSourcePreparation(String url, boolean bundled) {
        if (!bundled && (url == null || url.isEmpty())) throw new IllegalArgumentException("PBR worker URL is required");
        worker = bundled ? WebWorkerConnection.bundled("pbr", 15000, this::receive, this::unavailable)
                : WebWorkerConnection.open(url, 15000, this::receive, this::unavailable);
        fallback = worker == null;
    }
    public int completedWorkerJobs() { return completed; }
    public int fallbackJobs() { return fallbacks; }
    @Override public boolean isDisposed() { return disposed; }

    @Override public FdxFuture<StandardPbrSources> prepare(ShaderProfile profile, Consumer<Runnable> execute) {
        if (profile == null || execute == null) throw new NullPointerException("PBR profile/executor");
        if (disposed) return FdxFuture.failed(new FdxException("PBR worker is disposed"));
        if (cache[profile.ordinal()] != null) return FdxFuture.completed(cache[profile.ordinal()]);
        if (pending.size() >= 32) return FdxFuture.failed(new FdxException("PBR worker queue is full"));
        Request request = new Request(profile, execute); pending.add(request);
        if (fallback) runFallback(request); else pump();
        return request.result;
    }
    private void pump() {
        if (disposed || fallback || !ready || active != null || pending.isEmpty()) return;
        active = pending.get(0).profile;
        try { worker.post(message(++id, StandardPbrSources.VERSION, active.name())); }
        catch (RuntimeException | Error error) { unavailable(); }
    }
    private void receive(JSObject message) {
        if (disposed || fallback) return;
        if (ready(message)) { ready = true; pump(); return; }
        if (active == null || id(message) != id) { unavailable(); return; }
        ShaderProfile profile = active;
        StandardPbrSources sources = null;
        Throwable failure = null;
        try {
            if (error(message) != null) throw new FdxException("PBR worker: " + error(message));
            if (!valid(message, StandardPbrSources.VERSION, profile.name())) throw new FdxException("Invalid PBR worker result");
            String[] variants = new String[8];
            for (int i = 0; i < 8; i++) variants[i] = variant(message, i);
            sources = new StandardPbrSources(profile, surface(message), library(message), variants);
            cache[profile.ordinal()] = sources; completed++;
        } catch (RuntimeException | Error error) { failure = error; }
        active = null;
        ArrayList<Request> matching = new ArrayList<>();
        for (Request request : pending) if (request.profile == profile) matching.add(request);
        pending.removeAll(matching);
        Throwable listenerFailure = null;
        for (Request request : matching) {
            try {
                if (disposed) request.result.completeExceptionally(new CancellationException("PBR worker disposed"));
                else if (failure == null) request.result.complete(sources); else request.result.completeExceptionally(failure);
            } catch (RuntimeException | Error error) { if (listenerFailure == null) listenerFailure = error; }
        }
        pump();
        if (listenerFailure instanceof RuntimeException error) throw error;
        if (listenerFailure instanceof Error error) throw error;
    }
    private void unavailable() {
        if (disposed || fallback) return;
        fallback = true; active = null;
        if (worker != null) worker.terminate();
        worker = null;
        for (Request request : new ArrayList<>(pending)) runFallback(request);
    }
    private void runFallback(Request request) {
        if (request.scheduled) return;
        request.scheduled = true;
        try {
            request.execute.accept(() -> {
                if (disposed || request.result.isDone()) return;
                StandardPbrSources sources;
                try {
                    sources = cache[request.profile.ordinal()];
                    if (sources == null) { fallbacks++; sources = StandardPbrSources.compile(request.profile); cache[request.profile.ordinal()] = sources; }
                } catch (RuntimeException | Error error) {
                    pending.remove(request); request.result.completeExceptionally(error); return;
                }
                pending.remove(request); request.result.complete(sources);
            });
        } catch (RuntimeException | Error error) {
            if (request.result.isDone()) throw error;
            pending.remove(request); request.result.completeExceptionally(error);
        }
    }
    @Override public void dispose() {
        if (disposed) return;
        disposed = true;
        if (worker != null) worker.terminate();
        worker = null; active = null;
        java.util.Arrays.fill(cache, null);
        ArrayList<Request> cancelled = new ArrayList<>(pending); pending.clear();
        Throwable first = null;
        for (Request request : cancelled) {
            try { request.result.completeExceptionally(new CancellationException("PBR worker disposed")); }
            catch (RuntimeException | Error error) { if (first == null) first = error; }
        }
        if (first instanceof RuntimeException error) throw error;
        if (first instanceof Error error) throw error;
    }
    private static final class Request {
        final ShaderProfile profile;
        final Consumer<Runnable> execute;
        final FdxFuture<StandardPbrSources> result = FdxFuture.pending();
        boolean scheduled;
        Request(ShaderProfile profile, Consumer<Runnable> execute) { this.profile = profile; this.execute = execute; }
    }
    @JSBody(params={"id","version","profile"}, script="return {id:id,version:version,profile:profile};")
    private static native JSObject message(int id, int version, String profile);
    @JSBody(params="m", script="return m.ready===true;") private static native boolean ready(JSObject m);
    @JSBody(params="m", script="return m.id|0;") private static native int id(JSObject m);
    @JSBody(params="m", script="return m.error||null;") private static native String error(JSObject m);
    @JSBody(params="m", script="return m.surface;") private static native String surface(JSObject m);
    @JSBody(params="m", script="return m.library;") private static native String library(JSObject m);
    @JSBody(params={"m","i"}, script="return m.variants[i];") private static native String variant(JSObject m,int i);
    @JSBody(params={"m","version","profile"}, script="""
            if(m.version!==version||m.profile!==profile||!Array.isArray(m.variants)||m.variants.length!==8) return false;
            var texts=[m.surface,m.library].concat(m.variants),size=0;
            for(var i=0;i<texts.length;i++){if(typeof texts[i]!=='string'||texts[i].length===0)return false;size+=texts[i].length;}
            return size<=8388608;
            """) private static native boolean valid(JSObject m,int version,String profile);
}
