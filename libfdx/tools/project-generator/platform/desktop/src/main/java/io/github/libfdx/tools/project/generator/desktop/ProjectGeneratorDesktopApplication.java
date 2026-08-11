package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.tools.project.generator.ui.ProjectGeneratorApplication;

/**
 * Adapts the portable generator UI to the desktop launcher.
 *
 * @author xpenatan
 */
final class ProjectGeneratorDesktopApplication extends ApplicationAdapter {
    private final ProjectGeneratorApplication generator;

    ProjectGeneratorDesktopApplication(DesktopProjectExportTarget exportTarget, long exitAfterFrames) {
        generator = new ProjectGeneratorApplication(exportTarget, exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        generator.create(fdx);
    }

    @Override
    public void resize(int width, int height) {
        generator.resize(width, height);
    }

    @Override
    public void render() {
        generator.render();
    }

    @Override
    public void dispose() {
        generator.dispose();
    }
}
