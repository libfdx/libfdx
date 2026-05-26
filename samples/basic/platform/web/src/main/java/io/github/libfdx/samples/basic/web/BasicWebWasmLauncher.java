package io.github.libfdx.samples.basic.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.samples.basic.BasicApplication;

public final class BasicWebWasmLauncher {
    private BasicWebWasmLauncher() {
    }

    public static void main(String[] args) {
        WebApplicationConfig config = new WebApplicationConfig()
                .title("libfdx Basic - WebGL Wasm")
                .size(0, 0)
                .canvasId("libfdx-canvas")
                .graphics(new WebGLProvider());

        new WebApplicationBackend().start(config, new BasicApplication());
    }
}
