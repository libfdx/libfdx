package io.github.libfdx.tools.project.generator.ui;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ProjectGenerationResult;
import io.github.libfdx.tools.project.generator.ProjectGenerationSettings;
import io.github.libfdx.tools.project.generator.ProjectGenerator;
import io.github.libfdx.tools.project.generator.ProjectPlatform;
import io.github.libfdx.tools.project.generator.ProjectSample;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;
import java.util.List;
import java.util.Map;

/**
 * Presents the bundled-sample project generator.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorApplication extends ApplicationAdapter {
    private static final String UI_FONT_ASSET = "font/LiberationSans-Regular.ttf";
    private static final float FIELD_HEIGHT = 30.0f;
    private static final float PLATFORM_CHOICE_HEIGHT = 28.0f;

    private final ProjectExportTarget exportTarget;
    private final long exitAfterFrames;
    private final ProjectGenerator generator = new ProjectGenerator();
    private final List<ProjectSample> samples = generator.samples();
    private final UiState<String> projectName = Ui.state(ProjectGenerationSettings.DEFAULT_PROJECT_NAME);
    private final UiState<String> packageName = Ui.state(ProjectGenerationSettings.DEFAULT_PACKAGE_NAME);
    private final UiIntState sampleIndex = Ui.state(defaultSampleIndex());
    private final UiBooleanState desktop = Ui.state(true);
    private final UiBooleanState android = Ui.state(false);
    private final UiBooleanState web = Ui.state(false);
    private final UiBooleanState desktopC = Ui.state(false);
    private final UiBooleanState iosC = Ui.state(false);
    private final UiState<String> destination;
    private final UiState<String> status = Ui.state("Ready");
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
     * @param exitAfterFrames the optional automatic-exit frame count
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
        root = new UiToolkit(fdx.files())
                .theme(generatorTheme())
                .root(fdx.displays().main(), graphics)
                .input(fdx.input());
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

    private UiTheme generatorTheme() {
        UiFont font = UiFont.freeType(UI_FONT_ASSET, 16.0f);
        UiTheme theme = UiTheme.dark();
        for (Map.Entry<String, UiStyle> entry : theme.styles().entrySet()) {
            UiStyle style = entry.getValue();
            theme = theme.style(entry.getKey(), style.text(style.textStyle().font(font)));
        }
        return theme;
    }

    private void buildUi(UiScope ui) {
        ui.scroll(Ui.modifier().fill(), viewport ->
            viewport.column(Ui.modifier().fillWidth().padding(18.0f).gap(12.0f), page -> {
                page.text("libFDX Project Generator");
                page.text("Choose a starting point and the platforms for your new libFDX project.");
                page.panel(Ui.modifier().fillWidth().padding(14.0f).gap(6.0f), form -> {
                    ProjectSample selected = selectedSample();
                    form.row(Ui.modifier().fillWidth().gap(12.0f), sections -> {
                        sections.column(Ui.modifier().fillWidth().fillHeight().gap(4.0f), startingPoint -> {
                            startingPoint.text("Starting point");
                            for (int index = 0; index < samples.size(); index++) {
                                startingPoint.radioButton(samples.get(index).displayName(),
                                        Ui.modifier().fillWidth().height(28.0f), sampleIndex, index);
                            }
                        });
                        sections.column(Ui.modifier().fillWidth().fillHeight().gap(6.0f), options -> {
                            field(options, "Project name", projectName, 112.0f);
                            if (customizablePackage(selected)) {
                                field(options, "Package name", packageName, 112.0f);
                            } else {
                                options.spacer(Ui.modifier().height(FIELD_HEIGHT));
                            }
                            options.text("Platforms");
                            platformChoices(options, selected);
                            options.text("Dependency: " + dependencyLabel());
                        });
                    });
                    form.text(selected != null
                            ? selected.description()
                            : "No starting points are available.");
                    field(form, exportTarget.destinationLabel(), destination);
                    form.row(Ui.modifier().fillWidth().gap(8.0f), actions -> {
                        if (exportTarget.supportsOverwriteExisting()) {
                            actions.checkbox(exportTarget.overwriteLabel(),
                                    Ui.modifier().width(148.0f).height(32.0f), overwriteExisting);
                        }
                        actions.button("Create project",
                                Ui.modifier().fillWidth().height(32.0f), this::generate);
                    });
                    form.text("Status: " + status.get());
                });
            }));
    }

    private String dependencyLabel() {
        if ("snapshot".equals(generator.channel())) {
            return "libFDX snapshot";
        }
        return "libFDX " + generator.libfdxVersion() + " (" + generator.channel() + ")";
    }

    private int defaultSampleIndex() {
        for (int index = 0; index < samples.size(); index++) {
            if (ProjectGenerationSettings.DEFAULT_SAMPLE_ID.equals(samples.get(index).id())) {
                return index;
            }
        }
        return 0;
    }

    private void platformChoices(UiScope parent, ProjectSample sample) {
        parent.grid(2, Ui.modifier().fillWidth().gap(6.0f), choices -> {
            ProjectPlatform[] platforms = ProjectPlatform.values();
            for (int index = 0; index < platforms.length; index++) {
                ProjectPlatform platform = platforms[index];
                if (sample != null && sample.supports(platform)) {
                    choices.checkbox(platform.displayName(),
                            Ui.modifier().fillWidth().height(PLATFORM_CHOICE_HEIGHT), platformState(platform));
                } else {
                    choices.spacer(Ui.modifier().height(PLATFORM_CHOICE_HEIGHT));
                }
            }
        });
    }

    private UiBooleanState platformState(ProjectPlatform platform) {
        if (platform == ProjectPlatform.ANDROID) {
            return android;
        }
        if (platform == ProjectPlatform.WEB) {
            return web;
        }
        if (platform == ProjectPlatform.DESKTOP_C) {
            return desktopC;
        }
        if (platform == ProjectPlatform.IOS_C) {
            return iosC;
        }
        return desktop;
    }

    private boolean customizablePackage(ProjectSample sample) {
        return sample != null && ProjectGenerationSettings.DEFAULT_SAMPLE_ID.equals(sample.id());
    }

    private void field(UiScope parent, String label, UiState<String> value) {
        field(parent, label, value, 128.0f);
    }

    private void field(UiScope parent, String label, UiState<String> value, float labelWidth) {
        parent.row(Ui.modifier().fillWidth().gap(8.0f), row -> {
            row.text(label, Ui.modifier().width(labelWidth));
            row.textField(Ui.modifier().fillWidth().height(FIELD_HEIGHT), value);
        });
    }

    private ProjectSample selectedSample() {
        int index = sampleIndex.get();
        return index >= 0 && index < samples.size() ? samples.get(index) : null;
    }

    private void generate() {
        try {
            ProjectSample selected = selectedSample();
            if (selected == null) {
                throw new IllegalStateException("No bundled sample is available.");
            }
            ProjectGenerationSettings settings = ProjectGenerationSettings.builder()
                    .projectName(projectName.get())
                    .packageName(packageName.get())
                    .sampleId(selected.id())
                    .platforms(selectedPlatforms(selected))
                    .build();
            ProjectGenerationResult generation = generator.generate(settings);
            GeneratedProject project = generation.project();
            boolean overwrite = exportTarget.supportsOverwriteExisting() && overwriteExisting.get();
            ProjectExportResult result = exportTarget.export(new ProjectExportRequest(
                    project, destination.get(), overwrite));
            status.set((result.success() ? "Created " : "Could not create ")
                    + project.name() + ": " + result.message());
        } catch (RuntimeException error) {
            status.set("Error: " + error.getMessage());
        }
        if (root != null) {
            root.requestCompose();
        }
    }

    private ProjectPlatform[] selectedPlatforms(ProjectSample sample) {
        ProjectPlatform[] selected = new ProjectPlatform[ProjectPlatform.values().length];
        int count = 0;
        ProjectPlatform[] values = ProjectPlatform.values();
        for (int i = 0; i < values.length; i++) {
            ProjectPlatform platform = values[i];
            if (sample.supports(platform) && platformState(platform).get()) {
                selected[count++] = platform;
            }
        }
        ProjectPlatform[] compact = new ProjectPlatform[count];
        System.arraycopy(selected, 0, compact, 0, count);
        return compact;
    }
}
