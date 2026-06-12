package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ProjectGenerationResult;
import io.github.libfdx.tools.project.generator.ProjectGenerationSettings;
import io.github.libfdx.tools.project.generator.ProjectGenerator;
import java.io.ByteArrayInputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Runs the web project archive smoke test scenario.
 *
 * @author xpenatan
 */
public final class WebProjectArchiveSmokeTest {
    private WebProjectArchiveSmokeTest() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     * @throws Exception if the operation cannot be completed
     */
    public static void main(String[] args) throws Exception {
        ProjectGenerationResult generation = new ProjectGenerator().generate(ProjectGenerationSettings.builder()
                .projectName("web-smoke-game")
                .packageName("com.example.websmoke")
                .applicationClassName("WebSmokeApplication")
                .desktopLauncherClassName("WebSmokeDesktopLauncher")
                .libfdxVersion("-SNAPSHOT")
                .build());
        GeneratedProject project = generation.project();
        byte[] archive = WebProjectArchive.zip(project);
        require(archive.length > 0, "Expected non-empty ZIP archive.");

        Set<String> entries = entries(archive);
        require(entries.contains("settings.gradle.kts"), "Missing settings.gradle.kts in ZIP.");
        require(entries.contains("core/src/main/java/com/example/websmoke/WebSmokeApplication.java"),
                "Missing generated application in ZIP.");
        require(entries.contains(
                "platform/desktop/src/main/java/com/example/websmoke/desktop/WebSmokeDesktopLauncher.java"),
                "Missing generated desktop launcher in ZIP.");
        require(entries.size() == project.fileCount(), "ZIP entry count did not match generated file count.");
    }

    private static Set<String> entries(byte[] archive) throws Exception {
        LinkedHashSet<String> entries = new LinkedHashSet<String>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                entries.add(entry.getName());
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
        return entries;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
