package io.github.libfdx.tests.utils.androidvulkandevicelosstest;

/** Available only with -PlibfdxVulkanLossTests=true. The production JNI library exports none of these hooks. */
public final class VulkanLossFixture {
    private VulkanLossFixture() { }
    public static native void arm(long context, int site, int result);
    public static native int calls();
    public static native int destructions();
}
