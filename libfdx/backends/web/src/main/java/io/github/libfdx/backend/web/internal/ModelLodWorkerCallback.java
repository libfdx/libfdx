package io.github.libfdx.backend.web.internal;

import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;

@JSFunctor
interface ModelLodWorkerCallback extends JSObject {
    void run(int id, Int8Array bytes);
}
