package io.github.libfdx.tests;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.android.AndroidVulkanCacheChecks;

/** Explicit native binding probe, including drivers whose cache export contains only a header. */
public final class AndroidVulkanCacheMergeTest extends ApplicationAdapter {
    private Fdx fdx;

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        AndroidVulkanCacheChecks.verify(fdx);
    }

    @Override
    public void render() { fdx.app().requestExit(); }

}
