package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.web.WebPbrSourcePreparation;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.g3d.StandardPbrSources;
import io.github.libfdx.graphics.shader.ShaderProfile;
import java.util.ArrayDeque;
import java.util.ArrayList;

/** Real worker protocol parity, caller isolation, fallback, failure and lifecycle checks. */
public final class WebPbrSourceTest extends ApplicationAdapter {
    private final boolean automatic;
    private final ArrayDeque<Runnable> fallbackWork = new ArrayDeque<>();
    private final ArrayDeque<Runnable> paused = new ArrayDeque<>();
    private final ArrayList<FdxFuture<StandardPbrSources>> outputs = new ArrayList<>();
    private final ArrayList<StandardPbrSources> reference = new ArrayList<>();
    private WebPbrSourcePreparation worker, crashing, malformed, reentrant;
    private FdxFuture<StandardPbrSources> fallback, abandoned, invalid, cancelledDuringReply;
    private long deadline;
    private Fdx fdx;
    private boolean passed;
    public WebPbrSourceTest(boolean automatic) { this.automatic = automatic; }

    @Override public void create(Fdx fdx) {
        this.fdx = fdx; deadline = System.currentTimeMillis() + 20000;
        worker = new WebPbrSourcePreparation();
        for (ShaderProfile profile : new ShaderProfile[]{ShaderProfile.PORTABLE_WEBGL2,ShaderProfile.PORTABLE_WEBGPU}) {
            reference.add(StandardPbrSources.compile(profile));
            outputs.add(worker.prepare(profile, ignored -> { throw new AssertionError("Worker waited on caller executor"); }));
        }
        crashing = new WebPbrSourcePreparation("data:text/javascript,postMessage({ready:true});onmessage=function(){throw new Error('injected PBR crash');}");
        abandoned = crashing.prepare(ShaderProfile.PORTABLE_WEBGL2, paused::add);
        fallback = crashing.prepare(ShaderProfile.PORTABLE_WEBGL2, fallbackWork::add);
        malformed = new WebPbrSourcePreparation("data:text/javascript,postMessage({ready:true});onmessage=function(e){postMessage({id:e.data.id,version:999});}");
        invalid = malformed.prepare(ShaderProfile.PORTABLE_WEBGL2, fallbackWork::add);
        reentrant = new WebPbrSourcePreparation();
        reentrant.prepare(ShaderProfile.PORTABLE_WEBGL2,paused::add).onSuccess(ignored -> reentrant.dispose());
        cancelledDuringReply = reentrant.prepare(ShaderProfile.PORTABLE_WEBGL2,paused::add);

        WebPbrSourcePreparation cancelled = new WebPbrSourcePreparation();
        ArrayList<FdxFuture<StandardPbrSources>> accepted = new ArrayList<>();
        for (int i = 0; i < 32; i++) accepted.add(cancelled.prepare(ShaderProfile.PORTABLE_WEBGL2, paused::add));
        check(cancelled.prepare(ShaderProfile.PORTABLE_WEBGPU, paused::add).isFailed(), "Saturation did not reject");
        cancelled.dispose(); cancelled.dispose();
        for (var future : accepted) check(future.isFailed(), "Shutdown left a pending caller");
        check(cancelled.prepare(ShaderProfile.PORTABLE_WEBGL2,paused::add).isFailed(), "Disposed worker accepted work");
    }
    @Override public void render() {
        fdx.graphics().main().clear(.02f,.04f,.07f,1);
        if (passed) return;
        check(System.currentTimeMillis() < deadline, "PBR source worker timed out");
        if (!fallbackWork.isEmpty()) fallbackWork.remove().run();
        if (!fallback.isDone() || !invalid.isDone() || !cancelledDuringReply.isDone()
                || outputs.stream().anyMatch(future -> !future.isDone())) return;
        for (int i = 0; i < outputs.size(); i++) {
            StandardPbrSources result = outputs.get(i).get();
            compare(reference.get(i),result);
            check(worker.prepare(result.profile(),paused::add).get() == result, "Successful recipe was not reused");
        }
        compare(reference.get(0),fallback.get());
        check(!abandoned.isDone(), "Paused fallback ran without its executor");
        check(invalid.isFailed() && malformed.fallbackJobs()==0, "Malformed result silently fell back");
        check(cancelledDuringReply.isFailed(), "A listener disposed the worker but another caller received success");
        check(worker.completedWorkerJobs()==2 && worker.fallbackJobs()==0, "Expected two actual PBR worker jobs");
        check(crashing.fallbackJobs()==1, "Crash did not use loading fallback");
        crashing.dispose();
        check(abandoned.isFailed(), "Shutdown did not cancel paused fallback");
        while (!paused.isEmpty()) paused.remove().run(); // Late scheduled work cannot publish after disposal.
        passed=true;
        System.out.println("WEB_PBR_SOURCE_PASS worker=2 variants=16 source=exact surface=exact cache=1 isolation=1 fallback=1 malformed=1 disposal=1");
        if (!automatic) fdx.app().requestExit();
    }
    private static void compare(StandardPbrSources expected, StandardPbrSources actual) {
        check(expected.profile()==actual.profile(),"Profile changed");
        check(expected.surface().equals(actual.surface()) && expected.library().equals(actual.library()),"Surface compilation differs");
        for (int i=0;i<8;i++) check(expected.variant(i).equals(actual.variant(i)),"PBR variant source differs at "+i);
    }
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    @Override public void dispose() {
        if(worker!=null) worker.dispose(); if(crashing!=null) crashing.dispose(); if(malformed!=null) malformed.dispose();
        if(reentrant!=null) reentrant.dispose();
        check(passed,"PBR source test ended before completion");
    }
}
