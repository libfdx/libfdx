package io.github.libfdx.backend.web.internal;

import io.github.libfdx.graphics.g3d.StandardPbrSources;
import io.github.libfdx.graphics.shader.ShaderProfile;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.core.JSString;

/** Headless CPU entry point: only the standard PBR graph recipe, no backend/device initialization. */
public final class PbrSourceWorkerMain {
    private PbrSourceWorkerMain() { }
    public static void main(String[] args) { install(PbrSourceWorkerMain::prepare); }
    private static void prepare(int id, int version, String profile) {
        try {
            if (version != StandardPbrSources.VERSION) throw new IllegalArgumentException("PBR recipe version mismatch");
            StandardPbrSources result = StandardPbrSources.compile(ShaderProfile.valueOf(profile));
            JSArray<JSString> variants = JSArray.create();
            for (int i = 0; i < 8; i++) variants.push(JSString.valueOf(result.variant(i)));
            complete(id, version, profile, result.surface(), result.library(), variants);
        } catch (RuntimeException | Error failure) { fail(id, failure.toString()); }
    }
    @JSFunctor private interface Prepare extends JSObject { void run(int id, int version, String profile); }
    @JSBody(params="prepare", script="""
            self.onmessage=function(e) { var m=e.data; prepare(m.id,m.version,m.profile); };
            self.postMessage({ready:true});
            """) private static native void install(Prepare prepare);
    @JSBody(params={"id","version","profile","surface","library","variants"}, script="""
            self.postMessage({id:id,version:version,profile:profile,surface:surface,library:library,variants:variants});
            """) private static native void complete(int id, int version, String profile, String surface,
                    String library, JSArray<JSString> variants);
    @JSBody(params={"id","error"}, script="self.postMessage({id:id,error:error});")
    private static native void fail(int id, String error);
}
