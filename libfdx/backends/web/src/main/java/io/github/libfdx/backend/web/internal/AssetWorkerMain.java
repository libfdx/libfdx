package io.github.libfdx.backend.web.internal;

import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.graphics.TextureMipmaps;
import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.core.JSArray;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;

/** Separate TeaVM entry point. Never initializes the application backend or graphics provider. */
public final class AssetWorkerMain {
    private AssetWorkerMain() { }
    public static void main(String[] args) { install(AssetWorkerMain::prepare); }

    private static void prepare(int id, boolean decode, Int8Array bytes, int width, int height,
            boolean srgb, boolean alphaWeighted) {
        try {
            ByteBuffer[] levels;
            if (decode) {
                ImageData image = ImageAssetLoader.decodeRawPng(bytes.copyToJavaArray());
                if (image == null) { decodeNative(id, bytes); return; }
                width = image.width(); height = image.height(); levels = new ByteBuffer[] { image.rgba() };
            } else {
                ByteBuffer source = ByteBuffer.wrap(bytes.copyToJavaArray());
                levels = TextureMipmaps.rgba8(source, width, height, srgb, alphaWeighted);
            }
            JSArray<Uint8Array> buffers = JSArray.create();
            for (ByteBuffer level : levels) buffers.push(Uint8Array.fromJavaBuffer(level));
            complete(id, width, height, buffers);
        } catch (RuntimeException | Error failure) { fail(id, failure.toString()); }
    }

    @JSFunctor
    private interface Prepare extends JSObject {
        void run(int id, boolean decode, Int8Array bytes, int width, int height, boolean srgb, boolean alphaWeighted);
    }

    @JSBody(params="prepare", script="""
        self.onmessage=function(event) {
            var m=event.data;
            prepare(m.id,m.decode,new Int8Array(m.bytes),m.width,m.height,m.srgb,m.alphaWeighted);
        };
        self.postMessage({ready:true});
        """)
    private static native void install(Prepare prepare);

    @JSBody(params={"id","width","height","levels"}, script="""
        var copies=levels.map(function(level){return level.slice().buffer;});
        self.postMessage({id:id,width:width,height:height,levels:copies},copies);
        """)
    private static native void complete(int id, int width, int height, JSArray<Uint8Array> levels);

    @JSBody(params={"id","message"},script="self.postMessage({id:id,error:message});")
    private static native void fail(int id, String message);

    @JSBody(params={"id","bytes"},script="""
        createImageBitmap(new Blob([bytes])).then(function(image) {
            try {
                var width=image.width,height=image.height;
                if(width<1||height<1||width*height>16777216) throw new Error('Image exceeds 16M pixels');
                var canvas=new OffscreenCanvas(width,height),context=canvas.getContext('2d');
                if(!context) throw new Error('Worker canvas decode unavailable');
                context.drawImage(image,0,0);
                var rgba=context.getImageData(0,0,width,height).data.buffer;
                self.postMessage({id:id,width:width,height:height,levels:[rgba]},[rgba]);
            } catch(error) {self.postMessage({id:id,error:String(error)});}
            finally {image.close();}
        },function(error){self.postMessage({id:id,error:String(error)});});
        """)
    private static native void decodeNative(int id, Int8Array bytes);
}
