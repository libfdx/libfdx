package io.github.libfdx.testsupport.web;

import org.teavm.jso.JSBody;

/** Browser diagnostics for model loading, including work outside assets.update(). */
final class WebLoadingProbe {
    private WebLoadingProbe() { }

    @JSBody(script = """
        if (globalThis.libfdxLoadingProbe) return;
        const p = globalThis.libfdxLoadingProbe = {active:true, frames:0, maxGapMs:0, gapsOver50:0, gl:{}};
        p.shaderWorkerJobs=0; p.shaderMain={calls:0,totalMs:0,maxMs:0};
        const compile=globalThis.libfdxShaderCompileBase64;
        if (compile) globalThis.libfdxShaderCompileBase64=function() {
            const start=performance.now();
            try { return compile.apply(this,arguments); }
            finally {
                if(p.active) {const ms=performance.now()-start; p.shaderMain.calls++;
                    p.shaderMain.totalMs+=ms; p.shaderMain.maxMs=Math.max(p.shaderMain.maxMs,ms);}
            }
        };
        const WorkerType=globalThis.Worker;
        if (WorkerType) {
            globalThis.Worker=function(url,options) {
                const w=new WorkerType(url,options);
                if(String(url).indexOf('#libfdx-shader')>=0) w.addEventListener('message',function(e) {
                    if(p.active && e.data.result) p.shaderWorkerJobs++;
                });
                return w;
            };
            globalThis.Worker.prototype=WorkerType.prototype;
        }
        let previous;
        function frame(now) {
            if (!p.active) return;
            if (previous !== undefined) {
                const gap = now - previous;
                p.maxGapMs = Math.max(p.maxGapMs, gap);
                if (gap > 50) p.gapsOver50++;
            }
            previous = now; p.frames++; requestAnimationFrame(frame);
        }
        requestAnimationFrame(frame);
        [globalThis.WebGLRenderingContext, globalThis.WebGL2RenderingContext].forEach(function(type) {
            if (!type) return;
            ['compileShader','linkProgram','getShaderParameter','getProgramParameter',
                    'bufferData','texImage2D','drawArrays','drawElements'].forEach(function(name) {
                const original = type.prototype[name];
                if (!original || original.libfdxProbe) return;
                function measured() {
                    const start = performance.now();
                    try { return original.apply(this,arguments); }
                    finally {
                        if (p.active) {
                            const ms = performance.now()-start;
                            const item = p.gl[name] || (p.gl[name] = {calls:0,totalMs:0,maxMs:0});
                            item.calls++; item.totalMs+=ms; item.maxMs=Math.max(item.maxMs,ms);
                        }
                    }
                }
                measured.libfdxProbe=true; type.prototype[name]=measured;
            });
        });
        """)
    static native void start();

    @JSBody(script = """
        const p=globalThis.libfdxLoadingProbe;
        if (p && p.active) {p.active=false;console.info('[info] Browser loading probe: '+JSON.stringify(p));}
        """)
    static native void finish();
}
