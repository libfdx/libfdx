package io.github.libfdx.tools.project.generator.desktop;

import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ProjectGenerationResult;
import io.github.libfdx.tools.project.generator.ProjectGenerationSettings;
import io.github.libfdx.tools.project.generator.ProjectGenerator;
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

        ProjectGenerationResult generation = new ProjectGenerator().generate(ProjectGenerationSettings.builder()
                .projectName("smoke-game")
                .packageName("com.example.smoke")
                .applicationClassName("SmokeApplication")
                .desktopLauncherClassName("SmokeDesktopLauncher")
                .libfdxVersion("-SNAPSHOT")
                .build());
        GeneratedProject project = generation.project();
        DesktopProjectExportTarget target = new DesktopProjectExportTarget(destination.toString());

        ProjectExportResult firstExport = target.export(new ProjectExportRequest(project, destination.toString(), false));
        require(firstExport.success(), "Expected first export to succeed: " + firstExport.message());
        requireFile(destination.resolve("settings.gradle.kts"), "include(\":platform:desktop\")");
        requireFile(destination.resolve("core/src/main/java/com/example/smoke/SmokeApplication.java"),
                "class SmokeApplication");
        requireFile(destination.resolve("platform/desktop/build.gradle.kts"), "tasks.register<JavaExec>(\"run_gl\")");
        requireFile(destination.resolve(
                "platform/desktop/src/main/java/com/example/smoke/desktop/SmokeDesktopLauncher.java"),
                "new DesktopOpenGLProvider()");

        ProjectExportResult duplicateExport =
                target.export(new ProjectExportRequest(project, destination.toString(), false));
        require(!duplicateExport.success(), "Expected duplicate export without overwrite to fail.");

        ProjectExportResult overwriteExport =
                target.export(new ProjectExportRequest(project, destination.toString(), true));
        require(overwriteExport.success(), "Expected overwrite export to succeed: " + overwriteExport.message());
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
