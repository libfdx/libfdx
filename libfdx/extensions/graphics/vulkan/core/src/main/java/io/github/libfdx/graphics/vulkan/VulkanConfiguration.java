package io.github.libfdx.graphics.vulkan;

import io.github.libfdx.core.FdxException;

public final class VulkanConfiguration {
    private String applicationName = "libfdx";
    private boolean validation;
    private boolean vSync = true;
    private boolean preferMailboxPresentMode = true;
    private int framesInFlight = 2;

    public String applicationName() {
        return applicationName;
    }

    public VulkanConfiguration applicationName(String applicationName) {
        this.applicationName = applicationName != null && applicationName.length() > 0 ? applicationName : "libfdx";
        return this;
    }

    public boolean validation() {
        return validation;
    }

    public VulkanConfiguration validation(boolean validation) {
        this.validation = validation;
        return this;
    }

    public boolean vSync() {
        return vSync;
    }

    public VulkanConfiguration vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    public boolean preferMailboxPresentMode() {
        return preferMailboxPresentMode;
    }

    public VulkanConfiguration preferMailboxPresentMode(boolean preferMailboxPresentMode) {
        this.preferMailboxPresentMode = preferMailboxPresentMode;
        return this;
    }

    public int framesInFlight() {
        return framesInFlight;
    }

    public VulkanConfiguration framesInFlight(int framesInFlight) {
        if (framesInFlight < 1 || framesInFlight > 3) {
            throw new FdxException("Vulkan frames in flight must be between 1 and 3");
        }
        this.framesInFlight = framesInFlight;
        return this;
    }
}
