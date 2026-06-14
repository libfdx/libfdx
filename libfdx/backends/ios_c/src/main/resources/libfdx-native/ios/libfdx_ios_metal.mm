#include "libfdx_ios_metal.h"

#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>

#include <algorithm>
#include <cstdio>
#include <cstring>
#include <vector>

namespace {

static MTKView* g_view = nil;

struct IosMetalBuffer {
    __strong id<MTLBuffer> buffer;
    int32_t size = 0;
    int32_t usage = 0;
};

struct IosMetalTexture {
    __strong id<MTLTexture> texture;
    __strong id<MTLSamplerState> sampler;
    int32_t width = 0;
    int32_t height = 0;
};

struct IosMetalShaderModule {
    __strong id<MTLLibrary> library;
};

struct IosMetalPipeline {
    __strong id<MTLRenderPipelineState> pipeline;
    MTLPrimitiveType primitive = MTLPrimitiveTypeTriangle;
    int32_t sampledTextureCount = 0;
};

struct IosMetalContext {
    __strong MTKView* view;
    __strong id<MTLDevice> device;
    __strong id<MTLCommandQueue> commandQueue;
    __strong id<CAMetalDrawable> drawable;
    __strong id<MTLCommandBuffer> commandBuffer;
    __strong id<MTLRenderCommandEncoder> encoder;
    IosMetalPipeline* currentPipeline = nullptr;
    IosMetalBuffer* indexBuffer = nullptr;
    int32_t width = 1;
    int32_t height = 1;
};

template <typename T>
T* from_handle(int64_t handle) {
    return reinterpret_cast<T*>(static_cast<intptr_t>(handle));
}

template <typename T>
int64_t to_handle(T* pointer) {
    return static_cast<int64_t>(reinterpret_cast<intptr_t>(pointer));
}

void log_error(NSString* message) {
    if (message == nil) {
        return;
    }
    const char* text = [message UTF8String];
    if (text != nullptr) {
        std::fprintf(stderr, "%s\n", text);
        std::fflush(stderr);
    }
}

MTLPrimitiveType primitive_type(int32_t topology) {
    if (topology == 2) {
        return MTLPrimitiveTypeLine;
    }
    if (topology == 1) {
        return MTLPrimitiveTypeTriangleStrip;
    }
    return MTLPrimitiveTypeTriangle;
}

MTLVertexFormat vertex_format(int32_t format) {
    switch (format) {
        case 0:
            return MTLVertexFormatFloat;
        case 1:
            return MTLVertexFormatFloat2;
        case 2:
            return MTLVertexFormatFloat3;
        case 4:
            return MTLVertexFormatUChar4Normalized;
        case 3:
        default:
            return MTLVertexFormatFloat4;
    }
}

MTLSamplerAddressMode address_mode(int32_t wrap) {
    if (wrap == 1) {
        return MTLSamplerAddressModeRepeat;
    }
    if (wrap == 2) {
        return MTLSamplerAddressModeMirrorRepeat;
    }
    return MTLSamplerAddressModeClampToEdge;
}

MTLRenderPassDescriptor* render_pass_descriptor(
        IosMetalContext* context, bool clear, float red, float green, float blue, float alpha, bool store) {
    if (context == nullptr || context->drawable == nil) {
        return nil;
    }
    MTLRenderPassDescriptor* descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
    MTLRenderPassColorAttachmentDescriptor* color = descriptor.colorAttachments[0];
    color.texture = context->drawable.texture;
    color.loadAction = clear ? MTLLoadActionClear : MTLLoadActionLoad;
    color.storeAction = store ? MTLStoreActionStore : MTLStoreActionDontCare;
    color.clearColor = MTLClearColorMake(red, green, blue, alpha);
    return descriptor;
}

bool ensure_frame(IosMetalContext* context) {
    if (context == nullptr) {
        return false;
    }
    if (context->commandBuffer != nil && context->drawable != nil) {
        return true;
    }
    context->drawable = [context->view currentDrawable];
    if (context->drawable == nil) {
        return false;
    }
    context->commandBuffer = [context->commandQueue commandBuffer];
    if (context->commandBuffer == nil) {
        context->drawable = nil;
        return false;
    }
    return true;
}

void end_encoder(IosMetalContext* context) {
    if (context != nullptr && context->encoder != nil) {
        [context->encoder endEncoding];
        context->encoder = nil;
    }
}

} // namespace

extern "C" {

void libfdx_ios_metal_set_view(void* view) {
    g_view = (__bridge MTKView*) view;
}

int32_t libfdx_ios_metal_supported(void) {
    return g_view != nil && MTLCreateSystemDefaultDevice() != nil ? 1 : 0;
}

int64_t libfdx_ios_metal_create(int32_t width, int32_t height) {
    if (g_view == nil) {
        log_error(@"libFDX iOS Metal view was not installed");
        return 0;
    }
    id<MTLDevice> device = g_view.device != nil ? g_view.device : MTLCreateSystemDefaultDevice();
    if (device == nil) {
        log_error(@"Metal device is not available");
        return 0;
    }
    id<MTLCommandQueue> commandQueue = [device newCommandQueue];
    if (commandQueue == nil) {
        log_error(@"Could not create Metal command queue");
        return 0;
    }
    g_view.device = device;
    g_view.colorPixelFormat = MTLPixelFormatBGRA8Unorm;
    g_view.depthStencilPixelFormat = MTLPixelFormatInvalid;
    g_view.framebufferOnly = NO;
    g_view.drawableSize = CGSizeMake(std::max(1, width), std::max(1, height));

    IosMetalContext* context = new IosMetalContext();
    context->view = g_view;
    context->device = device;
    context->commandQueue = commandQueue;
    context->width = std::max(1, width);
    context->height = std::max(1, height);
    return to_handle(context);
}

void libfdx_ios_metal_resize(int64_t context_handle, int32_t width, int32_t height) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr) {
        return;
    }
    context->width = std::max(1, width);
    context->height = std::max(1, height);
    context->view.drawableSize = CGSizeMake(context->width, context->height);
}

int32_t libfdx_ios_metal_begin_frame(int64_t context_handle) {
    return ensure_frame(from_handle<IosMetalContext>(context_handle)) ? 1 : 0;
}

void libfdx_ios_metal_end_frame(int64_t context_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr) {
        return;
    }
    end_encoder(context);
    if (context->commandBuffer != nil && context->drawable != nil) {
        [context->commandBuffer presentDrawable:context->drawable];
        [context->commandBuffer commit];
    }
    context->commandBuffer = nil;
    context->drawable = nil;
    context->currentPipeline = nullptr;
    context->indexBuffer = nullptr;
}

void libfdx_ios_metal_read_pixels_rgba8(int64_t context_handle, void* target, int32_t byte_count) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (target == nullptr || byte_count <= 0) {
        return;
    }
    std::memset(target, 0, static_cast<size_t>(byte_count));
    if (context == nullptr || context->drawable == nil) {
        return;
    }
    id<MTLTexture> texture = context->drawable.texture;
    int32_t width = static_cast<int32_t>(texture.width);
    int32_t height = static_cast<int32_t>(texture.height);
    int32_t required = width * height * 4;
    if (required <= 0 || byte_count < required) {
        return;
    }
    std::vector<uint8_t> bgra(static_cast<size_t>(required));
    MTLRegion region = MTLRegionMake2D(0, 0, static_cast<NSUInteger>(width), static_cast<NSUInteger>(height));
    [texture getBytes:bgra.data() bytesPerRow:static_cast<NSUInteger>(width * 4) fromRegion:region mipmapLevel:0];
    uint8_t* rgba = static_cast<uint8_t*>(target);
    for (int32_t i = 0; i < width * height; i++) {
        rgba[i * 4 + 0] = bgra[i * 4 + 2];
        rgba[i * 4 + 1] = bgra[i * 4 + 1];
        rgba[i * 4 + 2] = bgra[i * 4 + 0];
        rgba[i * 4 + 3] = bgra[i * 4 + 3];
    }
}

void libfdx_ios_metal_clear(int64_t context_handle, float red, float green, float blue, float alpha) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (!ensure_frame(context)) {
        return;
    }
    end_encoder(context);
    MTLRenderPassDescriptor* descriptor = render_pass_descriptor(context, true, red, green, blue, alpha, true);
    if (descriptor == nil) {
        return;
    }
    id<MTLRenderCommandEncoder> encoder = [context->commandBuffer renderCommandEncoderWithDescriptor:descriptor];
    [encoder endEncoding];
}

int64_t libfdx_ios_metal_create_buffer(int64_t context_handle, int32_t size, int32_t usage) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || size <= 0) {
        return 0;
    }
    id<MTLBuffer> buffer = [context->device newBufferWithLength:static_cast<NSUInteger>(size)
                                                        options:MTLResourceStorageModeShared];
    if (buffer == nil) {
        log_error(@"Could not create Metal buffer");
        return 0;
    }
    IosMetalBuffer* handle = new IosMetalBuffer();
    handle->buffer = buffer;
    handle->size = size;
    handle->usage = usage;
    return to_handle(handle);
}

void libfdx_ios_metal_write_buffer(int64_t buffer_handle, const void* data, int32_t byte_count) {
    IosMetalBuffer* buffer = from_handle<IosMetalBuffer>(buffer_handle);
    if (buffer == nullptr || data == nullptr || byte_count <= 0 || buffer->buffer == nil) {
        return;
    }
    int32_t count = std::min(byte_count, buffer->size);
    std::memcpy([buffer->buffer contents], data, static_cast<size_t>(count));
}

int64_t libfdx_ios_metal_create_texture(
        int64_t context_handle, int32_t width, int32_t height, int32_t wrap_s, int32_t wrap_t) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || width <= 0 || height <= 0) {
        return 0;
    }
    MTLTextureDescriptor* textureDescriptor = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                         width:static_cast<NSUInteger>(width)
                                        height:static_cast<NSUInteger>(height)
                                     mipmapped:NO];
    textureDescriptor.usage = MTLTextureUsageShaderRead;
    id<MTLTexture> texture = [context->device newTextureWithDescriptor:textureDescriptor];
    if (texture == nil) {
        log_error(@"Could not create Metal texture");
        return 0;
    }
    MTLSamplerDescriptor* samplerDescriptor = [[MTLSamplerDescriptor alloc] init];
    samplerDescriptor.minFilter = MTLSamplerMinMagFilterLinear;
    samplerDescriptor.magFilter = MTLSamplerMinMagFilterLinear;
    samplerDescriptor.sAddressMode = address_mode(wrap_s);
    samplerDescriptor.tAddressMode = address_mode(wrap_t);
    id<MTLSamplerState> sampler = [context->device newSamplerStateWithDescriptor:samplerDescriptor];
    if (sampler == nil) {
        log_error(@"Could not create Metal sampler");
        return 0;
    }
    IosMetalTexture* handle = new IosMetalTexture();
    handle->texture = texture;
    handle->sampler = sampler;
    handle->width = width;
    handle->height = height;
    return to_handle(handle);
}

void libfdx_ios_metal_write_texture(int64_t texture_handle, const void* data, int32_t byte_count) {
    IosMetalTexture* texture = from_handle<IosMetalTexture>(texture_handle);
    if (texture == nullptr || texture->texture == nil || data == nullptr) {
        return;
    }
    int32_t required = texture->width * texture->height * 4;
    if (required <= 0 || byte_count < required) {
        return;
    }
    MTLRegion region = MTLRegionMake2D(0, 0,
            static_cast<NSUInteger>(texture->width), static_cast<NSUInteger>(texture->height));
    [texture->texture replaceRegion:region
                         mipmapLevel:0
                           withBytes:data
                         bytesPerRow:static_cast<NSUInteger>(texture->width * 4)];
}

int64_t libfdx_ios_metal_create_shader_module(
        int64_t context_handle, int32_t source_length, const int32_t* source_data) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || source_data == nullptr || source_length <= 0) {
        return 0;
    }
    std::vector<char> sourceBytes(static_cast<size_t>(source_length));
    for (int32_t i = 0; i < source_length; i++) {
        sourceBytes[static_cast<size_t>(i)] = static_cast<char>(source_data[i] & 0xff);
    }
    NSString* source = [[NSString alloc] initWithBytes:sourceBytes.data()
                                                length:static_cast<NSUInteger>(sourceBytes.size())
                                              encoding:NSUTF8StringEncoding];
    if (source == nil) {
        log_error(@"Could not decode Metal shader source");
        return 0;
    }
    NSError* error = nil;
    id<MTLLibrary> library = [context->device newLibraryWithSource:source options:nil error:&error];
    if (library == nil) {
        log_error(error != nil ? [error localizedDescription] : @"Could not compile Metal shader library");
        return 0;
    }
    IosMetalShaderModule* handle = new IosMetalShaderModule();
    handle->library = library;
    return to_handle(handle);
}

int64_t libfdx_ios_metal_create_render_pipeline(
        int64_t context_handle,
        int64_t shader_module_handle,
        int32_t primitive_topology,
        const int32_t* vertex_strides,
        const int32_t* vertex_step_modes,
        int32_t vertex_layout_count,
        const int32_t* attribute_bindings,
        const int32_t* attribute_locations,
        const int32_t* attribute_formats,
        const int32_t* attribute_offsets,
        int32_t attribute_count,
        int32_t sampled_texture_count) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalShaderModule* shaderModule = from_handle<IosMetalShaderModule>(shader_module_handle);
    if (context == nullptr || shaderModule == nullptr || shaderModule->library == nil) {
        return 0;
    }
    id<MTLFunction> vertexFunction = [shaderModule->library newFunctionWithName:@"vertexMain"];
    id<MTLFunction> fragmentFunction = [shaderModule->library newFunctionWithName:@"fragmentMain"];
    if (vertexFunction == nil || fragmentFunction == nil) {
        log_error(@"Metal shader library must contain vertexMain and fragmentMain");
        return 0;
    }

    MTLRenderPipelineDescriptor* descriptor = [[MTLRenderPipelineDescriptor alloc] init];
    descriptor.vertexFunction = vertexFunction;
    descriptor.fragmentFunction = fragmentFunction;
    descriptor.colorAttachments[0].pixelFormat = MTLPixelFormatBGRA8Unorm;
    descriptor.colorAttachments[0].blendingEnabled = YES;
    descriptor.colorAttachments[0].rgbBlendOperation = MTLBlendOperationAdd;
    descriptor.colorAttachments[0].alphaBlendOperation = MTLBlendOperationAdd;
    descriptor.colorAttachments[0].sourceRGBBlendFactor = MTLBlendFactorSourceAlpha;
    descriptor.colorAttachments[0].destinationRGBBlendFactor = MTLBlendFactorOneMinusSourceAlpha;
    descriptor.colorAttachments[0].sourceAlphaBlendFactor = MTLBlendFactorSourceAlpha;
    descriptor.colorAttachments[0].destinationAlphaBlendFactor = MTLBlendFactorOneMinusSourceAlpha;

    MTLVertexDescriptor* vertexDescriptor = [[MTLVertexDescriptor alloc] init];
    for (int32_t i = 0; i < vertex_layout_count; i++) {
        MTLVertexBufferLayoutDescriptor* layout = vertexDescriptor.layouts[static_cast<NSUInteger>(i)];
        layout.stride = static_cast<NSUInteger>(std::max(0, vertex_strides[i]));
        layout.stepRate = 1;
        layout.stepFunction = vertex_step_modes[i] == 1
                ? MTLVertexStepFunctionPerInstance
                : MTLVertexStepFunctionPerVertex;
    }
    for (int32_t i = 0; i < attribute_count; i++) {
        NSUInteger location = static_cast<NSUInteger>(std::max(0, attribute_locations[i]));
        MTLVertexAttributeDescriptor* attribute = vertexDescriptor.attributes[location];
        attribute.bufferIndex = static_cast<NSUInteger>(std::max(0, attribute_bindings[i]));
        attribute.format = vertex_format(attribute_formats[i]);
        attribute.offset = static_cast<NSUInteger>(std::max(0, attribute_offsets[i]));
    }
    descriptor.vertexDescriptor = vertexDescriptor;

    NSError* error = nil;
    id<MTLRenderPipelineState> pipeline = [context->device newRenderPipelineStateWithDescriptor:descriptor
                                                                                         error:&error];
    if (pipeline == nil) {
        log_error(error != nil ? [error localizedDescription] : @"Could not create Metal render pipeline");
        return 0;
    }
    IosMetalPipeline* handle = new IosMetalPipeline();
    handle->pipeline = pipeline;
    handle->primitive = primitive_type(primitive_topology);
    handle->sampledTextureCount = sampled_texture_count;
    return to_handle(handle);
}

void libfdx_ios_metal_begin_render_pass(
        int64_t context_handle, int32_t clear, float red, float green, float blue, float alpha, int32_t store) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (!ensure_frame(context)) {
        return;
    }
    end_encoder(context);
    MTLRenderPassDescriptor* descriptor = render_pass_descriptor(
            context, clear != 0, red, green, blue, alpha, store != 0);
    if (descriptor == nil) {
        return;
    }
    context->encoder = [context->commandBuffer renderCommandEncoderWithDescriptor:descriptor];
}

void libfdx_ios_metal_set_pipeline(int64_t context_handle, int64_t pipeline_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalPipeline* pipeline = from_handle<IosMetalPipeline>(pipeline_handle);
    if (context == nullptr || context->encoder == nil || pipeline == nullptr || pipeline->pipeline == nil) {
        return;
    }
    context->currentPipeline = pipeline;
    [context->encoder setRenderPipelineState:pipeline->pipeline];
}

void libfdx_ios_metal_set_vertex_buffer(int64_t context_handle, int32_t slot, int64_t buffer_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalBuffer* buffer = from_handle<IosMetalBuffer>(buffer_handle);
    if (context == nullptr || context->encoder == nil || buffer == nullptr || buffer->buffer == nil || slot < 0) {
        return;
    }
    [context->encoder setVertexBuffer:buffer->buffer offset:0 atIndex:static_cast<NSUInteger>(slot)];
}

void libfdx_ios_metal_set_index_buffer(int64_t context_handle, int64_t buffer_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalBuffer* buffer = from_handle<IosMetalBuffer>(buffer_handle);
    if (context == nullptr || buffer == nullptr || buffer->buffer == nil) {
        return;
    }
    context->indexBuffer = buffer;
}

void libfdx_ios_metal_set_texture(int64_t context_handle, int32_t slot, int64_t texture_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalTexture* texture = from_handle<IosMetalTexture>(texture_handle);
    if (context == nullptr || context->encoder == nil || texture == nullptr || texture->texture == nil || slot < 0) {
        return;
    }
    NSUInteger index = static_cast<NSUInteger>(slot);
    [context->encoder setFragmentTexture:texture->texture atIndex:index];
    [context->encoder setFragmentSamplerState:texture->sampler atIndex:index];
}

void libfdx_ios_metal_draw(
        int64_t context_handle, int32_t vertex_count, int32_t instance_count, int32_t first_vertex, int32_t first_instance) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || context->encoder == nil || context->currentPipeline == nullptr || vertex_count <= 0) {
        return;
    }
    [context->encoder drawPrimitives:context->currentPipeline->primitive
                          vertexStart:static_cast<NSUInteger>(std::max(0, first_vertex))
                          vertexCount:static_cast<NSUInteger>(vertex_count)
                        instanceCount:static_cast<NSUInteger>(std::max(1, instance_count))
                         baseInstance:static_cast<NSUInteger>(std::max(0, first_instance))];
}

void libfdx_ios_metal_draw_indexed(
        int64_t context_handle,
        int32_t index_count,
        int32_t instance_count,
        int32_t first_index,
        int32_t base_vertex,
        int32_t first_instance) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || context->encoder == nil || context->currentPipeline == nullptr
            || context->indexBuffer == nullptr || context->indexBuffer->buffer == nil || index_count <= 0) {
        return;
    }
    [context->encoder drawIndexedPrimitives:context->currentPipeline->primitive
                                 indexCount:static_cast<NSUInteger>(index_count)
                                  indexType:MTLIndexTypeUInt16
                                indexBuffer:context->indexBuffer->buffer
                          indexBufferOffset:static_cast<NSUInteger>(std::max(0, first_index) * 2)
                              instanceCount:static_cast<NSUInteger>(std::max(1, instance_count))
                                 baseVertex:base_vertex
                               baseInstance:static_cast<NSUInteger>(std::max(0, first_instance))];
}

void libfdx_ios_metal_end_render_pass(int64_t context_handle) {
    end_encoder(from_handle<IosMetalContext>(context_handle));
}

void libfdx_ios_metal_destroy_shader_module(int64_t shader_module) {
    delete from_handle<IosMetalShaderModule>(shader_module);
}

void libfdx_ios_metal_destroy_render_pipeline(int64_t pipeline) {
    delete from_handle<IosMetalPipeline>(pipeline);
}

void libfdx_ios_metal_destroy_buffer(int64_t buffer) {
    delete from_handle<IosMetalBuffer>(buffer);
}

void libfdx_ios_metal_destroy_texture(int64_t texture) {
    delete from_handle<IosMetalTexture>(texture);
}

void libfdx_ios_metal_destroy(int64_t context_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr) {
        return;
    }
    end_encoder(context);
    context->commandBuffer = nil;
    context->drawable = nil;
    delete context;
}

} // extern "C"
