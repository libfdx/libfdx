// Test-only JNI bridge. Context is the same canonical type used by the provider above.
extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_testsupport_android_VulkanLossFixture_arm(JNIEnv* env, jclass, jlong value, jint site, jint result) {
    auto* context = ptr<Context>(value);
    try {
        check(context, vkDeviceWaitIdle(context->device), "Vulkan loss test precondition");
        vulkan_loss_test::device = context->device;
        vulkan_loss_test::result.store(result);
        vulkan_loss_test::calls.store(0);
        vulkan_loss_test::destructions.store(0);
        vulkan_loss_test::site.store(site);
    } catch (const std::exception& error) { throwFdx(env, error); }
}
extern "C" JNIEXPORT jint JNICALL
Java_io_github_libfdx_testsupport_android_VulkanLossFixture_calls(JNIEnv*, jclass) {
    return vulkan_loss_test::calls.load();
}
extern "C" JNIEXPORT jint JNICALL
Java_io_github_libfdx_testsupport_android_VulkanLossFixture_destructions(JNIEnv*, jclass) {
    return vulkan_loss_test::destructions.load();
}
