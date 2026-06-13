#include "libfdx_native_image.h"

#include <cstdint>
#include <cstdio>

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#include <windows.h>
#include <objbase.h>
#include <wincodec.h>
#endif

#define IMAGE_LOGE(...) std::fprintf(stderr, "[libfdx-native-image] error: " __VA_ARGS__), std::fprintf(stderr, "\n")

#ifdef _WIN32
namespace {

static void releaseUnknown(IUnknown* value) {
    if (value != nullptr) {
        value->Release();
    }
}

struct ComScope {
    bool uninitialize = false;

    ComScope() {
        HRESULT result = CoInitializeEx(nullptr, COINIT_MULTITHREADED);
        if (SUCCEEDED(result)) {
            uninitialize = true;
        } else if (result != RPC_E_CHANGED_MODE) {
            IMAGE_LOGE("Could not initialize COM for native image decode: 0x%08x",
                    static_cast<unsigned>(result));
        }
    }

    ~ComScope() {
        if (uninitialize) {
            CoUninitialize();
        }
    }
};

static bool decodeWicImage(const int8_t* data, int32_t size, int32_t* dimensions,
        void* target, int32_t targetSize) {
    if (data == nullptr || size <= 0) {
        return false;
    }

    ComScope com;
    IWICImagingFactory* factory = nullptr;
    IWICStream* stream = nullptr;
    IWICBitmapDecoder* decoder = nullptr;
    IWICBitmapFrameDecode* frame = nullptr;
    IWICFormatConverter* converter = nullptr;

    HRESULT result = CoCreateInstance(CLSID_WICImagingFactory, nullptr, CLSCTX_INPROC_SERVER,
            IID_IWICImagingFactory, reinterpret_cast<void**>(&factory));
    if (FAILED(result)) {
        IMAGE_LOGE("Could not create WIC imaging factory: 0x%08x", static_cast<unsigned>(result));
        return false;
    }

    bool success = false;
    do {
        result = factory->CreateStream(&stream);
        if (FAILED(result)) {
            IMAGE_LOGE("Could not create WIC image stream: 0x%08x", static_cast<unsigned>(result));
            break;
        }
        result = stream->InitializeFromMemory(reinterpret_cast<BYTE*>(const_cast<int8_t*>(data)),
                static_cast<DWORD>(size));
        if (FAILED(result)) {
            IMAGE_LOGE("Could not initialize WIC image stream: 0x%08x", static_cast<unsigned>(result));
            break;
        }
        result = factory->CreateDecoderFromStream(stream, nullptr, WICDecodeMetadataCacheOnDemand, &decoder);
        if (FAILED(result)) {
            IMAGE_LOGE("Could not create WIC image decoder: 0x%08x", static_cast<unsigned>(result));
            break;
        }
        result = decoder->GetFrame(0, &frame);
        if (FAILED(result)) {
            IMAGE_LOGE("Could not read first WIC image frame: 0x%08x", static_cast<unsigned>(result));
            break;
        }
        result = factory->CreateFormatConverter(&converter);
        if (FAILED(result)) {
            IMAGE_LOGE("Could not create WIC image format converter: 0x%08x",
                    static_cast<unsigned>(result));
            break;
        }
        result = converter->Initialize(frame, GUID_WICPixelFormat32bppRGBA, WICBitmapDitherTypeNone,
                nullptr, 0.0, WICBitmapPaletteTypeCustom);
        if (FAILED(result)) {
            IMAGE_LOGE("Could not convert WIC image to RGBA8: 0x%08x", static_cast<unsigned>(result));
            break;
        }
        UINT width = 0;
        UINT height = 0;
        result = converter->GetSize(&width, &height);
        if (FAILED(result) || width == 0 || height == 0) {
            IMAGE_LOGE("Could not read WIC image dimensions: 0x%08x", static_cast<unsigned>(result));
            break;
        }
        if (dimensions != nullptr) {
            dimensions[0] = static_cast<int32_t>(width);
            dimensions[1] = static_cast<int32_t>(height);
        }
        if (target != nullptr) {
            uint64_t requiredSize = static_cast<uint64_t>(width) * static_cast<uint64_t>(height) * 4ull;
            if (requiredSize > static_cast<uint64_t>(targetSize)) {
                IMAGE_LOGE("WIC image target buffer is too small");
                break;
            }
            UINT stride = width * 4u;
            result = converter->CopyPixels(nullptr, stride, static_cast<UINT>(requiredSize),
                    reinterpret_cast<BYTE*>(target));
            if (FAILED(result)) {
                IMAGE_LOGE("Could not copy WIC RGBA8 pixels: 0x%08x", static_cast<unsigned>(result));
                break;
            }
        }
        success = true;
    } while (false);

    releaseUnknown(converter);
    releaseUnknown(frame);
    releaseUnknown(decoder);
    releaseUnknown(stream);
    releaseUnknown(factory);
    return success;
}

} // namespace
#endif

extern "C" int32_t fdx_native_image_dimensions(const int8_t* data, int32_t size, int32_t* dimensions) {
#ifdef _WIN32
    return decodeWicImage(data, size, dimensions, nullptr, 0) ? 1 : 0;
#else
    (void)data;
    (void)size;
    (void)dimensions;
    return 0;
#endif
}

extern "C" int32_t fdx_native_image_decode_rgba8(const int8_t* data, int32_t size, void* target,
        int32_t targetSize) {
#ifdef _WIN32
    return decodeWicImage(data, size, nullptr, target, targetSize) ? 1 : 0;
#else
    (void)data;
    (void)size;
    (void)target;
    (void)targetSize;
    return 0;
#endif
}
