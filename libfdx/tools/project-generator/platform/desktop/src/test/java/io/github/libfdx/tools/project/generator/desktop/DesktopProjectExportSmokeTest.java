package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ProjectGenerationResult;
import io.github.libfdx.tools.project.generator.ProjectGenerationSettings;
import io.github.libfdx.tools.project.generator.ProjectGenerator;
import io.github.libfdx.tools.project.generator.ProjectPlatform;
import io.github.libfdx.tools.project.generator.ProjectSample;
import io.github.libfdx.tools.project.generator.ui.ProjectExportRequest;
import io.github.libfdx.tools.project.generator.ui.ProjectExportResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Runs the desktop project export smoke test scenario.
 *
 * @author xpenatan
 */
public final class DesktopProjectExportSmokeTest {
    private DesktopProjectExportSmokeTest() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     * @throws Exception if the operation cannot be completed
     */
    public static void main(String[] args) throws Exception {
        Path destination = exportSmokeDir();
        deleteRecursively(destination);
        ProjectGenerator generator = new ProjectGenerator();
        Path spriteDestination = destination.resolve("sprite-movement");

        ProjectGenerationResult generation = generator.generate(ProjectGenerationSettings.builder()
                .projectName("smoke-game")
                .sampleId("2d/sprite-movement")
                .build());
        GeneratedProject project = generation.project();
        DesktopProjectExportTarget target = new DesktopProjectExportTarget(spriteDestination.toString());

        ProjectExportResult firstExport =
                target.export(new ProjectExportRequest(project, spriteDestination.toString(), false));
        require(firstExport.success(), "Expected first export to succeed: " + firstExport.message());
        requireFile(spriteDestination.resolve("settings.gradle.kts"), "include(\":platform:desktop\")");
        requireFile(spriteDestination.resolve(
                "core/src/main/java/io/github/libfdx/samples/g2d/spritemovement/SpriteMovementProject.java"),
                "class SpriteMovementProject");
        requireFile(spriteDestination.resolve("platform/desktop/build.gradle.kts"),
                "sprite_movement_desktop_gl_run");
        requireFile(spriteDestination.resolve(
                "platform/desktop/src/main/java/io/github/libfdx/samples/g2d/spritemovement/desktop/"
                        + "SpriteMovementDesktopLauncher.java"),
                "new DesktopOpenGLProvider()");

        ProjectExportResult duplicateExport =
                target.export(new ProjectExportRequest(project, spriteDestination.toString(), false));
        require(!duplicateExport.success(), "Expected duplicate export without overwrite to fail.");

        ProjectExportResult overwriteExport =
                target.export(new ProjectExportRequest(project, spriteDestination.toString(), true));
        require(overwriteExport.success(), "Expected overwrite export to succeed: " + overwriteExport.message());

        for (ProjectSample sample : generator.samples()) {
            if ("2d/sprite-movement".equals(sample.id())) {
                continue;
            }
            Path sampleDestination = destination.resolve(sample.id().replace('/', '-'));
            ProjectGenerationSettings.Builder settings = ProjectGenerationSettings.builder()
                    .projectName(sample.id().replace('/', '-'))
                    .sampleId(sample.id());
            if (ProjectGenerationSettings.DEFAULT_SAMPLE_ID.equals(sample.id())) {
                settings.packageName("org.example.starter");
            }
            GeneratedProject copiedSample = generator.generate(settings.build()).project();
            ProjectExportResult sampleExport = new DesktopProjectExportTarget(sampleDestination.toString())
                    .export(new ProjectExportRequest(copiedSample, sampleDestination.toString(), false));
            require(sampleExport.success(), "Expected " + sample.id() + " export to succeed: "
                    + sampleExport.message());
            requireFile(sampleDestination.resolve("settings.gradle.kts"), "include(\":core\")");
            require(Files.isRegularFile(sampleDestination.resolve("PROJECT_GENERATOR.md")),
                    "Missing generator provenance for " + sample.id());
            if (ProjectGenerationSettings.DEFAULT_SAMPLE_ID.equals(sample.id())) {
                requireFile(sampleDestination.resolve(
                                "core/src/main/java/org/example/starter/StarterProjectApplication.java"),
                        "package org.example.starter;");
                require(!Files.exists(sampleDestination.resolve("platform/android")),
                        "Unselected Starter Project Android platform was exported");
            }
        }

        exportStarterVariant(generator, destination.resolve("starter-web-only"),
                "starter-web-only", "org.example.webgame", ProjectPlatform.WEB, true);
        exportStarterVariant(generator, destination.resolve("starter-android-only"),
                "starter-android-only", "org.example.androidgame", ProjectPlatform.ANDROID, false);
        exportStarterVariant(generator, destination.resolve("starter-desktop-c-only"),
                "starter-desktop-c-only", "org.example.desktopcgame", ProjectPlatform.DESKTOP_C, true);
        exportStarterVariant(generator, destination.resolve("starter-ios-c-only"),
                "starter-ios-c-only", "org.example.ioscgame", ProjectPlatform.IOS_C, true);
    }

    private static void exportStarterVariant(ProjectGenerator generator, Path destination,
            String projectName, String packageName, ProjectPlatform platform,
            boolean expectsBuildSupport) throws IOException {
        GeneratedProject project = generator.generate(ProjectGenerationSettings.builder()
                .projectName(projectName)
                .packageName(packageName)
                .platforms(platform)
                .build()).project();
        ProjectExportResult result = new DesktopProjectExportTarget(destination.toString())
                .export(new ProjectExportRequest(project, destination.toString(), false));
        require(result.success(), "Expected " + platform.displayName()
                + " Starter Project export to succeed: " + result.message());
        requireFile(destination.resolve("settings.gradle.kts"),
                "include(\":platform:" + platform.directory() + "\")");
        requireFile(destination.resolve(
                        "core/src/main/java/" + packageName.replace('.', '/')
                                + "/StarterProjectApplication.java"),
                "package " + packageName + ";");
        for (ProjectPlatform candidate : ProjectPlatform.values()) {
            if (candidate != platform) {
                require(!Files.exists(destination.resolve("platform").resolve(candidate.directory())),
                        "Unselected " + candidate.displayName() + " platform was exported");
            }
        }
        require(Files.exists(destination.resolve("platform/plugin")) == expectsBuildSupport,
                "Unexpected shared build-support module for " + platform.displayName());
    }

    private static Path exportSmokeDir() {
        String configured = System.getProperty("libfdx.projectGenerator.exportSmokeDir");
        require(configured != null && configured.length() > 0, "Missing export smoke directory system property.");
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static void requireFile(Path path, String requiredText) throws IOException {
        require(Files.isRegularFile(path), "Expected generated file: " + path);
        String content = Files.readString(path);
        require(content.contains(requiredText), "Expected " + path + " to contain: " + requiredText);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            Path[] ordered = paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new);
            for (int i = 0; i < ordered.length; i++) {
                Files.delete(ordered[i]);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
