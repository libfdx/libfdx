package io.github.libfdx.tools.project.generator.ui;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ProjectGenerationResult;
import io.github.libfdx.tools.project.generator.ProjectGenerationSettings;
import io.github.libfdx.tools.project.generator.ProjectGenerator;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiToolkit;

/**
 * Represents a project generator application.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorApplication extends ApplicationAdapter {
    private final ProjectExportTarget exportTarget;
    private final long exitAfterFrames;
    private final ProjectGenerator generator = new ProjectGenerator();
    private final UiState<String> projectName = Ui.state(ProjectGenerationSettings.DEFAULT_PROJECT_NAME);
    private final UiState<String> packageName = Ui.state(ProjectGenerationSettings.DEFAULT_PACKAGE_NAME);
    private final UiState<String> applicationClassName = Ui.state(ProjectGenerationSettings.DEFAULT_APPLICATION_CLASS_NAME);
    private final UiState<String> desktopLauncherClassName =
            Ui.state(ProjectGenerationSettings.DEFAULT_DESKTOP_LAUNCHER_CLASS_NAME);
    private final UiState<String> libfdxVersion = Ui.state(ProjectGenerationSettings.DEFAULT_LIBFDX_VERSION);
    private final UiState<String> destination;
    private final UiState<String> status = Ui.state("Ready");
    private final UiBooleanState desktopPlatform = Ui.state(true);
    private final UiBooleanState overwriteExisting = Ui.state(false);
    private Application application;
    private GraphicsContext graphics;
    private UiRoot root;
    private long renderedFrames;

    /**
     * Creates a project generator application.
     *
     * @param exportTarget the export target
     */
    public ProjectGeneratorApplication(ProjectExportTarget exportTarget) {
        this(exportTarget, 0L);
    }

    /**
     * Creates a project generator application.
     *
     * @param exportTarget the export target
     * @param exitAfterFrames the exit after frames
     */
    public ProjectGeneratorApplication(ProjectExportTarget exportTarget, long exitAfterFrames) {
        if (exportTarget == null) {
            throw new IllegalArgumentException("exportTarget cannot be null.");
        }
        this.exportTarget = exportTarget;
        this.exitAfterFrames = exitAfterFrames;
        destination = Ui.state(exportTarget.defaultDestination());
    }

    /**
     * Initializes the application with the libFDX runtime root.
     *
     * @param fdx the libFDX runtime root
     */
    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        graphics = fdx.graphics().main();
        root = new UiToolkit(fdx.files()).root(fdx.displays().main(), graphics).input(fdx.input());
        root.setContent(this::buildUi);
    }

    /**
     * Handles a size change.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        if (graphics != null) {
            graphics.clear(0.07f, 0.08f, 0.10f, 1.0f);
        }
        if (root != null) {
            root.update(application.deltaTime());
            root.render();
        }
        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    /**
     * Releases resources held by this instance.
     */
    @Override
    public void dispose() {
        if (root != null) {
            root.dispose();
            root = null;
        }
    }

    private void buildUi(UiScope ui) {
        ui.column(Ui.modifier().fill().padding(12.0f).gap(8.0f), page -> {
            page.text("libfdx Project Generator");
            page.row(Ui.modifier().fillWidth().gap(8.0f), body -> {
                body.panel(Ui.modifier().width(420.0f).padding(10.0f).gap(6.0f), form -> {
                    field(form, "Project name", projectName);
                    field(form, "Package", packageName);
                    field(form, "Application class", applicationClassName);
                    field(form, "Desktop launcher", desktopLauncherClassName);
                    field(form, "libfdx version", libfdxVersion);
                    field(form, exportTarget.destinationLabel(), destination);
                    if (exportTarget.supportsOverwriteExisting()) {
                        form.row(Ui.modifier().fillWidth().gap(14.0f), flags -> {
                            flags.checkbox("Desktop", desktopPlatform);
                            flags.checkbox(exportTarget.overwriteLabel(), overwriteExisting);
                        });
                    } else {
                        form.checkbox("Desktop", desktopPlatform);
                    }
                    form.button("Generate", Ui.modifier().fillWidth().height(32.0f), this::generate);
                });
                body.panel(Ui.modifier().width(190.0f).padding(10.0f).gap(6.0f), summary -> {
                    summary.text("Output");
                    summary.text(status.get());
                });
            });
        });
    }

    private void field(UiScope parent, String label, UiState<String> value) {
        parent.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(128.0f));
            row.textField(Ui.modifier().fillWidth().height(30.0f), value);
        });
    }

    private void generate() {
        try {
            ProjectGenerationSettings settings = ProjectGenerationSettings.builder()
                    .projectName(projectName.get())
                    .packageName(packageName.get())
                    .applicationClassName(applicationClassName.get())
                    .desktopLauncherClassName(desktopLauncherClassName.get())
                    .libfdxVersion(libfdxVersion.get())
                    .desktopPlatform(desktopPlatform.get())
                    .build();
            ProjectGenerationResult generation = generator.generate(settings);
            GeneratedProject project = generation.project();
            boolean overwrite = exportTarget.supportsOverwriteExisting() && overwriteExisting.get();
            ProjectExportResult result = exportTarget.export(new ProjectExportRequest(
                    project, destination.get(), overwrite));
            status.set((result.success() ? "Generated " : "Could not generate ")
                    + project.name() + ": " + result.message());
        } catch (RuntimeException error) {
            status.set("Error: " + error.getMessage());
        }
        if (root != null) {
            root.requestCompose();
        }
    }
}
