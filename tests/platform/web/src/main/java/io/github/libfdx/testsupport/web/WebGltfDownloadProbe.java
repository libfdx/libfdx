package io.github.libfdx.testsupport.web;

import io.github.libfdx.tests.graphics.GltfLoadingTest;
import io.github.libfdx.testsupport.graphics.GltfLoadingObserver;
import org.teavm.jso.JSBody;

/** Delays real responses for this fixture only; it never initiates asset requests. */
public final class WebGltfDownloadProbe implements GltfLoadingObserver {
    @Override
    public void beforeRequest() {
        install(GltfLoadingTest.MODEL_PATH, GltfLoadingTest.BUFFER_PATH, GltfLoadingTest.IMAGE_PATH);
    }

    @Override
    public void frame(boolean modelReady) { checkFrame(modelReady); }
    @Override
    public void dispose() { restore(); }

    @JSBody(params = {"model", "buffer", "image"}, script = """
            var paths = [model, buffer, image];
            var assets = window.libfdxAssets || {};
            var manifest = window.libfdxAssetManifest || {};
            var preload = window.libfdxAssetPaths || [];
            paths.forEach(function(path) {
                if (!manifest[path]) throw new Error('Deferred glTF fixture is not packaged: ' + path);
                if (assets[path] || assets['assets/' + path] || preload.indexOf(path) >= 0)
                    throw new Error('glTF fixture was preloaded: ' + path);
            });
            if (window.__libfdxGltfDownloadProbe) throw new Error('glTF download probe already installed');
            var state = {paths: paths, counts: [0, 0, 0], delivered: [false, false, false],
                rootWaitFrames: 0, dependencyWaitFrames: 0, ready: false, failure: null,
                previousFetch: window.fetch};
            var urls = paths.map(function(path) { return new URL('assets/' + path, document.baseURI).href; });
            state.wrapper = function(input, init) {
                var url = new URL(typeof input === 'string' ? input : input.url, document.baseURI).href;
                var index = urls.indexOf(url);
                if (index < 0) return state.previousFetch.call(this, input, init);
                state.counts[index]++;
                if (index > 0 && !state.delivered[0]) {
                    state.failure = 'Dependency requested before the glTF document arrived';
                    return Promise.reject(new Error(state.failure));
                }
                return state.previousFetch.call(this, input, init).then(function(response) {
                    if (!response.ok) throw new Error('Runtime download failed: ' + paths[index] + ' HTTP ' + response.status);
                    // Keep rendering observable while the real HTTP responses are withheld.
                    return new Promise(function(resolve) {
                        setTimeout(function() { state.delivered[index] = true; resolve(response); },
                            [350, 650, 1000][index]);
                    });
                }).catch(function(error) { state.failure = String(error); throw error; });
            };
            window.__libfdxGltfDownloadProbe = state;
            window.fetch = state.wrapper;
            console.info('[info] GltfLoadingTest preload exclusion verified: root, buffer and image are packaged but uncached');
            """)
    private static native void install(String model, String buffer, String image);

    @JSBody(params = "ready", script = """
            var state = window.__libfdxGltfDownloadProbe;
            if (!state) throw new Error('glTF download probe missing');
            if (state.failure) throw new Error(state.failure);
            if (!state.delivered[0]) state.rootWaitFrames++;
            else if (!state.delivered[1] || !state.delivered[2]) state.dependencyWaitFrames++;
            if (ready && !state.ready) {
                if (!state.delivered.every(function(value) { return value; }))
                    throw new Error('Model became ready before all HTTP responses arrived');
                if (!state.counts.every(function(value) { return value === 1; }))
                    throw new Error('Expected one runtime request per glTF resource: ' + state.counts);
                if (state.rootWaitFrames < 2 || state.dependencyWaitFrames < 2)
                    throw new Error('Rendering did not continue during both download stages');
                state.ready = true;
                window.libfdxGltfDownloadResult = {ready: true, requests: state.counts.slice(),
                    rootWaitFrames: state.rootWaitFrames, dependencyWaitFrames: state.dependencyWaitFrames};
                console.info('[info] GltfLoadingTest downloads verified: requests=' + state.counts.join(',')
                    + ', rootWaitFrames=' + state.rootWaitFrames + ', dependencyWaitFrames=' + state.dependencyWaitFrames);
            }
            """)
    private static native void checkFrame(boolean ready);

    @JSBody(script = """
            var state = window.__libfdxGltfDownloadProbe;
            if (state && window.fetch === state.wrapper) window.fetch = state.previousFetch;
            delete window.__libfdxGltfDownloadProbe;
            """)
    private static native void restore();
}
