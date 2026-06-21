#define VK_USE_PLATFORM_ANDROID_KHR

#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <vulkan/vulkan.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

#define LOG_TAG "libfdx-vulkan"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct QueueFamilies {
    uint32_t graphicsFamily = UINT32_MAX;
    uint32_t presentFamily = UINT32_MAX;

    bool complete() const {
        return graphicsFamily != UINT32_MAX && presentFamily != UINT32_MAX;
    }
};

struct TransientBuffer {
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
};

struct FrameSync {
    VkSemaphore imageAvailable = VK_NULL_HANDLE;
    VkSemaphore renderFinished = VK_NULL_HANDLE;
    VkFence inFlight = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    std::vector<TransientBuffer> transientBuffers;
};

struct Context;

struct ShaderModule {
    Context* context = nullptr;
    VkShaderModule vertex = VK_NULL_HANDLE;
    VkShaderModule fragment = VK_NULL_HANDLE;
};

struct Pipeline {
    Context* context = nullptr;
    VkPipeline pipeline = VK_NULL_HANDLE;
    VkPipelineLayout layout = VK_NULL_HANDLE;
    VkDescriptorSetLayout textureDescriptorSetLayout = VK_NULL_HANDLE;
    VkDescriptorSetLayout uniformDescriptorSetLayout = VK_NULL_HANDLE;
    int sampledTextureCount = 0;
    bool uniformBufferEnabled = false;
    int uniformDescriptorSetIndex = 0;
};

struct Buffer {
    Context* context = nullptr;
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkDeviceSize size = 0;
    int usage = 0;
};

struct DepthAttachment {
    VkImage image = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkImageView imageView = VK_NULL_HANDLE;
};

struct Texture {
    Context* context = nullptr;
    VkImage image = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkImageView imageView = VK_NULL_HANDLE;
    VkSampler sampler = VK_NULL_HANDLE;
    VkFormat format = VK_FORMAT_R8G8B8A8_UNORM;
    VkImageLayout layout = VK_IMAGE_LAYOUT_UNDEFINED;
    uint32_t width = 1;
    uint32_t height = 1;
    bool sampled = false;
    bool renderAttachment = false;
    DepthAttachment depthAttachment;
    std::vector<std::pair<VkRenderPass, VkFramebuffer>> framebuffers;
};

struct TextureRenderPass {
    VkFormat colorFormat = VK_FORMAT_UNDEFINED;
    VkAttachmentLoadOp colorLoadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    VkAttachmentStoreOp colorStoreOp = VK_ATTACHMENT_STORE_OP_STORE;
    VkAttachmentLoadOp depthLoadOp = VK_ATTACHMENT_LOAD_OP_CLEAR;
    VkImageLayout initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    VkImageLayout finalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
    VkRenderPass renderPass = VK_NULL_HANDLE;
};

struct Context {
    ANativeWindow* window = nullptr;
    VkInstance instance = VK_NULL_HANDLE;
    VkSurfaceKHR surface = VK_NULL_HANDLE;
    VkPhysicalDevice physicalDevice = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;
    VkQueue graphicsQueue = VK_NULL_HANDLE;
    VkQueue presentQueue = VK_NULL_HANDLE;
    uint32_t graphicsQueueFamily = UINT32_MAX;
    uint32_t presentQueueFamily = UINT32_MAX;
    VkFormat surfaceFormat = VK_FORMAT_R8G8B8A8_UNORM;
    VkColorSpaceKHR colorSpace = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
    VkExtent2D extent = {1, 1};
    VkSwapchainKHR swapchain = VK_NULL_HANDLE;
    std::vector<VkImage> swapchainImages;
    std::vector<VkImageView> imageViews;
    std::vector<DepthAttachment> depthAttachments;
    std::vector<VkFramebuffer> framebuffers;
    VkRenderPass renderPasses[2][2][2] = {};
    std::vector<TextureRenderPass> textureRenderPasses;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    std::vector<FrameSync> frames;
    uint32_t frameIndex = 0;
    uint32_t imageIndex = 0;
    int requestedWidth = 1;
    int requestedHeight = 1;
    bool vSync = true;
    bool preferMailboxPresentMode = true;
    bool swapchainTransferSrcSupported = false;
    bool frameStarted = false;
    bool renderPassStarted = false;
    bool pendingResize = false;
    Texture* activeRenderTarget = nullptr;
    VkImageLayout activeRenderTargetFinalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
};

template <typename T>
T* ptr(jlong handle) {
    return reinterpret_cast<T*>(static_cast<intptr_t>(handle));
}

jlong handle(void* pointer) {
    return static_cast<jlong>(reinterpret_cast<intptr_t>(pointer));
}

constexpr int PBR_UNIFORM_BYTE_COUNT = 5088;
constexpr int MAX_FRAME_DESCRIPTOR_SETS = 1024;
constexpr int MAX_FRAME_SAMPLED_IMAGES = 4096;
constexpr int MAX_FRAME_UNIFORM_BUFFERS = 1024;
constexpr int MAX_TEXTURE_DESCRIPTOR_SLOTS = 16;
constexpr VkFormat DEPTH_FORMAT = VK_FORMAT_D32_SFLOAT;
constexpr uint64_t FRAME_FENCE_TIMEOUT_NS = 33000000ULL;
constexpr uint64_t FRAME_ACQUIRE_TIMEOUT_NS = 33000000ULL;
constexpr uint64_t READBACK_FENCE_TIMEOUT_NS = 250000000ULL;
constexpr uint64_t SINGLE_COMMAND_TIMEOUT_NS = 250000000ULL;

uint32_t findMemoryType(Context* context, uint32_t typeFilter, VkMemoryPropertyFlags properties);

void check(VkResult result, const char* message) {
    if (result != VK_SUCCESS) {
        throw std::runtime_error(std::string(message) + ": " + std::to_string(result));
    }
}

bool waitForFenceOrTimeout(Context* context, VkFence fence, uint64_t timeoutNs, const char* reason) {
    VkResult waitResult = vkWaitForFences(context->device, 1, &fence, VK_TRUE, timeoutNs);
    if (waitResult == VK_TIMEOUT) {
        LOGW("Android Vulkan timed out waiting for %s", reason);
        return false;
    }
    check(waitResult, "Could not wait for Android Vulkan in-flight fence");
    return true;
}

bool waitForActiveFramesOrTimeout(Context* context, uint64_t timeoutNs, const char* reason) {
    if (context == nullptr || context->device == VK_NULL_HANDLE || context->frames.empty()) {
        return true;
    }
    for (FrameSync& frame : context->frames) {
        if (frame.inFlight == VK_NULL_HANDLE) {
            continue;
        }
        if (!waitForFenceOrTimeout(context, frame.inFlight, timeoutNs, reason)) {
            return false;
        }
    }
    return true;
}

void ensureNoActiveFramesOrThrow(Context* context, uint64_t timeoutNs, const char* reason) {
    if (!waitForActiveFramesOrTimeout(context, timeoutNs, reason)) {
        throw std::runtime_error(std::string("Timed out while waiting for Android Vulkan ") + reason);
    }
}

void waitDeviceIdleBeforeDestroy(Context* context, const char* resourceName) {
    if (context == nullptr || context->device == VK_NULL_HANDLE) {
        return;
    }
    try {
        ensureNoActiveFramesOrThrow(context, FRAME_FENCE_TIMEOUT_NS, resourceName);
    } catch (const std::exception& error) {
        LOGW("Could not confirm Android Vulkan frame work completion before destroying %s",
                resourceName);
        LOGW("Android Vulkan destroy error: %s", error.what());
    }
}

void recreateSignaledFrameFence(Context* context, FrameSync& frame) {
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    VkFence replacement = VK_NULL_HANDLE;
    check(vkCreateFence(context->device, &fenceInfo, nullptr, &replacement),
            "Could not recreate Android Vulkan in-flight fence");
    if (frame.inFlight != VK_NULL_HANDLE) {
        vkDestroyFence(context->device, frame.inFlight, nullptr);
    }
    frame.inFlight = replacement;
}

VkApplicationInfo androidVulkanApplicationInfo() {
    VkApplicationInfo appInfo{};
    appInfo.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
    appInfo.pApplicationName = "libfdx Android";
    appInfo.applicationVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.pEngineName = "libfdx";
    appInfo.engineVersion = VK_MAKE_VERSION(1, 0, 0);
    appInfo.apiVersion = VK_API_VERSION_1_0;
    return appInfo;
}

const char* vkResultName(VkResult result) {
    switch (result) {
        case VK_SUCCESS:
            return "VK_SUCCESS";
        case VK_NOT_READY:
            return "VK_NOT_READY";
        case VK_TIMEOUT:
            return "VK_TIMEOUT";
        case VK_EVENT_SET:
            return "VK_EVENT_SET";
        case VK_EVENT_RESET:
            return "VK_EVENT_RESET";
        case VK_INCOMPLETE:
            return "VK_INCOMPLETE";
        case VK_ERROR_OUT_OF_HOST_MEMORY:
            return "VK_ERROR_OUT_OF_HOST_MEMORY";
        case VK_ERROR_OUT_OF_DEVICE_MEMORY:
            return "VK_ERROR_OUT_OF_DEVICE_MEMORY";
        case VK_ERROR_INITIALIZATION_FAILED:
            return "VK_ERROR_INITIALIZATION_FAILED";
        case VK_ERROR_DEVICE_LOST:
            return "VK_ERROR_DEVICE_LOST";
        case VK_ERROR_MEMORY_MAP_FAILED:
            return "VK_ERROR_MEMORY_MAP_FAILED";
        case VK_ERROR_LAYER_NOT_PRESENT:
            return "VK_ERROR_LAYER_NOT_PRESENT";
        case VK_ERROR_EXTENSION_NOT_PRESENT:
            return "VK_ERROR_EXTENSION_NOT_PRESENT";
        case VK_ERROR_FEATURE_NOT_PRESENT:
            return "VK_ERROR_FEATURE_NOT_PRESENT";
        case VK_ERROR_INCOMPATIBLE_DRIVER:
            return "VK_ERROR_INCOMPATIBLE_DRIVER";
        case VK_ERROR_TOO_MANY_OBJECTS:
            return "VK_ERROR_TOO_MANY_OBJECTS";
        case VK_ERROR_FORMAT_NOT_SUPPORTED:
            return "VK_ERROR_FORMAT_NOT_SUPPORTED";
        case VK_ERROR_SURFACE_LOST_KHR:
            return "VK_ERROR_SURFACE_LOST_KHR";
        case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR:
            return "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR";
        case VK_SUBOPTIMAL_KHR:
            return "VK_SUBOPTIMAL_KHR";
        case VK_ERROR_OUT_OF_DATE_KHR:
            return "VK_ERROR_OUT_OF_DATE_KHR";
        case VK_ERROR_INCOMPATIBLE_DISPLAY_KHR:
            return "VK_ERROR_INCOMPATIBLE_DISPLAY_KHR";
        default:
            return "VK_UNKNOWN_RESULT";
    }
}

void throwFdx(JNIEnv* env, const std::string& message) {
    LOGE("%s", message.c_str());
    jclass exceptionClass = env->FindClass("io/github/libfdx/core/FdxException");
    if (exceptionClass == nullptr) {
        env->ThrowNew(env->FindClass("java/lang/RuntimeException"), message.c_str());
        return;
    }
    env->ThrowNew(exceptionClass, message.c_str());
}

std::vector<const char*> requiredInstanceExtensions() {
    return {
            VK_KHR_SURFACE_EXTENSION_NAME,
            VK_KHR_ANDROID_SURFACE_EXTENSION_NAME
    };
}

bool containsExtension(const std::vector<VkExtensionProperties>& extensions, const char* name) {
    for (const VkExtensionProperties& extension : extensions) {
        if (std::string(extension.extensionName) == name) {
            return true;
        }
    }
    return false;
}

std::vector<VkExtensionProperties> enumerateInstanceExtensions() {
    uint32_t count = 0;
    VkResult result = vkEnumerateInstanceExtensionProperties(nullptr, &count, nullptr);
    if (result != VK_SUCCESS) {
        throw std::runtime_error(std::string("Could not enumerate Android Vulkan instance extension count: ")
                + vkResultName(result) + " (" + std::to_string(result) + ")");
    }
    std::vector<VkExtensionProperties> extensions(count);
    if (count > 0) {
        result = vkEnumerateInstanceExtensionProperties(nullptr, &count, extensions.data());
        if (result != VK_SUCCESS) {
            throw std::runtime_error(std::string("Could not enumerate Android Vulkan instance extensions: ")
                    + vkResultName(result) + " (" + std::to_string(result) + ")");
        }
    }
    return extensions;
}

std::string extensionNames(const std::vector<VkExtensionProperties>& extensions) {
    std::string names;
    for (const VkExtensionProperties& extension : extensions) {
        if (!names.empty()) {
            names += ", ";
        }
        names += extension.extensionName;
    }
    return names.empty() ? std::string("<none>") : names;
}

std::string requestedExtensionNames(const std::vector<const char*>& extensions) {
    std::string names;
    for (const char* extension : extensions) {
        if (!names.empty()) {
            names += ", ";
        }
        names += extension;
    }
    return names.empty() ? std::string("<none>") : names;
}

VkResult tryCreateDiagnosticInstance(const VkApplicationInfo& appInfo,
        const std::vector<const char*>& extensions) {
    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
    createInfo.ppEnabledExtensionNames = extensions.empty() ? nullptr : extensions.data();

    VkInstance instance = VK_NULL_HANDLE;
    VkResult result = vkCreateInstance(&createInfo, nullptr, &instance);
    if (result == VK_SUCCESS && instance != VK_NULL_HANDLE) {
        vkDestroyInstance(instance, nullptr);
    }
    return result;
}

void logInstanceCreateDiagnostics(const VkApplicationInfo& appInfo) {
    const std::vector<std::vector<const char*>> attempts = {
            {},
            {VK_KHR_SURFACE_EXTENSION_NAME},
            {VK_KHR_ANDROID_SURFACE_EXTENSION_NAME},
            {VK_KHR_SURFACE_EXTENSION_NAME, VK_KHR_ANDROID_SURFACE_EXTENSION_NAME}
    };
    for (const std::vector<const char*>& attempt : attempts) {
        VkResult result = tryCreateDiagnosticInstance(appInfo, attempt);
        LOGI("Android Vulkan diagnostic vkCreateInstance extensions=[%s] result=%s (%d)",
                requestedExtensionNames(attempt).c_str(), vkResultName(result), result);
    }
}

std::string probeInstanceFailure() {
    VkApplicationInfo appInfo = androidVulkanApplicationInfo();
    std::vector<const char*> requiredExtensions = requiredInstanceExtensions();
    std::vector<VkExtensionProperties> availableExtensions = enumerateInstanceExtensions();
    for (const char* extension : requiredExtensions) {
        if (!containsExtension(availableExtensions, extension)) {
            return std::string("Android Vulkan is not usable because required instance extension ")
                    + extension + " is missing. Available instance extensions: "
                    + extensionNames(availableExtensions);
        }
    }

    VkResult result = tryCreateDiagnosticInstance(appInfo, requiredExtensions);
    if (result == VK_SUCCESS) {
        return "";
    }

    VkResult noExtensionResult = tryCreateDiagnosticInstance(appInfo, {});
    return std::string("Android Vulkan is not usable on this device: vkCreateInstance failed with ")
            + "required extensions [" + requestedExtensionNames(requiredExtensions) + "]: "
            + vkResultName(result) + " (" + std::to_string(result) + "); zero-extension probe result: "
            + vkResultName(noExtensionResult) + " (" + std::to_string(noExtensionResult)
            + "). Available instance extensions: " + extensionNames(availableExtensions);
}

std::vector<VkExtensionProperties> enumerateDeviceExtensions(VkPhysicalDevice device) {
    uint32_t count = 0;
    check(vkEnumerateDeviceExtensionProperties(device, nullptr, &count, nullptr),
            "Could not enumerate Vulkan device extension count");
    std::vector<VkExtensionProperties> extensions(count);
    if (count > 0) {
        check(vkEnumerateDeviceExtensionProperties(device, nullptr, &count, extensions.data()),
                "Could not enumerate Vulkan device extensions");
    }
    return extensions;
}

bool supportsNegativeViewport(VkPhysicalDevice device) {
    VkPhysicalDeviceProperties properties;
    vkGetPhysicalDeviceProperties(device, &properties);
    if (VK_VERSION_MAJOR(properties.apiVersion) > 1
            || (VK_VERSION_MAJOR(properties.apiVersion) == 1 && VK_VERSION_MINOR(properties.apiVersion) >= 1)) {
        return true;
    }
    return containsExtension(enumerateDeviceExtensions(device), VK_KHR_MAINTENANCE1_EXTENSION_NAME);
}

QueueFamilies findQueueFamilies(VkPhysicalDevice device, VkSurfaceKHR surface) {
    QueueFamilies families;
    uint32_t count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, nullptr);
    std::vector<VkQueueFamilyProperties> properties(count);
    vkGetPhysicalDeviceQueueFamilyProperties(device, &count, properties.data());

    for (uint32_t i = 0; i < count; i++) {
        if ((properties[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) != 0) {
            families.graphicsFamily = i;
        }
        VkBool32 presentSupport = VK_FALSE;
        vkGetPhysicalDeviceSurfaceSupportKHR(device, i, surface, &presentSupport);
        if (presentSupport == VK_TRUE) {
            families.presentFamily = i;
        }
        if (families.complete()) {
            break;
        }
    }
    return families;
}

VkSurfaceFormatKHR chooseSurfaceFormat(const std::vector<VkSurfaceFormatKHR>& formats) {
    if (formats.empty()) {
        throw std::runtime_error("Android Vulkan surface did not report any supported formats");
    }
    if (formats.size() == 1 && formats[0].format == VK_FORMAT_UNDEFINED) {
        return {VK_FORMAT_R8G8B8A8_UNORM, VK_COLOR_SPACE_SRGB_NONLINEAR_KHR};
    }

    const VkFormat preferredFormats[] = {
            VK_FORMAT_R8G8B8A8_UNORM,
            VK_FORMAT_B8G8R8A8_UNORM,
            VK_FORMAT_R8G8B8A8_SRGB,
            VK_FORMAT_B8G8R8A8_SRGB
    };
    for (VkFormat preferred : preferredFormats) {
        for (const VkSurfaceFormatKHR& format : formats) {
            if (format.format == preferred && format.colorSpace == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                return format;
            }
        }
    }
    return formats[0];
}

VkPresentModeKHR choosePresentMode(VkPhysicalDevice device, VkSurfaceKHR surface, bool vSync,
        bool preferMailboxPresentMode) {
    uint32_t count = 0;
    check(vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, &count, nullptr),
            "Could not get Android Vulkan present mode count");
    std::vector<VkPresentModeKHR> modes(count);
    if (count > 0) {
        check(vkGetPhysicalDeviceSurfacePresentModesKHR(device, surface, &count, modes.data()),
                "Could not get Android Vulkan present modes");
    }

    if (vSync) {
        return VK_PRESENT_MODE_FIFO_KHR;
    }
    if (preferMailboxPresentMode
            && std::find(modes.begin(), modes.end(), VK_PRESENT_MODE_MAILBOX_KHR) != modes.end()) {
        return VK_PRESENT_MODE_MAILBOX_KHR;
    }
    if (std::find(modes.begin(), modes.end(), VK_PRESENT_MODE_IMMEDIATE_KHR) != modes.end()) {
        return VK_PRESENT_MODE_IMMEDIATE_KHR;
    }
    return VK_PRESENT_MODE_FIFO_KHR;
}

VkExtent2D chooseExtent(const VkSurfaceCapabilitiesKHR& capabilities, int requestedWidth, int requestedHeight) {
    if (capabilities.currentExtent.width != UINT32_MAX) {
        return capabilities.currentExtent;
    }
    VkExtent2D extent = {
            static_cast<uint32_t>(std::max(1, requestedWidth)),
            static_cast<uint32_t>(std::max(1, requestedHeight))
    };
    extent.width = std::max(capabilities.minImageExtent.width,
            std::min(capabilities.maxImageExtent.width, extent.width));
    extent.height = std::max(capabilities.minImageExtent.height,
            std::min(capabilities.maxImageExtent.height, extent.height));
    return extent;
}

VkCompositeAlphaFlagBitsKHR chooseCompositeAlpha(VkSurfaceCapabilitiesKHR capabilities) {
    const VkCompositeAlphaFlagBitsKHR options[] = {
            VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR,
            VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR,
            VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR
    };
    for (VkCompositeAlphaFlagBitsKHR option : options) {
        if ((capabilities.supportedCompositeAlpha & option) != 0) {
            return option;
        }
    }
    return VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
}

VkSurfaceTransformFlagBitsKHR choosePreTransform(const VkSurfaceCapabilitiesKHR& capabilities) {
    if ((capabilities.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR) != 0) {
        return VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    }
    return capabilities.currentTransform;
}

VkRenderPass createRenderPass(Context* context, VkFormat colorFormat, VkAttachmentLoadOp colorLoadOp,
        VkAttachmentStoreOp colorStoreOp, VkAttachmentLoadOp depthLoadOp, VkImageLayout colorInitialLayout,
        VkImageLayout colorFinalLayout) {
    VkAttachmentDescription attachments[2]{};
    attachments[0].format = colorFormat;
    attachments[0].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[0].loadOp = colorLoadOp;
    attachments[0].storeOp = colorStoreOp;
    attachments[0].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachments[0].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[0].initialLayout = colorInitialLayout;
    attachments[0].finalLayout = colorFinalLayout;

    attachments[1].format = DEPTH_FORMAT;
    attachments[1].samples = VK_SAMPLE_COUNT_1_BIT;
    attachments[1].loadOp = depthLoadOp;
    attachments[1].storeOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].stencilLoadOp = VK_ATTACHMENT_LOAD_OP_DONT_CARE;
    attachments[1].stencilStoreOp = VK_ATTACHMENT_STORE_OP_DONT_CARE;
    attachments[1].initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    attachments[1].finalLayout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

    VkAttachmentReference colorAttachmentRef{};
    colorAttachmentRef.attachment = 0;
    colorAttachmentRef.layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;

    VkAttachmentReference depthAttachmentRef{};
    depthAttachmentRef.attachment = 1;
    depthAttachmentRef.layout = VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;

    VkSubpassDescription subpass{};
    subpass.pipelineBindPoint = VK_PIPELINE_BIND_POINT_GRAPHICS;
    subpass.colorAttachmentCount = 1;
    subpass.pColorAttachments = &colorAttachmentRef;
    subpass.pDepthStencilAttachment = &depthAttachmentRef;

    VkSubpassDependency dependency{};
    dependency.srcSubpass = VK_SUBPASS_EXTERNAL;
    dependency.dstSubpass = 0;
    dependency.srcStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    dependency.dstStageMask = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
            | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT;
    dependency.dstAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
            | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;

    VkRenderPassCreateInfo renderPassInfo{};
    renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO;
    renderPassInfo.attachmentCount = 2;
    renderPassInfo.pAttachments = attachments;
    renderPassInfo.subpassCount = 1;
    renderPassInfo.pSubpasses = &subpass;
    renderPassInfo.dependencyCount = 1;
    renderPassInfo.pDependencies = &dependency;

    VkRenderPass renderPass = VK_NULL_HANDLE;
    check(vkCreateRenderPass(context->device, &renderPassInfo, nullptr, &renderPass),
            "Could not create Android Vulkan render pass");
    return renderPass;
}

DepthAttachment createDepthAttachment(Context* context, uint32_t width, uint32_t height) {
    DepthAttachment depth{};

    VkImageCreateInfo imageInfo{};
    imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
    imageInfo.imageType = VK_IMAGE_TYPE_2D;
    imageInfo.extent = {width, height, 1};
    imageInfo.mipLevels = 1;
    imageInfo.arrayLayers = 1;
    imageInfo.format = DEPTH_FORMAT;
    imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
    imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
    imageInfo.usage = VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT;
    imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
    imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    check(vkCreateImage(context->device, &imageInfo, nullptr, &depth.image),
            "Could not create Android Vulkan depth image");

    VkMemoryRequirements memoryRequirements;
    vkGetImageMemoryRequirements(context->device, depth.image, &memoryRequirements);

    VkMemoryAllocateInfo allocationInfo{};
    allocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocationInfo.allocationSize = memoryRequirements.size;
    allocationInfo.memoryTypeIndex = findMemoryType(context, memoryRequirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

    try {
        check(vkAllocateMemory(context->device, &allocationInfo, nullptr, &depth.memory),
                "Could not allocate Android Vulkan depth memory");
        check(vkBindImageMemory(context->device, depth.image, depth.memory, 0),
                "Could not bind Android Vulkan depth memory");

        VkImageViewCreateInfo viewInfo{};
        viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        viewInfo.image = depth.image;
        viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        viewInfo.format = DEPTH_FORMAT;
        viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_DEPTH_BIT;
        viewInfo.subresourceRange.baseMipLevel = 0;
        viewInfo.subresourceRange.levelCount = 1;
        viewInfo.subresourceRange.baseArrayLayer = 0;
        viewInfo.subresourceRange.layerCount = 1;
        check(vkCreateImageView(context->device, &viewInfo, nullptr, &depth.imageView),
                "Could not create Android Vulkan depth image view");
        return depth;
    } catch (...) {
        if (depth.imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(context->device, depth.imageView, nullptr);
        }
        if (depth.memory != VK_NULL_HANDLE) {
            vkFreeMemory(context->device, depth.memory, nullptr);
        }
        if (depth.image != VK_NULL_HANDLE) {
            vkDestroyImage(context->device, depth.image, nullptr);
        }
        throw;
    }
}

void destroyDepthAttachment(Context* context, DepthAttachment* depth) {
    if (depth == nullptr) {
        return;
    }
    if (depth->imageView != VK_NULL_HANDLE) {
        vkDestroyImageView(context->device, depth->imageView, nullptr);
        depth->imageView = VK_NULL_HANDLE;
    }
    if (depth->image != VK_NULL_HANDLE) {
        vkDestroyImage(context->device, depth->image, nullptr);
        depth->image = VK_NULL_HANDLE;
    }
    if (depth->memory != VK_NULL_HANDLE) {
        vkFreeMemory(context->device, depth->memory, nullptr);
        depth->memory = VK_NULL_HANDLE;
    }
}

void destroySwapchainResources(Context* context) {
    if (context->device == VK_NULL_HANDLE) {
        return;
    }
    for (VkFramebuffer framebuffer : context->framebuffers) {
        vkDestroyFramebuffer(context->device, framebuffer, nullptr);
    }
    context->framebuffers.clear();

    for (VkImageView imageView : context->imageViews) {
        vkDestroyImageView(context->device, imageView, nullptr);
    }
    context->imageViews.clear();

    for (DepthAttachment& depth : context->depthAttachments) {
        destroyDepthAttachment(context, &depth);
    }
    context->depthAttachments.clear();
    context->swapchainImages.clear();

    for (int clear = 0; clear < 2; clear++) {
        for (int store = 0; store < 2; store++) {
            for (int depthClear = 0; depthClear < 2; depthClear++) {
                if (context->renderPasses[clear][store][depthClear] != VK_NULL_HANDLE) {
                    vkDestroyRenderPass(context->device, context->renderPasses[clear][store][depthClear],
                            nullptr);
                    context->renderPasses[clear][store][depthClear] = VK_NULL_HANDLE;
                }
            }
        }
    }
    if (context->swapchain != VK_NULL_HANDLE) {
        vkDestroySwapchainKHR(context->device, context->swapchain, nullptr);
        context->swapchain = VK_NULL_HANDLE;
    }
}

void createSwapchain(Context* context) {
    ensureNoActiveFramesOrThrow(context, FRAME_FENCE_TIMEOUT_NS, "Android Vulkan swapchain recreation");
    destroySwapchainResources(context);

    VkSurfaceCapabilitiesKHR capabilities{};
    check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(context->physicalDevice, context->surface, &capabilities),
            "Could not get Android Vulkan surface capabilities");

    uint32_t formatCount = 0;
    check(vkGetPhysicalDeviceSurfaceFormatsKHR(context->physicalDevice, context->surface, &formatCount, nullptr),
            "Could not get Android Vulkan surface format count");
    std::vector<VkSurfaceFormatKHR> formats(formatCount);
    if (formatCount > 0) {
        check(vkGetPhysicalDeviceSurfaceFormatsKHR(context->physicalDevice, context->surface, &formatCount,
                formats.data()), "Could not get Android Vulkan surface formats");
    }

    VkSurfaceFormatKHR format = chooseSurfaceFormat(formats);
    context->surfaceFormat = format.format;
    context->colorSpace = format.colorSpace;
    context->extent = chooseExtent(capabilities, context->requestedWidth, context->requestedHeight);
    VkPresentModeKHR presentMode = choosePresentMode(context->physicalDevice, context->surface, context->vSync,
            context->preferMailboxPresentMode);
    VkSurfaceTransformFlagBitsKHR preTransform = choosePreTransform(capabilities);
    LOGI("Android Vulkan swapchain selection: format=%d colorSpace=%d extent=%ux%u presentMode=%d minImages=%u maxImages=%u currentTransform=%u preTransform=%u",
            context->surfaceFormat, context->colorSpace, context->extent.width, context->extent.height,
            presentMode, capabilities.minImageCount, capabilities.maxImageCount, capabilities.currentTransform,
            preTransform);

    uint32_t imageCount = capabilities.minImageCount + 1;
    if (capabilities.maxImageCount > 0 && imageCount > capabilities.maxImageCount) {
        imageCount = capabilities.maxImageCount;
    }

    context->swapchainTransferSrcSupported =
            (capabilities.supportedUsageFlags & VK_IMAGE_USAGE_TRANSFER_SRC_BIT) != 0;
    VkImageUsageFlags imageUsage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
    if (context->swapchainTransferSrcSupported) {
        imageUsage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
    }

    VkSwapchainCreateInfoKHR createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
    createInfo.surface = context->surface;
    createInfo.minImageCount = imageCount;
    createInfo.imageFormat = context->surfaceFormat;
    createInfo.imageColorSpace = context->colorSpace;
    createInfo.imageExtent = context->extent;
    createInfo.imageArrayLayers = 1;
    createInfo.imageUsage = imageUsage;

    uint32_t queueFamilyIndices[] = {context->graphicsQueueFamily, context->presentQueueFamily};
    if (context->graphicsQueueFamily != context->presentQueueFamily) {
        createInfo.imageSharingMode = VK_SHARING_MODE_CONCURRENT;
        createInfo.queueFamilyIndexCount = 2;
        createInfo.pQueueFamilyIndices = queueFamilyIndices;
    } else {
        createInfo.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    }

    createInfo.preTransform = preTransform;
    createInfo.compositeAlpha = chooseCompositeAlpha(capabilities);
    createInfo.presentMode = presentMode;
    createInfo.clipped = VK_TRUE;
    createInfo.oldSwapchain = VK_NULL_HANDLE;

    check(vkCreateSwapchainKHR(context->device, &createInfo, nullptr, &context->swapchain),
            "Could not create Android Vulkan swapchain");

    uint32_t swapchainImageCount = 0;
    check(vkGetSwapchainImagesKHR(context->device, context->swapchain, &swapchainImageCount, nullptr),
            "Could not get Android Vulkan swapchain image count");
    LOGI("Android Vulkan swapchain created with %u images", swapchainImageCount);
    context->swapchainImages.resize(swapchainImageCount);
    check(vkGetSwapchainImagesKHR(context->device, context->swapchain, &swapchainImageCount,
            context->swapchainImages.data()), "Could not get Android Vulkan swapchain images");

    context->imageViews.resize(context->swapchainImages.size());
    for (size_t i = 0; i < context->swapchainImages.size(); i++) {
        VkImageViewCreateInfo imageViewInfo{};
        imageViewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
        imageViewInfo.image = context->swapchainImages[i];
        imageViewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
        imageViewInfo.format = context->surfaceFormat;
        imageViewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        imageViewInfo.subresourceRange.baseMipLevel = 0;
        imageViewInfo.subresourceRange.levelCount = 1;
        imageViewInfo.subresourceRange.baseArrayLayer = 0;
        imageViewInfo.subresourceRange.layerCount = 1;
        check(vkCreateImageView(context->device, &imageViewInfo, nullptr, &context->imageViews[i]),
                "Could not create Android Vulkan swapchain image view");
    }

    for (int clear = 0; clear < 2; clear++) {
        for (int store = 0; store < 2; store++) {
            for (int depthClear = 0; depthClear < 2; depthClear++) {
                context->renderPasses[clear][store][depthClear] = createRenderPass(context,
                        context->surfaceFormat,
                        clear != 0 ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD,
                        store != 0 ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE,
                        depthClear != 0 ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD,
                        clear != 0 ? VK_IMAGE_LAYOUT_UNDEFINED : VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                        VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
            }
        }
    }

    context->depthAttachments.resize(context->imageViews.size());
    for (size_t i = 0; i < context->depthAttachments.size(); i++) {
        context->depthAttachments[i] = createDepthAttachment(context, context->extent.width,
                context->extent.height);
    }

    context->framebuffers.resize(context->imageViews.size());
    for (size_t i = 0; i < context->imageViews.size(); i++) {
        VkImageView attachments[] = {context->imageViews[i], context->depthAttachments[i].imageView};
        VkFramebufferCreateInfo framebufferInfo{};
        framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
        framebufferInfo.renderPass = context->renderPasses[1][1][1];
        framebufferInfo.attachmentCount = 2;
        framebufferInfo.pAttachments = attachments;
        framebufferInfo.width = context->extent.width;
        framebufferInfo.height = context->extent.height;
        framebufferInfo.layers = 1;
        check(vkCreateFramebuffer(context->device, &framebufferInfo, nullptr, &context->framebuffers[i]),
                "Could not create Android Vulkan framebuffer");
    }
    context->pendingResize = false;
}

void recoverFailedFrame(Context* context, const char* operation) {
    if (context == nullptr) {
        return;
    }
    context->frameStarted = false;
    context->renderPassStarted = false;
    if (context->device == VK_NULL_HANDLE || context->swapchain == VK_NULL_HANDLE) {
        return;
    }
    try {
        ensureNoActiveFramesOrThrow(context, FRAME_FENCE_TIMEOUT_NS, "Android Vulkan frame recovery");
    } catch (const std::exception& error) {
        LOGW("Could not confirm Android Vulkan frame work completion after failed %s: %s",
                operation, error.what());
        return;
    }
    if (!context->frames.empty()) {
        FrameSync& frame = context->frames[context->frameIndex % context->frames.size()];
        VkResult resetResult = vkResetCommandBuffer(frame.commandBuffer, 0);
        if (resetResult != VK_SUCCESS) {
            LOGW("Could not reset Android Vulkan command buffer after failed %s: %d",
                    operation, resetResult);
        }
    }
    try {
        createSwapchain(context);
    } catch (const std::exception& error) {
        LOGW("Could not recreate Android Vulkan swapchain after failed %s: %s",
                operation, error.what());
    }
}

void createInstance(Context* context) {
    VkApplicationInfo appInfo = androidVulkanApplicationInfo();

    std::vector<const char*> extensions = requiredInstanceExtensions();
    std::vector<VkExtensionProperties> availableExtensions = enumerateInstanceExtensions();
    LOGI("Android Vulkan available instance extension count: %zu", availableExtensions.size());
    LOGI("Android Vulkan requested instance extensions: %s",
            requestedExtensionNames(extensions).c_str());
    for (const char* extension : extensions) {
        if (!containsExtension(availableExtensions, extension)) {
            throw std::runtime_error(std::string("Required Android Vulkan instance extension is missing: ")
                    + extension + ". Available extensions: " + extensionNames(availableExtensions));
        }
    }

    VkInstanceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
    createInfo.pApplicationInfo = &appInfo;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
    createInfo.ppEnabledExtensionNames = extensions.data();

    VkResult result = vkCreateInstance(&createInfo, nullptr, &context->instance);
    if (result != VK_SUCCESS) {
        logInstanceCreateDiagnostics(appInfo);
        throw std::runtime_error(std::string("Could not create Android Vulkan instance with extensions [")
                + requestedExtensionNames(extensions) + "]: " + vkResultName(result) + " ("
                + std::to_string(result) + ")");
    }
    LOGI("Android Vulkan instance created");
}

void createSurface(Context* context) {
    VkAndroidSurfaceCreateInfoKHR surfaceInfo{};
    surfaceInfo.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
    surfaceInfo.window = context->window;
    check(vkCreateAndroidSurfaceKHR(context->instance, &surfaceInfo, nullptr, &context->surface),
            "Could not create Android Vulkan surface");
    LOGI("Android Vulkan surface created");
}

void pickPhysicalDevice(Context* context) {
    uint32_t count = 0;
    check(vkEnumeratePhysicalDevices(context->instance, &count, nullptr),
            "Could not enumerate Android Vulkan physical device count");
    if (count == 0) {
        throw std::runtime_error("No Android Vulkan physical devices are available");
    }
    LOGI("Android Vulkan physical devices available: %u", count);
    std::vector<VkPhysicalDevice> devices(count);
    check(vkEnumeratePhysicalDevices(context->instance, &count, devices.data()),
            "Could not enumerate Android Vulkan physical devices");

    for (VkPhysicalDevice device : devices) {
        VkPhysicalDeviceProperties properties;
        vkGetPhysicalDeviceProperties(device, &properties);
        LOGI("Android Vulkan device candidate: %s api=%u.%u.%u driver=%u",
                properties.deviceName,
                VK_VERSION_MAJOR(properties.apiVersion),
                VK_VERSION_MINOR(properties.apiVersion),
                VK_VERSION_PATCH(properties.apiVersion),
                properties.driverVersion);
        QueueFamilies families = findQueueFamilies(device, context->surface);
        if (!families.complete()) {
            LOGW("Skipping Android Vulkan device %s: missing graphics/present queue family",
                    properties.deviceName);
            continue;
        }
        LOGI("Android Vulkan device %s queue families: graphics=%u present=%u",
                properties.deviceName, families.graphicsFamily, families.presentFamily);
        std::vector<VkExtensionProperties> extensions = enumerateDeviceExtensions(device);
        LOGI("Android Vulkan device extension count for %s: %zu",
                properties.deviceName, extensions.size());
        if (!containsExtension(extensions, VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
            LOGW("Skipping Android Vulkan device %s: missing %s",
                    properties.deviceName, VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            continue;
        }
        if (!supportsNegativeViewport(device)) {
            LOGW("Skipping Android Vulkan device %s: Vulkan 1.1 or %s required for negative viewport",
                    properties.deviceName, VK_KHR_MAINTENANCE1_EXTENSION_NAME);
            continue;
        }
        context->physicalDevice = device;
        context->graphicsQueueFamily = families.graphicsFamily;
        context->presentQueueFamily = families.presentFamily;
        LOGI("Android Vulkan selected physical device: %s", properties.deviceName);
        return;
    }
    throw std::runtime_error("No Android Vulkan device supports graphics, presentation, swapchain, and negative viewport");
}

void createDevice(Context* context) {
    float queuePriority = 1.0f;
    std::vector<VkDeviceQueueCreateInfo> queueCreateInfos;
    VkDeviceQueueCreateInfo graphicsQueueInfo{};
    graphicsQueueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
    graphicsQueueInfo.queueFamilyIndex = context->graphicsQueueFamily;
    graphicsQueueInfo.queueCount = 1;
    graphicsQueueInfo.pQueuePriorities = &queuePriority;
    queueCreateInfos.push_back(graphicsQueueInfo);

    if (context->presentQueueFamily != context->graphicsQueueFamily) {
        VkDeviceQueueCreateInfo presentQueueInfo{};
        presentQueueInfo.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        presentQueueInfo.queueFamilyIndex = context->presentQueueFamily;
        presentQueueInfo.queueCount = 1;
        presentQueueInfo.pQueuePriorities = &queuePriority;
        queueCreateInfos.push_back(presentQueueInfo);
    }

    std::vector<const char*> extensions = {VK_KHR_SWAPCHAIN_EXTENSION_NAME};
    VkPhysicalDeviceProperties properties;
    vkGetPhysicalDeviceProperties(context->physicalDevice, &properties);
    bool deviceIsVulkan11 = VK_VERSION_MAJOR(properties.apiVersion) > 1
            || (VK_VERSION_MAJOR(properties.apiVersion) == 1 && VK_VERSION_MINOR(properties.apiVersion) >= 1);
    if (!deviceIsVulkan11) {
        extensions.push_back(VK_KHR_MAINTENANCE1_EXTENSION_NAME);
    }
    LOGI("Android Vulkan requested device extensions: %s", requestedExtensionNames(extensions).c_str());

    VkPhysicalDeviceFeatures features{};
    VkDeviceCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
    createInfo.queueCreateInfoCount = static_cast<uint32_t>(queueCreateInfos.size());
    createInfo.pQueueCreateInfos = queueCreateInfos.data();
    createInfo.pEnabledFeatures = &features;
    createInfo.enabledExtensionCount = static_cast<uint32_t>(extensions.size());
    createInfo.ppEnabledExtensionNames = extensions.data();

    VkResult result = vkCreateDevice(context->physicalDevice, &createInfo, nullptr, &context->device);
    if (result != VK_SUCCESS) {
        throw std::runtime_error(std::string("Could not create Android Vulkan logical device with extensions [")
                + requestedExtensionNames(extensions) + "]: " + vkResultName(result) + " ("
                + std::to_string(result) + ")");
    }
    vkGetDeviceQueue(context->device, context->graphicsQueueFamily, 0, &context->graphicsQueue);
    vkGetDeviceQueue(context->device, context->presentQueueFamily, 0, &context->presentQueue);
    LOGI("Android Vulkan logical device created");
}

VkDescriptorPool createFrameDescriptorPool(Context* context) {
    VkDescriptorPoolSize poolSizes[2]{};
    poolSizes[0].type = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
    poolSizes[0].descriptorCount = MAX_FRAME_SAMPLED_IMAGES;
    poolSizes[1].type = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    poolSizes[1].descriptorCount = MAX_FRAME_UNIFORM_BUFFERS;

    VkDescriptorPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO;
    poolInfo.maxSets = MAX_FRAME_DESCRIPTOR_SETS;
    poolInfo.poolSizeCount = 2;
    poolInfo.pPoolSizes = poolSizes;

    VkDescriptorPool descriptorPool = VK_NULL_HANDLE;
    check(vkCreateDescriptorPool(context->device, &poolInfo, nullptr, &descriptorPool),
            "Could not create Android Vulkan frame descriptor pool");
    return descriptorPool;
}

void destroyTransientBuffers(Context* context, FrameSync& frame) {
    for (TransientBuffer& allocation : frame.transientBuffers) {
        if (allocation.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(context->device, allocation.buffer, nullptr);
        }
        if (allocation.memory != VK_NULL_HANDLE) {
            vkFreeMemory(context->device, allocation.memory, nullptr);
        }
    }
    frame.transientBuffers.clear();
}

void createCommandResources(Context* context, int framesInFlight) {
    VkCommandPoolCreateInfo poolInfo{};
    poolInfo.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
    poolInfo.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    poolInfo.queueFamilyIndex = context->graphicsQueueFamily;
    check(vkCreateCommandPool(context->device, &poolInfo, nullptr, &context->commandPool),
            "Could not create Android Vulkan command pool");

    int actualFramesInFlight = std::max(1, std::min(3, framesInFlight));
    context->frames.resize(static_cast<size_t>(actualFramesInFlight));

    std::vector<VkCommandBuffer> commandBuffers(context->frames.size());
    VkCommandBufferAllocateInfo allocateInfo{};
    allocateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocateInfo.commandPool = context->commandPool;
    allocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocateInfo.commandBufferCount = static_cast<uint32_t>(commandBuffers.size());
    check(vkAllocateCommandBuffers(context->device, &allocateInfo, commandBuffers.data()),
            "Could not allocate Android Vulkan command buffers");

    VkSemaphoreCreateInfo semaphoreInfo{};
    semaphoreInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.flags = VK_FENCE_CREATE_SIGNALED_BIT;

    for (size_t i = 0; i < context->frames.size(); i++) {
        context->frames[i].commandBuffer = commandBuffers[i];
        check(vkCreateSemaphore(context->device, &semaphoreInfo, nullptr, &context->frames[i].imageAvailable),
                "Could not create Android Vulkan image-available semaphore");
        check(vkCreateSemaphore(context->device, &semaphoreInfo, nullptr, &context->frames[i].renderFinished),
                "Could not create Android Vulkan render-finished semaphore");
        check(vkCreateFence(context->device, &fenceInfo, nullptr, &context->frames[i].inFlight),
                "Could not create Android Vulkan in-flight fence");
        context->frames[i].descriptorPool = createFrameDescriptorPool(context);
    }
}

void destroyTextureRenderPasses(Context* context);

void destroyContext(Context* context) {
    if (context == nullptr) {
        return;
    }
    if (context->device != VK_NULL_HANDLE) {
        ensureNoActiveFramesOrThrow(context, FRAME_FENCE_TIMEOUT_NS, "Android Vulkan context destroy");
        destroySwapchainResources(context);
        destroyTextureRenderPasses(context);
        for (FrameSync& frame : context->frames) {
            destroyTransientBuffers(context, frame);
            if (frame.descriptorPool != VK_NULL_HANDLE) {
                vkDestroyDescriptorPool(context->device, frame.descriptorPool, nullptr);
            }
            if (frame.imageAvailable != VK_NULL_HANDLE) {
                vkDestroySemaphore(context->device, frame.imageAvailable, nullptr);
            }
            if (frame.renderFinished != VK_NULL_HANDLE) {
                vkDestroySemaphore(context->device, frame.renderFinished, nullptr);
            }
            if (frame.inFlight != VK_NULL_HANDLE) {
                vkDestroyFence(context->device, frame.inFlight, nullptr);
            }
        }
        if (context->commandPool != VK_NULL_HANDLE) {
            vkDestroyCommandPool(context->device, context->commandPool, nullptr);
        }
        vkDestroyDevice(context->device, nullptr);
    }
    if (context->surface != VK_NULL_HANDLE && context->instance != VK_NULL_HANDLE) {
        vkDestroySurfaceKHR(context->instance, context->surface, nullptr);
    }
    if (context->instance != VK_NULL_HANDLE) {
        vkDestroyInstance(context->instance, nullptr);
    }
    if (context->window != nullptr) {
        ANativeWindow_release(context->window);
    }
    delete context;
}

VkRenderPass selectRenderPass(Context* context, bool clear, bool store, bool depthClear) {
    return context->renderPasses[clear ? 1 : 0][store ? 1 : 0][depthClear ? 1 : 0];
}

VkRenderPass textureRenderPass(Context* context, VkFormat colorFormat, VkAttachmentLoadOp colorLoadOp,
        VkAttachmentStoreOp colorStoreOp, VkAttachmentLoadOp depthLoadOp, VkImageLayout initialLayout,
        VkImageLayout finalLayout) {
    for (TextureRenderPass& candidate : context->textureRenderPasses) {
        if (candidate.colorFormat == colorFormat
                && candidate.colorLoadOp == colorLoadOp
                && candidate.colorStoreOp == colorStoreOp
                && candidate.depthLoadOp == depthLoadOp
                && candidate.initialLayout == initialLayout
                && candidate.finalLayout == finalLayout) {
            return candidate.renderPass;
        }
    }

    TextureRenderPass created{};
    created.colorFormat = colorFormat;
    created.colorLoadOp = colorLoadOp;
    created.colorStoreOp = colorStoreOp;
    created.depthLoadOp = depthLoadOp;
    created.initialLayout = initialLayout;
    created.finalLayout = finalLayout;
    created.renderPass = createRenderPass(context, colorFormat, colorLoadOp, colorStoreOp, depthLoadOp,
            initialLayout, finalLayout);
    context->textureRenderPasses.push_back(created);
    return created.renderPass;
}

VkRenderPass selectTextureRenderPass(Context* context, Texture* texture, bool clear, bool store, bool depthClear,
        VkImageLayout finalLayout) {
    VkAttachmentLoadOp colorLoadOp = clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
    VkAttachmentStoreOp colorStoreOp = store ? VK_ATTACHMENT_STORE_OP_STORE : VK_ATTACHMENT_STORE_OP_DONT_CARE;
    VkAttachmentLoadOp depthLoadOp = depthClear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
    VkImageLayout initialLayout = clear ? VK_IMAGE_LAYOUT_UNDEFINED : texture->layout;
    return textureRenderPass(context, texture->format, colorLoadOp, colorStoreOp, depthLoadOp, initialLayout,
            finalLayout);
}

VkRenderPass pipelineRenderPass(Context* context, VkFormat colorFormat) {
    if (colorFormat == context->surfaceFormat) {
        return context->renderPasses[1][1][1];
    }
    return textureRenderPass(context, colorFormat, VK_ATTACHMENT_LOAD_OP_CLEAR, VK_ATTACHMENT_STORE_OP_STORE,
            VK_ATTACHMENT_LOAD_OP_CLEAR, VK_IMAGE_LAYOUT_UNDEFINED,
            VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
}

VkFramebuffer textureFramebuffer(Context* context, Texture* texture, VkRenderPass renderPass) {
    if (texture == nullptr || !texture->renderAttachment) {
        throw std::runtime_error("Android Vulkan texture is not a render attachment");
    }
    for (const auto& entry : texture->framebuffers) {
        if (entry.first == renderPass) {
            return entry.second;
        }
    }
    if (texture->depthAttachment.imageView == VK_NULL_HANDLE) {
        texture->depthAttachment = createDepthAttachment(context, texture->width, texture->height);
    }
    VkImageView attachments[] = {texture->imageView, texture->depthAttachment.imageView};
    VkFramebufferCreateInfo framebufferInfo{};
    framebufferInfo.sType = VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO;
    framebufferInfo.renderPass = renderPass;
    framebufferInfo.attachmentCount = 2;
    framebufferInfo.pAttachments = attachments;
    framebufferInfo.width = texture->width;
    framebufferInfo.height = texture->height;
    framebufferInfo.layers = 1;

    VkFramebuffer framebuffer = VK_NULL_HANDLE;
    check(vkCreateFramebuffer(context->device, &framebufferInfo, nullptr, &framebuffer),
            "Could not create Android Vulkan texture framebuffer");
    texture->framebuffers.push_back(std::make_pair(renderPass, framebuffer));
    return framebuffer;
}

void destroyTextureRenderPasses(Context* context) {
    for (TextureRenderPass& renderPass : context->textureRenderPasses) {
        if (renderPass.renderPass != VK_NULL_HANDLE) {
            vkDestroyRenderPass(context->device, renderPass.renderPass, nullptr);
            renderPass.renderPass = VK_NULL_HANDLE;
        }
    }
    context->textureRenderPasses.clear();
}

FrameSync& currentFrame(Context* context) {
    return context->frames[context->frameIndex % context->frames.size()];
}

uint32_t findMemoryType(Context* context, uint32_t typeFilter, VkMemoryPropertyFlags properties) {
    VkPhysicalDeviceMemoryProperties memoryProperties;
    vkGetPhysicalDeviceMemoryProperties(context->physicalDevice, &memoryProperties);
    for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; i++) {
        if ((typeFilter & (1u << i)) != 0
                && (memoryProperties.memoryTypes[i].propertyFlags & properties) == properties) {
            return i;
        }
    }
    throw std::runtime_error("Could not find suitable Android Vulkan memory type");
}

struct ReadbackBuffer {
    VkBuffer buffer = VK_NULL_HANDLE;
    VkDeviceMemory memory = VK_NULL_HANDLE;
    VkDeviceSize size = 0;
};

ReadbackBuffer createReadbackBuffer(Context* context, VkDeviceSize size) {
    ReadbackBuffer readback{};
    readback.size = size;

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = VK_BUFFER_USAGE_TRANSFER_DST_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    check(vkCreateBuffer(context->device, &bufferInfo, nullptr, &readback.buffer),
            "Could not create Android Vulkan readback buffer");

    VkMemoryRequirements memoryRequirements;
    vkGetBufferMemoryRequirements(context->device, readback.buffer, &memoryRequirements);

    VkMemoryAllocateInfo allocationInfo{};
    allocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocationInfo.allocationSize = memoryRequirements.size;
    allocationInfo.memoryTypeIndex = findMemoryType(context, memoryRequirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
    try {
        check(vkAllocateMemory(context->device, &allocationInfo, nullptr, &readback.memory),
                "Could not allocate Android Vulkan readback memory");
        check(vkBindBufferMemory(context->device, readback.buffer, readback.memory, 0),
                "Could not bind Android Vulkan readback memory");
        return readback;
    } catch (...) {
        if (readback.memory != VK_NULL_HANDLE) {
            vkFreeMemory(context->device, readback.memory, nullptr);
        }
        if (readback.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(context->device, readback.buffer, nullptr);
        }
        throw;
    }
}

void destroyReadbackBuffer(Context* context, ReadbackBuffer* readback) {
    if (readback->buffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(context->device, readback->buffer, nullptr);
        readback->buffer = VK_NULL_HANDLE;
    }
    if (readback->memory != VK_NULL_HANDLE) {
        vkFreeMemory(context->device, readback->memory, nullptr);
        readback->memory = VK_NULL_HANDLE;
    }
}

void transitionSwapchainImageLayout(VkCommandBuffer commandBuffer, VkImage image,
        VkImageLayout oldLayout, VkImageLayout newLayout) {
    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = oldLayout;
    barrier.newLayout = newLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = image;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount = 1;

    VkPipelineStageFlags sourceStage;
    VkPipelineStageFlags destinationStage;
    if (oldLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR && newLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        sourceStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL && newLayout == VK_IMAGE_LAYOUT_PRESENT_SRC_KHR) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_READ_BIT;
        barrier.dstAccessMask = 0;
        sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        destinationStage = VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT;
    } else {
        throw std::runtime_error("Unsupported Android Vulkan swapchain layout transition");
    }

    vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0,
            0, nullptr, 0, nullptr, 1, &barrier);
}

void recordReadbackCopy(Context* context, ReadbackBuffer* readback) {
    VkCommandBuffer commandBuffer = currentFrame(context).commandBuffer;
    VkImage image = context->swapchainImages[context->imageIndex];
    transitionSwapchainImageLayout(commandBuffer, image, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);

    VkBufferImageCopy region{};
    region.bufferOffset = 0;
    region.bufferRowLength = 0;
    region.bufferImageHeight = 0;
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.mipLevel = 0;
    region.imageSubresource.baseArrayLayer = 0;
    region.imageSubresource.layerCount = 1;
    region.imageOffset = {0, 0, 0};
    region.imageExtent = {context->extent.width, context->extent.height, 1};
    vkCmdCopyImageToBuffer(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            readback->buffer, 1, &region);

    transitionSwapchainImageLayout(commandBuffer, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
}

void submitAndPresentFrame(Context* context, bool waitForCompletion) {
    if (context->renderPassStarted) {
        vkCmdEndRenderPass(currentFrame(context).commandBuffer);
        context->renderPassStarted = false;
    }
    FrameSync& frame = currentFrame(context);
    check(vkEndCommandBuffer(frame.commandBuffer), "Could not end Android Vulkan command buffer");

    VkSemaphore waitSemaphores[] = {frame.imageAvailable};
    VkPipelineStageFlags waitStages[] = {VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT};
    VkSemaphore signalSemaphores[] = {frame.renderFinished};

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.waitSemaphoreCount = 1;
    submitInfo.pWaitSemaphores = waitSemaphores;
    submitInfo.pWaitDstStageMask = waitStages;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &frame.commandBuffer;
    submitInfo.signalSemaphoreCount = 1;
    submitInfo.pSignalSemaphores = signalSemaphores;

    check(vkResetFences(context->device, 1, &frame.inFlight),
            "Could not reset Android Vulkan in-flight fence before submit");
    VkResult submitResult = vkQueueSubmit(context->graphicsQueue, 1, &submitInfo, frame.inFlight);
    if (submitResult != VK_SUCCESS) {
        recreateSignaledFrameFence(context, frame);
        throw std::runtime_error("Could not submit Android Vulkan command buffer: "
                + std::to_string(submitResult));
    }
    if (waitForCompletion) {
        if (!waitForFenceOrTimeout(context, frame.inFlight, READBACK_FENCE_TIMEOUT_NS,
                "Android Vulkan readback fence")) {
            throw std::runtime_error("Could not wait for Android Vulkan readback fence");
        }
    }

    VkSwapchainKHR swapchains[] = {context->swapchain};
    VkPresentInfoKHR presentInfo{};
    presentInfo.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    presentInfo.waitSemaphoreCount = 1;
    presentInfo.pWaitSemaphores = signalSemaphores;
    presentInfo.swapchainCount = 1;
    presentInfo.pSwapchains = swapchains;
    presentInfo.pImageIndices = &context->imageIndex;

    VkResult presentResult = vkQueuePresentKHR(context->presentQueue, &presentInfo);
    context->frameStarted = false;
    context->frameIndex = (context->frameIndex + 1) % static_cast<uint32_t>(context->frames.size());

    if (presentResult != VK_SUCCESS && presentResult != VK_ERROR_OUT_OF_DATE_KHR
            && presentResult != VK_SUBOPTIMAL_KHR) {
        throw std::runtime_error("Could not present Android Vulkan frame: " + std::to_string(presentResult));
    }
    if (presentResult == VK_ERROR_OUT_OF_DATE_KHR || context->pendingResize) {
        createSwapchain(context);
    }
}

void copyReadbackToJava(Context* context, ReadbackBuffer* readback, void* target, int targetSize) {
    int width = static_cast<int>(context->extent.width);
    int height = static_cast<int>(context->extent.height);
    int byteCount = width * height * 4;
    if (targetSize < byteCount) {
        throw std::runtime_error("Android Vulkan readback target buffer is too small");
    }
    void* mapped = nullptr;
    check(vkMapMemory(context->device, readback->memory, 0, readback->size, 0, &mapped),
            "Could not map Android Vulkan readback memory");
    try {
        const uint8_t* source = static_cast<const uint8_t*>(mapped);
        uint8_t* destination = static_cast<uint8_t*>(target);
        bool bgra = context->surfaceFormat == VK_FORMAT_B8G8R8A8_UNORM
                || context->surfaceFormat == VK_FORMAT_B8G8R8A8_SRGB;
        size_t out = 0;
        for (int y = height - 1; y >= 0; y--) {
            size_t rowOffset = static_cast<size_t>(y) * static_cast<size_t>(width) * 4u;
            for (int x = 0; x < width; x++) {
                size_t pixelOffset = rowOffset + static_cast<size_t>(x) * 4u;
                uint8_t first = source[pixelOffset];
                uint8_t second = source[pixelOffset + 1];
                uint8_t third = source[pixelOffset + 2];
                uint8_t alpha = source[pixelOffset + 3];
                if (bgra) {
                    destination[out++] = third;
                    destination[out++] = second;
                    destination[out++] = first;
                    destination[out++] = alpha;
                } else {
                    destination[out++] = first;
                    destination[out++] = second;
                    destination[out++] = third;
                    destination[out++] = alpha;
                }
            }
        }
        vkUnmapMemory(context->device, readback->memory);
    } catch (...) {
        vkUnmapMemory(context->device, readback->memory);
        throw;
    }
}

std::vector<int> intsFromJava(JNIEnv* env, jintArray array, const char* name, bool allowEmpty) {
    if (array == nullptr) {
        throw std::runtime_error(std::string(name) + " cannot be null");
    }
    jsize length = env->GetArrayLength(array);
    if (length <= 0) {
        if (allowEmpty) {
            return {};
        }
        throw std::runtime_error(std::string(name) + " cannot be empty");
    }
    std::vector<jint> javaValues(static_cast<size_t>(length));
    env->GetIntArrayRegion(array, 0, length, javaValues.data());
    std::vector<int> values(static_cast<size_t>(length));
    for (jsize i = 0; i < length; i++) {
        values[static_cast<size_t>(i)] = static_cast<int>(javaValues[static_cast<size_t>(i)]);
    }
    return values;
}

std::vector<uint32_t> wordsFromJava(JNIEnv* env, jintArray array, const char* name) {
    if (array == nullptr) {
        throw std::runtime_error(std::string(name) + " cannot be null");
    }
    jsize length = env->GetArrayLength(array);
    if (length <= 0) {
        throw std::runtime_error(std::string(name) + " cannot be empty");
    }
    std::vector<jint> javaWords(static_cast<size_t>(length));
    env->GetIntArrayRegion(array, 0, length, javaWords.data());
    std::vector<uint32_t> words(static_cast<size_t>(length));
    for (jsize i = 0; i < length; i++) {
        words[static_cast<size_t>(i)] = static_cast<uint32_t>(javaWords[static_cast<size_t>(i)]);
    }
    return words;
}

VkShaderModule createShaderModule(Context* context, const std::vector<uint32_t>& words) {
    VkShaderModuleCreateInfo createInfo{};
    createInfo.sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO;
    createInfo.codeSize = words.size() * sizeof(uint32_t);
    createInfo.pCode = words.data();

    VkShaderModule shaderModule = VK_NULL_HANDLE;
    check(vkCreateShaderModule(context->device, &createInfo, nullptr, &shaderModule),
            "Could not create Android Vulkan shader module");
    return shaderModule;
}

Buffer* createBuffer(Context* context, int size, int usage) {
    if (size <= 0) {
        throw std::runtime_error("Android Vulkan buffer size must be greater than zero");
    }

    Buffer* buffer = new Buffer();
    buffer->context = context;
    buffer->size = static_cast<VkDeviceSize>(size);
    buffer->usage = usage;

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = buffer->size;
    bufferInfo.usage = usage == 1 ? VK_BUFFER_USAGE_INDEX_BUFFER_BIT : VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    check(vkCreateBuffer(context->device, &bufferInfo, nullptr, &buffer->buffer),
            usage == 1 ? "Could not create Android Vulkan index buffer"
                    : "Could not create Android Vulkan vertex buffer");

    VkMemoryRequirements memoryRequirements;
    vkGetBufferMemoryRequirements(context->device, buffer->buffer, &memoryRequirements);

    VkMemoryAllocateInfo allocationInfo{};
    allocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocationInfo.allocationSize = memoryRequirements.size;
    allocationInfo.memoryTypeIndex = findMemoryType(context, memoryRequirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    try {
        check(vkAllocateMemory(context->device, &allocationInfo, nullptr, &buffer->memory),
                "Could not allocate Android Vulkan vertex buffer memory");
        check(vkBindBufferMemory(context->device, buffer->buffer, buffer->memory, 0),
                "Could not bind Android Vulkan vertex buffer memory");
        return buffer;
    } catch (const std::exception&) {
        if (buffer->memory != VK_NULL_HANDLE) {
            vkFreeMemory(context->device, buffer->memory, nullptr);
        }
        if (buffer->buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(context->device, buffer->buffer, nullptr);
        }
        delete buffer;
        throw;
    }
}

TransientBuffer createHostVisibleBuffer(Context* context, VkDeviceSize size, VkBufferUsageFlags usage,
        const char* label) {
    TransientBuffer allocation{};

    VkBufferCreateInfo bufferInfo{};
    bufferInfo.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
    bufferInfo.size = size;
    bufferInfo.usage = usage;
    bufferInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
    check(vkCreateBuffer(context->device, &bufferInfo, nullptr, &allocation.buffer), label);

    VkMemoryRequirements memoryRequirements;
    vkGetBufferMemoryRequirements(context->device, allocation.buffer, &memoryRequirements);

    VkMemoryAllocateInfo allocationInfo{};
    allocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
    allocationInfo.allocationSize = memoryRequirements.size;
    allocationInfo.memoryTypeIndex = findMemoryType(context, memoryRequirements.memoryTypeBits,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);

    try {
        check(vkAllocateMemory(context->device, &allocationInfo, nullptr, &allocation.memory),
                "Could not allocate Android Vulkan host-visible buffer memory");
        check(vkBindBufferMemory(context->device, allocation.buffer, allocation.memory, 0),
                "Could not bind Android Vulkan host-visible buffer memory");
        return allocation;
    } catch (...) {
        if (allocation.memory != VK_NULL_HANDLE) {
            vkFreeMemory(context->device, allocation.memory, nullptr);
        }
        if (allocation.buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(context->device, allocation.buffer, nullptr);
        }
        throw;
    }
}

void destroyHostVisibleBuffer(Context* context, TransientBuffer* allocation) {
    if (allocation->buffer != VK_NULL_HANDLE) {
        vkDestroyBuffer(context->device, allocation->buffer, nullptr);
        allocation->buffer = VK_NULL_HANDLE;
    }
    if (allocation->memory != VK_NULL_HANDLE) {
        vkFreeMemory(context->device, allocation->memory, nullptr);
        allocation->memory = VK_NULL_HANDLE;
    }
}

void copyToHostVisibleBuffer(Context* context, TransientBuffer* allocation, const void* source,
        VkDeviceSize size) {
    void* mapped = nullptr;
    check(vkMapMemory(context->device, allocation->memory, 0, size, 0, &mapped),
            "Could not map Android Vulkan host-visible buffer memory");
    std::memcpy(mapped, source, static_cast<size_t>(size));
    vkUnmapMemory(context->device, allocation->memory);
}

VkCommandBuffer beginSingleTimeCommands(Context* context) {
    VkCommandBufferAllocateInfo allocateInfo{};
    allocateInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
    allocateInfo.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    allocateInfo.commandPool = context->commandPool;
    allocateInfo.commandBufferCount = 1;

    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    check(vkAllocateCommandBuffers(context->device, &allocateInfo, &commandBuffer),
            "Could not allocate Android Vulkan one-time command buffer");

    VkCommandBufferBeginInfo beginInfo{};
    beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
    beginInfo.flags = VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
    check(vkBeginCommandBuffer(commandBuffer, &beginInfo),
            "Could not begin Android Vulkan one-time command buffer");
    return commandBuffer;
}

void endSingleTimeCommands(Context* context, VkCommandBuffer commandBuffer) {
    check(vkEndCommandBuffer(commandBuffer), "Could not end Android Vulkan one-time command buffer");

    VkSubmitInfo submitInfo{};
    submitInfo.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submitInfo.commandBufferCount = 1;
    submitInfo.pCommandBuffers = &commandBuffer;
    VkFence fence = VK_NULL_HANDLE;
    VkFenceCreateInfo fenceInfo{};
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    check(vkCreateFence(context->device, &fenceInfo, nullptr, &fence),
            "Could not create Android Vulkan one-time command fence");
    try {
        check(vkQueueSubmit(context->graphicsQueue, 1, &submitInfo, fence),
                "Could not submit Android Vulkan one-time command buffer");
        if (!waitForFenceOrTimeout(context, fence, SINGLE_COMMAND_TIMEOUT_NS,
                "Android Vulkan one-time command fence")) {
            throw std::runtime_error("Could not wait for Android Vulkan one-time command fence");
        }
    } catch (...) {
        vkDestroyFence(context->device, fence, nullptr);
        vkFreeCommandBuffers(context->device, context->commandPool, 1, &commandBuffer);
        throw;
    }
    vkDestroyFence(context->device, fence, nullptr);
    vkFreeCommandBuffers(context->device, context->commandPool, 1, &commandBuffer);
}

VkImageView createTextureImageView(Context* context, VkImage image, VkFormat format) {
    VkImageViewCreateInfo viewInfo{};
    viewInfo.sType = VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO;
    viewInfo.image = image;
    viewInfo.viewType = VK_IMAGE_VIEW_TYPE_2D;
    viewInfo.format = format;
    viewInfo.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    viewInfo.subresourceRange.baseMipLevel = 0;
    viewInfo.subresourceRange.levelCount = 1;
    viewInfo.subresourceRange.baseArrayLayer = 0;
    viewInfo.subresourceRange.layerCount = 1;

    VkImageView imageView = VK_NULL_HANDLE;
    check(vkCreateImageView(context->device, &viewInfo, nullptr, &imageView),
            "Could not create Android Vulkan texture image view");
    return imageView;
}

VkSamplerAddressMode toAddressMode(int wrap) {
    if (wrap == 1) {
        return VK_SAMPLER_ADDRESS_MODE_REPEAT;
    }
    if (wrap == 2) {
        return VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT;
    }
    return VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
}

VkSampler createTextureSampler(Context* context, int wrapS, int wrapT) {
    VkSamplerCreateInfo samplerInfo{};
    samplerInfo.sType = VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO;
    samplerInfo.magFilter = VK_FILTER_LINEAR;
    samplerInfo.minFilter = VK_FILTER_LINEAR;
    samplerInfo.addressModeU = toAddressMode(wrapS);
    samplerInfo.addressModeV = toAddressMode(wrapT);
    samplerInfo.addressModeW = VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE;
    samplerInfo.anisotropyEnable = VK_FALSE;
    samplerInfo.maxAnisotropy = 1.0f;
    samplerInfo.borderColor = VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK;
    samplerInfo.unnormalizedCoordinates = VK_FALSE;
    samplerInfo.compareEnable = VK_FALSE;
    samplerInfo.mipmapMode = VK_SAMPLER_MIPMAP_MODE_LINEAR;
    samplerInfo.mipLodBias = 0.0f;
    samplerInfo.minLod = 0.0f;
    samplerInfo.maxLod = 0.0f;

    VkSampler sampler = VK_NULL_HANDLE;
    check(vkCreateSampler(context->device, &samplerInfo, nullptr, &sampler),
            "Could not create Android Vulkan texture sampler");
    return sampler;
}

Texture* createTextureResource(Context* context, int width, int height, VkFormat format, int wrapS, int wrapT,
        bool sampled, bool renderAttachment) {
    if (width <= 0 || height <= 0) {
        throw std::runtime_error("Android Vulkan texture size must be greater than zero");
    }
    if (format != VK_FORMAT_R8G8B8A8_UNORM && format != VK_FORMAT_R8G8B8A8_SRGB) {
        throw std::runtime_error("Android Vulkan currently supports RGBA8 textures only");
    }
    if (!sampled && !renderAttachment) {
        throw std::runtime_error("Android Vulkan texture usage must allow sampling or render attachment binding");
    }

    Texture* texture = new Texture();
    texture->context = context;
    texture->width = static_cast<uint32_t>(width);
    texture->height = static_cast<uint32_t>(height);
    texture->format = format;
    texture->sampled = sampled;
    texture->renderAttachment = renderAttachment;

    try {
        VkImageCreateInfo imageInfo{};
        imageInfo.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        imageInfo.imageType = VK_IMAGE_TYPE_2D;
        imageInfo.extent = {texture->width, texture->height, 1};
        imageInfo.mipLevels = 1;
        imageInfo.arrayLayers = 1;
        imageInfo.format = format;
        imageInfo.tiling = VK_IMAGE_TILING_OPTIMAL;
        imageInfo.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        imageInfo.usage = 0;
        if (sampled) {
            imageInfo.usage |= VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        }
        if (renderAttachment) {
            imageInfo.usage |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        }
        imageInfo.samples = VK_SAMPLE_COUNT_1_BIT;
        imageInfo.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        check(vkCreateImage(context->device, &imageInfo, nullptr, &texture->image),
                "Could not create Android Vulkan texture image");

        VkMemoryRequirements memoryRequirements;
        vkGetImageMemoryRequirements(context->device, texture->image, &memoryRequirements);

        VkMemoryAllocateInfo allocationInfo{};
        allocationInfo.sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO;
        allocationInfo.allocationSize = memoryRequirements.size;
        allocationInfo.memoryTypeIndex = findMemoryType(context, memoryRequirements.memoryTypeBits,
                VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        check(vkAllocateMemory(context->device, &allocationInfo, nullptr, &texture->memory),
                "Could not allocate Android Vulkan texture memory");
        check(vkBindImageMemory(context->device, texture->image, texture->memory, 0),
                "Could not bind Android Vulkan texture memory");

        texture->imageView = createTextureImageView(context, texture->image, format);
        if (sampled) {
            texture->sampler = createTextureSampler(context, wrapS, wrapT);
        }
        return texture;
    } catch (...) {
        if (texture->sampler != VK_NULL_HANDLE) {
            vkDestroySampler(context->device, texture->sampler, nullptr);
        }
        if (texture->imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(context->device, texture->imageView, nullptr);
        }
        if (texture->image != VK_NULL_HANDLE) {
            vkDestroyImage(context->device, texture->image, nullptr);
        }
        if (texture->memory != VK_NULL_HANDLE) {
            vkFreeMemory(context->device, texture->memory, nullptr);
        }
        delete texture;
        throw;
    }
}

void transitionTextureLayout(VkCommandBuffer commandBuffer, Texture* texture,
        VkImageLayout oldLayout, VkImageLayout newLayout) {
    VkImageMemoryBarrier barrier{};
    barrier.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
    barrier.oldLayout = oldLayout;
    barrier.newLayout = newLayout;
    barrier.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
    barrier.image = texture->image;
    barrier.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    barrier.subresourceRange.baseMipLevel = 0;
    barrier.subresourceRange.levelCount = 1;
    barrier.subresourceRange.baseArrayLayer = 0;
    barrier.subresourceRange.layerCount = 1;

    VkPipelineStageFlags sourceStage;
    VkPipelineStageFlags destinationStage;
    if (oldLayout == VK_IMAGE_LAYOUT_UNDEFINED
            && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask = 0;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        sourceStage = VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
            && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_SHADER_READ_BIT;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        sourceStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL
            && newLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        sourceStage = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
        destinationStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
    } else if (oldLayout == VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL
            && newLayout == VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
        barrier.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        barrier.dstAccessMask = VK_ACCESS_SHADER_READ_BIT;
        sourceStage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        destinationStage = VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
    } else {
        throw std::runtime_error("Unsupported Android Vulkan texture layout transition");
    }

    vkCmdPipelineBarrier(commandBuffer, sourceStage, destinationStage, 0,
            0, nullptr, 0, nullptr, 1, &barrier);
    texture->layout = newLayout;
}

void copyBufferToTexture(VkCommandBuffer commandBuffer, VkBuffer source, Texture* texture) {
    VkBufferImageCopy region{};
    region.bufferOffset = 0;
    region.bufferRowLength = 0;
    region.bufferImageHeight = 0;
    region.imageSubresource.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
    region.imageSubresource.mipLevel = 0;
    region.imageSubresource.baseArrayLayer = 0;
    region.imageSubresource.layerCount = 1;
    region.imageOffset = {0, 0, 0};
    region.imageExtent = {texture->width, texture->height, 1};
    vkCmdCopyBufferToImage(commandBuffer, source, texture->image,
            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);
}

void writeTextureData(Texture* texture, const void* source, int size) {
    if (texture == nullptr || texture->context == nullptr) {
        throw std::runtime_error("Android Vulkan texture is not valid");
    }
    Context* context = texture->context;
    int byteCount = static_cast<int>(texture->width * texture->height * 4u);
    if (size != byteCount) {
        throw std::runtime_error("Android Vulkan texture upload expects "
                + std::to_string(byteCount) + " RGBA bytes");
    }

    TransientBuffer staging = createHostVisibleBuffer(context, static_cast<VkDeviceSize>(byteCount),
            VK_BUFFER_USAGE_TRANSFER_SRC_BIT, "Could not create Android Vulkan texture staging buffer");
    try {
        copyToHostVisibleBuffer(context, &staging, source, static_cast<VkDeviceSize>(byteCount));
        VkCommandBuffer commandBuffer = beginSingleTimeCommands(context);
        transitionTextureLayout(commandBuffer, texture, texture->layout, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        copyBufferToTexture(commandBuffer, staging.buffer, texture);
        transitionTextureLayout(commandBuffer, texture, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        endSingleTimeCommands(context, commandBuffer);
        destroyHostVisibleBuffer(context, &staging);
    } catch (...) {
        destroyHostVisibleBuffer(context, &staging);
        throw;
    }
}

VkDescriptorSet allocateDescriptorSet(Context* context, VkDescriptorSetLayout layout) {
    FrameSync& frame = currentFrame(context);
    VkDescriptorSetAllocateInfo allocateInfo{};
    allocateInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO;
    allocateInfo.descriptorPool = frame.descriptorPool;
    allocateInfo.descriptorSetCount = 1;
    allocateInfo.pSetLayouts = &layout;

    VkDescriptorSet descriptorSet = VK_NULL_HANDLE;
    check(vkAllocateDescriptorSets(context->device, &allocateInfo, &descriptorSet),
            "Could not allocate Android Vulkan descriptor set");
    return descriptorSet;
}

VkDescriptorSetLayout createTextureDescriptorSetLayout(Context* context, int sampledTextureCount) {
    std::vector<VkDescriptorSetLayoutBinding> bindings(static_cast<size_t>(sampledTextureCount));
    for (int i = 0; i < sampledTextureCount; i++) {
        bindings[static_cast<size_t>(i)].binding = static_cast<uint32_t>(i);
        bindings[static_cast<size_t>(i)].descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        bindings[static_cast<size_t>(i)].descriptorCount = 1;
        bindings[static_cast<size_t>(i)].stageFlags = VK_SHADER_STAGE_FRAGMENT_BIT;
    }

    VkDescriptorSetLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = static_cast<uint32_t>(bindings.size());
    layoutInfo.pBindings = bindings.data();

    VkDescriptorSetLayout layout = VK_NULL_HANDLE;
    check(vkCreateDescriptorSetLayout(context->device, &layoutInfo, nullptr, &layout),
            "Could not create Android Vulkan texture descriptor set layout");
    return layout;
}

VkDescriptorSetLayout createUniformDescriptorSetLayout(Context* context) {
    VkDescriptorSetLayoutBinding binding{};
    binding.binding = 0;
    binding.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
    binding.descriptorCount = 1;
    binding.stageFlags = VK_SHADER_STAGE_VERTEX_BIT | VK_SHADER_STAGE_FRAGMENT_BIT;

    VkDescriptorSetLayoutCreateInfo layoutInfo{};
    layoutInfo.sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO;
    layoutInfo.bindingCount = 1;
    layoutInfo.pBindings = &binding;

    VkDescriptorSetLayout layout = VK_NULL_HANDLE;
    check(vkCreateDescriptorSetLayout(context->device, &layoutInfo, nullptr, &layout),
            "Could not create Android Vulkan uniform descriptor set layout");
    return layout;
}

void bindTextureDescriptors(Context* context, Pipeline* pipeline, Texture** textures, int count) {
    if (pipeline->sampledTextureCount <= 0) {
        return;
    }
    if (count != pipeline->sampledTextureCount) {
        throw std::runtime_error("Android Vulkan texture count does not match the active pipeline");
    }
    VkDescriptorSet descriptorSet = allocateDescriptorSet(context, pipeline->textureDescriptorSetLayout);
    std::vector<VkDescriptorImageInfo> imageInfos(static_cast<size_t>(count));
    std::vector<VkWriteDescriptorSet> descriptorWrites(static_cast<size_t>(count));
    for (int i = 0; i < count; i++) {
        Texture* texture = textures[i];
        if (texture == nullptr) {
            throw std::runtime_error("Android Vulkan texture slot is not valid");
        }
        imageInfos[static_cast<size_t>(i)].sampler = texture->sampler;
        imageInfos[static_cast<size_t>(i)].imageView = texture->imageView;
        imageInfos[static_cast<size_t>(i)].imageLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

        VkWriteDescriptorSet& write = descriptorWrites[static_cast<size_t>(i)];
        write.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        write.dstSet = descriptorSet;
        write.dstBinding = static_cast<uint32_t>(i);
        write.dstArrayElement = 0;
        write.descriptorType = VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
        write.descriptorCount = 1;
        write.pImageInfo = &imageInfos[static_cast<size_t>(i)];
    }
    vkUpdateDescriptorSets(context->device, static_cast<uint32_t>(descriptorWrites.size()),
            descriptorWrites.data(), 0, nullptr);
    vkCmdBindDescriptorSets(currentFrame(context).commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipeline->layout, 0, 1, &descriptorSet, 0, nullptr);
}

void bindUniformDescriptor(Context* context, Pipeline* pipeline, const void* source, int size) {
    if (!pipeline->uniformBufferEnabled) {
        return;
    }
    if (size != PBR_UNIFORM_BYTE_COUNT) {
        throw std::runtime_error("Android Vulkan uniform upload has an unexpected size");
    }
    TransientBuffer uniformBuffer = createHostVisibleBuffer(context, PBR_UNIFORM_BYTE_COUNT,
            VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, "Could not create Android Vulkan uniform buffer");
    try {
        copyToHostVisibleBuffer(context, &uniformBuffer, source, PBR_UNIFORM_BYTE_COUNT);
        VkDescriptorSet descriptorSet = allocateDescriptorSet(context, pipeline->uniformDescriptorSetLayout);

        VkDescriptorBufferInfo bufferInfo{};
        bufferInfo.buffer = uniformBuffer.buffer;
        bufferInfo.offset = 0;
        bufferInfo.range = PBR_UNIFORM_BYTE_COUNT;

        VkWriteDescriptorSet descriptorWrite{};
        descriptorWrite.sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET;
        descriptorWrite.dstSet = descriptorSet;
        descriptorWrite.dstBinding = 0;
        descriptorWrite.dstArrayElement = 0;
        descriptorWrite.descriptorType = VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
        descriptorWrite.descriptorCount = 1;
        descriptorWrite.pBufferInfo = &bufferInfo;
        vkUpdateDescriptorSets(context->device, 1, &descriptorWrite, 0, nullptr);

        currentFrame(context).transientBuffers.push_back(uniformBuffer);
        vkCmdBindDescriptorSets(currentFrame(context).commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline->layout, static_cast<uint32_t>(pipeline->uniformDescriptorSetIndex),
                1, &descriptorSet, 0, nullptr);
    } catch (...) {
        destroyHostVisibleBuffer(context, &uniformBuffer);
        throw;
    }
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_probeInstance(JNIEnv* env, jclass) {
    try {
        std::string failure = probeInstanceFailure();
        if (failure.empty()) {
            return nullptr;
        }
        LOGE("%s", failure.c_str());
        return env->NewStringUTF(failure.c_str());
    } catch (const std::exception& error) {
        std::string failure = std::string("Android Vulkan instance probe failed: ") + error.what();
        LOGE("%s", failure.c_str());
        return env->NewStringUTF(failure.c_str());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_create(JNIEnv* env, jclass, jobject surface,
        jint width, jint height, jboolean vSync, jboolean preferMailboxPresentMode, jint framesInFlight) {
    Context* context = new Context();
    try {
        context->window = ANativeWindow_fromSurface(env, surface);
        if (context->window == nullptr) {
            throw std::runtime_error("Could not get ANativeWindow from Android Surface");
        }
        context->requestedWidth = std::max(1, static_cast<int>(width));
        context->requestedHeight = std::max(1, static_cast<int>(height));
        context->vSync = vSync == JNI_TRUE;
        context->preferMailboxPresentMode = preferMailboxPresentMode == JNI_TRUE;

        createInstance(context);
        createSurface(context);
        pickPhysicalDevice(context);
        createDevice(context);
        createCommandResources(context, framesInFlight);
        createSwapchain(context);
        return handle(context);
    } catch (const std::exception& error) {
        destroyContext(context);
        throwFdx(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_resize(JNIEnv* env, jclass, jlong contextHandle,
        jint width, jint height) {
    Context* context = ptr<Context>(contextHandle);
    try {
        context->requestedWidth = std::max(1, static_cast<int>(width));
        context->requestedHeight = std::max(1, static_cast<int>(height));
        if (context->frameStarted) {
            context->pendingResize = true;
        } else {
            createSwapchain(context);
        }
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_beginFrame(JNIEnv* env, jclass, jlong contextHandle) {
    Context* context = ptr<Context>(contextHandle);
    bool imageAcquired = false;
    try {
        if (context->frames.empty()) {
            return JNI_FALSE;
        }
        if (context->pendingResize) {
            createSwapchain(context);
        }
        FrameSync& frame = currentFrame(context);
        if (!waitForFenceOrTimeout(context, frame.inFlight, FRAME_FENCE_TIMEOUT_NS,
                "Android Vulkan in-flight fence")) {
            return JNI_FALSE;
        }
        destroyTransientBuffers(context, frame);
        if (frame.descriptorPool != VK_NULL_HANDLE) {
            check(vkResetDescriptorPool(context->device, frame.descriptorPool, 0),
                    "Could not reset Android Vulkan frame descriptor pool");
        }

        VkResult acquireResult = vkAcquireNextImageKHR(context->device, context->swapchain, FRAME_ACQUIRE_TIMEOUT_NS,
                frame.imageAvailable, VK_NULL_HANDLE, &context->imageIndex);
        if (acquireResult == VK_TIMEOUT || acquireResult == VK_NOT_READY) {
            LOGW("Android Vulkan frame begin timed out waiting for swapchain image");
            return JNI_FALSE;
        }
        if (acquireResult == VK_ERROR_OUT_OF_DATE_KHR) {
            createSwapchain(context);
            return JNI_FALSE;
        }
        if (acquireResult != VK_SUCCESS && acquireResult != VK_SUBOPTIMAL_KHR) {
            throw std::runtime_error("Could not acquire Android Vulkan swapchain image: "
                    + std::to_string(acquireResult));
        }
        imageAcquired = true;

        check(vkResetCommandBuffer(frame.commandBuffer, 0), "Could not reset Android Vulkan command buffer");

        VkCommandBufferBeginInfo beginInfo{};
        beginInfo.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
        check(vkBeginCommandBuffer(frame.commandBuffer, &beginInfo),
                "Could not begin Android Vulkan command buffer");

        context->frameStarted = true;
        context->renderPassStarted = false;
        context->activeRenderTarget = nullptr;
        context->activeRenderTargetFinalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        return JNI_TRUE;
    } catch (const std::exception& error) {
        if (imageAcquired) {
            recoverFailedFrame(context, "beginFrame");
        }
        throwFdx(env, error.what());
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_endFrame(JNIEnv* env, jclass, jlong contextHandle) {
    Context* context = ptr<Context>(contextHandle);
    try {
        if (!context->frameStarted) {
            return;
        }
        submitAndPresentFrame(context, false);
    } catch (const std::exception& error) {
        recoverFailedFrame(context, "endFrame");
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_readPixelsRgba8(JNIEnv* env, jclass,
        jlong contextHandle, jobject target, jint size) {
    Context* context = ptr<Context>(contextHandle);
    ReadbackBuffer readback{};
    try {
        if (!context->frameStarted) {
            throw std::runtime_error("Cannot read Android Vulkan pixels before beginFrame()");
        }
        if (!context->swapchainTransferSrcSupported) {
            throw std::runtime_error("Android Vulkan swapchain does not support transfer-source readback");
        }
        void* targetAddress = env->GetDirectBufferAddress(target);
        if (targetAddress == nullptr) {
            throw std::runtime_error("Android Vulkan readback requires a direct ByteBuffer");
        }
        int byteCount = static_cast<int>(context->extent.width * context->extent.height * 4u);
        if (size < byteCount) {
            throw std::runtime_error("Android Vulkan readback target buffer is too small");
        }
        if (context->renderPassStarted) {
            vkCmdEndRenderPass(currentFrame(context).commandBuffer);
            context->renderPassStarted = false;
        }

        readback = createReadbackBuffer(context, static_cast<VkDeviceSize>(byteCount));
        recordReadbackCopy(context, &readback);
        submitAndPresentFrame(context, true);
        copyReadbackToJava(context, &readback, targetAddress, size);
        destroyReadbackBuffer(context, &readback);
    } catch (const std::exception& error) {
        if (readback.buffer != VK_NULL_HANDLE || readback.memory != VK_NULL_HANDLE) {
            destroyReadbackBuffer(context, &readback);
        }
        if (context != nullptr) {
            recoverFailedFrame(context, "readPixelsRgba8");
        }
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_clear(JNIEnv* env, jclass, jlong contextHandle,
        jfloat red, jfloat green, jfloat blue, jfloat alpha) {
    Context* context = ptr<Context>(contextHandle);
    try {
        VkClearValue clearValues[2]{};
        clearValues[0].color.float32[0] = red;
        clearValues[0].color.float32[1] = green;
        clearValues[0].color.float32[2] = blue;
        clearValues[0].color.float32[3] = alpha;
        clearValues[1].depthStencil.depth = 1.0f;
        clearValues[1].depthStencil.stencil = 0;

        VkRenderPassBeginInfo renderPassInfo{};
        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        renderPassInfo.renderPass = context->renderPasses[1][1][1];
        renderPassInfo.framebuffer = context->framebuffers[context->imageIndex];
        renderPassInfo.renderArea.offset = {0, 0};
        renderPassInfo.renderArea.extent = context->extent;
        renderPassInfo.clearValueCount = 2;
        renderPassInfo.pClearValues = clearValues;

        vkCmdBeginRenderPass(currentFrame(context).commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);
        vkCmdEndRenderPass(currentFrame(context).commandBuffer);
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_createBuffer(JNIEnv* env, jclass,
        jlong contextHandle, jint size, jint usage) {
    Context* context = ptr<Context>(contextHandle);
    try {
        return handle(createBuffer(context, static_cast<int>(size), static_cast<int>(usage)));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_writeBuffer(JNIEnv* env, jclass,
        jlong bufferHandle, jobject data, jint size) {
    Buffer* buffer = ptr<Buffer>(bufferHandle);
    try {
        if (buffer == nullptr || buffer->context == nullptr) {
            throw std::runtime_error("Android Vulkan buffer is not valid");
        }
        if (size < 0 || static_cast<VkDeviceSize>(size) > buffer->size) {
            throw std::runtime_error("Android Vulkan buffer data is larger than the destination buffer");
        }
        void* source = env->GetDirectBufferAddress(data);
        if (source == nullptr) {
            throw std::runtime_error("Android Vulkan buffer writes require a direct ByteBuffer");
        }
        void* mapped = nullptr;
        check(vkMapMemory(buffer->context->device, buffer->memory, 0, static_cast<VkDeviceSize>(size), 0,
                &mapped), "Could not map Android Vulkan vertex buffer memory");
        std::memcpy(mapped, source, static_cast<size_t>(size));
        vkUnmapMemory(buffer->context->device, buffer->memory);
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_createTexture(JNIEnv* env, jclass,
        jlong contextHandle, jint width, jint height, jint format, jint wrapS, jint wrapT,
        jboolean sampled, jboolean renderAttachment) {
    Context* context = ptr<Context>(contextHandle);
    try {
        return handle(createTextureResource(context, static_cast<int>(width), static_cast<int>(height),
                static_cast<VkFormat>(format), static_cast<int>(wrapS), static_cast<int>(wrapT),
                sampled == JNI_TRUE, renderAttachment == JNI_TRUE));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_writeTexture(JNIEnv* env, jclass,
        jlong textureHandle, jobject data, jint size) {
    Texture* texture = ptr<Texture>(textureHandle);
    try {
        void* source = env->GetDirectBufferAddress(data);
        if (source == nullptr) {
            throw std::runtime_error("Android Vulkan texture writes require a direct ByteBuffer");
        }
        writeTextureData(texture, source, static_cast<int>(size));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_createShaderModule(JNIEnv* env, jclass,
        jlong contextHandle, jintArray vertexWords, jintArray fragmentWords) {
    Context* context = ptr<Context>(contextHandle);
    ShaderModule* module = new ShaderModule();
    module->context = context;
    try {
        module->vertex = createShaderModule(context, wordsFromJava(env, vertexWords, "Vertex SPIR-V words"));
        module->fragment = createShaderModule(context, wordsFromJava(env, fragmentWords, "Fragment SPIR-V words"));
        return handle(module);
    } catch (const std::exception& error) {
        if (module->vertex != VK_NULL_HANDLE) {
            vkDestroyShaderModule(context->device, module->vertex, nullptr);
        }
        if (module->fragment != VK_NULL_HANDLE) {
            vkDestroyShaderModule(context->device, module->fragment, nullptr);
        }
        delete module;
        throwFdx(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_createRenderPipeline(JNIEnv* env, jclass,
        jlong contextHandle, jlong shaderModuleHandle, jint colorFormat, jint primitiveTopology,
        jintArray vertexStridesArray, jintArray vertexStepModesArray, jintArray attributeBindingsArray,
        jintArray attributeLocationsArray, jintArray attributeFormatsArray, jintArray attributeOffsetsArray,
        jint sampledTextureCountValue, jboolean pbrUniformsEnabled, jboolean depthTestEnabled,
        jboolean depthWriteEnabled) {
    Context* context = ptr<Context>(contextHandle);
    ShaderModule* shaderModule = ptr<ShaderModule>(shaderModuleHandle);
    Pipeline* pipeline = new Pipeline();
    pipeline->context = context;
    pipeline->sampledTextureCount = static_cast<int>(sampledTextureCountValue);
    pipeline->uniformBufferEnabled = pbrUniformsEnabled == JNI_TRUE;
    pipeline->uniformDescriptorSetIndex = pipeline->sampledTextureCount > 0 ? 1 : 0;

    try {
        if (pipeline->sampledTextureCount < 0 || pipeline->sampledTextureCount > MAX_TEXTURE_DESCRIPTOR_SLOTS) {
            throw std::runtime_error("Android Vulkan sampled texture count exceeds the descriptor slot limit");
        }
        VkPipelineShaderStageCreateInfo shaderStages[2]{};
        shaderStages[0].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        shaderStages[0].stage = VK_SHADER_STAGE_VERTEX_BIT;
        shaderStages[0].module = shaderModule->vertex;
        shaderStages[0].pName = "vertexMain";
        shaderStages[1].sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
        shaderStages[1].stage = VK_SHADER_STAGE_FRAGMENT_BIT;
        shaderStages[1].module = shaderModule->fragment;
        shaderStages[1].pName = "fragmentMain";

        std::vector<int> vertexStrides = intsFromJava(env, vertexStridesArray,
                "Vertex layout strides", true);
        std::vector<int> vertexStepModes = intsFromJava(env, vertexStepModesArray,
                "Vertex layout step modes", true);
        std::vector<int> attributeBindings = intsFromJava(env, attributeBindingsArray,
                "Vertex attribute bindings", true);
        std::vector<int> attributeLocations = intsFromJava(env, attributeLocationsArray,
                "Vertex attribute locations", true);
        std::vector<int> attributeFormats = intsFromJava(env, attributeFormatsArray,
                "Vertex attribute formats", true);
        std::vector<int> attributeOffsets = intsFromJava(env, attributeOffsetsArray,
                "Vertex attribute offsets", true);
        if (vertexStrides.size() != vertexStepModes.size()) {
            throw std::runtime_error("Android Vulkan vertex layout arrays must have the same length");
        }
        if (attributeBindings.size() != attributeLocations.size()
                || attributeLocations.size() != attributeFormats.size()
                || attributeLocations.size() != attributeOffsets.size()) {
            throw std::runtime_error("Android Vulkan vertex attribute arrays must have the same length");
        }

        std::vector<VkVertexInputBindingDescription> bindingDescriptions(vertexStrides.size());
        std::vector<VkVertexInputAttributeDescription> attributeDescriptions(attributeLocations.size());
        for (size_t i = 0; i < vertexStrides.size(); i++) {
            if (vertexStrides[i] <= 0) {
                throw std::runtime_error("Android Vulkan vertex layout stride must be greater than zero");
            }
            bindingDescriptions[i].binding = static_cast<uint32_t>(i);
            bindingDescriptions[i].stride = static_cast<uint32_t>(vertexStrides[i]);
            bindingDescriptions[i].inputRate = vertexStepModes[i] == 1
                    ? VK_VERTEX_INPUT_RATE_INSTANCE : VK_VERTEX_INPUT_RATE_VERTEX;
        }
        for (size_t i = 0; i < attributeLocations.size(); i++) {
            int binding = attributeBindings[i];
            if (binding < 0 || binding >= static_cast<int>(vertexStrides.size())) {
                throw std::runtime_error("Android Vulkan vertex attribute binding is out of range");
            }
            attributeDescriptions[i].binding = static_cast<uint32_t>(binding);
            attributeDescriptions[i].location = static_cast<uint32_t>(attributeLocations[i]);
            attributeDescriptions[i].format = static_cast<VkFormat>(attributeFormats[i]);
            attributeDescriptions[i].offset = static_cast<uint32_t>(attributeOffsets[i]);
        }

        VkPipelineVertexInputStateCreateInfo vertexInput{};
        vertexInput.sType = VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO;
        if (!bindingDescriptions.empty()) {
            vertexInput.vertexBindingDescriptionCount = static_cast<uint32_t>(bindingDescriptions.size());
            vertexInput.pVertexBindingDescriptions = bindingDescriptions.data();
        }
        if (!attributeDescriptions.empty()) {
            vertexInput.vertexAttributeDescriptionCount =
                    static_cast<uint32_t>(attributeDescriptions.size());
            vertexInput.pVertexAttributeDescriptions = attributeDescriptions.data();
        }

        VkPipelineInputAssemblyStateCreateInfo inputAssembly{};
        inputAssembly.sType = VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO;
        if (primitiveTopology == 2) {
            inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_LINE_LIST;
        } else if (primitiveTopology == 1) {
            inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP;
        } else {
            inputAssembly.topology = VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST;
        }

        VkPipelineViewportStateCreateInfo viewportState{};
        viewportState.sType = VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO;
        viewportState.viewportCount = 1;
        viewportState.scissorCount = 1;

        VkPipelineRasterizationStateCreateInfo rasterizer{};
        rasterizer.sType = VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO;
        rasterizer.polygonMode = VK_POLYGON_MODE_FILL;
        rasterizer.lineWidth = 1.0f;
        rasterizer.cullMode = VK_CULL_MODE_NONE;
        rasterizer.frontFace = VK_FRONT_FACE_COUNTER_CLOCKWISE;

        VkPipelineMultisampleStateCreateInfo multisampling{};
        multisampling.sType = VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO;
        multisampling.rasterizationSamples = VK_SAMPLE_COUNT_1_BIT;

        VkPipelineColorBlendAttachmentState colorBlendAttachment{};
        colorBlendAttachment.colorWriteMask = VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT;
        colorBlendAttachment.blendEnable = VK_TRUE;
        colorBlendAttachment.srcColorBlendFactor = VK_BLEND_FACTOR_SRC_ALPHA;
        colorBlendAttachment.dstColorBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        colorBlendAttachment.colorBlendOp = VK_BLEND_OP_ADD;
        colorBlendAttachment.srcAlphaBlendFactor = VK_BLEND_FACTOR_ONE;
        colorBlendAttachment.dstAlphaBlendFactor = VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        colorBlendAttachment.alphaBlendOp = VK_BLEND_OP_ADD;

        VkPipelineColorBlendStateCreateInfo colorBlending{};
        colorBlending.sType = VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO;
        colorBlending.attachmentCount = 1;
        colorBlending.pAttachments = &colorBlendAttachment;

        VkDynamicState dynamicStates[] = {VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR};
        VkPipelineDynamicStateCreateInfo dynamicState{};
        dynamicState.sType = VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO;
        dynamicState.dynamicStateCount = 2;
        dynamicState.pDynamicStates = dynamicStates;

        VkPipelineDepthStencilStateCreateInfo depthStencil{};
        depthStencil.sType = VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO;
        depthStencil.depthTestEnable = depthTestEnabled == JNI_TRUE ? VK_TRUE : VK_FALSE;
        depthStencil.depthWriteEnable = depthWriteEnabled == JNI_TRUE ? VK_TRUE : VK_FALSE;
        depthStencil.depthCompareOp = VK_COMPARE_OP_LESS_OR_EQUAL;
        depthStencil.depthBoundsTestEnable = VK_FALSE;
        depthStencil.stencilTestEnable = VK_FALSE;
        depthStencil.minDepthBounds = 0.0f;
        depthStencil.maxDepthBounds = 1.0f;

        std::vector<VkDescriptorSetLayout> setLayouts;
        if (pipeline->sampledTextureCount > 0) {
            pipeline->textureDescriptorSetLayout = createTextureDescriptorSetLayout(context,
                    pipeline->sampledTextureCount);
            setLayouts.push_back(pipeline->textureDescriptorSetLayout);
        }
        if (pipeline->uniformBufferEnabled) {
            pipeline->uniformDescriptorSetLayout = createUniformDescriptorSetLayout(context);
            setLayouts.push_back(pipeline->uniformDescriptorSetLayout);
        }

        VkPipelineLayoutCreateInfo layoutInfo{};
        layoutInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO;
        layoutInfo.setLayoutCount = static_cast<uint32_t>(setLayouts.size());
        layoutInfo.pSetLayouts = setLayouts.empty() ? nullptr : setLayouts.data();
        check(vkCreatePipelineLayout(context->device, &layoutInfo, nullptr, &pipeline->layout),
                "Could not create Android Vulkan pipeline layout");

        VkGraphicsPipelineCreateInfo pipelineInfo{};
        pipelineInfo.sType = VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO;
        pipelineInfo.stageCount = 2;
        pipelineInfo.pStages = shaderStages;
        pipelineInfo.pVertexInputState = &vertexInput;
        pipelineInfo.pInputAssemblyState = &inputAssembly;
        pipelineInfo.pViewportState = &viewportState;
        pipelineInfo.pRasterizationState = &rasterizer;
        pipelineInfo.pMultisampleState = &multisampling;
        pipelineInfo.pColorBlendState = &colorBlending;
        pipelineInfo.pDynamicState = &dynamicState;
        pipelineInfo.pDepthStencilState = &depthStencil;
        pipelineInfo.layout = pipeline->layout;
        pipelineInfo.renderPass = pipelineRenderPass(context, static_cast<VkFormat>(colorFormat));
        pipelineInfo.subpass = 0;

        check(vkCreateGraphicsPipelines(context->device, VK_NULL_HANDLE, 1, &pipelineInfo, nullptr,
                &pipeline->pipeline), "Could not create Android Vulkan graphics pipeline");
        return handle(pipeline);
    } catch (const std::exception& error) {
        if (pipeline->pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(context->device, pipeline->pipeline, nullptr);
        }
        if (pipeline->layout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(context->device, pipeline->layout, nullptr);
        }
        if (pipeline->uniformDescriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(context->device, pipeline->uniformDescriptorSetLayout, nullptr);
        }
        if (pipeline->textureDescriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(context->device, pipeline->textureDescriptorSetLayout, nullptr);
        }
        delete pipeline;
        throwFdx(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_beginRenderPass(JNIEnv* env, jclass,
        jlong contextHandle, jlong colorTextureHandle, jint, jint, jint, jboolean clear,
        jfloat red, jfloat green, jfloat blue, jfloat alpha, jboolean store, jboolean depthClear,
        jfloat depthClearValue) {
    Context* context = ptr<Context>(contextHandle);
    try {
        if (!context->frameStarted) {
            throw std::runtime_error("Cannot begin Android Vulkan render pass outside a frame");
        }
        if (context->renderPassStarted) {
            throw std::runtime_error("Android Vulkan render pass is already active");
        }
        VkClearValue clearValues[2]{};
        clearValues[0].color.float32[0] = red;
        clearValues[0].color.float32[1] = green;
        clearValues[0].color.float32[2] = blue;
        clearValues[0].color.float32[3] = alpha;
        clearValues[1].depthStencil.depth = depthClearValue;
        clearValues[1].depthStencil.stencil = 0;

        Texture* colorTexture = ptr<Texture>(colorTextureHandle);
        bool textureBacked = colorTexture != nullptr;
        VkExtent2D passExtent = context->extent;
        VkFramebuffer framebuffer = context->framebuffers[context->imageIndex];
        VkRenderPass renderPass = selectRenderPass(context, clear == JNI_TRUE, store == JNI_TRUE,
                depthClear == JNI_TRUE);
        context->activeRenderTarget = nullptr;
        context->activeRenderTargetFinalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        if (textureBacked) {
            VkImageLayout finalLayout = colorTexture->sampled
                    ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL
                    : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            renderPass = selectTextureRenderPass(context, colorTexture, clear == JNI_TRUE, store == JNI_TRUE,
                    depthClear == JNI_TRUE, finalLayout);
            framebuffer = textureFramebuffer(context, colorTexture, renderPass);
            passExtent = {colorTexture->width, colorTexture->height};
            context->activeRenderTarget = colorTexture;
            context->activeRenderTargetFinalLayout = finalLayout;
            colorTexture->layout = VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
        }

        VkRenderPassBeginInfo renderPassInfo{};
        renderPassInfo.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
        renderPassInfo.renderPass = renderPass;
        renderPassInfo.framebuffer = framebuffer;
        renderPassInfo.renderArea.offset = {0, 0};
        renderPassInfo.renderArea.extent = passExtent;
        renderPassInfo.clearValueCount = 2;
        renderPassInfo.pClearValues = clearValues;

        VkCommandBuffer commandBuffer = currentFrame(context).commandBuffer;
        vkCmdBeginRenderPass(commandBuffer, &renderPassInfo, VK_SUBPASS_CONTENTS_INLINE);

        VkViewport viewport{};
        viewport.x = 0.0f;
        viewport.y = static_cast<float>(passExtent.height);
        viewport.width = static_cast<float>(passExtent.width);
        viewport.height = -static_cast<float>(passExtent.height);
        viewport.minDepth = 0.0f;
        viewport.maxDepth = 1.0f;
        VkRect2D scissor{};
        scissor.offset = {0, 0};
        scissor.extent = passExtent;
        vkCmdSetViewport(commandBuffer, 0, 1, &viewport);
        vkCmdSetScissor(commandBuffer, 0, 1, &scissor);
        context->renderPassStarted = true;
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_setPipeline(JNIEnv* env, jclass, jlong contextHandle,
        jlong pipelineHandle) {
    Context* context = ptr<Context>(contextHandle);
    Pipeline* pipeline = ptr<Pipeline>(pipelineHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot bind Android Vulkan pipeline outside a render pass");
        }
        vkCmdBindPipeline(currentFrame(context).commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS,
                pipeline->pipeline);
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_setVertexBuffer(JNIEnv* env, jclass,
        jlong contextHandle, jint slot, jlong bufferHandle) {
    Context* context = ptr<Context>(contextHandle);
    Buffer* buffer = ptr<Buffer>(bufferHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot bind Android Vulkan vertex buffer outside a render pass");
        }
        if (slot < 0) {
            throw std::runtime_error("Android Vulkan vertex buffer slot cannot be negative");
        }
        VkDeviceSize offset = 0;
        uint32_t binding = static_cast<uint32_t>(slot);
        vkCmdBindVertexBuffers(currentFrame(context).commandBuffer, binding, 1, &buffer->buffer, &offset);
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_setIndexBuffer(JNIEnv* env, jclass,
        jlong contextHandle, jlong bufferHandle) {
    Context* context = ptr<Context>(contextHandle);
    Buffer* buffer = ptr<Buffer>(bufferHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot bind Android Vulkan index buffer outside a render pass");
        }
        vkCmdBindIndexBuffer(currentFrame(context).commandBuffer, buffer->buffer, 0, VK_INDEX_TYPE_UINT16);
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_setScissor(JNIEnv* env, jclass,
        jlong contextHandle, jint x, jint y, jint width, jint height) {
    Context* context = ptr<Context>(contextHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot set Android Vulkan scissor outside a render pass");
        }
        if (width <= 0 || height <= 0) {
            throw std::runtime_error("Android Vulkan scissor size must be greater than zero");
        }
        VkRect2D scissor{};
        scissor.offset = {static_cast<int32_t>(x), static_cast<int32_t>(y)};
        scissor.extent = {static_cast<uint32_t>(width), static_cast<uint32_t>(height)};
        vkCmdSetScissor(currentFrame(context).commandBuffer, 0, 1, &scissor);
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_bindTextures(JNIEnv* env, jclass,
        jlong contextHandle, jlong pipelineHandle, jlongArray textureHandles, jint count) {
    Context* context = ptr<Context>(contextHandle);
    Pipeline* pipeline = ptr<Pipeline>(pipelineHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot bind Android Vulkan textures outside a render pass");
        }
        if (textureHandles == nullptr) {
            throw std::runtime_error("Android Vulkan texture handles cannot be null");
        }
        std::vector<jlong> handles(static_cast<size_t>(count));
        env->GetLongArrayRegion(textureHandles, 0, count, handles.data());
        std::vector<Texture*> textures(static_cast<size_t>(count));
        for (int i = 0; i < count; i++) {
            textures[static_cast<size_t>(i)] = ptr<Texture>(handles[static_cast<size_t>(i)]);
        }
        bindTextureDescriptors(context, pipeline, textures.data(), static_cast<int>(count));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_bindUniforms(JNIEnv* env, jclass,
        jlong contextHandle, jlong pipelineHandle, jobject data, jint size) {
    Context* context = ptr<Context>(contextHandle);
    Pipeline* pipeline = ptr<Pipeline>(pipelineHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot bind Android Vulkan uniforms outside a render pass");
        }
        void* source = env->GetDirectBufferAddress(data);
        if (source == nullptr) {
            throw std::runtime_error("Android Vulkan uniform uploads require a direct ByteBuffer");
        }
        bindUniformDescriptor(context, pipeline, source, static_cast<int>(size));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_draw(JNIEnv* env, jclass, jlong contextHandle,
        jint vertexCount, jint instanceCount, jint firstVertex, jint firstInstance) {
    Context* context = ptr<Context>(contextHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot draw Android Vulkan geometry outside a render pass");
        }
        vkCmdDraw(currentFrame(context).commandBuffer, static_cast<uint32_t>(vertexCount),
                static_cast<uint32_t>(instanceCount), static_cast<uint32_t>(firstVertex),
                static_cast<uint32_t>(firstInstance));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_drawIndexed(JNIEnv* env, jclass, jlong contextHandle,
        jint indexCount, jint instanceCount, jint firstIndex, jint baseVertex, jint firstInstance) {
    Context* context = ptr<Context>(contextHandle);
    try {
        if (!context->renderPassStarted) {
            throw std::runtime_error("Cannot draw indexed Android Vulkan geometry outside a render pass");
        }
        vkCmdDrawIndexed(currentFrame(context).commandBuffer, static_cast<uint32_t>(indexCount),
                static_cast<uint32_t>(instanceCount), static_cast<uint32_t>(firstIndex),
                static_cast<int32_t>(baseVertex), static_cast<uint32_t>(firstInstance));
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_endRenderPass(JNIEnv* env, jclass,
        jlong contextHandle) {
    Context* context = ptr<Context>(contextHandle);
    try {
        if (!context->renderPassStarted) {
            return;
        }
        vkCmdEndRenderPass(currentFrame(context).commandBuffer);
        if (context->activeRenderTarget != nullptr) {
            context->activeRenderTarget->layout = context->activeRenderTargetFinalLayout;
            context->activeRenderTarget = nullptr;
            context->activeRenderTargetFinalLayout = VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        context->renderPassStarted = false;
    } catch (const std::exception& error) {
        throwFdx(env, error.what());
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_surfaceFormat(JNIEnv*, jclass, jlong contextHandle) {
    Context* context = ptr<Context>(contextHandle);
    return static_cast<jint>(context->surfaceFormat);
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_destroyShaderModule(JNIEnv*, jclass,
        jlong shaderModuleHandle) {
    ShaderModule* module = ptr<ShaderModule>(shaderModuleHandle);
    if (module == nullptr) {
        return;
    }
    if (module->context != nullptr && module->context->device != VK_NULL_HANDLE) {
        waitDeviceIdleBeforeDestroy(module->context, "shader module");
        if (module->fragment != VK_NULL_HANDLE) {
            vkDestroyShaderModule(module->context->device, module->fragment, nullptr);
        }
        if (module->vertex != VK_NULL_HANDLE) {
            vkDestroyShaderModule(module->context->device, module->vertex, nullptr);
        }
    }
    delete module;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_destroyRenderPipeline(JNIEnv*, jclass,
        jlong pipelineHandle) {
    Pipeline* pipeline = ptr<Pipeline>(pipelineHandle);
    if (pipeline == nullptr) {
        return;
    }
    if (pipeline->context != nullptr && pipeline->context->device != VK_NULL_HANDLE) {
        waitDeviceIdleBeforeDestroy(pipeline->context, "render pipeline");
        if (pipeline->pipeline != VK_NULL_HANDLE) {
            vkDestroyPipeline(pipeline->context->device, pipeline->pipeline, nullptr);
        }
        if (pipeline->layout != VK_NULL_HANDLE) {
            vkDestroyPipelineLayout(pipeline->context->device, pipeline->layout, nullptr);
        }
        if (pipeline->uniformDescriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(pipeline->context->device,
                    pipeline->uniformDescriptorSetLayout, nullptr);
        }
        if (pipeline->textureDescriptorSetLayout != VK_NULL_HANDLE) {
            vkDestroyDescriptorSetLayout(pipeline->context->device,
                    pipeline->textureDescriptorSetLayout, nullptr);
        }
    }
    delete pipeline;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_destroyBuffer(JNIEnv*, jclass,
        jlong bufferHandle) {
    Buffer* buffer = ptr<Buffer>(bufferHandle);
    if (buffer == nullptr) {
        return;
    }
    if (buffer->context != nullptr && buffer->context->device != VK_NULL_HANDLE) {
        waitDeviceIdleBeforeDestroy(buffer->context, "buffer");
        if (buffer->buffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(buffer->context->device, buffer->buffer, nullptr);
        }
        if (buffer->memory != VK_NULL_HANDLE) {
            vkFreeMemory(buffer->context->device, buffer->memory, nullptr);
        }
    }
    delete buffer;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_destroyTexture(JNIEnv*, jclass,
        jlong textureHandle) {
    Texture* texture = ptr<Texture>(textureHandle);
    if (texture == nullptr) {
        return;
    }
    if (texture->context != nullptr && texture->context->device != VK_NULL_HANDLE) {
        waitDeviceIdleBeforeDestroy(texture->context, "texture");
        for (const auto& entry : texture->framebuffers) {
            if (entry.second != VK_NULL_HANDLE) {
                vkDestroyFramebuffer(texture->context->device, entry.second, nullptr);
            }
        }
        texture->framebuffers.clear();
        destroyDepthAttachment(texture->context, &texture->depthAttachment);
        if (texture->sampler != VK_NULL_HANDLE) {
            vkDestroySampler(texture->context->device, texture->sampler, nullptr);
        }
        if (texture->imageView != VK_NULL_HANDLE) {
            vkDestroyImageView(texture->context->device, texture->imageView, nullptr);
        }
        if (texture->image != VK_NULL_HANDLE) {
            vkDestroyImage(texture->context->device, texture->image, nullptr);
        }
        if (texture->memory != VK_NULL_HANDLE) {
            vkFreeMemory(texture->context->device, texture->memory, nullptr);
        }
    }
    delete texture;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_backend_android_AndroidVulkanNative_destroy(JNIEnv*, jclass, jlong contextHandle) {
    destroyContext(ptr<Context>(contextHandle));
}
