package io.github.libfdx.backend.desktop;

import io.github.libfdx.backend.desktop.DesktopVulkanProvider.VulkanResourceDomain;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.runtime.ShaderArtifactCache;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheKey;
import io.github.libfdx.graphics.shader.runtime.ShaderCacheLayer;
import io.github.libfdx.graphics.shader.runtime.ShaderPreparationTrace;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.EXTPipelineCreationFeedback;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.lwjgl.vulkan.VkPipelineCreationFeedbackEXT;
import org.lwjgl.vulkan.VkPipelineCreationFeedbackCreateInfoEXT;

/** One internally synchronized Vulkan cache per preparation queue. All preparation and snapshots
 * run on workers. The domain retains the device until jobs finish, then destroys this cache before
 * destroying the device. Application snapshot/merge locks are not shared with rendering or pipeline creation. */
final class DesktopVulkanPipelineCache {
    private final VulkanResourceDomain domain;
    private final VkDevice device;
    private final VkPhysicalDevice physicalDevice;
    private final ShaderArtifactCache artifacts;
    private final Consumer<Runnable> execute;
    private final Runnable retainSave, releaseSave;
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();
    private final AtomicInteger mutations = new AtomicInteger();
    private final Object snapshots = new Object();
    private FdxFuture<DesktopVulkanPipelineCache> initialization;
    private ShaderCacheKey key;
    private int vendor, deviceId;
    private byte[] uuid;
    private long handle;
    private String lastSnapshot;
    private int savedVersion;
    private PendingSave pendingSave;

    private static final class PendingSave {
        byte[] bytes;
        String digest;
        PendingSave(byte[] bytes, String digest) { this.bytes = bytes; this.digest = digest; }
    }

    DesktopVulkanPipelineCache(VulkanResourceDomain domain, VkPhysicalDevice physicalDevice,
            ShaderArtifactCache artifacts, Consumer<Runnable> execute, Runnable retainSave, Runnable releaseSave) {
        this.domain = domain; this.device = domain.device(); this.physicalDevice = physicalDevice;
        this.artifacts = artifacts; this.execute = execute;
        this.retainSave = retainSave; this.releaseSave = releaseSave;
    }

    synchronized FdxFuture<DesktopVulkanPipelineCache> initializeAsync() {
        if (initialization != null) return initialization;
        initialization = FdxFuture.pending();
        if (artifacts == null || !artifacts.supportsAtomicUpdate()) {
            initialization.complete(this);
            return initialization;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.malloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physicalDevice, properties);
            vendor = properties.vendorID(); deviceId = properties.deviceID();
            uuid = new byte[VK10.VK_UUID_SIZE]; properties.pipelineCacheUUID().get(uuid);
            key = ShaderCacheKey.of(ShaderCacheLayer.DRIVER_PIPELINE, "vulkan-pipeline-cache:1",
                    Integer.toString(vendor), Integer.toString(deviceId), Integer.toString(properties.driverVersion()),
                    Integer.toString(properties.apiVersion()), HexFormat.of().formatHex(uuid),
                    System.getProperty("os.name"), System.getProperty("os.arch"));
            ShaderPreparationTrace trace = ShaderPreparationTrace.current();
            Consumer<Runnable> initializeExecutor = trace == null ? execute : trace.executor(execute);
            artifacts.readAsync(key).onSuccess(bytes -> {
                try { initializeExecutor.accept(() -> initialize(bytes)); }
                catch (RuntimeException | Error failure) { initialization.completeExceptionally(failure); }
            });
        } catch (RuntimeException | Error failure) { initialization.completeExceptionally(failure); }
        return initialization;
    }

    private void initialize(byte[] bytes) {
        ByteBuffer nativeBytes = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            domain.requireUsable();
            if (bytes != null && !validHeader(bytes, vendor, deviceId, uuid)) {
                artifacts.rejected(key); bytes = null;
            }
            if (bytes != null) nativeBytes = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
            VkPipelineCacheCreateInfo info = VkPipelineCacheCreateInfo.calloc(stack).sType$Default()
                    .flags(0).pInitialData(nativeBytes);
            LongBuffer result = stack.callocLong(1);
            int status = VK10.vkCreatePipelineCache(device, info, null, result);
            rejectDeviceLoss(status, result.get(0));
            if (status != VK10.VK_SUCCESS && bytes != null) {
                artifacts.rejected(key);
                if (result.get(0) != 0) VK10.vkDestroyPipelineCache(device, result.get(0), null);
                result.put(0, 0); info.pInitialData(null); bytes = null;
                status = VK10.vkCreatePipelineCache(device, info, null, result);
                rejectDeviceLoss(status, result.get(0));
            }
            // Cache allocation is optional. Failed creation leaves ordinary uncached preparation usable.
            if (status == VK10.VK_SUCCESS) {
                handle = result.get(0);
                lastSnapshot = bytes == null ? null : digest(bytes);
            } else if (result.get(0) != 0) VK10.vkDestroyPipelineCache(device, result.get(0), null);
            initialization.complete(this);
        } catch (RuntimeException | Error failure) { initialization.completeExceptionally(failure); }
        finally { if (nativeBytes != null) MemoryUtil.memFree(nativeBytes); }
    }

    private void rejectDeviceLoss(int status, long created) {
        if (status != VK10.VK_ERROR_DEVICE_LOST) return;
        if (created != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipelineCache(device, created, null);
        domain.check(status, "Create Vulkan pipeline cache");
    }

    static boolean validHeader(byte[] bytes, int vendor, int device, byte[] uuid) {
        if (bytes == null || bytes.length < 32 || uuid == null || uuid.length != 16) return false;
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        return header.getInt(0) == 32 && header.getInt(4) == VK10.VK_PIPELINE_CACHE_HEADER_VERSION_ONE
                && header.getInt(8) == vendor && header.getInt(12) == device
                && Arrays.equals(bytes, 16, 32, uuid, 0, 16);
    }

    int create(VkGraphicsPipelineCreateInfo.Buffer info, LongBuffer output) {
        domain.requireUsable();
        active.incrementAndGet();
        long previousNext = info.pNext();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPipelineCreationFeedbackEXT feedback = null;
            if (device.getCapabilities().VK_EXT_pipeline_creation_feedback) {
                feedback = VkPipelineCreationFeedbackEXT.calloc(stack);
                info.pNext(VkPipelineCreationFeedbackCreateInfoEXT.calloc(stack).sType$Default()
                        .pNext(previousNext).pPipelineCreationFeedback(feedback).address());
            }
            if (key != null) artifacts.pipelineInvoked(key, handle != VK10.VK_NULL_HANDLE);
            int result = VK10.vkCreateGraphicsPipelines(device, handle, info, null, output);
            domain.observeResult(result);
            boolean hit = false;
            if (result == VK10.VK_SUCCESS && feedback != null
                    && (feedback.flags() & EXTPipelineCreationFeedback.VK_PIPELINE_CREATION_FEEDBACK_VALID_BIT_EXT) != 0) {
                hit = (feedback.flags() & EXTPipelineCreationFeedback.VK_PIPELINE_CREATION_FEEDBACK_APPLICATION_PIPELINE_CACHE_HIT_BIT_EXT) != 0;
                if (key != null) artifacts.pipelineFeedback(key, hit);
            }
            if (!hit) mutations.incrementAndGet();
            return result;
        } finally {
            info.pNext(previousNext);
            int remaining = active.decrementAndGet();
            int count = completed.incrementAndGet();
            if (handle != VK10.VK_NULL_HANDLE && (remaining == 0 || count % 32 == 0)) snapshot();
        }
    }

    private void snapshot() {
        synchronized (snapshots) {
            if (domain.isLost()) return;
            int version = mutations.get();
            if (version == savedVersion) return;
            ByteBuffer bytes = null;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var size = stack.callocPointer(1);
                if (domain.observeResult(VK10.vkGetPipelineCacheData(device, handle, size, (ByteBuffer)null)) != VK10.VK_SUCCESS) return;
                long required = size.get(0);
                if (required < 32 || required > ShaderArtifactCache.MAX_PAYLOAD_BYTES) return;
                bytes = MemoryUtil.memAlloc((int)required);
                // Pipeline creation may grow the internally synchronized cache during this query.
                // Skip incomplete snapshots; the last finishing job snapshots again when quiescent.
                if (domain.observeResult(VK10.vkGetPipelineCacheData(device, handle, size, bytes)) != VK10.VK_SUCCESS) return;
                int actual = (int)size.get(0);
                if (actual < 32 || actual > required) return;
                byte[] payload = new byte[actual]; bytes.get(payload);
                String digest = digest(payload);
                savedVersion = version;
                if (digest.equals(lastSnapshot)) return;
                lastSnapshot = digest;
                // Replace a queued payload until its transaction starts. Every final save is already
                // accepted before its producer drains, so store.flushAsync/dispose can drain normally.
                if (pendingSave != null) {
                    pendingSave.bytes = payload; pendingSave.digest = digest;
                    return;
                }
                retainSave.run();
                PendingSave pending = new PendingSave(payload, digest);
                pendingSave = pending;
                FdxFuture<Boolean> save;
                try {
                    save = artifacts.mergeAsync(key, payload, (current, incoming) -> {
                        synchronized (snapshots) {
                            incoming = pending.bytes;
                            pendingSave = null;
                        }
                        return merge(current, incoming);
                    });
                } catch (RuntimeException | Error failure) {
                    pendingSave = null; releaseSave.run(); throw failure;
                }
                save.onSuccess(saved -> {
                    try {
                        if (!saved) synchronized (snapshots) {
                            if (pendingSave == pending) pendingSave = null;
                            if (pending.digest.equals(lastSnapshot)) { lastSnapshot = null; savedVersion = -1; }
                        }
                    } finally { releaseSave.run(); }
                });
            } catch (RuntimeException ignored) {
                // Optional persistence cannot turn a usable pipeline into a failed shader.
                lastSnapshot = null; savedVersion = -1;
            } finally { if (bytes != null) MemoryUtil.memFree(bytes); }
        }
    }

    /** Runs inside the store transaction. Private destination/source caches satisfy Vulkan's
     * external merge synchronization without locking the live compilation cache. */
    private byte[] merge(byte[] current, byte[] incoming) {
        domain.requireUsable();
        if (current == null || Arrays.equals(current, incoming)) return incoming;
        if (!validHeader(current, vendor, deviceId, uuid)) { artifacts.rejected(key); return incoming; }
        long destination = VK10.VK_NULL_HANDLE, sourceCache = VK10.VK_NULL_HANDLE;
        ByteBuffer output = null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            destination = createPrivateCache(stack, incoming);
            sourceCache = createPrivateCache(stack, current);
            domain.check(VK10.vkMergePipelineCaches(device, destination, stack.longs(sourceCache)),
                    "Vulkan cache merge failed");
            var size = stack.callocPointer(1);
            if (domain.observeResult(VK10.vkGetPipelineCacheData(device, destination, size, (ByteBuffer)null)) != VK10.VK_SUCCESS
                    || size.get(0) < 32 || size.get(0) > ShaderArtifactCache.MAX_PAYLOAD_BYTES)
                throw new FdxException("Vulkan merged cache exceeds the storage bound or is unavailable");
            output = MemoryUtil.memAlloc((int)size.get(0));
            if (domain.observeResult(VK10.vkGetPipelineCacheData(device, destination, size, output)) != VK10.VK_SUCCESS
                    || size.get(0) < 32 || size.get(0) > output.capacity())
                throw new FdxException("Vulkan merged cache snapshot failed");
            byte[] merged = new byte[(int)size.get(0)]; output.get(merged); return merged;
        } finally {
            if (output != null) MemoryUtil.memFree(output);
            if (sourceCache != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipelineCache(device, sourceCache, null);
            if (destination != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipelineCache(device, destination, null);
        }
    }

    private long createPrivateCache(MemoryStack stack, byte[] payload) {
        domain.requireUsable();
        ByteBuffer bytes = MemoryUtil.memAlloc(payload.length).put(payload).flip();
        try {
            LongBuffer created = stack.callocLong(1);
            int status = VK10.vkCreatePipelineCache(device,
                    VkPipelineCacheCreateInfo.calloc(stack).sType$Default().pInitialData(bytes), null, created);
            if (status != VK10.VK_SUCCESS) {
                if (created.get(0) != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipelineCache(device, created.get(0), null);
                domain.check(status, "Vulkan merge cache allocation failed");
            }
            return created.get(0);
        } finally { MemoryUtil.memFree(bytes); }
    }

    /** Called only after the resource domain has no contexts or preparation jobs left. */
    void disposeNative() {
        if (handle != VK10.VK_NULL_HANDLE) VK10.vkDestroyPipelineCache(device, handle, null);
        handle = VK10.VK_NULL_HANDLE;
    }

    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException failure) { throw new AssertionError(failure); }
    }
}
