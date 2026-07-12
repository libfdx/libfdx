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

struct IosMetalContext;

struct IosMetalBuffer {
    IosMetalContext* context = nullptr;
    __strong id<MTLBuffer> buffer;
    int32_t size = 0;
    int32_t usage = 0;
};

struct IosMetalTexture {
    IosMetalContext* context = nullptr;
    __strong id<MTLTexture> texture;
    __strong id<MTLSamplerState> sampler;
    int32_t width = 0;
    int32_t height = 0;
};

struct IosMetalShaderModule {
    __strong id<MTLLibrary> library;
};

struct IosMetalPipeline {
    IosMetalContext* context = nullptr;
    __strong id<MTLRenderPipelineState> pipeline;
    __strong id<MTLDepthStencilState> depthState;
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
    __strong id<MTLTexture> depthTexture;
    __strong NSMutableArray* frameResources;
    __strong id<MTLRenderPipelineState> currentPipelineState;
    __strong id<MTLBuffer> indexBuffer;
    MTLPrimitiveType currentPrimitive = MTLPrimitiveTypeTriangle;
    bool pipelineBound = false;
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

bool ensure_depth_texture(IosMetalContext* context) {
    if (context == nullptr) {
        return false;
    }
    if (context->depthTexture != nil
            && static_cast<int32_t>(context->depthTexture.width) == context->width
            && static_cast<int32_t>(context->depthTexture.height) == context->height) {
        return true;
    }
    MTLTextureDescriptor* descriptor = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatDepth32Float
                                         width:static_cast<NSUInteger>(context->width)
                                        height:static_cast<NSUInteger>(context->height)
                                     mipmapped:NO];
    descriptor.usage = MTLTextureUsageRenderTarget;
    descriptor.storageMode = MTLStorageModePrivate;
    context->depthTexture = [context->device newTextureWithDescriptor:descriptor];
    if (context->depthTexture == nil) {
        log_error(@"Could not create Metal depth texture");
        return false;
    }
    return true;
}

MTLRenderPassDescriptor* render_pass_descriptor(
        IosMetalContext* context,
        bool clear,
        float red,
        float green,
        float blue,
        float alpha,
        bool store,
        bool depth_enabled,
        bool depth_clear,
        float depth_clear_value) {
    if (context == nullptr || context->drawable == nil) {
        return nil;
    }
    MTLRenderPassDescriptor* descriptor = [MTLRenderPassDescriptor renderPassDescriptor];
    MTLRenderPassColorAttachmentDescriptor* color = descriptor.colorAttachments[0];
    color.texture = context->drawable.texture;
    color.loadAction = clear ? MTLLoadActionClear : MTLLoadActionLoad;
    color.storeAction = store ? MTLStoreActionStore : MTLStoreActionDontCare;
    color.clearColor = MTLClearColorMake(red, green, blue, alpha);
    if (depth_enabled) {
        if (!ensure_depth_texture(context)) {
            return nil;
        }
        MTLRenderPassDepthAttachmentDescriptor* depth = descriptor.depthAttachment;
        depth.texture = context->depthTexture;
        depth.loadAction = depth_clear ? MTLLoadActionClear : MTLLoadActionLoad;
        depth.storeAction = MTLStoreActionStore;
        depth.clearDepth = depth_clear_value;
    }
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

void retain_frame_resource(IosMetalContext* context, id<MTLResource> resource) {
    if (context != nullptr && context->commandBuffer != nil && resource != nil) {
        [context->frameResources addObject:resource];
    }
}

void retain_frame_object(IosMetalContext* context, id object) {
    if (context != nullptr && context->commandBuffer != nil && object != nil) {
        [context->frameResources addObject:object];
    }
}

id<MTLCommandBuffer> submit_frame(IosMetalContext* context, bool wait_for_completion) {
    if (context == nullptr) {
        return nil;
    }
    end_encoder(context);
    id<MTLCommandBuffer> commandBuffer = context->commandBuffer;
    id<CAMetalDrawable> drawable = context->drawable;
    if (commandBuffer != nil && drawable != nil) {
        NSMutableArray* submittedFrameResources = context->frameResources;
        context->frameResources = [[NSMutableArray alloc] init];
        [commandBuffer addCompletedHandler:^(id<MTLCommandBuffer> completedBuffer) {
            (void)completedBuffer;
            [submittedFrameResources removeAllObjects];
        }];
        [commandBuffer presentDrawable:drawable];
        [commandBuffer commit];
        if (wait_for_completion) {
            [commandBuffer waitUntilCompleted];
        }
    } else {
        [context->frameResources removeAllObjects];
    }
    context->commandBuffer = nil;
    context->drawable = nil;
    context->pipelineBound = false;
    context->currentPipelineState = nil;
    context->indexBuffer = nil;
    return commandBuffer;
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
    context->frameResources = [[NSMutableArray alloc] init];
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
    context->depthTexture = nil;
}

int32_t libfdx_ios_metal_begin_frame(int64_t context_handle) {
    return ensure_frame(from_handle<IosMetalContext>(context_handle)) ? 1 : 0;
}

void libfdx_ios_metal_end_frame(int64_t context_handle) {
    submit_frame(from_handle<IosMetalContext>(context_handle), false);
}

void libfdx_ios_metal_read_pixels_rgba8(int64_t context_handle, void* target, int32_t byte_count) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (target == nullptr || byte_count <= 0) {
        return;
    }
    std::memset(target, 0, static_cast<size_t>(byte_count));
    if (context == nullptr || context->drawable == nil || context->commandBuffer == nil) {
        return;
    }
    id<CAMetalDrawable> drawable = context->drawable;
    id<MTLTexture> texture = context->drawable.texture;
    int32_t width = static_cast<int32_t>(texture.width);
    int32_t height = static_cast<int32_t>(texture.height);
    int32_t required = width * height * 4;
    if (required <= 0 || byte_count < required) {
        return;
    }
    id<MTLCommandBuffer> commandBuffer = submit_frame(context, true);
    if (commandBuffer == nil || commandBuffer.status == MTLCommandBufferStatusError) {
        if (commandBuffer.error != nil) {
            log_error(commandBuffer.error.localizedDescription);
        }
        return;
    }
    (void)drawable;
    std::vector<uint8_t> bgra(static_cast<size_t>(required));
    MTLRegion region = MTLRegionMake2D(0, 0, static_cast<NSUInteger>(width), static_cast<NSUInteger>(height));
    [texture getBytes:bgra.data() bytesPerRow:static_cast<NSUInteger>(width * 4) fromRegion:region mipmapLevel:0];
    uint8_t* rgba = static_cast<uint8_t*>(target);
    for (int32_t y = 0; y < height; y++) {
        int32_t sourceRow = height - 1 - y;
        for (int32_t x = 0; x < width; x++) {
            int32_t source = (sourceRow * width + x) * 4;
            int32_t destination = (y * width + x) * 4;
            rgba[destination + 0] = bgra[source + 2];
            rgba[destination + 1] = bgra[source + 1];
            rgba[destination + 2] = bgra[source + 0];
            rgba[destination + 3] = bgra[source + 3];
        }
    }
}

void libfdx_ios_metal_clear(int64_t context_handle, float red, float green, float blue, float alpha) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (!ensure_frame(context)) {
        return;
    }
    end_encoder(context);
    MTLRenderPassDescriptor* descriptor = render_pass_descriptor(
            context, true, red, green, blue, alpha, true, false, false, 1.0f);
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
    handle->context = context;
    handle->buffer = buffer;
    handle->size = size;
    handle->usage = usage;
    return to_handle(handle);
}

void libfdx_ios_metal_write_buffer(int64_t buffer_handle, const void* data, int32_t byte_count) {
    IosMetalBuffer* buffer = from_handle<IosMetalBuffer>(buffer_handle);
    if (buffer == nullptr || buffer->context == nullptr || data == nullptr || byte_count <= 0
            || buffer->buffer == nil) {
        return;
    }
    int32_t count = std::min(byte_count, buffer->size);
    id<MTLBuffer> replacement = [buffer->context->device newBufferWithLength:static_cast<NSUInteger>(buffer->size)
                                                                  options:MTLResourceStorageModeShared];
    if (replacement == nil) {
        log_error(@"Could not replace Metal buffer for a recorded write");
        return;
    }
    std::memcpy([replacement contents], data, static_cast<size_t>(count));
    retain_frame_resource(buffer->context, buffer->buffer);
    buffer->buffer = replacement;
}

int64_t libfdx_ios_metal_create_texture(
        int64_t context_handle, int32_t width, int32_t height, int32_t wrap_s, int32_t wrap_t, int32_t filter) {
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
    MTLSamplerMinMagFilter samplerFilter = filter == 0
            ? MTLSamplerMinMagFilterNearest
            : MTLSamplerMinMagFilterLinear;
    samplerDescriptor.minFilter = samplerFilter;
    samplerDescriptor.magFilter = samplerFilter;
    samplerDescriptor.sAddressMode = address_mode(wrap_s);
    samplerDescriptor.tAddressMode = address_mode(wrap_t);
    id<MTLSamplerState> sampler = [context->device newSamplerStateWithDescriptor:samplerDescriptor];
    if (sampler == nil) {
        log_error(@"Could not create Metal sampler");
        return 0;
    }
    IosMetalTexture* handle = new IosMetalTexture();
    handle->context = context;
    handle->texture = texture;
    handle->sampler = sampler;
    handle->width = width;
    handle->height = height;
    return to_handle(handle);
}

void libfdx_ios_metal_write_texture(int64_t texture_handle, const void* data, int32_t byte_count) {
    IosMetalTexture* texture = from_handle<IosMetalTexture>(texture_handle);
    if (texture == nullptr || texture->context == nullptr || texture->texture == nil || data == nullptr) {
        return;
    }
    int32_t required = texture->width * texture->height * 4;
    if (required <= 0 || byte_count < required) {
        return;
    }
    MTLTextureDescriptor* descriptor = [MTLTextureDescriptor
            texture2DDescriptorWithPixelFormat:MTLPixelFormatRGBA8Unorm
                                         width:static_cast<NSUInteger>(texture->width)
                                        height:static_cast<NSUInteger>(texture->height)
                                     mipmapped:NO];
    descriptor.usage = MTLTextureUsageShaderRead;
    id<MTLTexture> replacement = [texture->context->device newTextureWithDescriptor:descriptor];
    if (replacement == nil) {
        log_error(@"Could not replace Metal texture for a recorded write");
        return;
    }
    MTLRegion region = MTLRegionMake2D(0, 0,
            static_cast<NSUInteger>(texture->width), static_cast<NSUInteger>(texture->height));
    [replacement replaceRegion:region
                    mipmapLevel:0
                      withBytes:data
                    bytesPerRow:static_cast<NSUInteger>(texture->width * 4)];
    retain_frame_resource(texture->context, texture->texture);
    texture->texture = replacement;
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
        int32_t sampled_texture_count,
        int32_t pbr_uniforms_enabled,
        int32_t depth_test_enabled,
        int32_t depth_write_enabled) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalShaderModule* shaderModule = from_handle<IosMetalShaderModule>(shader_module_handle);
    if (context == nullptr || shaderModule == nullptr || shaderModule->library == nil) {
        return 0;
    }
    (void)pbr_uniforms_enabled;
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
    if (depth_test_enabled != 0 || depth_write_enabled != 0) {
        descriptor.depthAttachmentPixelFormat = MTLPixelFormatDepth32Float;
    }
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
    handle->context = context;
    handle->pipeline = pipeline;
    if (depth_test_enabled != 0 || depth_write_enabled != 0) {
        MTLDepthStencilDescriptor* depthDescriptor = [[MTLDepthStencilDescriptor alloc] init];
        depthDescriptor.depthCompareFunction = depth_test_enabled != 0
                ? MTLCompareFunctionLessEqual
                : MTLCompareFunctionAlways;
        depthDescriptor.depthWriteEnabled = depth_write_enabled != 0;
        handle->depthState = [context->device newDepthStencilStateWithDescriptor:depthDescriptor];
        if (handle->depthState == nil) {
            log_error(@"Could not create Metal depth stencil state");
            delete handle;
            return 0;
        }
    }
    handle->primitive = primitive_type(primitive_topology);
    handle->sampledTextureCount = sampled_texture_count;
    return to_handle(handle);
}

void libfdx_ios_metal_begin_render_pass(
        int64_t context_handle,
        int32_t clear,
        float red,
        float green,
        float blue,
        float alpha,
        int32_t store,
        int32_t depth_enabled,
        int32_t depth_clear,
        float depth_clear_value) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (!ensure_frame(context)) {
        return;
    }
    end_encoder(context);
    MTLRenderPassDescriptor* descriptor = render_pass_descriptor(context, clear != 0, red, green, blue, alpha,
            store != 0, depth_enabled != 0, depth_clear != 0, depth_clear_value);
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
    context->currentPrimitive = pipeline->primitive;
    context->pipelineBound = true;
    context->currentPipelineState = pipeline->pipeline;
    [context->encoder setRenderPipelineState:pipeline->pipeline];
    [context->encoder setDepthStencilState:pipeline->depthState];
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
    context->indexBuffer = buffer->buffer;
}

void libfdx_ios_metal_set_scissor(
        int64_t context_handle, int32_t x, int32_t y, int32_t width, int32_t height) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || context->encoder == nil || width <= 0 || height <= 0) {
        return;
    }
    int64_t left = std::max<int64_t>(0, x);
    int64_t top = std::max<int64_t>(0, y);
    int64_t right = std::min<int64_t>(context->width, static_cast<int64_t>(x) + width);
    int64_t bottom = std::min<int64_t>(context->height, static_cast<int64_t>(y) + height);
    MTLScissorRect scissor = {
            static_cast<NSUInteger>(left),
            static_cast<NSUInteger>(top),
            static_cast<NSUInteger>(std::max<int64_t>(0, right - left)),
            static_cast<NSUInteger>(std::max<int64_t>(0, bottom - top))};
    [context->encoder setScissorRect:scissor];
}

void libfdx_ios_metal_set_viewport(
        int64_t context_handle, int32_t x, int32_t y, int32_t width, int32_t height) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || context->encoder == nil || width <= 0 || height <= 0) {
        return;
    }
    MTLViewport viewport = {
            static_cast<double>(x), static_cast<double>(y),
            static_cast<double>(width), static_cast<double>(height), 0.0, 1.0};
    [context->encoder setViewport:viewport];
}

void libfdx_ios_metal_set_texture(
        int64_t context_handle, int32_t texture_slot, int32_t sampler_slot, int64_t texture_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    IosMetalTexture* texture = from_handle<IosMetalTexture>(texture_handle);
    if (context == nullptr || context->encoder == nil || texture == nullptr || texture->texture == nil
            || texture_slot < 0 || sampler_slot < 0) {
        return;
    }
    [context->encoder setFragmentTexture:texture->texture atIndex:static_cast<NSUInteger>(texture_slot)];
    [context->encoder setFragmentSamplerState:texture->sampler atIndex:static_cast<NSUInteger>(sampler_slot)];
}

void libfdx_ios_metal_set_uniform_buffer(int64_t context_handle, const void* data, int32_t byte_count) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || context->encoder == nil || data == nullptr || byte_count <= 0) {
        return;
    }
    id<MTLBuffer> buffer = [context->device newBufferWithLength:static_cast<NSUInteger>(byte_count)
                                                        options:MTLResourceStorageModeShared];
    if (buffer == nil) {
        log_error(@"Could not create Metal uniform buffer");
        return;
    }
    std::memcpy([buffer contents], data, static_cast<size_t>(byte_count));
    [context->frameResources addObject:buffer];
    [context->encoder setVertexBuffer:buffer offset:0 atIndex:0];
    [context->encoder setFragmentBuffer:buffer offset:0 atIndex:0];
}

void libfdx_ios_metal_draw(
        int64_t context_handle, int32_t vertex_count, int32_t instance_count, int32_t first_vertex, int32_t first_instance) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr || context->encoder == nil || !context->pipelineBound || vertex_count <= 0) {
        return;
    }
    [context->encoder drawPrimitives:context->currentPrimitive
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
    if (context == nullptr || context->encoder == nil || !context->pipelineBound
            || context->indexBuffer == nil || index_count <= 0) {
        return;
    }
    [context->encoder drawIndexedPrimitives:context->currentPrimitive
                                 indexCount:static_cast<NSUInteger>(index_count)
                                  indexType:MTLIndexTypeUInt16
                                indexBuffer:context->indexBuffer
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
    IosMetalPipeline* handle = from_handle<IosMetalPipeline>(pipeline);
    if (handle == nullptr) {
        return;
    }
    retain_frame_object(handle->context, handle->pipeline);
    retain_frame_object(handle->context, handle->depthState);
    if (handle->context != nullptr && handle->context->currentPipelineState == handle->pipeline) {
        handle->context->pipelineBound = false;
        handle->context->currentPipelineState = nil;
    }
    delete handle;
}

void libfdx_ios_metal_destroy_buffer(int64_t buffer) {
    IosMetalBuffer* handle = from_handle<IosMetalBuffer>(buffer);
    if (handle == nullptr) {
        return;
    }
    retain_frame_resource(handle->context, handle->buffer);
    if (handle->context != nullptr && handle->context->indexBuffer == handle->buffer) {
        handle->context->indexBuffer = nil;
    }
    delete handle;
}

void libfdx_ios_metal_destroy_texture(int64_t texture) {
    IosMetalTexture* handle = from_handle<IosMetalTexture>(texture);
    if (handle == nullptr) {
        return;
    }
    retain_frame_resource(handle->context, handle->texture);
    retain_frame_object(handle->context, handle->sampler);
    delete handle;
}

void libfdx_ios_metal_destroy(int64_t context_handle) {
    IosMetalContext* context = from_handle<IosMetalContext>(context_handle);
    if (context == nullptr) {
        return;
    }
    end_encoder(context);
    context->commandBuffer = nil;
    context->drawable = nil;
    [context->frameResources removeAllObjects];
    context->frameResources = nil;
    context->depthTexture = nil;
    delete context;
}

} // extern "C"
