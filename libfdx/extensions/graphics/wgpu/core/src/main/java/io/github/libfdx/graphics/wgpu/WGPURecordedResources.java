package io.github.libfdx.graphics.wgpu;

import io.github.libfdx.collections.Array;

/**
 * Reuses one context-local list of native resources referenced by the current recording.
 */
final class WGPURecordedResources {
    private final Array<WGPURecordedResource> resources = new Array<WGPURecordedResource>();

    void mark(WGPURecordedResource resource) {
        if (resource == null || resources.contains(resource)) {
            return;
        }
        resource.retainForRecording();
        try {
            resources.add(resource);
        } catch (RuntimeException | Error error) {
            resource.releaseFromRecording();
            throw error;
        }
    }

    void releaseAll() {
        Throwable firstFailure = null;
        for (int i = 0; i < resources.size(); i++) {
            try {
                resources.get(i).releaseFromRecording();
            } catch (RuntimeException | Error failure) {
                firstFailure = WGPUCleanup.merge(firstFailure, failure);
            }
        }
        resources.clear();
        WGPUCleanup.rethrow(firstFailure);
    }

    int size() {
        return resources.size();
    }
}
