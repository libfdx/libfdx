package io.github.libfdx.backend.web;

import org.teavm.platform.metadata.Resource;

public interface WebGeneratedAsset extends Resource {
    String getPath();

    int getSize();
}
