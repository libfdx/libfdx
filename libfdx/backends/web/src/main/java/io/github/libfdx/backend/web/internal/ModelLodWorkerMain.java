package io.github.libfdx.backend.web.internal;

import io.github.libfdx.graphics.meshoptimizer.ModelLodWorkerCodec;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

/** Headless Java optimizer entry point. Never creates an application or graphics context. */
public final class ModelLodWorkerMain {
    private ModelLodWorkerMain() { }
    public static void main(String[] args) { install(ModelLodWorkerMain::prepare); }
    private static void prepare(int id, Int8Array bytes) {
        try {
            byte[] output = ModelLodWorkerCodec.execute(bytes.copyToJavaArray());
            Int8Array result = Int8Array.create(output.length);
            result.set(output);
            complete(id, result);
        } catch(RuntimeException | Error failure) { fail(id, failure.toString()); }
    }
    @JSBody(params = "callback", script = """
        self.onmessage=function(e) { callback(e.data.id,new Int8Array(e.data.bytes)); };
        self.postMessage({ready:true});
        """)
    private static native void install(ModelLodWorkerCallback callback);
    @JSBody(params = {"id", "bytes"}, script = "self.postMessage({id:id,bytes:bytes.buffer},[bytes.buffer]);")
    private static native void complete(int id, Int8Array bytes);
    @JSBody(params = {"id", "error"}, script = "self.postMessage({id:id,error:error});")
    private static native void fail(int id, String error);
}
