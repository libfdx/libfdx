package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.core.FdxException;

/**
 * Keeps a native resource alive while one or more contexts reference it from recorded commands.
 */
abstract class WGPURecordedResource {
    private final WGPUResourceDomain resourceDomain;
    private int recordingReferences;
    private boolean retired;
    private boolean released;

    WGPURecordedResource(WGPUResourceDomain resourceDomain) {
        if (resourceDomain == null) {
            throw new FdxException("WGPU resource domain cannot be null");
        }
        this.resourceDomain = resourceDomain;
    }

    final WGPUResourceDomain resourceDomain() {
        return resourceDomain;
    }

    final void retainForRecording() {
        if (retired) {
            throw new FdxException("Cannot record a retired WGPU resource");
        }
        recordingReferences++;
    }

    final void releaseFromRecording() {
        if (recordingReferences <= 0) {
            throw new FdxException("WGPU resource recording reference underflow");
        }
        recordingReferences--;
        releaseIfUnused();
    }

    final void retire() {
        if (retired) {
            return;
        }
        retired = true;
        Throwable firstFailure = null;
        try {
            onRetired();
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }
        try {
            releaseIfUnused();
        } catch (RuntimeException | Error failure) {
            firstFailure = WGPUCleanup.merge(firstFailure, failure);
        }
        WGPUCleanup.rethrow(firstFailure);
    }

    final boolean hasRecordingReferences() {
        return recordingReferences > 0;
    }

    final int recordingReferences() {
        return recordingReferences;
    }

    final boolean isRetired() {
        return retired;
    }

    final boolean isReleased() {
        return released;
    }

    protected abstract void releaseNative();

    protected void onRetired() {
    }

    private void releaseIfUnused() {
        if (!retired || recordingReferences != 0 || released) {
            return;
        }
        released = true;
        releaseNative();
    }
}
