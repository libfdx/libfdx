#pragma once

// Included only by an explicitly enabled debug test build. No invalid GPU work is submitted.
namespace vulkan_loss_test {
inline std::atomic<int> site{0}, result{VK_ERROR_DEVICE_LOST}, calls{0}, destructions{0};
inline VkDevice device = VK_NULL_HANDLE;
inline bool take(int expected) {
    if (!site.compare_exchange_strong(expected, 0)) return false;
    calls.fetch_add(1);
    return true;
}
inline VkResult pipelines(VkDevice device, VkPipelineCache cache, uint32_t count,
        const VkGraphicsPipelineCreateInfo* info, const VkAllocationCallbacks* allocator, VkPipeline* output) {
    if (take(1)) { std::fill(output, output + count, VK_NULL_HANDLE); return static_cast<VkResult>(result.load()); }
    return vkCreateGraphicsPipelines(device, cache, count, info, allocator, output);
}
inline VkResult cache(VkDevice device, const VkPipelineCacheCreateInfo* info,
        const VkAllocationCallbacks* allocator, VkPipelineCache* output) {
    if (take(2)) { *output = VK_NULL_HANDLE; return static_cast<VkResult>(result.load()); }
    return vkCreatePipelineCache(device, info, allocator, output);
}
inline VkResult snapshot(VkDevice device, VkPipelineCache cache, size_t* size, void* output) {
    if (take(3)) return static_cast<VkResult>(result.load());
    return vkGetPipelineCacheData(device, cache, size, output);
}
inline VkResult acquire(VkDevice device, VkSwapchainKHR swapchain, uint64_t timeout,
        VkSemaphore semaphore, VkFence fence, uint32_t* output) {
    if (take(4)) return static_cast<VkResult>(result.load());
    return vkAcquireNextImageKHR(device, swapchain, timeout, semaphore, fence, output);
}
inline VkResult submit(VkQueue queue, uint32_t count, const VkSubmitInfo* info, VkFence fence) {
    if (take(5)) return static_cast<VkResult>(result.load());
    return vkQueueSubmit(queue, count, info, fence);
}
inline VkResult present(VkQueue queue, const VkPresentInfoKHR* info) {
    if (take(6)) {
        // Submission really happened, so drain it before faking terminal loss.
        VkResult idle = vkDeviceWaitIdle(device);
        return idle == VK_SUCCESS ? static_cast<VkResult>(result.load()) : idle;
    }
    return vkQueuePresentKHR(queue, info);
}
inline void destroy(VkDevice value, const VkAllocationCallbacks* allocator) {
    if (value == device) destructions.fetch_add(1);
    vkDestroyDevice(value, allocator);
}
}
#define vkCreateGraphicsPipelines vulkan_loss_test::pipelines
#define vkCreatePipelineCache vulkan_loss_test::cache
#define vkGetPipelineCacheData vulkan_loss_test::snapshot
#define vkAcquireNextImageKHR vulkan_loss_test::acquire
#define vkQueueSubmit vulkan_loss_test::submit
#define vkQueuePresentKHR vulkan_loss_test::present
#define vkDestroyDevice vulkan_loss_test::destroy
