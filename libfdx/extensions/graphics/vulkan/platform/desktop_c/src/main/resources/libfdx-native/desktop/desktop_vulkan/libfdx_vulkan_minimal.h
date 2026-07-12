#ifndef LIBFDX_VULKAN_MINIMAL_H
#define LIBFDX_VULKAN_MINIMAL_H

#include <stddef.h>
#include <stdint.h>

#ifdef _WIN32
#define VKAPI_ATTR
#define VKAPI_CALL __stdcall
#define VKAPI_PTR VKAPI_CALL
#else
#define VKAPI_ATTR
#define VKAPI_CALL
#define VKAPI_PTR
#endif

#define VK_VERSION_1_0 1
#define VK_MAKE_VERSION(major, minor, patch) \
    ((((uint32_t)(major)) << 22) | (((uint32_t)(minor)) << 12) | ((uint32_t)(patch)))
#define VK_VERSION_MAJOR(version) ((uint32_t)(version) >> 22)
#define VK_VERSION_MINOR(version) (((uint32_t)(version) >> 12) & 0x3ffu)
#define VK_VERSION_PATCH(version) ((uint32_t)(version) & 0xfffu)
#define VK_API_VERSION_1_0 VK_MAKE_VERSION(1, 0, 0)

#define VK_FALSE 0u
#define VK_TRUE 1u
#define VK_NULL_HANDLE 0
#define VK_QUEUE_FAMILY_IGNORED (~0u)
#define VK_SUBPASS_EXTERNAL (~0u)
#define VK_MAX_EXTENSION_NAME_SIZE 256
#define VK_UUID_SIZE 16
#define VK_MAX_MEMORY_TYPES 32
#define VK_MAX_MEMORY_HEAPS 16

#define VK_KHR_SWAPCHAIN_EXTENSION_NAME "VK_KHR_swapchain"
#define VK_KHR_MAINTENANCE1_EXTENSION_NAME "VK_KHR_maintenance1"

#define VK_SUCCESS 0
#define VK_NOT_READY 1
#define VK_TIMEOUT 2
#define VK_EVENT_SET 3
#define VK_EVENT_RESET 4
#define VK_INCOMPLETE 5
#define VK_ERROR_OUT_OF_HOST_MEMORY -1
#define VK_ERROR_OUT_OF_DEVICE_MEMORY -2
#define VK_ERROR_INITIALIZATION_FAILED -3
#define VK_ERROR_DEVICE_LOST -4
#define VK_ERROR_MEMORY_MAP_FAILED -5
#define VK_ERROR_LAYER_NOT_PRESENT -6
#define VK_ERROR_EXTENSION_NOT_PRESENT -7
#define VK_ERROR_FEATURE_NOT_PRESENT -8
#define VK_ERROR_INCOMPATIBLE_DRIVER -9
#define VK_ERROR_TOO_MANY_OBJECTS -10
#define VK_ERROR_FORMAT_NOT_SUPPORTED -11
#define VK_ERROR_SURFACE_LOST_KHR -1000000000
#define VK_ERROR_NATIVE_WINDOW_IN_USE_KHR -1000000001
#define VK_SUBOPTIMAL_KHR 1000001003
#define VK_ERROR_OUT_OF_DATE_KHR -1000001004
#define VK_ERROR_INCOMPATIBLE_DISPLAY_KHR -1000003001

#define VK_STRUCTURE_TYPE_APPLICATION_INFO 0
#define VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO 1
#define VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO 2
#define VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO 3
#define VK_STRUCTURE_TYPE_SUBMIT_INFO 4
#define VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO 5
#define VK_STRUCTURE_TYPE_FENCE_CREATE_INFO 8
#define VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO 9
#define VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO 12
#define VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO 14
#define VK_STRUCTURE_TYPE_IMAGE_VIEW_CREATE_INFO 15
#define VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO 16
#define VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO 18
#define VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO 19
#define VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO 20
#define VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO 22
#define VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO 23
#define VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO 24
#define VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO 25
#define VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO 26
#define VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO 27
#define VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO 28
#define VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO 30
#define VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO 31
#define VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO 32
#define VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO 33
#define VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO 34
#define VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET 35
#define VK_STRUCTURE_TYPE_FRAMEBUFFER_CREATE_INFO 37
#define VK_STRUCTURE_TYPE_RENDER_PASS_CREATE_INFO 38
#define VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO 39
#define VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO 40
#define VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO 42
#define VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO 43
#define VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER 45
#define VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR 1000001000
#define VK_STRUCTURE_TYPE_PRESENT_INFO_KHR 1000001001

#define VK_FORMAT_UNDEFINED 0
#define VK_FORMAT_R8G8B8A8_UNORM 37
#define VK_FORMAT_R8G8B8A8_SRGB 43
#define VK_FORMAT_B8G8R8A8_UNORM 44
#define VK_FORMAT_B8G8R8A8_SRGB 50
#define VK_FORMAT_D32_SFLOAT 126

#define VK_COLOR_SPACE_SRGB_NONLINEAR_KHR 0

#define VK_PRESENT_MODE_IMMEDIATE_KHR 0
#define VK_PRESENT_MODE_MAILBOX_KHR 1
#define VK_PRESENT_MODE_FIFO_KHR 2

#define VK_IMAGE_TYPE_2D 1
#define VK_IMAGE_TILING_OPTIMAL 0
#define VK_IMAGE_VIEW_TYPE_2D 1
#define VK_IMAGE_LAYOUT_UNDEFINED 0
#define VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL 2
#define VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL 3
#define VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL 5
#define VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL 6
#define VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL 7
#define VK_IMAGE_LAYOUT_PRESENT_SRC_KHR 1000001002

#define VK_SHARING_MODE_EXCLUSIVE 0
#define VK_SHARING_MODE_CONCURRENT 1

#define VK_IMAGE_USAGE_TRANSFER_SRC_BIT 0x00000001u
#define VK_IMAGE_USAGE_TRANSFER_DST_BIT 0x00000002u
#define VK_IMAGE_USAGE_SAMPLED_BIT 0x00000004u
#define VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT 0x00000010u
#define VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT 0x00000020u

#define VK_BUFFER_USAGE_TRANSFER_SRC_BIT 0x00000001u
#define VK_BUFFER_USAGE_TRANSFER_DST_BIT 0x00000002u
#define VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT 0x00000010u
#define VK_BUFFER_USAGE_INDEX_BUFFER_BIT 0x00000040u
#define VK_BUFFER_USAGE_VERTEX_BUFFER_BIT 0x00000080u

#define VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT 0x00000001u
#define VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT 0x00000002u
#define VK_MEMORY_PROPERTY_HOST_COHERENT_BIT 0x00000004u

#define VK_IMAGE_ASPECT_COLOR_BIT 0x00000001u
#define VK_IMAGE_ASPECT_DEPTH_BIT 0x00000002u

#define VK_ACCESS_SHADER_READ_BIT 0x00000020u
#define VK_ACCESS_COLOR_ATTACHMENT_READ_BIT 0x00000080u
#define VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT 0x00000100u
#define VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT 0x00000200u
#define VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT 0x00000400u
#define VK_ACCESS_TRANSFER_READ_BIT 0x00000800u
#define VK_ACCESS_TRANSFER_WRITE_BIT 0x00001000u

#define VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT 0x00000001u
#define VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT 0x00000100u
#define VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT 0x00000200u
#define VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT 0x00000400u
#define VK_PIPELINE_STAGE_TRANSFER_BIT 0x00001000u
#define VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT 0x00002000u
#define VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT 0x00000080u

#define VK_DEPENDENCY_BY_REGION_BIT 0x00000001u

#define VK_QUEUE_GRAPHICS_BIT 0x00000001u
#define VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT 0x00000002u
#define VK_COMMAND_BUFFER_LEVEL_PRIMARY 0
#define VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT 0x00000001u
#define VK_FENCE_CREATE_SIGNALED_BIT 0x00000001u

#define VK_ATTACHMENT_LOAD_OP_LOAD 0
#define VK_ATTACHMENT_LOAD_OP_CLEAR 1
#define VK_ATTACHMENT_LOAD_OP_DONT_CARE 2
#define VK_ATTACHMENT_STORE_OP_STORE 0
#define VK_ATTACHMENT_STORE_OP_DONT_CARE 1

#define VK_PIPELINE_BIND_POINT_GRAPHICS 0
#define VK_SUBPASS_CONTENTS_INLINE 0
#define VK_PRIMITIVE_TOPOLOGY_LINE_LIST 1
#define VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST 3
#define VK_PRIMITIVE_TOPOLOGY_TRIANGLE_STRIP 4
#define VK_POLYGON_MODE_FILL 0
#define VK_CULL_MODE_NONE 0
#define VK_FRONT_FACE_COUNTER_CLOCKWISE 1
#define VK_COMPARE_OP_LESS_OR_EQUAL 3
#define VK_SAMPLE_COUNT_1_BIT 0x00000001u

#define VK_BLEND_FACTOR_ZERO 0
#define VK_BLEND_FACTOR_ONE 1
#define VK_BLEND_FACTOR_SRC_ALPHA 6
#define VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA 7
#define VK_BLEND_OP_ADD 0
#define VK_COLOR_COMPONENT_R_BIT 0x00000001u
#define VK_COLOR_COMPONENT_G_BIT 0x00000002u
#define VK_COLOR_COMPONENT_B_BIT 0x00000004u
#define VK_COLOR_COMPONENT_A_BIT 0x00000008u

#define VK_DYNAMIC_STATE_VIEWPORT 0
#define VK_DYNAMIC_STATE_SCISSOR 1
#define VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER 1
#define VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER 6
#define VK_SHADER_STAGE_VERTEX_BIT 0x00000001u
#define VK_SHADER_STAGE_FRAGMENT_BIT 0x00000010u

#define VK_FILTER_NEAREST 0
#define VK_FILTER_LINEAR 1
#define VK_SAMPLER_MIPMAP_MODE_NEAREST 0
#define VK_SAMPLER_MIPMAP_MODE_LINEAR 1
#define VK_SAMPLER_ADDRESS_MODE_REPEAT 0
#define VK_SAMPLER_ADDRESS_MODE_MIRRORED_REPEAT 1
#define VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE 2
#define VK_BORDER_COLOR_FLOAT_OPAQUE_BLACK 1
#define VK_INDEX_TYPE_UINT16 0
#define VK_VERTEX_INPUT_RATE_VERTEX 0
#define VK_VERTEX_INPUT_RATE_INSTANCE 1

#define VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR 0x00000001u
#define VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR 0x00000002u
#define VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR 0x00000004u
#define VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR 0x00000008u

typedef uint32_t VkBool32;
typedef uint32_t VkFlags;
typedef uint64_t VkDeviceSize;
typedef int32_t VkResult;
typedef int32_t VkStructureType;
typedef int32_t VkFormat;
typedef int32_t VkColorSpaceKHR;
typedef int32_t VkPresentModeKHR;
typedef int32_t VkImageLayout;
typedef int32_t VkAttachmentLoadOp;
typedef int32_t VkAttachmentStoreOp;
typedef int32_t VkSamplerAddressMode;
typedef uint32_t VkAccessFlags;
typedef uint32_t VkBufferUsageFlags;
typedef uint32_t VkImageUsageFlags;
typedef uint32_t VkImageAspectFlags;
typedef uint32_t VkMemoryPropertyFlags;
typedef uint32_t VkPipelineStageFlags;
typedef uint32_t VkShaderStageFlags;
typedef uint32_t VkSampleCountFlagBits;
typedef uint32_t VkCompositeAlphaFlagBitsKHR;
typedef uint32_t VkDynamicState;
typedef uint32_t VkQueueFlags;

typedef struct VkInstance_T* VkInstance;
typedef struct VkPhysicalDevice_T* VkPhysicalDevice;
typedef struct VkDevice_T* VkDevice;
typedef struct VkQueue_T* VkQueue;
typedef struct VkCommandBuffer_T* VkCommandBuffer;

typedef uint64_t VkBuffer;
typedef uint64_t VkCommandPool;
typedef uint64_t VkDescriptorPool;
typedef uint64_t VkDescriptorSet;
typedef uint64_t VkDescriptorSetLayout;
typedef uint64_t VkDeviceMemory;
typedef uint64_t VkFence;
typedef uint64_t VkFramebuffer;
typedef uint64_t VkImage;
typedef uint64_t VkImageView;
typedef uint64_t VkPipeline;
typedef uint64_t VkPipelineCache;
typedef uint64_t VkPipelineLayout;
typedef uint64_t VkRenderPass;
typedef uint64_t VkSampler;
typedef uint64_t VkSemaphore;
typedef uint64_t VkShaderModule;
typedef uint64_t VkSurfaceKHR;
typedef uint64_t VkSwapchainKHR;
typedef uint64_t VkBufferView;

typedef struct VkAllocationCallbacks VkAllocationCallbacks;
typedef struct VkCommandBufferInheritanceInfo VkCommandBufferInheritanceInfo;
typedef struct VkPipelineTessellationStateCreateInfo VkPipelineTessellationStateCreateInfo;
typedef struct VkPushConstantRange VkPushConstantRange;
typedef struct VkSpecializationInfo VkSpecializationInfo;

typedef struct VkExtent2D {
    uint32_t width;
    uint32_t height;
} VkExtent2D;

typedef struct VkExtent3D {
    uint32_t width;
    uint32_t height;
    uint32_t depth;
} VkExtent3D;

typedef struct VkOffset2D {
    int32_t x;
    int32_t y;
} VkOffset2D;

typedef struct VkOffset3D {
    int32_t x;
    int32_t y;
    int32_t z;
} VkOffset3D;

typedef struct VkRect2D {
    VkOffset2D offset;
    VkExtent2D extent;
} VkRect2D;

typedef struct VkApplicationInfo {
    VkStructureType sType;
    const void* pNext;
    const char* pApplicationName;
    uint32_t applicationVersion;
    const char* pEngineName;
    uint32_t engineVersion;
    uint32_t apiVersion;
} VkApplicationInfo;

typedef struct VkInstanceCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    const VkApplicationInfo* pApplicationInfo;
    uint32_t enabledLayerCount;
    const char* const* ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* ppEnabledExtensionNames;
} VkInstanceCreateInfo;

typedef struct VkExtensionProperties {
    char extensionName[VK_MAX_EXTENSION_NAME_SIZE];
    uint32_t specVersion;
} VkExtensionProperties;

typedef struct VkPhysicalDeviceProperties {
    uint32_t apiVersion;
    uint32_t driverVersion;
    uint32_t vendorID;
    uint32_t deviceID;
    uint32_t deviceType;
    char deviceName[256];
    uint8_t pipelineCacheUUID[VK_UUID_SIZE];
    uint8_t limitsAndSparseProperties[2048];
} VkPhysicalDeviceProperties;

typedef struct VkPhysicalDeviceFeatures {
    VkBool32 fields[64];
} VkPhysicalDeviceFeatures;

typedef struct VkQueueFamilyProperties {
    VkQueueFlags queueFlags;
    uint32_t queueCount;
    uint32_t timestampValidBits;
    VkExtent3D minImageTransferGranularity;
} VkQueueFamilyProperties;

typedef struct VkMemoryType {
    VkMemoryPropertyFlags propertyFlags;
    uint32_t heapIndex;
} VkMemoryType;

typedef struct VkMemoryHeap {
    VkDeviceSize size;
    VkFlags flags;
} VkMemoryHeap;

typedef struct VkPhysicalDeviceMemoryProperties {
    uint32_t memoryTypeCount;
    VkMemoryType memoryTypes[VK_MAX_MEMORY_TYPES];
    uint32_t memoryHeapCount;
    VkMemoryHeap memoryHeaps[VK_MAX_MEMORY_HEAPS];
} VkPhysicalDeviceMemoryProperties;

typedef struct VkMemoryRequirements {
    VkDeviceSize size;
    VkDeviceSize alignment;
    uint32_t memoryTypeBits;
} VkMemoryRequirements;

typedef struct VkSurfaceCapabilitiesKHR {
    uint32_t minImageCount;
    uint32_t maxImageCount;
    VkExtent2D currentExtent;
    VkExtent2D minImageExtent;
    VkExtent2D maxImageExtent;
    uint32_t maxImageArrayLayers;
    VkFlags supportedTransforms;
    VkFlags currentTransform;
    VkFlags supportedCompositeAlpha;
    VkImageUsageFlags supportedUsageFlags;
} VkSurfaceCapabilitiesKHR;

typedef struct VkSurfaceFormatKHR {
    VkFormat format;
    VkColorSpaceKHR colorSpace;
} VkSurfaceFormatKHR;

typedef struct VkAttachmentDescription {
    VkFlags flags;
    VkFormat format;
    VkSampleCountFlagBits samples;
    VkAttachmentLoadOp loadOp;
    VkAttachmentStoreOp storeOp;
    VkAttachmentLoadOp stencilLoadOp;
    VkAttachmentStoreOp stencilStoreOp;
    VkImageLayout initialLayout;
    VkImageLayout finalLayout;
} VkAttachmentDescription;

typedef struct VkAttachmentReference {
    uint32_t attachment;
    VkImageLayout layout;
} VkAttachmentReference;

typedef struct VkSubpassDescription {
    VkFlags flags;
    uint32_t pipelineBindPoint;
    uint32_t inputAttachmentCount;
    const VkAttachmentReference* pInputAttachments;
    uint32_t colorAttachmentCount;
    const VkAttachmentReference* pColorAttachments;
    const VkAttachmentReference* pResolveAttachments;
    const VkAttachmentReference* pDepthStencilAttachment;
    uint32_t preserveAttachmentCount;
    const uint32_t* pPreserveAttachments;
} VkSubpassDescription;

typedef struct VkSubpassDependency {
    uint32_t srcSubpass;
    uint32_t dstSubpass;
    VkPipelineStageFlags srcStageMask;
    VkPipelineStageFlags dstStageMask;
    VkAccessFlags srcAccessMask;
    VkAccessFlags dstAccessMask;
    VkFlags dependencyFlags;
} VkSubpassDependency;

typedef struct VkRenderPassCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t attachmentCount;
    const VkAttachmentDescription* pAttachments;
    uint32_t subpassCount;
    const VkSubpassDescription* pSubpasses;
    uint32_t dependencyCount;
    const VkSubpassDependency* pDependencies;
} VkRenderPassCreateInfo;

typedef struct VkImageSubresourceRange {
    VkImageAspectFlags aspectMask;
    uint32_t baseMipLevel;
    uint32_t levelCount;
    uint32_t baseArrayLayer;
    uint32_t layerCount;
} VkImageSubresourceRange;

typedef struct VkImageSubresourceLayers {
    VkImageAspectFlags aspectMask;
    uint32_t mipLevel;
    uint32_t baseArrayLayer;
    uint32_t layerCount;
} VkImageSubresourceLayers;

typedef struct VkImageCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t imageType;
    VkFormat format;
    VkExtent3D extent;
    uint32_t mipLevels;
    uint32_t arrayLayers;
    VkSampleCountFlagBits samples;
    uint32_t tiling;
    VkImageUsageFlags usage;
    uint32_t sharingMode;
    uint32_t queueFamilyIndexCount;
    const uint32_t* pQueueFamilyIndices;
    VkImageLayout initialLayout;
} VkImageCreateInfo;

typedef struct VkMemoryAllocateInfo {
    VkStructureType sType;
    const void* pNext;
    VkDeviceSize allocationSize;
    uint32_t memoryTypeIndex;
} VkMemoryAllocateInfo;

typedef struct VkComponentMapping {
    uint32_t r;
    uint32_t g;
    uint32_t b;
    uint32_t a;
} VkComponentMapping;

typedef struct VkImageViewCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkImage image;
    uint32_t viewType;
    VkFormat format;
    VkComponentMapping components;
    VkImageSubresourceRange subresourceRange;
} VkImageViewCreateInfo;

typedef struct VkSwapchainCreateInfoKHR {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkSurfaceKHR surface;
    uint32_t minImageCount;
    VkFormat imageFormat;
    VkColorSpaceKHR imageColorSpace;
    VkExtent2D imageExtent;
    uint32_t imageArrayLayers;
    VkImageUsageFlags imageUsage;
    uint32_t imageSharingMode;
    uint32_t queueFamilyIndexCount;
    const uint32_t* pQueueFamilyIndices;
    VkFlags preTransform;
    VkCompositeAlphaFlagBitsKHR compositeAlpha;
    VkPresentModeKHR presentMode;
    VkBool32 clipped;
    VkSwapchainKHR oldSwapchain;
} VkSwapchainCreateInfoKHR;

typedef struct VkFramebufferCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkRenderPass renderPass;
    uint32_t attachmentCount;
    const VkImageView* pAttachments;
    uint32_t width;
    uint32_t height;
    uint32_t layers;
} VkFramebufferCreateInfo;

typedef struct VkDeviceQueueCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t queueFamilyIndex;
    uint32_t queueCount;
    const float* pQueuePriorities;
} VkDeviceQueueCreateInfo;

typedef struct VkDeviceCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t queueCreateInfoCount;
    const VkDeviceQueueCreateInfo* pQueueCreateInfos;
    uint32_t enabledLayerCount;
    const char* const* ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* ppEnabledExtensionNames;
    const VkPhysicalDeviceFeatures* pEnabledFeatures;
} VkDeviceCreateInfo;

typedef struct VkDescriptorPoolSize {
    uint32_t type;
    uint32_t descriptorCount;
} VkDescriptorPoolSize;

typedef struct VkDescriptorPoolCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t maxSets;
    uint32_t poolSizeCount;
    const VkDescriptorPoolSize* pPoolSizes;
} VkDescriptorPoolCreateInfo;

typedef struct VkCommandPoolCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t queueFamilyIndex;
} VkCommandPoolCreateInfo;

typedef struct VkCommandBufferAllocateInfo {
    VkStructureType sType;
    const void* pNext;
    VkCommandPool commandPool;
    uint32_t level;
    uint32_t commandBufferCount;
} VkCommandBufferAllocateInfo;

typedef struct VkSemaphoreCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
} VkSemaphoreCreateInfo;

typedef struct VkFenceCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
} VkFenceCreateInfo;

typedef struct VkBufferCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkDeviceSize size;
    VkBufferUsageFlags usage;
    uint32_t sharingMode;
    uint32_t queueFamilyIndexCount;
    const uint32_t* pQueueFamilyIndices;
} VkBufferCreateInfo;

typedef struct VkImageMemoryBarrier {
    VkStructureType sType;
    const void* pNext;
    VkAccessFlags srcAccessMask;
    VkAccessFlags dstAccessMask;
    VkImageLayout oldLayout;
    VkImageLayout newLayout;
    uint32_t srcQueueFamilyIndex;
    uint32_t dstQueueFamilyIndex;
    VkImage image;
    VkImageSubresourceRange subresourceRange;
} VkImageMemoryBarrier;

typedef struct VkBufferImageCopy {
    VkDeviceSize bufferOffset;
    uint32_t bufferRowLength;
    uint32_t bufferImageHeight;
    VkImageSubresourceLayers imageSubresource;
    VkOffset3D imageOffset;
    VkExtent3D imageExtent;
} VkBufferImageCopy;

typedef struct VkSubmitInfo {
    VkStructureType sType;
    const void* pNext;
    uint32_t waitSemaphoreCount;
    const VkSemaphore* pWaitSemaphores;
    const VkPipelineStageFlags* pWaitDstStageMask;
    uint32_t commandBufferCount;
    const VkCommandBuffer* pCommandBuffers;
    uint32_t signalSemaphoreCount;
    const VkSemaphore* pSignalSemaphores;
} VkSubmitInfo;

typedef struct VkPresentInfoKHR {
    VkStructureType sType;
    const void* pNext;
    uint32_t waitSemaphoreCount;
    const VkSemaphore* pWaitSemaphores;
    uint32_t swapchainCount;
    const VkSwapchainKHR* pSwapchains;
    const uint32_t* pImageIndices;
    VkResult* pResults;
} VkPresentInfoKHR;

typedef struct VkShaderModuleCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    size_t codeSize;
    const uint32_t* pCode;
} VkShaderModuleCreateInfo;

typedef struct VkCommandBufferBeginInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    const VkCommandBufferInheritanceInfo* pInheritanceInfo;
} VkCommandBufferBeginInfo;

typedef struct VkSamplerCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t magFilter;
    uint32_t minFilter;
    uint32_t mipmapMode;
    VkSamplerAddressMode addressModeU;
    VkSamplerAddressMode addressModeV;
    VkSamplerAddressMode addressModeW;
    float mipLodBias;
    VkBool32 anisotropyEnable;
    float maxAnisotropy;
    VkBool32 compareEnable;
    uint32_t compareOp;
    float minLod;
    float maxLod;
    uint32_t borderColor;
    VkBool32 unnormalizedCoordinates;
} VkSamplerCreateInfo;

typedef struct VkDescriptorSetAllocateInfo {
    VkStructureType sType;
    const void* pNext;
    VkDescriptorPool descriptorPool;
    uint32_t descriptorSetCount;
    const VkDescriptorSetLayout* pSetLayouts;
} VkDescriptorSetAllocateInfo;

typedef struct VkDescriptorSetLayoutBinding {
    uint32_t binding;
    uint32_t descriptorType;
    uint32_t descriptorCount;
    VkShaderStageFlags stageFlags;
    const VkSampler* pImmutableSamplers;
} VkDescriptorSetLayoutBinding;

typedef struct VkDescriptorSetLayoutCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t bindingCount;
    const VkDescriptorSetLayoutBinding* pBindings;
} VkDescriptorSetLayoutCreateInfo;

typedef struct VkDescriptorImageInfo {
    VkSampler sampler;
    VkImageView imageView;
    VkImageLayout imageLayout;
} VkDescriptorImageInfo;

typedef struct VkDescriptorBufferInfo {
    VkBuffer buffer;
    VkDeviceSize offset;
    VkDeviceSize range;
} VkDescriptorBufferInfo;

typedef struct VkWriteDescriptorSet {
    VkStructureType sType;
    const void* pNext;
    VkDescriptorSet dstSet;
    uint32_t dstBinding;
    uint32_t dstArrayElement;
    uint32_t descriptorCount;
    uint32_t descriptorType;
    const VkDescriptorImageInfo* pImageInfo;
    const VkDescriptorBufferInfo* pBufferInfo;
    const VkBufferView* pTexelBufferView;
} VkWriteDescriptorSet;

typedef struct VkPipelineShaderStageCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkShaderStageFlags stage;
    VkShaderModule module;
    const char* pName;
    const VkSpecializationInfo* pSpecializationInfo;
} VkPipelineShaderStageCreateInfo;

typedef struct VkVertexInputBindingDescription {
    uint32_t binding;
    uint32_t stride;
    uint32_t inputRate;
} VkVertexInputBindingDescription;

typedef struct VkVertexInputAttributeDescription {
    uint32_t location;
    uint32_t binding;
    VkFormat format;
    uint32_t offset;
} VkVertexInputAttributeDescription;

typedef struct VkPipelineVertexInputStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t vertexBindingDescriptionCount;
    const VkVertexInputBindingDescription* pVertexBindingDescriptions;
    uint32_t vertexAttributeDescriptionCount;
    const VkVertexInputAttributeDescription* pVertexAttributeDescriptions;
} VkPipelineVertexInputStateCreateInfo;

typedef struct VkPipelineInputAssemblyStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t topology;
    VkBool32 primitiveRestartEnable;
} VkPipelineInputAssemblyStateCreateInfo;

typedef struct VkViewport {
    float x;
    float y;
    float width;
    float height;
    float minDepth;
    float maxDepth;
} VkViewport;

typedef struct VkPipelineViewportStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t viewportCount;
    const VkViewport* pViewports;
    uint32_t scissorCount;
    const VkRect2D* pScissors;
} VkPipelineViewportStateCreateInfo;

typedef struct VkPipelineRasterizationStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkBool32 depthClampEnable;
    VkBool32 rasterizerDiscardEnable;
    uint32_t polygonMode;
    VkFlags cullMode;
    uint32_t frontFace;
    VkBool32 depthBiasEnable;
    float depthBiasConstantFactor;
    float depthBiasClamp;
    float depthBiasSlopeFactor;
    float lineWidth;
} VkPipelineRasterizationStateCreateInfo;

typedef struct VkPipelineMultisampleStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkSampleCountFlagBits rasterizationSamples;
    VkBool32 sampleShadingEnable;
    float minSampleShading;
    const uint32_t* pSampleMask;
    VkBool32 alphaToCoverageEnable;
    VkBool32 alphaToOneEnable;
} VkPipelineMultisampleStateCreateInfo;

typedef struct VkPipelineColorBlendAttachmentState {
    VkBool32 blendEnable;
    uint32_t srcColorBlendFactor;
    uint32_t dstColorBlendFactor;
    uint32_t colorBlendOp;
    uint32_t srcAlphaBlendFactor;
    uint32_t dstAlphaBlendFactor;
    uint32_t alphaBlendOp;
    VkFlags colorWriteMask;
} VkPipelineColorBlendAttachmentState;

typedef struct VkPipelineColorBlendStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkBool32 logicOpEnable;
    uint32_t logicOp;
    uint32_t attachmentCount;
    const VkPipelineColorBlendAttachmentState* pAttachments;
    float blendConstants[4];
} VkPipelineColorBlendStateCreateInfo;

typedef struct VkPipelineDynamicStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t dynamicStateCount;
    const VkDynamicState* pDynamicStates;
} VkPipelineDynamicStateCreateInfo;

typedef struct VkStencilOpState {
    uint32_t failOp;
    uint32_t passOp;
    uint32_t depthFailOp;
    uint32_t compareOp;
    uint32_t compareMask;
    uint32_t writeMask;
    uint32_t reference;
} VkStencilOpState;

typedef struct VkPipelineDepthStencilStateCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    VkBool32 depthTestEnable;
    VkBool32 depthWriteEnable;
    uint32_t depthCompareOp;
    VkBool32 depthBoundsTestEnable;
    VkBool32 stencilTestEnable;
    VkStencilOpState front;
    VkStencilOpState back;
    float minDepthBounds;
    float maxDepthBounds;
} VkPipelineDepthStencilStateCreateInfo;

typedef struct VkPipelineLayoutCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t setLayoutCount;
    const VkDescriptorSetLayout* pSetLayouts;
    uint32_t pushConstantRangeCount;
    const VkPushConstantRange* pPushConstantRanges;
} VkPipelineLayoutCreateInfo;

typedef struct VkGraphicsPipelineCreateInfo {
    VkStructureType sType;
    const void* pNext;
    VkFlags flags;
    uint32_t stageCount;
    const VkPipelineShaderStageCreateInfo* pStages;
    const VkPipelineVertexInputStateCreateInfo* pVertexInputState;
    const VkPipelineInputAssemblyStateCreateInfo* pInputAssemblyState;
    const VkPipelineTessellationStateCreateInfo* pTessellationState;
    const VkPipelineViewportStateCreateInfo* pViewportState;
    const VkPipelineRasterizationStateCreateInfo* pRasterizationState;
    const VkPipelineMultisampleStateCreateInfo* pMultisampleState;
    const VkPipelineDepthStencilStateCreateInfo* pDepthStencilState;
    const VkPipelineColorBlendStateCreateInfo* pColorBlendState;
    const VkPipelineDynamicStateCreateInfo* pDynamicState;
    VkPipelineLayout layout;
    VkRenderPass renderPass;
    uint32_t subpass;
    VkPipeline basePipelineHandle;
    int32_t basePipelineIndex;
} VkGraphicsPipelineCreateInfo;

typedef union VkClearColorValue {
    float float32[4];
    int32_t int32[4];
    uint32_t uint32[4];
} VkClearColorValue;

typedef struct VkClearDepthStencilValue {
    float depth;
    uint32_t stencil;
} VkClearDepthStencilValue;

typedef union VkClearValue {
    VkClearColorValue color;
    VkClearDepthStencilValue depthStencil;
} VkClearValue;

typedef struct VkRenderPassBeginInfo {
    VkStructureType sType;
    const void* pNext;
    VkRenderPass renderPass;
    VkFramebuffer framebuffer;
    VkRect2D renderArea;
    uint32_t clearValueCount;
    const VkClearValue* pClearValues;
} VkRenderPassBeginInfo;

typedef void (VKAPI_PTR *PFN_vkVoidFunction)(void);
typedef PFN_vkVoidFunction (VKAPI_PTR *PFN_vkGetInstanceProcAddr)(VkInstance instance, const char* pName);
typedef PFN_vkVoidFunction (VKAPI_PTR *PFN_vkGetDeviceProcAddr)(VkDevice device, const char* pName);

#define LIBFDX_VULKAN_FUNCTIONS(_) \
    _(vkAcquireNextImageKHR) \
    _(vkAllocateCommandBuffers) \
    _(vkAllocateDescriptorSets) \
    _(vkAllocateMemory) \
    _(vkBeginCommandBuffer) \
    _(vkBindBufferMemory) \
    _(vkBindImageMemory) \
    _(vkCmdBeginRenderPass) \
    _(vkCmdBindDescriptorSets) \
    _(vkCmdBindIndexBuffer) \
    _(vkCmdBindPipeline) \
    _(vkCmdBindVertexBuffers) \
    _(vkCmdCopyBufferToImage) \
    _(vkCmdCopyImageToBuffer) \
    _(vkCmdDraw) \
    _(vkCmdDrawIndexed) \
    _(vkCmdEndRenderPass) \
    _(vkCmdPipelineBarrier) \
    _(vkCmdSetScissor) \
    _(vkCmdSetViewport) \
    _(vkCreateBuffer) \
    _(vkCreateCommandPool) \
    _(vkCreateDescriptorPool) \
    _(vkCreateDescriptorSetLayout) \
    _(vkCreateDevice) \
    _(vkCreateFence) \
    _(vkCreateFramebuffer) \
    _(vkCreateGraphicsPipelines) \
    _(vkCreateImage) \
    _(vkCreateImageView) \
    _(vkCreateInstance) \
    _(vkCreatePipelineLayout) \
    _(vkCreateRenderPass) \
    _(vkCreateSampler) \
    _(vkCreateSemaphore) \
    _(vkCreateShaderModule) \
    _(vkCreateSwapchainKHR) \
    _(vkDestroyBuffer) \
    _(vkDestroyCommandPool) \
    _(vkDestroyDescriptorPool) \
    _(vkDestroyDescriptorSetLayout) \
    _(vkDestroyDevice) \
    _(vkDestroyFence) \
    _(vkDestroyFramebuffer) \
    _(vkDestroyImage) \
    _(vkDestroyImageView) \
    _(vkDestroyInstance) \
    _(vkDestroyPipeline) \
    _(vkDestroyPipelineLayout) \
    _(vkDestroyRenderPass) \
    _(vkDestroySampler) \
    _(vkDestroySemaphore) \
    _(vkDestroyShaderModule) \
    _(vkDestroySurfaceKHR) \
    _(vkDestroySwapchainKHR) \
    _(vkDeviceWaitIdle) \
    _(vkEndCommandBuffer) \
    _(vkEnumerateDeviceExtensionProperties) \
    _(vkEnumerateInstanceExtensionProperties) \
    _(vkEnumeratePhysicalDevices) \
    _(vkFreeCommandBuffers) \
    _(vkFreeMemory) \
    _(vkGetBufferMemoryRequirements) \
    _(vkGetDeviceQueue) \
    _(vkGetImageMemoryRequirements) \
    _(vkGetPhysicalDeviceMemoryProperties) \
    _(vkGetPhysicalDeviceProperties) \
    _(vkGetPhysicalDeviceQueueFamilyProperties) \
    _(vkGetPhysicalDeviceSurfaceCapabilitiesKHR) \
    _(vkGetPhysicalDeviceSurfaceFormatsKHR) \
    _(vkGetPhysicalDeviceSurfacePresentModesKHR) \
    _(vkGetPhysicalDeviceSurfaceSupportKHR) \
    _(vkGetSwapchainImagesKHR) \
    _(vkMapMemory) \
    _(vkQueuePresentKHR) \
    _(vkQueueSubmit) \
    _(vkQueueWaitIdle) \
    _(vkResetCommandBuffer) \
    _(vkResetDescriptorPool) \
    _(vkResetFences) \
    _(vkUnmapMemory) \
    _(vkUpdateDescriptorSets) \
    _(vkWaitForFences)

#ifdef __cplusplus
extern "C" {
#endif

VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance instance, const char* pName);
VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetDeviceProcAddr(VkDevice device, const char* pName);
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateInstanceExtensionProperties(const char* pLayerName, uint32_t* pPropertyCount, VkExtensionProperties* pProperties);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateInstance(const VkInstanceCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkInstance* pInstance);
VKAPI_ATTR void VKAPI_CALL vkDestroyInstance(VkInstance instance, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkEnumeratePhysicalDevices(VkInstance instance, uint32_t* pPhysicalDeviceCount, VkPhysicalDevice* pPhysicalDevices);
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceProperties(VkPhysicalDevice physicalDevice, VkPhysicalDeviceProperties* pProperties);
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceQueueFamilyProperties(VkPhysicalDevice physicalDevice, uint32_t* pQueueFamilyPropertyCount, VkQueueFamilyProperties* pQueueFamilyProperties);
VKAPI_ATTR VkResult VKAPI_CALL vkGetPhysicalDeviceSurfaceSupportKHR(VkPhysicalDevice physicalDevice, uint32_t queueFamilyIndex, VkSurfaceKHR surface, VkBool32* pSupported);
VKAPI_ATTR VkResult VKAPI_CALL vkGetPhysicalDeviceSurfaceCapabilitiesKHR(VkPhysicalDevice physicalDevice, VkSurfaceKHR surface, VkSurfaceCapabilitiesKHR* pSurfaceCapabilities);
VKAPI_ATTR VkResult VKAPI_CALL vkGetPhysicalDeviceSurfaceFormatsKHR(VkPhysicalDevice physicalDevice, VkSurfaceKHR surface, uint32_t* pSurfaceFormatCount, VkSurfaceFormatKHR* pSurfaceFormats);
VKAPI_ATTR VkResult VKAPI_CALL vkGetPhysicalDeviceSurfacePresentModesKHR(VkPhysicalDevice physicalDevice, VkSurfaceKHR surface, uint32_t* pPresentModeCount, VkPresentModeKHR* pPresentModes);
VKAPI_ATTR VkResult VKAPI_CALL vkEnumerateDeviceExtensionProperties(VkPhysicalDevice physicalDevice, const char* pLayerName, uint32_t* pPropertyCount, VkExtensionProperties* pProperties);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateDevice(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkDevice* pDevice);
VKAPI_ATTR void VKAPI_CALL vkDestroyDevice(VkDevice device, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR void VKAPI_CALL vkGetDeviceQueue(VkDevice device, uint32_t queueFamilyIndex, uint32_t queueIndex, VkQueue* pQueue);
VKAPI_ATTR VkResult VKAPI_CALL vkDeviceWaitIdle(VkDevice device);
VKAPI_ATTR void VKAPI_CALL vkDestroySurfaceKHR(VkInstance instance, VkSurfaceKHR surface, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR void VKAPI_CALL vkGetPhysicalDeviceMemoryProperties(VkPhysicalDevice physicalDevice, VkPhysicalDeviceMemoryProperties* pMemoryProperties);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateSwapchainKHR(VkDevice device, const VkSwapchainCreateInfoKHR* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkSwapchainKHR* pSwapchain);
VKAPI_ATTR void VKAPI_CALL vkDestroySwapchainKHR(VkDevice device, VkSwapchainKHR swapchain, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkGetSwapchainImagesKHR(VkDevice device, VkSwapchainKHR swapchain, uint32_t* pSwapchainImageCount, VkImage* pSwapchainImages);
VKAPI_ATTR VkResult VKAPI_CALL vkAcquireNextImageKHR(VkDevice device, VkSwapchainKHR swapchain, uint64_t timeout, VkSemaphore semaphore, VkFence fence, uint32_t* pImageIndex);
VKAPI_ATTR VkResult VKAPI_CALL vkQueuePresentKHR(VkQueue queue, const VkPresentInfoKHR* pPresentInfo);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateImage(VkDevice device, const VkImageCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkImage* pImage);
VKAPI_ATTR void VKAPI_CALL vkDestroyImage(VkDevice device, VkImage image, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR void VKAPI_CALL vkGetImageMemoryRequirements(VkDevice device, VkImage image, VkMemoryRequirements* pMemoryRequirements);
VKAPI_ATTR VkResult VKAPI_CALL vkBindImageMemory(VkDevice device, VkImage image, VkDeviceMemory memory, VkDeviceSize memoryOffset);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateImageView(VkDevice device, const VkImageViewCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkImageView* pView);
VKAPI_ATTR void VKAPI_CALL vkDestroyImageView(VkDevice device, VkImageView imageView, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkAllocateMemory(VkDevice device, const VkMemoryAllocateInfo* pAllocateInfo, const VkAllocationCallbacks* pAllocator, VkDeviceMemory* pMemory);
VKAPI_ATTR void VKAPI_CALL vkFreeMemory(VkDevice device, VkDeviceMemory memory, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateRenderPass(VkDevice device, const VkRenderPassCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkRenderPass* pRenderPass);
VKAPI_ATTR void VKAPI_CALL vkDestroyRenderPass(VkDevice device, VkRenderPass renderPass, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateFramebuffer(VkDevice device, const VkFramebufferCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkFramebuffer* pFramebuffer);
VKAPI_ATTR void VKAPI_CALL vkDestroyFramebuffer(VkDevice device, VkFramebuffer framebuffer, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateCommandPool(VkDevice device, const VkCommandPoolCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkCommandPool* pCommandPool);
VKAPI_ATTR void VKAPI_CALL vkDestroyCommandPool(VkDevice device, VkCommandPool commandPool, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkAllocateCommandBuffers(VkDevice device, const VkCommandBufferAllocateInfo* pAllocateInfo, VkCommandBuffer* pCommandBuffers);
VKAPI_ATTR void VKAPI_CALL vkFreeCommandBuffers(VkDevice device, VkCommandPool commandPool, uint32_t commandBufferCount, const VkCommandBuffer* pCommandBuffers);
VKAPI_ATTR VkResult VKAPI_CALL vkBeginCommandBuffer(VkCommandBuffer commandBuffer, const VkCommandBufferBeginInfo* pBeginInfo);
VKAPI_ATTR VkResult VKAPI_CALL vkEndCommandBuffer(VkCommandBuffer commandBuffer);
VKAPI_ATTR VkResult VKAPI_CALL vkResetCommandBuffer(VkCommandBuffer commandBuffer, VkFlags flags);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateSemaphore(VkDevice device, const VkSemaphoreCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkSemaphore* pSemaphore);
VKAPI_ATTR void VKAPI_CALL vkDestroySemaphore(VkDevice device, VkSemaphore semaphore, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateFence(VkDevice device, const VkFenceCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkFence* pFence);
VKAPI_ATTR void VKAPI_CALL vkDestroyFence(VkDevice device, VkFence fence, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkWaitForFences(VkDevice device, uint32_t fenceCount, const VkFence* pFences, VkBool32 waitAll, uint64_t timeout);
VKAPI_ATTR VkResult VKAPI_CALL vkResetFences(VkDevice device, uint32_t fenceCount, const VkFence* pFences);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateDescriptorPool(VkDevice device, const VkDescriptorPoolCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkDescriptorPool* pDescriptorPool);
VKAPI_ATTR void VKAPI_CALL vkDestroyDescriptorPool(VkDevice device, VkDescriptorPool descriptorPool, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkResetDescriptorPool(VkDevice device, VkDescriptorPool descriptorPool, VkFlags flags);
VKAPI_ATTR VkResult VKAPI_CALL vkAllocateDescriptorSets(VkDevice device, const VkDescriptorSetAllocateInfo* pAllocateInfo, VkDescriptorSet* pDescriptorSets);
VKAPI_ATTR void VKAPI_CALL vkUpdateDescriptorSets(VkDevice device, uint32_t descriptorWriteCount, const VkWriteDescriptorSet* pDescriptorWrites, uint32_t descriptorCopyCount, const void* pDescriptorCopies);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateDescriptorSetLayout(VkDevice device, const VkDescriptorSetLayoutCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkDescriptorSetLayout* pSetLayout);
VKAPI_ATTR void VKAPI_CALL vkDestroyDescriptorSetLayout(VkDevice device, VkDescriptorSetLayout descriptorSetLayout, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateBuffer(VkDevice device, const VkBufferCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkBuffer* pBuffer);
VKAPI_ATTR void VKAPI_CALL vkDestroyBuffer(VkDevice device, VkBuffer buffer, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR void VKAPI_CALL vkGetBufferMemoryRequirements(VkDevice device, VkBuffer buffer, VkMemoryRequirements* pMemoryRequirements);
VKAPI_ATTR VkResult VKAPI_CALL vkBindBufferMemory(VkDevice device, VkBuffer buffer, VkDeviceMemory memory, VkDeviceSize memoryOffset);
VKAPI_ATTR VkResult VKAPI_CALL vkMapMemory(VkDevice device, VkDeviceMemory memory, VkDeviceSize offset, VkDeviceSize size, VkFlags flags, void** ppData);
VKAPI_ATTR void VKAPI_CALL vkUnmapMemory(VkDevice device, VkDeviceMemory memory);
VKAPI_ATTR VkResult VKAPI_CALL vkQueueSubmit(VkQueue queue, uint32_t submitCount, const VkSubmitInfo* pSubmits, VkFence fence);
VKAPI_ATTR VkResult VKAPI_CALL vkQueueWaitIdle(VkQueue queue);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateShaderModule(VkDevice device, const VkShaderModuleCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkShaderModule* pShaderModule);
VKAPI_ATTR void VKAPI_CALL vkDestroyShaderModule(VkDevice device, VkShaderModule shaderModule, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateSampler(VkDevice device, const VkSamplerCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkSampler* pSampler);
VKAPI_ATTR void VKAPI_CALL vkDestroySampler(VkDevice device, VkSampler sampler, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreatePipelineLayout(VkDevice device, const VkPipelineLayoutCreateInfo* pCreateInfo, const VkAllocationCallbacks* pAllocator, VkPipelineLayout* pPipelineLayout);
VKAPI_ATTR void VKAPI_CALL vkDestroyPipelineLayout(VkDevice device, VkPipelineLayout pipelineLayout, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR VkResult VKAPI_CALL vkCreateGraphicsPipelines(VkDevice device, VkPipelineCache pipelineCache, uint32_t createInfoCount, const VkGraphicsPipelineCreateInfo* pCreateInfos, const VkAllocationCallbacks* pAllocator, VkPipeline* pPipelines);
VKAPI_ATTR void VKAPI_CALL vkDestroyPipeline(VkDevice device, VkPipeline pipeline, const VkAllocationCallbacks* pAllocator);
VKAPI_ATTR void VKAPI_CALL vkCmdBeginRenderPass(VkCommandBuffer commandBuffer, const VkRenderPassBeginInfo* pRenderPassBegin, uint32_t contents);
VKAPI_ATTR void VKAPI_CALL vkCmdEndRenderPass(VkCommandBuffer commandBuffer);
VKAPI_ATTR void VKAPI_CALL vkCmdSetViewport(VkCommandBuffer commandBuffer, uint32_t firstViewport, uint32_t viewportCount, const VkViewport* pViewports);
VKAPI_ATTR void VKAPI_CALL vkCmdSetScissor(VkCommandBuffer commandBuffer, uint32_t firstScissor, uint32_t scissorCount, const VkRect2D* pScissors);
VKAPI_ATTR void VKAPI_CALL vkCmdBindPipeline(VkCommandBuffer commandBuffer, uint32_t pipelineBindPoint, VkPipeline pipeline);
VKAPI_ATTR void VKAPI_CALL vkCmdBindVertexBuffers(VkCommandBuffer commandBuffer, uint32_t firstBinding, uint32_t bindingCount, const VkBuffer* pBuffers, const VkDeviceSize* pOffsets);
VKAPI_ATTR void VKAPI_CALL vkCmdBindIndexBuffer(VkCommandBuffer commandBuffer, VkBuffer buffer, VkDeviceSize offset, uint32_t indexType);
VKAPI_ATTR void VKAPI_CALL vkCmdBindDescriptorSets(VkCommandBuffer commandBuffer, uint32_t pipelineBindPoint, VkPipelineLayout layout, uint32_t firstSet, uint32_t descriptorSetCount, const VkDescriptorSet* pDescriptorSets, uint32_t dynamicOffsetCount, const uint32_t* pDynamicOffsets);
VKAPI_ATTR void VKAPI_CALL vkCmdDraw(VkCommandBuffer commandBuffer, uint32_t vertexCount, uint32_t instanceCount, uint32_t firstVertex, uint32_t firstInstance);
VKAPI_ATTR void VKAPI_CALL vkCmdDrawIndexed(VkCommandBuffer commandBuffer, uint32_t indexCount, uint32_t instanceCount, uint32_t firstIndex, int32_t vertexOffset, uint32_t firstInstance);
VKAPI_ATTR void VKAPI_CALL vkCmdPipelineBarrier(VkCommandBuffer commandBuffer, VkPipelineStageFlags srcStageMask, VkPipelineStageFlags dstStageMask, VkFlags dependencyFlags, uint32_t memoryBarrierCount, const void* pMemoryBarriers, uint32_t bufferMemoryBarrierCount, const void* pBufferMemoryBarriers, uint32_t imageMemoryBarrierCount, const VkImageMemoryBarrier* pImageMemoryBarriers);
VKAPI_ATTR void VKAPI_CALL vkCmdCopyImageToBuffer(VkCommandBuffer commandBuffer, VkImage srcImage, VkImageLayout srcImageLayout, VkBuffer dstBuffer, uint32_t regionCount, const VkBufferImageCopy* pRegions);
VKAPI_ATTR void VKAPI_CALL vkCmdCopyBufferToImage(VkCommandBuffer commandBuffer, VkBuffer srcBuffer, VkImage dstImage, VkImageLayout dstImageLayout, uint32_t regionCount, const VkBufferImageCopy* pRegions);

#ifdef __cplusplus
}
#endif

#endif
