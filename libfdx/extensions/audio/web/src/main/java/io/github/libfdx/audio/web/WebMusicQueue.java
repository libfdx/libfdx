package io.github.libfdx.audio.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;

/** Fixed queue membership; browser buffers/source nodes allocate at block and resume boundaries. */
final class WebMusicQueue {
    private WebMusicQueue() { }
    @JSBody(params = {"engine","capacity","channels","rate"}, script = """
        var ctx=engine.ctx, split=ctx.createChannelSplitter(2), merge=ctx.createChannelMerger(2);
        var left=ctx.createGain(), right=ctx.createGain();
        try {
            split.connect(left,0); split.connect(right,1);
            left.connect(merge,0,0); right.connect(merge,0,1); merge.connect(ctx.destination);
            left.gain.value=right.gain.value=channels===1 ? Math.SQRT1_2 : 1;
            return {ctx:ctx,split:split,merge:merge,left:left,right:right,channels:channels,rate:rate,
                    queue:new Array(capacity),head:0,count:0,offset:0,playing:false};
        } catch(error) { split.disconnect(); merge.disconnect(); left.disconnect(); right.disconnect(); throw error; }
        """)
    static native JSObject create(JSObject engine,int capacity,int channels,int rate);
    @JSBody(params = {"m","buffer"}, script = """
        if(m.count===m.queue.length) throw new Error('Music queue capacity exceeded');
        var index=(m.head+m.count)%m.queue.length;
        m.queue[index]={buffer:buffer,node:null,start:0,end:0}; m.count++;
        """)
    static native void enqueue(JSObject m,JSObject buffer);
    @JSBody(params = "m", script = """
        var finished=0,now=m.ctx.currentTime;
        while(m.count>0 && m.playing) {
            var entry=m.queue[m.head];
            if(!entry.node || now<entry.end) break;
            entry.node.disconnect(); entry.node=null; m.queue[m.head]=null;
            m.head=(m.head+1)%m.queue.length; m.count--; m.offset=0; finished++;
        }
        if(m.count===0) m.playing=false;
        return finished;
        """)
    static native int processed(JSObject m);
    @JSBody(params = "m", script = """
        if(m.count===0) return 0;
        var entry=m.queue[m.head];
        var frame=m.playing && entry.node ? (m.ctx.currentTime-entry.start)*m.rate : m.offset;
        return Math.floor(Math.max(0,Math.min(entry.buffer.length,frame)));
        """)
    static native int offset(JSObject m);
    @JSBody(params = "m", script = """
        if(m.count===0) return;
        var time=m.ctx.currentTime+0.005;
        for(var i=0;i<m.count;i++) {
            var entry=m.queue[(m.head+i)%m.queue.length];
            if(entry.node) { time=entry.end; continue; }
            if(time<m.ctx.currentTime) time=m.ctx.currentTime+0.005;
            var offset=i===0 ? m.offset/m.rate : 0;
            var source=m.ctx.createBufferSource(); source.buffer=entry.buffer;
            try {
                if(m.channels===1) { source.connect(m.left); source.connect(m.right); }
                else source.connect(m.split);
                entry.start=time-offset; entry.end=entry.start+entry.buffer.duration;
                source.start(time,offset); entry.node=source; time=entry.end;
            } catch(error) { try { source.stop(); } catch(ignore) {} source.disconnect(); throw error; }
        }
        m.playing=true;
        """)
    static native void play(JSObject m);
    static void pause(JSObject m) { pause(m,offset(m)); }
    @JSBody(params = {"m","offset"}, script = """
        if(!m.playing) return;
        m.offset=offset; m.playing=false;
        for(var i=0;i<m.count;i++) {
            var entry=m.queue[(m.head+i)%m.queue.length];
            if(entry.node) { entry.node.stop(); entry.node.disconnect(); entry.node=null; }
        }
        """)
    private static native void pause(JSObject m,int offset);
    @JSBody(params = "m", script = """
        for(var i=0;i<m.count;i++) {
            var index=(m.head+i)%m.queue.length, entry=m.queue[index];
            if(entry.node) { entry.node.stop(); entry.node.disconnect(); }
            m.queue[index]=null;
        }
        m.head=m.count=m.offset=0; m.playing=false;
        """)
    static native void clear(JSObject m);
    @JSBody(params = {"m","gain","pan"}, script = """
        m.left.gain.value=gain*(m.channels===2 ? Math.min(1,1-pan) : Math.cos((pan+1)*Math.PI/4));
        m.right.gain.value=gain*(m.channels===2 ? Math.min(1,1+pan) : Math.sin((pan+1)*Math.PI/4));
        """)
    static native void parameters(JSObject m,float gain,float pan);
    static void close(JSObject m) { clear(m); disconnect(m); }
    @JSBody(params = "m", script = "m.split.disconnect(); m.left.disconnect(); m.right.disconnect(); m.merge.disconnect();")
    private static native void disconnect(JSObject m);
}
