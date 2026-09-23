package io.github.libfdx.testsupport.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.backend.web.WebGeneratedAssets;
import io.github.libfdx.runtime.core.RuntimeCore;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Browser-runner fixture for startup ordering and disposal before native initialization completes. */
final class WebBootstrapProbe {
    static void start(WebApplicationConfig config) {
        WebApplicationBackend backend = new WebApplicationBackend();
        install(() -> { backend.dispose(); record("disposed"); });
        config.preloadApplication(new ApplicationAdapter() {
            @Override
            public void create(Fdx fdx) { record("preload"); }
        });
        backend.start(config, new ApplicationAdapter() {
            @Override
            public void create(Fdx fdx) {
                var assets = WebGeneratedAssets.assets();
                for (int index = 0; index < assets.size(); index++) {
                    var asset = assets.get(index);
                    recordAsset(asset.getPath(), asset.getSize());
                }
                recordCompiler(RuntimeCore.shaderCompiler().cacheIdentity());
                record("created");
            }
        });
        record("startReturned");
    }

    @JSFunctor
    private interface Action extends JSObject { void run(); }

    @JSBody(params = "dispose", script = """
            window.libfdxBootstrapProbe={preload:0,created:0,disposed:0,startReturned:0,dispose:dispose,assets:{}};
            """)
    private static native void install(Action dispose);

    @JSBody(params = {"path", "size"}, script = "window.libfdxBootstrapProbe.assets[path]=size;")
    private static native void recordAsset(String path, int size);

    @JSBody(params = "identity", script = "window.libfdxBootstrapProbe.compilerIdentity=identity;")
    private static native void recordCompiler(String identity);

    @JSBody(params = "name", script = "window.libfdxBootstrapProbe[name]++;")
    private static native void record(String name);
}
