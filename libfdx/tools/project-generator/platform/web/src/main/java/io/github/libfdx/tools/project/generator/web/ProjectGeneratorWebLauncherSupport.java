package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.backend.web.WebApplicationBackend;
import io.github.libfdx.backend.web.WebApplicationConfig;
import io.github.libfdx.graphics.gl.web.WebGLProvider;
import io.github.libfdx.tools.project.generator.ui.ProjectGeneratorApplication;

final class ProjectGeneratorWebLauncherSupport {
    private static final String CANVAS_ID = "libfdx-canvas";

    private ProjectGeneratorWebLauncherSupport() {
    }

    static void start(String runtimeName) {
        String title = "libfdx Project Generator - " + runtimeName;
        WebApplicationConfig config = new WebApplicationConfig()
                .title(title)
                .size(0, 0)
                .canvasId(CANVAS_ID)
                .graphics(new WebGLProvider());

        new WebApplicationBackend().start(config, new ProjectGeneratorApplication(new WebProjectExportTarget()));
    }
}
