package io.github.libfdx.backend.web.internal;

import org.teavm.jso.JSBody;

/** Worker-global bridge to the existing native shader compiler, with no graphics device. */
final class ShaderWorkerMain {
    private ShaderWorkerMain() { }
    @JSBody(script = """
        importScripts(new URL('fdx.js', self.libfdxRuntimeBaseUrl).href);
        Promise.resolve(FdxModule({locateFile:function(path) {
            return new URL(path,self.libfdxRuntimeBaseUrl).href;
        }})).then(function(module) {
            self.libfdxInstallShaderCompiler(module);
            if(!self.libfdxShaderCompileBase64)throw new Error('Shader compiler unavailable');
            self.onmessage=function(event) {
                var job=event.data;
                try {
                    var result=self.libfdxShaderCompileBase64(job.source,job.target,job.stage,
                            job.entry,job.glsl,job.es);
                    self.postMessage({id:job.id,result:result});
                } catch(error) {self.postMessage({id:job.id,error:String(error)});}
            };
            self.postMessage({ready:true});
        }).catch(function(error){self.postMessage({startupError:String(error)});});
        """)
    static native void start();
}
