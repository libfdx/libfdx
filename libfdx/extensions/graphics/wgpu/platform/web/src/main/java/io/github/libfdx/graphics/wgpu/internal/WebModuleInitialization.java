package io.github.libfdx.graphics.wgpu.internal;

import com.github.xpenatan.jParser.loader.JParserLibraryLoaderListener;
import io.github.libfdx.core.FdxException;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Promise completion for the browser's jParser module factories. */
public final class WebModuleInitialization {
    private WebModuleInitialization() {
    }

    public static void initialize(String name, JParserLibraryLoaderListener listener) {
        initialize(name, reason -> listener.onLoad(false, new FdxException("Could not initialize " + name + ": " + reason)));
    }

    @JSBody(params = {"name", "fail"}, script = """
            let pending;
            try { pending = window[name](); }
            catch (error) { fail(String(error)); return; }
            Promise.resolve(pending).then(module => {
                window[name] = module;
                window[name + 'OnInit']();
            }, error => fail(String(error)));
            """)
    private static native void initialize(String name, Failure fail);

    @JSFunctor
    private interface Failure extends JSObject {
        void accept(String reason);

    }
}
