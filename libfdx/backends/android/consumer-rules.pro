# Called by the bundled Vulkan JNI bridge when a native operation reports device loss.
-keepclassmembers class io.github.libfdx.backend.android.AndroidVulkanNative {
    private static java.lang.RuntimeException deviceLostException();
}
