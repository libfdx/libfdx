package io.github.libfdx.backend.desktopcpp;

import com.github.xpenatan.jnative.interop.NativeImport;
import com.github.xpenatan.jnative.interop.NativeInclude;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.core.FdxException;
import java.nio.ByteBuffer;

/** Native image fallback for formats not handled by the portable PNG decoder. */
@NativeInclude("libfdx_jnative.hpp")
public final class DesktopCppImageDecoder {
    private DesktopCppImageDecoder() {}

    public static ImageData decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new FdxException("Encoded image is empty");
        int[] dimensions = new int[2];
        if (!dimensions(bytes, dimensions)) throw new FdxException("Unsupported or invalid image");
        int width = dimensions[0];
        int height = dimensions[1];
        if (width < 1 || height < 1 || (long) width * height > 16777216L)
            throw new FdxException("Image exceeds 16M pixel budget");
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        if (!decode(bytes, pixels, pixels.capacity()))
            throw new FdxException("Native image decode failed");
        return new ImageData(width, height, pixels);
    }

    @NativeImport("fdx_cpp_image_dimensions")
    private static native boolean dimensions(byte[] bytes, int[] dimensions);

    @NativeImport("fdx_cpp_image_decode")
    private static native boolean decode(byte[] bytes, ByteBuffer target, int size);
}
