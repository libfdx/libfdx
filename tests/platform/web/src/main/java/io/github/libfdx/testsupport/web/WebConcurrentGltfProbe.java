package io.github.libfdx.testsupport.web;

import io.github.libfdx.testsupport.graphics.ConcurrentGltfFixtures;
import io.github.libfdx.testsupport.graphics.ConcurrentGltfObserver;
import org.teavm.jso.JSBody;

/** Observes actual HTTP requests, withholding responses briefly to expose overlap and frame progress. */
public final class WebConcurrentGltfProbe implements ConcurrentGltfObserver {
    @Override
    public void beforeRequests() { install(ConcurrentGltfFixtures.joinedPaths()); }
    @Override
    public void modelReady(int index) { checkModel(index); }
    @Override
    public void frame(boolean allReady) { checkFrame(allReady); }
    @Override
    public void dispose() { restore(); }
    @Override
    public void loadingComplete(long assets, long shaders, long render, long gap) {
        timings(assets / 1e6, shaders / 1e6, render / 1e6, gap / 1e6);
    }

    @JSBody(params = {"assets", "shaders", "render", "gap"}, script = """
        const r=window.libfdxConcurrentGltfResult;
        r.maxAssetUpdateMs=assets;r.maxShaderUpdateMs=shaders;r.maxRenderMs=render;r.maxLoadingFrameGapMs=gap;
        """)
    private static native void timings(double assets, double shaders, double render, double gap);

    @JSBody(params = "joinedPaths", script = """
        const roots=joinedPaths.split(String.fromCharCode(10));
        const base=new URL('assets/',document.baseURI);
        const urls=roots.map(path=>new URL(path,base).href);
        const s={roots:roots,urls:urls,counts:new Map(),delivered:new Set(),deps:roots.map(()=>[]),owners:new Map(),
            ready:new Set(),pendingRoots:0,pendingDependencies:0,peakRoots:0,rootFrames:0,dependencyFrames:0,loadingFrames:0,
            reported:false,failure:null,previousFetch:window.fetch,inputEvents:0,preparationInputEvents:0};
        s.input=function(){if(!s.reported){s.inputEvents++;
            if(s.pendingRoots===0 && s.pendingDependencies===0)s.preparationInputEvents++;}};
        document.addEventListener('pointermove',s.input);
        function uncached(url) {
            const path=decodeURIComponent(url.substring(base.href.length));
            if (!(window.libfdxAssetManifest || {})[path]) throw new Error('Missing packaged fixture: '+path);
            if ((window.libfdxAssets || {})[path] || (window.libfdxAssets || {})['assets/'+path]
                    || (window.libfdxAssetPaths || []).includes(path)) throw new Error('Fixture was preloaded: '+path);
        }
        urls.forEach(uncached);
        s.wrapper=function(input,init) {
            const url=new URL(typeof input==='string'?input:input.url,document.baseURI).href;
            const root=urls.indexOf(url), owners=s.owners.get(url);
            if (root<0 && !owners) return s.previousFetch.call(this,input,init);
            uncached(url);
            s.counts.set(url,(s.counts.get(url)||0)+1);
            if (s.counts.get(url)!==1) throw new Error('Duplicate managed download: '+url);
            if (root>=0) {s.pendingRoots++;s.peakRoots=Math.max(s.peakRoots,s.pendingRoots);}
            else if (!owners.some(index=>s.delivered.has(urls[index])))
                throw new Error('Dependency requested before its document: '+url);
            else s.pendingDependencies++;
            return s.previousFetch.call(this,input,init).then(response=>{
                if (!response.ok) throw new Error('Download failed: '+url+' HTTP '+response.status);
                return (root>=0?response.clone().json():Promise.resolve(null)).then(document=>{
                if (root>=0) {
                    const dependencies=(document.buffers||[]).concat(document.images||[])
                        .filter(item=>item.uri && !item.uri.startsWith('data:'))
                        .map(item=>new URL(item.uri,url).href);
                    s.deps[root]=Array.from(new Set(dependencies));
                    s.deps[root].forEach(dependency=>{
                        // Previously requested shared dependencies may already be cached.
                        if (!s.owners.has(dependency)) {uncached(dependency);s.owners.set(dependency,[]);}
                        s.owners.get(dependency).push(root);
                    });
                }
                return new Promise(resolve=>setTimeout(()=>{
                    s.delivered.add(url);if(root>=0)s.pendingRoots--;else s.pendingDependencies--;resolve(response);
                },root>=0?350+(root%4)*75:400+(url.length%4)*150));
                });
            }).catch(error=>{s.failure=String(error);throw error;});
        };
        window.__libfdxConcurrentGltf=s;
        window.fetch=s.wrapper;
        console.info('[info] CONCURRENT_GLTF_UNCACHED roots='+roots.length);
        """)
    private static native void install(String joinedPaths);

    @JSBody(params = "index", script = """
        const s=window.__libfdxConcurrentGltf;
        if (!s || s.failure) throw new Error('Concurrent download failure: '+(s && s.failure));
        if (!s.delivered.has(s.urls[index]) || !s.deps[index].every(path=>s.delivered.has(path)))
            throw new Error('Model ready before its dependencies: '+s.roots[index]);
        if (s.ready.has(index)) throw new Error('Model completed twice');
        s.ready.add(index);
        """)
    private static native void checkModel(int index);

    @JSBody(params = "allReady", script = """
        const s=window.__libfdxConcurrentGltf;
        if (!s || s.failure) throw new Error('Concurrent download failure: '+(s && s.failure));
        if (!allReady) s.loadingFrames++;
        if(s.pendingRoots>0)s.rootFrames++;
        if(s.pendingDependencies>0)s.dependencyFrames++;
        if(allReady && !s.reported) {
            if(s.ready.size!==s.roots.length || s.peakRoots!==s.roots.length)
                throw new Error('Not all distinct models loaded concurrently: '+s.peakRoots);
            if(s.rootFrames<2 || s.dependencyFrames<2)throw new Error('Frames did not continue during downloads');
            if(s.counts.size!==s.roots.length+s.owners.size || Array.from(s.counts.values()).some(count=>count!==1))
                throw new Error('Missing or duplicate dependency request');
            const shared=Array.from(s.owners.values()).filter(owners=>owners.length>1).length;
            if(shared===0)throw new Error('Shared dependency coverage missing');
            window.libfdxConcurrentGltfResult={models:s.ready.size,requests:s.counts.size,sharedDependencies:shared,
                peakRoots:s.peakRoots,rootWaitFrames:s.rootFrames,dependencyWaitFrames:s.dependencyFrames,
                loadingFrames:s.loadingFrames,inputEvents:s.inputEvents,preparationInputEvents:s.preparationInputEvents};
            s.reported=true;
            console.info('[info] CONCURRENT_GLTF_DOWNLOADS '+JSON.stringify(window.libfdxConcurrentGltfResult));
        }
        """)
    private static native void checkFrame(boolean allReady);

    @JSBody(script = """
        const s=window.__libfdxConcurrentGltf;
        if(s && window.fetch===s.wrapper)window.fetch=s.previousFetch;
        if(s)document.removeEventListener('pointermove',s.input);
        delete window.__libfdxConcurrentGltf;
        """)
    private static native void restore();
}
