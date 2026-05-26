package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUTextureFormat;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.TextureFormat;

final class WGPUTextureFormats {
    private WGPUTextureFormats() {
    }

    static TextureFormat toCommon(WGPUTextureFormat format) {
        if (format == WGPUTextureFormat.RGBA8Unorm) {
            return TextureFormat.RGBA8_UNORM;
        }
        if (format == WGPUTextureFormat.RGBA8UnormSrgb) {
            return TextureFormat.RGBA8_UNORM_SRGB;
        }
        if (format == WGPUTextureFormat.BGRA8Unorm) {
            return TextureFormat.BGRA8_UNORM;
        }
        if (format == WGPUTextureFormat.BGRA8UnormSrgb) {
            return TextureFormat.BGRA8_UNORM_SRGB;
        }
        return TextureFormat.UNKNOWN;
    }

    static WGPUTextureFormat toNative(TextureFormat format) {
        switch (format) {
            case RGBA8_UNORM:
                return WGPUTextureFormat.RGBA8Unorm;
            case RGBA8_UNORM_SRGB:
                return WGPUTextureFormat.RGBA8UnormSrgb;
            case BGRA8_UNORM:
                return WGPUTextureFormat.BGRA8Unorm;
            case BGRA8_UNORM_SRGB:
                return WGPUTextureFormat.BGRA8UnormSrgb;
            case UNKNOWN:
            default:
                throw new FdxException("Cannot create WGPU pipeline for unknown texture format");
        }
    }
}
