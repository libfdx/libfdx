package io.github.libfdx.graphics.g3d;

import io.github.libfdx.core.FdxException;

/** Application-owned, single-thread playback and TRS crossfades. Clips and instance are borrowed.
 * Controls every node targeted by clips played here; nodes absent from a later clip return to their
 * first bound authored defaults. Apply before drawing, with one controller owning each animated node.
 * Pose sampling reuses storage and updates the instance hierarchy once per operation. */
public final class AnimationController {
    /** Callbacks run synchronously after the pose/time commit. Mutating playback or replacing the
     * listener cancels remaining callbacks for the old update. Recursive update is forbidden.
     * A throwing callback leaves the committed pose/time intact and aborts remaining delivery. */
    public interface Listener {
        void onEvent(AnimationController controller, AnimationClip clip, AnimationClip.Event event);
        default void onLoop(AnimationController controller, AnimationClip clip, long loops) { }
        default void onComplete(AnimationController controller, AnimationClip clip) { }
    }

    private final ModelInstance instance;
    private AnimationPose pose;
    private AnimationClip clip, outgoingClip;
    private double timeSeconds, outgoingTime, fadeDuration, fadeElapsed;
    private boolean looping, outgoingLooping, complete, updating;
    private Listener listener;
    private int maxEventsPerUpdate=256;
    private long revision;

    public AnimationController(ModelInstance instance) {
        if (instance == null) throw new FdxException("AnimationController instance cannot be null");
        this.instance=instance;
    }

    /** Starts at zero without firing events. Null stops and preserves the current pose. */
    public AnimationController play(AnimationClip clip, boolean looping) {
        if (clip == null) return stop();
        bind(clip,false,false);
        if (pose != null) pose.apply(0,0,1);
        this.clip=clip; this.looping=looping; timeSeconds=0; complete=false;
        clearFade(); revision++; return this;
    }

    /** Fades to a clip starting at zero. Both clips advance during an ordinary transition.
     * Interrupting a transition fades from its current mixed pose, avoiding a pose discontinuity.
     * Missing target channels blend toward authored defaults; zero duration is play(). */
    public AnimationController crossFade(AnimationClip target, boolean looping, float durationSeconds) {
        if (target == null || !Float.isFinite(durationSeconds) || durationSeconds < 0)
            throw new FdxException("Crossfade needs a clip and finite nonnegative duration");
        if (durationSeconds == 0) return play(target,looping);
        boolean interrupted=isBlending() || clip == null;
        bind(target,true,interrupted);
        if (pose != null) pose.apply(0,timeSeconds,0);
        outgoingClip=interrupted ? null : clip; outgoingTime=timeSeconds; outgoingLooping=this.looping;
        clip=target; this.looping=looping; timeSeconds=0; complete=false;
        fadeDuration=durationSeconds; fadeElapsed=0; revision++; return this;
    }

    /** Seeks without events and cancels any transition. A nonlooping seek to the end is complete. */
    public AnimationController time(float seconds) {
        if (!Float.isFinite(seconds)) throw new FdxException("Animation time must be finite");
        double next=normalize(seconds,clip,looping);
        if (clip != null && pose != null) pose.apply(next,0,1);
        timeSeconds=next; complete=clip != null && !looping && next >= clip.durationSeconds();
        clearFade(); revision++; return this;
    }

    /** Advances playback. Positive updates deliver markers in (previous,current], across every loop.
     * End markers precede zero markers at a loop boundary. Only the incoming clip emits events during
     * a fade. Negative updates seek backward without events and cancel transitions. Zero does nothing.
     * If a marker budget would be exceeded, throws before changing the pose/time; subdivide the update
     * or explicitly raise the budget. Loop notifications aggregate all crossings in one callback. */
    public AnimationController update(float deltaSeconds) {
        if (updating) throw new FdxException("AnimationController.update cannot be recursive");
        if (!Float.isFinite(deltaSeconds)) throw new FdxException("Animation delta must be finite");
        if (clip == null || deltaSeconds == 0) return this;
        if (deltaSeconds < 0) {
            double next=normalize(timeSeconds+deltaSeconds,clip,looping);
            if (pose != null) pose.apply(next,0,1);
            timeSeconds=next; complete=false; clearFade(); revision++; return this;
        }
        AnimationClip playing=clip;
        AnimationClip.Event[] events=playing.eventsUnsafe();
        double old=timeSeconds,duration=playing.durationSeconds(),total=old+deltaSeconds;
        double next=normalize(total,playing,looping);
        double cycles=looping && duration > 0 ? Math.floor(total/duration) : 0;
        int start=upper(events,old),end=upper(events,next);
        Listener callback=listener;
        if (callback != null) {
            double count=cycles == 0 ? end-start : events.length-start+(cycles-1)*events.length+end;
            if (cycles >= 0x1p63 || count > maxEventsPerUpdate)
                throw new FdxException("Animation event budget exceeded; subdivide this update or raise maxEventsPerUpdate");
        }
        double nextOutgoing=normalize(outgoingTime+deltaSeconds,outgoingClip,outgoingLooping);
        double elapsed=Math.min(fadeDuration,fadeElapsed+deltaSeconds);
        double alpha=fadeDuration > 0 ? elapsed/fadeDuration : 1;
        boolean completed=!looping && !complete && next >= duration;
        updating=true;
        try {
            if (pose != null) pose.apply(next,nextOutgoing,alpha);
            timeSeconds=next; outgoingTime=nextOutgoing; fadeElapsed=elapsed;
            if (fadeDuration > 0 && alpha >= 1) clearFade();
            if (completed) complete=true;
            long version=revision;
            if (callback != null) {
                if (cycles == 0) {
                    if (!deliver(callback,playing,events,start,end,version)) return this;
                } else {
                    if (!deliver(callback,playing,events,start,events.length,version)) return this;
                    if (events.length != 0) for (long i=1;i<(long)cycles;i++)
                        if (!deliver(callback,playing,events,0,events.length,version)) return this;
                    if (!deliver(callback,playing,events,0,end,version)) return this;
                    callback.onLoop(this,playing,(long)cycles);
                    if (revision != version) return this;
                }
                if (completed) callback.onComplete(this,playing);
            }
        } finally { updating=false; }
        return this;
    }

    /** Stops playback without resetting the last visible pose. */
    public AnimationController stop() {
        clip=null; timeSeconds=0; complete=false; clearFade(); revision++; return this;
    }
    public AnimationController listener(Listener listener) { this.listener=listener; revision++; return this; }
    /** Maximum marker callbacks per update, 1..65536; default 256. No events are silently dropped. */
    public AnimationController maxEventsPerUpdate(int maximum) {
        if (maximum < 1 || maximum > 65_536) throw new FdxException("Animation event budget must be 1..65536");
        maxEventsPerUpdate=maximum; return this;
    }
    public ModelInstance instance() { return instance; }
    public AnimationClip clip() { return clip; }
    public float timeSeconds() { return (float)timeSeconds; }
    public boolean isBlending() { return fadeDuration > 0; }
    public float blendProgress() { return fadeDuration > 0 ? (float)(fadeElapsed/fadeDuration) : 1; }
    public boolean isComplete() { return complete; }

    private void bind(AnimationClip target,boolean fade,boolean interrupted) {
        if (instance instanceof DefaultModelInstance model) {
            if (pose == null) pose=new AnimationPose(model);
            pose.bind(target,fade,interrupted);
        } else if (target.nodeTransformChannelsUnsafe().length != 0)
            throw new FdxException("Node transform animation requires DefaultModelInstance");
    }
    private void clearFade() {
        fadeDuration=fadeElapsed=0; outgoingClip=null; outgoingTime=0;
        if (pose != null) pose.clearOutgoing();
    }
    private boolean deliver(Listener callback,AnimationClip playing,AnimationClip.Event[] events,int start,int end,long version) {
        for (int i=start;i<end;i++) {
            callback.onEvent(this,playing,events[i]);
            if (revision != version) return false;
        }
        return true;
    }
    private static int upper(AnimationClip.Event[] events,double time) {
        int low=0,high=events.length;
        while (low < high) { int middle=(low+high)>>>1; if (events[middle].timeSeconds() <= time) low=middle+1; else high=middle; }
        return low;
    }
    private static double normalize(double time,AnimationClip clip,boolean looping) {
        if (clip == null) return time;
        double duration=clip.durationSeconds();
        if (duration <= 0) return 0;
        if (!looping) return Math.max(0,Math.min(duration,time));
        double wrapped=time%duration;
        return wrapped < 0 ? wrapped+duration : wrapped;
    }
}
