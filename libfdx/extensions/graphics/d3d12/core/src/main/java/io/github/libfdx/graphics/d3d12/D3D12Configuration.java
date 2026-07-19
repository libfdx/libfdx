package io.github.libfdx.graphics.d3d12;

import io.github.libfdx.core.FdxException;

/**
 * Stores Direct3D 12 startup configuration.
 */
public final class D3D12Configuration {
    private boolean validation;
    private boolean vSync = true;
    private int framesInFlight = 2;

    /**
     * Returns whether the Direct3D 12 debug layer is requested.
     *
     * @return true when validation is requested
     */
    public boolean validation() {
        return validation;
    }

    /**
     * Requests the Direct3D 12 debug layer.
     *
     * @param validation whether validation is requested
     * @return this configuration
     */
    public D3D12Configuration validation(boolean validation) {
        this.validation = validation;
        return this;
    }

    /**
     * Returns whether presentation is synchronized to the display.
     *
     * @return true when vertical synchronization is enabled
     */
    public boolean vSync() {
        return vSync;
    }

    /**
     * Sets vertical synchronization.
     *
     * @param vSync whether vertical synchronization is enabled
     * @return this configuration
     */
    public D3D12Configuration vSync(boolean vSync) {
        this.vSync = vSync;
        return this;
    }

    /**
     * Returns the number of swap-chain frames.
     *
     * @return frames in flight
     */
    public int framesInFlight() {
        return framesInFlight;
    }

    /**
     * Sets the number of swap-chain frames.
     *
     * @param framesInFlight frames in flight, from two through three
     * @return this configuration
     */
    public D3D12Configuration framesInFlight(int framesInFlight) {
        if (framesInFlight < 2 || framesInFlight > 3) {
            throw new FdxException("Direct3D 12 frames in flight must be between 2 and 3");
        }
        this.framesInFlight = framesInFlight;
        return this;
    }
}
