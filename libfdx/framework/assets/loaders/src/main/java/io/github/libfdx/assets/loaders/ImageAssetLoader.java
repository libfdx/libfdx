package io.github.libfdx.assets.loaders;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import org.teavm.classlib.PlatformDetector;
import org.teavm.interop.Address;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.typedarrays.Uint8Array;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Loads image asset data.
 *
 * @author xpenatan
 */
@Include("libfdx_native_image.h")
public final class ImageAssetLoader implements AssetLoader<ImageData> {
    private final ImageDecoder decoder;

    /** Uses the manager-owned platform decoder when available (a shared worker on web). */
    public ImageAssetLoader() { decoder = null; }

    /** Borrows a CPU decoder for all managed requests, including deferred files. */
    public ImageAssetLoader(ImageDecoder decoder) { this.decoder = java.util.Objects.requireNonNull(decoder); }
    /**
     * Runs the register step.
     *
     * @param assets the assets
     */
    public static void register(AssetManager assets) {
        assets.registerLoader(ImageData.class, new ImageAssetLoader());
    }

    /**
     * Returns the type.
     *
     * @return the type
     */
    @Override
    public Class<ImageData> type() {
        return ImageData.class;
    }

    /**
     * Loads the requested resource.
     *
     * @param context the context
     * @param descriptor the descriptor
     * @return the created value
     */
    @Override
    public FdxFuture<ImageData> load(final AssetLoadContext context, final AssetDescriptor<ImageData> descriptor) {
        ImageDecoder selected = decoder != null ? decoder : ImageDecoder.platformDefault(context);
        FdxFuture<ImageData> result = FdxFuture.pending();
        context.readBytes(context.files().internal(descriptor.path()))
                .onSuccess(bytes -> context.asyncFuture(() -> selected.decodeAsync(descriptor.path(), bytes))
                        .onSuccess(result::complete).onFailure(result::completeExceptionally))
                .onFailure(result::completeExceptionally);
        return result;
    }

    /**
     * Runs the decode step.
     *
     * @param bytes the bytes
     * @return the decode
     */
    public static ImageData decode(byte[] bytes) {
        return decode(null, bytes);
    }

    /** Decodes supported raw RGB/RGBA8 PNG layouts without platform services, preserving hidden
     * RGB. Returns null for other formats/layouts; malformed supported PNGs fail explicitly.
     * CPU-only and synchronous: worker entry points may use this without a browser document. */
    public static ImageData decodeRawPng(byte[] bytes) { return PngRgbaDecoder.decode(bytes); }

    /**
     * Runs the decode step.
     *
     * @param path the asset or file path
     * @param bytes the bytes
     * @return the decode
     */
    public static ImageData decode(String path, byte[] bytes) {
        ImageData png = PngRgbaDecoder.decode(bytes);
        if (png != null) return png;
        ImageData browserImage = decodeWithBrowser(path);
        if (browserImage != null) {
            return browserImage;
        }
        ImageData nativeImage = decodeWithNative(bytes);
        if (nativeImage != null) {
            return nativeImage;
        }
        ImageData android = decodeWithAndroid(bytes);
        if (android != null) {
            return android;
        }
        ImageData imageIo = decodeWithImageIo(bytes);
        if (imageIo != null) {
            return imageIo;
        }
        throw new FdxException("Could not decode image as PNG or JPG");
    }

    /**
     * Prepares image pixels, including deferred browser images. May complete inline
     * on the caller (CPU decoding) or later on the browser event loop. Managed loads
     * dispatch this through the preparation executor and deliver through update.
     * On browsers, raw PNG work is sliced between animation frames; other formats
     * use browser decoding and bounded pixel transfers between frames.
     * Cancellation of a managed request does not interrupt
     * an already running decode; it completes and releases its staging state.
     * Raw RGB/RGBA8 non-interlaced PNG preserves RGB at alpha zero; other browser
     * layouts use canvas conversion, which can lose that hidden RGB and precision.
     * The input bytes must remain unchanged until completion. No global cache is added.
     */
    public static FdxFuture<ImageData> decodeAsync(String path,byte[] bytes) {
        try {
            if(!isBrowserRuntime()) return FdxFuture.completed(decode(path,bytes));
            // TeaVM C needs a direct target guard to prune browser interop during dependency analysis.
            if(PlatformDetector.isLowLevel()) return FdxFuture.completed(decode(path,bytes));
            PngRgbaDecoder.Decoder png = PngRgbaDecoder.begin(bytes);
            if (png != null) {
                FdxFuture<ImageData> result = FdxFuture.pending();
                new BrowserPngDecode(png, path, bytes, result).schedule();
                return result;
            }
            return decodeBrowserAsync(path, bytes);
        } catch(RuntimeException | Error error) { return FdxFuture.failed(error); }
    }

    private static FdxFuture<ImageData> decodeBrowserAsync(String path, byte[] bytes) {
        try {
            if (path != null && !path.isEmpty()) {
                String normalized = normalizePath(path);
                int width = browserImageWidth(normalized), height = browserImageHeight(normalized);
                Int8Array pixels = browserImageRgba(normalized);
                if (width > 0 && height > 0 && pixels != null) {
                    FdxFuture<ImageData> cached = FdxFuture.pending();
                    copyBrowserPixels(width, height, pixels, cached);
                    return cached;
                }
            }
            if(bytes==null || bytes.length==0 || bytes.length>64*1024*1024) throw new FdxException("Encoded image size is invalid");
            FdxFuture<ImageData> result=FdxFuture.pending();
            Int8Array encoded=Int8Array.create(bytes.length);
            encoded.set(bytes);
            decodeBrowserBytes(encoded,(width,height,pixels) -> {
                copyBrowserPixels(width, height, pixels, result);
            },error -> result.completeExceptionally(new FdxException("Browser image decode failed: "+error)));
            return result;
        } catch(RuntimeException | Error error) { return FdxFuture.failed(error); }
    }

    private static void copyBrowserPixels(int width, int height, Int8Array pixels, FdxFuture<ImageData> result) {
        try {
            new BrowserPixelCopy(width, height, pixels, result).schedule();
        } catch (RuntimeException | Error error) { result.completeExceptionally(error); }
    }

    /** Copies directly into Java buffer storage, avoiding a large Wasm GC array round trip. */
    private static final class BrowserPixelCopy {
        final int width, height;
        final Int8Array pixels;
        final ByteBuffer buffer;
        final FdxFuture<ImageData> result;
        int offset;

        BrowserPixelCopy(int width, int height, Int8Array pixels, FdxFuture<ImageData> result) {
            if (width < 1 || height < 1 || (long) width * height > 16777216L
                    || pixels.getLength() != width * height * 4) throw new FdxException("Invalid browser image pixels");
            this.width = width; this.height = height; this.pixels = pixels; this.result = result;
            buffer = ByteBuffer.allocateDirect(width * height * 4);
        }

        void schedule() { nextDecodeFrame(this::run); }

        void run() {
            try {
                // Recreate the view after yielding: Wasm memory may have grown between frames.
                Uint8Array target = Uint8Array.fromJavaBuffer(buffer);
                long deadline = System.nanoTime() + 1_000_000L;
                for (int steps = 0; steps < 64; steps++) {
                    int end = Math.min(offset + 262144, buffer.capacity());
                    copyPixelRange(pixels, target, offset, end);
                    offset = end;
                    if (offset == buffer.capacity()) {
                        result.complete(new ImageData(width, height, buffer));
                        return;
                    }
                    if (System.nanoTime() >= deadline) break;
                }
                schedule();
            } catch (RuntimeException | Error error) { result.completeExceptionally(error); }
        }
    }

    @JSBody(params={"source", "target", "start", "end"},
            script="target.set(source.subarray(start,end),start);")
    private static native void copyPixelRange(Int8Array source, Uint8Array target, int start, int end);

    /** Browser decode slices yield to presentation/input while retaining exact PNG channels. */
    private static final class BrowserPngDecode {
        final PngRgbaDecoder.Decoder decoder;
        final String path;
        final byte[] bytes;
        final FdxFuture<ImageData> result;
        BrowserPngDecode(PngRgbaDecoder.Decoder decoder, String path, byte[] bytes, FdxFuture<ImageData> result) {
            this.decoder=decoder;this.path=path;this.bytes=bytes;this.result=result;
        }
        void schedule() { nextDecodeFrame(this::run); }
        void run() {
            try {
                long deadline=System.nanoTime()+1_000_000L;
                for(int steps=0;steps<64;steps++) {
                    if(decoder.step(8192)) {
                        ImageData image=decoder.result();
                        if(image!=null) result.complete(image);
                        else decodeBrowserAsync(path,bytes).onSuccess(result::complete).onFailure(result::completeExceptionally);
                        return;
                    }
                    if(System.nanoTime()>=deadline)break;
                }
                schedule();
            } catch(RuntimeException | Error error) {decoder.close();result.completeExceptionally(error);}
        }
    }

    @JSFunctor private interface DecodeStep extends JSObject { void run(); }
    @JSBody(params="step", script="requestAnimationFrame(function(){step();});")
    private static native void nextDecodeFrame(DecodeStep step);

    private static boolean isBrowserRuntime() {
        try { return PlatformDetector.isJavaScript() || PlatformDetector.isWebAssemblyGC(); }
        catch(LinkageError ignored) { return false; }
    }

    @JSFunctor private interface Decoded extends JSObject { void accept(int width,int height,Int8Array pixels); }
    @JSFunctor private interface DecodeFailure extends JSObject { void accept(String message); }
    @JSBody(params={"bytes","success","failure"},script=
            "var blob=new Blob([bytes]);\n"+
            "var promise;\n"+
            "if(typeof createImageBitmap==='function') promise=createImageBitmap(blob);\n"+
            "else promise=new Promise(function(resolve,reject) {\n"+
            "  var image=new Image(),url=URL.createObjectURL(blob);\n"+
            "  image.onload=function(){URL.revokeObjectURL(url);resolve(image);};\n"+
            "  image.onerror=function(){URL.revokeObjectURL(url);reject(new Error('Invalid image'));};image.src=url;\n"+
            "});\n"+
            "promise.then(function(image) {\n"+
            "  var width,height,rgba;\n"+
            "  try {\n"+
            "    width=image.width||image.naturalWidth;height=image.height||image.naturalHeight;\n"+
            "    if(width<1||height<1||width*height>16777216) throw new Error('Image exceeds 16M pixels');\n"+
            "    var canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;\n"+
            "    var context=canvas.getContext('2d');if(!context) throw new Error('Canvas decode unavailable');\n"+
            "    context.drawImage(image,0,0);rgba=context.getImageData(0,0,width,height).data;\n"+
            "  } catch(error) { failure(String(error)); return;\n"+
            "  } finally { if(typeof image.close==='function') image.close(); }\n"+
            "  success(width,height,new Int8Array(rgba.buffer,rgba.byteOffset,rgba.byteLength));\n"+
            "},function(error){failure(String(error));});")
    private static native void decodeBrowserBytes(Int8Array bytes,Decoded success,DecodeFailure failure);

    private static ImageData decodeWithBrowser(String path) {
        try {
            if ((!PlatformDetector.isJavaScript() && !PlatformDetector.isWebAssemblyGC())
                    || path == null || path.length() == 0) {
                return null;
            }
            String normalized = normalizePath(path);
            int width = browserImageWidth(normalized);
            int height = browserImageHeight(normalized);
            Int8Array rgbaArray = browserImageRgba(normalized);
            if (width <= 0 || height <= 0 || rgbaArray == null) {
                return null;
            }
            byte[] bytes = rgbaArray.copyToJavaArray();
            ByteBuffer rgba = ByteBuffer.allocateDirect(bytes.length);
            rgba.put(bytes);
            rgba.flip();
            return new ImageData(width, height, rgba);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ImageData decodeWithNative(byte[] bytes) {
        try {
            if (PlatformDetector.isJavaScript() || PlatformDetector.isWebAssemblyGC()) {
                return null;
            }
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            int[] dimensions = new int[2];
            if (fdxNativeImageDimensions(bytes, bytes.length, Address.ofData(dimensions)) == 0) {
                return null;
            }
            int width = dimensions[0];
            int height = dimensions[1];
            if (width <= 0 || height <= 0) {
                return null;
            }
            int byteCount = width * height * 4;
            ByteBuffer rgba = ByteBuffer.allocateDirect(byteCount);
            if (fdxNativeImageDecodeRgba8(bytes, bytes.length, rgba, byteCount) == 0) {
                return null;
            }
            rgba.position(0);
            rgba.limit(byteCount);
            return new ImageData(width, height, rgba);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static ImageData decodeWithAndroid(byte[] bytes) {
        try {
            Class<?> bitmapFactoryClass = Class.forName("android.graphics.BitmapFactory");
            Method decode = bitmapFactoryClass.getMethod("decodeByteArray", byte[].class, int.class, int.class);
            Object bitmap = decode.invoke(null, bytes, 0, bytes.length);
            if (bitmap == null) {
                return null;
            }
            Class<?> bitmapClass = bitmap.getClass();
            int width = ((Integer) bitmapClass.getMethod("getWidth").invoke(bitmap)).intValue();
            int height = ((Integer) bitmapClass.getMethod("getHeight").invoke(bitmap)).intValue();
            int[] pixels = new int[width * height];
            bitmapClass.getMethod("getPixels", int[].class, int.class, int.class, int.class, int.class, int.class, int.class)
                    .invoke(bitmap, pixels, 0, width, 0, 0, width, height);
            recycle(bitmapClass, bitmap);
            return rgba(width, height, pixels);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable error) {
            throw new FdxException("Android image decode failed", error);
        }
    }

    private static ImageData decodeWithImageIo(byte[] bytes) {
        try {
            Class<?> imageIoClass = Class.forName("javax.imageio.ImageIO");
            Method read = imageIoClass.getMethod("read", InputStream.class);
            Object image = read.invoke(null, new ByteArrayInputStream(bytes));
            if (image == null) {
                return null;
            }
            Class<?> imageClass = image.getClass();
            int width = ((Integer) imageClass.getMethod("getWidth").invoke(image)).intValue();
            int height = ((Integer) imageClass.getMethod("getHeight").invoke(image)).intValue();
            int[] pixels = new int[width * height];
            Method getRgb = imageClass.getMethod("getRGB", int.class, int.class, int.class, int.class,
                    int[].class, int.class, int.class);
            Object result = getRgb.invoke(image, 0, 0, width, height, pixels, 0, width);
            if (result != null && result.getClass().isArray()) {
                for (int i = 0; i < pixels.length; i++) {
                    pixels[i] = ((Integer) Array.get(result, i)).intValue();
                }
            }
            return rgba(width, height, pixels);
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (Throwable error) {
            throw new FdxException("ImageIO decode failed", error);
        }
    }

    private static void recycle(Class<?> bitmapClass, Object bitmap) {
        try {
            bitmapClass.getMethod("recycle").invoke(bitmap);
        } catch (Throwable ignored) {
        }
    }

    private static String normalizePath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("assets/")) {
            normalized = normalized.substring("assets/".length());
        }
        return normalized;
    }

    private static ImageData rgba(int width, int height, int[] argbPixels) {
        ByteBuffer rgba = ByteBuffer.allocateDirect(width * height * 4);
        for (int i = 0; i < argbPixels.length; i++) {
            int argb = argbPixels[i];
            rgba.put((byte) ((argb >> 16) & 0xff));
            rgba.put((byte) ((argb >> 8) & 0xff));
            rgba.put((byte) (argb & 0xff));
            rgba.put((byte) ((argb >> 24) & 0xff));
        }
        rgba.flip();
        return new ImageData(width, height, rgba);
    }

    @Import(name = "fdx_native_image_dimensions")
    private static native int fdxNativeImageDimensions(byte[] data, int size, Address dimensions);

    @Import(name = "fdx_native_image_decode_rgba8")
    private static native int fdxNativeImageDecodeRgba8(byte[] data, int size, ByteBuffer target, int targetSize);

    @JSBody(params = { "path" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var images = root.libfdxImageData || {};\n" +
            "var image = images[path] || images['assets/' + path];\n" +
            "return image ? image.width : 0;")
    private static native int browserImageWidth(String path);

    @JSBody(params = { "path" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var images = root.libfdxImageData || {};\n" +
            "var image = images[path] || images['assets/' + path];\n" +
            "return image ? image.height : 0;")
    private static native int browserImageHeight(String path);

    @JSBody(params = { "path" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var images = root.libfdxImageData || {};\n" +
            "var image = images[path] || images['assets/' + path];\n" +
            "return image ? image.rgba : null;")
    private static native Int8Array browserImageRgba(String path);
}
