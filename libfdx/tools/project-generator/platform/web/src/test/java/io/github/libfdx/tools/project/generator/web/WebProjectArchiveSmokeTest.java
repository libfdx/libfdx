package io.github.libfdx.tools.project.generator.web;

import io.github.libfdx.tools.project.generator.GeneratedProject;
import io.github.libfdx.tools.project.generator.ProjectGenerationResult;
import io.github.libfdx.tools.project.generator.ProjectGenerationSettings;
import io.github.libfdx.tools.project.generator.ProjectGenerator;
import io.github.libfdx.tools.project.generator.ProjectPlatform;
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
                .sampleId("2d/platformer")
                .platforms(ProjectPlatform.WEB)
                .build());
        GeneratedProject project = generation.project();
        byte[] archive = WebProjectArchive.zip(project);
        require(archive.length > 0, "Expected non-empty ZIP archive.");

        Set<String> entries = entries(archive);
        require(entries.contains("settings.gradle.kts"), "Missing settings.gradle.kts in ZIP.");
        require(entries.contains(
                "core/src/main/java/io/github/libfdx/samples/g2d/platformer/PlatformerApplication.java"),
                "Missing generated application in ZIP.");
        require(entries.contains(
                "platform/web/src/main/java/io/github/libfdx/samples/g2d/platformer/web/"
                        + "PlatformerWebJsLauncher.java"),
                "Missing selected web launcher in ZIP.");
        require(!entries.contains("platform/desktop/build.gradle.kts"),
                "Unselected desktop platform was included in ZIP.");
        require(entries.contains("assets/kenney/pixel-platformer/Tilemap/tilemap_packed.png"),
                "Missing bundled sample asset in ZIP.");
        require(entries.contains("PROJECT_GENERATOR.md"), "Missing generator provenance in ZIP.");
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
