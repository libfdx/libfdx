package io.github.libfdx.backend.android;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;

/** Explicit native binding probe, including drivers whose cache export contains only a header. */
public final class AndroidVulkanCacheMergeTest extends ApplicationAdapter {
    private Fdx fdx;

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        long context;
        try {
            var attachment = fdx.graphics().main();
            var field = attachment.getClass().getDeclaredField("context");
            field.setAccessible(true);
            context = field.getLong(attachment);
        } catch (ReflectiveOperationException failure) {
            throw new FdxException("This probe requires the direct Android Vulkan attachment", failure);
        }
        AndroidVulkanNative.retainPreparationDevice(context);
        try {
            require(AndroidVulkanNative.initializePipelineCache(context, null) != 0, "Cache allocation failed");
            byte[] initial = AndroidVulkanNative.snapshotPipelineCache(context);
            require(initial != null, "Cache export unavailable");
            byte[] merged = AndroidVulkanNative.mergePipelineCaches(context, initial, initial);
            require(AndroidVulkanPipelineCache.validHeader(merged, AndroidVulkanNative.pipelineCacheIdentity(context)),
                    "JNI merge did not return a compatible cache");
            byte[] foreign = initial.clone(); foreign[16] ^= 1;
            require(AndroidVulkanNative.mergePipelineCaches(context, foreign, initial) == null,
                    "Foreign UUID reached native cache import");
            require(AndroidVulkanNative.mergePipelineCaches(context, initial, new byte[0]) == null,
                    "Truncated input reached native cache import");
            require(AndroidVulkanNative.snapshotPipelineCache(context) != null, "Live cache was damaged by private merges");
            System.out.println("[info] VULKAN_CACHE_MERGE_PASS bytes=" + merged.length + " foreign_rejected=true truncated_rejected=true");
        } finally { AndroidVulkanNative.releasePreparationDevice(context); }
    }

    @Override
    public void render() { fdx.app().requestExit(); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new FdxException(message);
    }
}
