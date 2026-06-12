package io.github.libfdx.graphics.vulkan;

import io.github.libfdx.core.FdxException;

/**
 * Stores configuration values for a vulkan.
 *
 * @author xpenatan
 */
public final class VulkanConfiguration {
    private String applicationName = "libfdx";
    private boolean validation;
    private boolean vSync = true;
    private boolean preferMailboxPresentMode = true;
    private int framesInFlight = 2;

    /**
     * Returns the application name.
     *
     * @return the application name
     */
    public String applicationName() {
        return applicationName;
    }

    /**
     * Sets the application name and returns this vulkan configuration.
     *
     * @param applicationName the application name
     * @return this vulkan configuration for chaining
     */
    public VulkanConfiguration applicationName(String applicationName) {
        this.applicationName = applicationName != null && applicationName.length() > 0 ? applicationName : "libfdx";
        return this;
    }

    /**
     * Returns the validation.
     *
     * @return true if validation succeeds or is active; false otherwise
     */
    public boolean validation() {
        return validation;
    }

    /**
     * Sets the validation and returns this vulkan configuration.
     *
     * @param validation the validation
     * @return this vulkan configuration for chaining
     */
    public VulkanConfiguration validation(boolean validation) {
        this.validation = validation;
        return this;
    }

    /**
     * Returns the v sync.
     *
     * @return true if v sync succeeds or is active; false otherwise
     */
    public boolean vSync() {
        return vSync;
    }

    /**
     * Sets the v sync and returns this vulkan configuration.
     *
     * @param vSync the v sync
     * @return this vulkan configuration for chaining
     */
    public VulkanConfiguration vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    /**
     * Returns the prefer mailbox present mode.
     *
     * @return true if prefer mailbox present mode succeeds or is active; false otherwise
     */
    public boolean preferMailboxPresentMode() {
        return preferMailboxPresentMode;
    }

    /**
     * Sets the prefer mailbox present mode and returns this vulkan configuration.
     *
     * @param preferMailboxPresentMode the prefer mailbox present mode
     * @return this vulkan configuration for chaining
     */
    public VulkanConfiguration preferMailboxPresentMode(boolean preferMailboxPresentMode) {
        this.preferMailboxPresentMode = preferMailboxPresentMode;
        return this;
    }

    /**
     * Returns the frames in flight.
     *
     * @return the frames in flight
     */
    public int framesInFlight() {
        return framesInFlight;
    }

    /**
     * Sets the frames in flight and returns this vulkan configuration.
     *
     * @param framesInFlight the frames in flight
     * @return this vulkan configuration for chaining
     */
    public VulkanConfiguration framesInFlight(int framesInFlight) {
        if (framesInFlight < 1 || framesInFlight > 3) {
            throw new FdxException("Vulkan frames in flight must be between 1 and 3");
        }
        this.framesInFlight = framesInFlight;
        return this;
    }
}
