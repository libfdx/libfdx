package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLScriptElement;

/** Page-owned native runtime and bootstrap logo. Application callbacks remain gated by the backend.
 * Access only on the browser event loop. Concurrent backends share one initialization; disposing a
 * backend does not cancel the page's native module or create listeners after disposal. */
final class WebRuntimeBootstrap {
    private static WebRuntimeBootstrap shared;
    private final FdxFuture<Void> result = FdxFuture.pending();
    private boolean nativeReady, logoReady;

    private WebRuntimeBootstrap() { }

    static FdxFuture<Void> start() {
        if (shared != null) return shared.result;
        shared = new WebRuntimeBootstrap();
        shared.begin();
        return shared.result;
    }

    private void begin() {
        try {
            String base = installMetadata();
            var document = HTMLDocument.current();
            var script = (HTMLScriptElement) document.createElement("script");
            script.setSrc(base + "fdx.js");
            script.addEventListener("load", event -> {
                if (result.isDone()) return;
                initializeNative(base, module -> {
                    if (result.isDone()) return;
                    try {
                        installNative(module);
                        nativeReady = true;
                        completeIfReady();
                    } catch (RuntimeException | Error failure) { fail(failure.toString()); }
                }, this::fail);
            });
            script.addEventListener("error", event -> fail("Could not load script: " + base + "fdx.js"));
            document.getHead().appendChild(script);
            prepareLogo(WebAssets.DEFAULT_PRELOAD_LOGO_PATH, () -> {
                logoReady = true;
                completeIfReady();
            }, this::fail);
        } catch (RuntimeException | Error failure) { fail(failure.toString()); }
    }

    private void completeIfReady() {
        if (!result.isDone() && nativeReady && logoReady) {
            result.complete(null);
        }
    }

    private void fail(String message) {
        if (result.isDone()) return;
        String detail = "Web runtime startup failed: " + message;
        result.completeExceptionally(new FdxException(detail));
        reportFailure(detail);
    }

    // TeaVM's browser Error stack may omit the Java exception message. Preserve the actionable cause.
    @JSBody(params = "message", script = """
            if(typeof globalThis.libfdxStartupError==='function')globalThis.libfdxStartupError(message);
            """)
    private static native void reportFailure(String message);

    /** Reads packaging data, with defaults for custom hosts using compiler-generated assets. */
    @JSBody(script = """
            var root=globalThis;
            var element=document.getElementById('libfdx-bootstrap-data');
            if(element) {
                var data=JSON.parse(element.textContent);
                root.libfdxPublishedAssets=data.assets;
                root.libfdxShaderCompilerIdentity=data.shaderCompilerIdentity;
                root.libfdxRuntimeBaseUrl=new URL(data.runtimeBase || 'scripts/',document.baseURI).href;
            } else if(!root.libfdxRuntimeBaseUrl) {
                root.libfdxRuntimeBaseUrl=new URL('scripts/',document.baseURI).href;
            }
            return root.libfdxRuntimeBaseUrl;
            """)
    static native String installMetadata();

    @JSFunctor private interface Success extends JSObject { void run(JSObject module); }
    @JSFunctor private interface Failure extends JSObject { void run(String message); }
    @JSFunctor private interface Ready extends JSObject { void run(); }

    // The packaged Emscripten incoming API accepts instantiateWasm, not wasmBinary. Reject the
    // outer promise explicitly if instantiation fails: the native callback handshake would stay pending.
    @JSBody(params = {"base", "success", "failure"}, script = """
            Promise.resolve().then(function() {
                if(typeof FdxModule!=='function')throw new Error('fdx.js did not define FdxModule');
                return fetch(new URL('fdx.wasm',base).href);
            }).then(function(response) {
                if(!response.ok)throw new Error('Could not load fdx.wasm: '+response.status);
                return response.arrayBuffer();
            }).then(function(bytes) {
                return new Promise(function(resolve,reject) {
                    var factory=FdxModule({instantiateWasm:function(imports,accept) {
                        WebAssembly.instantiate(bytes,imports).then(function(result) {
                            accept(result.instance);
                        }).catch(reject);
                        return {};
                    },locateFile:function(path) { return new URL(path,base).href; }});
                    Promise.resolve(factory).then(resolve,reject);
                });
            }).then(success,function(error){failure('Could not initialize fdx.wasm: '+String(error && error.stack || error));});
            """)
    private static native void initializeNative(String base, Success success, Failure failure);

    @JSBody(params = "module", script = "var root=globalThis;\n" + WebShaderCompilerScript.SOURCE
            + "\ninstallShaderCompiler(module); root.libfdxCoreModule=module;")
    private static native void installNative(JSObject module);

    @JSBody(params = {"path", "ready", "failure"}, script = """
            globalThis.libfdxPreloadAssets([path]).then(function(){ready();},function(error){failure(String(error));});
            """)
    private static native void prepareLogo(String path, Ready ready, Failure failure);
}
