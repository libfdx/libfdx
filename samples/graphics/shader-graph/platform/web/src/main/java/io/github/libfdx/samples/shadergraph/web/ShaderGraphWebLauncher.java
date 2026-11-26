package io.github.libfdx.samples.shadergraph.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.graphics.wgpu.WebWGPUProvider;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleApplication;
import io.github.libfdx.samples.shadergraph.ShaderGraphSampleGraphs.Example;
import org.teavm.jso.JSBody;

public final class ShaderGraphWebLauncher {
    private ShaderGraphWebLauncher() {
    }

    public static void main(String[] args) {
        var config = new WebApplicationConfig().title("Shader graph gallery").size(0, 0)
                .canvasId("libfdx-canvas").graphics(webGpuRequested() ? new WebWGPUProvider() : new WebGLProvider());
        new WebApplicationBackend().start(config, new ShaderGraphSampleApplication(Example.DISSOLVE, false));
    }

    @JSBody(script = "return new URLSearchParams(window.location.search).get('graphics') === 'webgpu';")
    private static native boolean webGpuRequested();

}
