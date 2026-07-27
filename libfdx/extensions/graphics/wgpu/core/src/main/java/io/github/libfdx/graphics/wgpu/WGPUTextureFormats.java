package io.github.libfdx.graphics.wgpu;

import com.github.xpenatan.webgpu.WGPUTextureFormat;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.TextureFormat;
import io.github.libfdx.graphics.shader.reflection.ShaderStorageTextureFormat;

/**
 * Represents a WGPU texture formats.
 *
 * @author xpenatan
 */
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
        if (format == WGPUTextureFormat.RGBA16Float) {
            return TextureFormat.RGBA16_FLOAT;
        }
        if (format == WGPUTextureFormat.R32Float) {
            return TextureFormat.R32_FLOAT;
        }
        if (format == WGPUTextureFormat.Depth24PlusStencil8) {
            return TextureFormat.DEPTH24_STENCIL8;
        }
        if (format == WGPUTextureFormat.Depth32Float) {
            return TextureFormat.DEPTH32_FLOAT;
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
            case RGBA16_FLOAT:
                return WGPUTextureFormat.RGBA16Float;
            case R32_FLOAT:
                return WGPUTextureFormat.R32Float;
            case DEPTH24_STENCIL8:
                return WGPUTextureFormat.Depth24PlusStencil8;
            case DEPTH32_FLOAT:
                return WGPUTextureFormat.Depth32Float;
            case UNKNOWN:
            default:
                throw new FdxException("Cannot create WGPU pipeline for unknown texture format");
        }
    }

    static TextureFormat toCommon(ShaderStorageTextureFormat format) {
        return switch (format) {
            case RGBA8_UNORM -> TextureFormat.RGBA8_UNORM;
            case BGRA8_UNORM -> TextureFormat.BGRA8_UNORM;
            case RGBA16_FLOAT -> TextureFormat.RGBA16_FLOAT;
            case R32_FLOAT -> TextureFormat.R32_FLOAT;
            default -> throw new FdxException(
                    "WGPU storage texture format is not exposed by the common texture API: " + format);
        };
    }
}
