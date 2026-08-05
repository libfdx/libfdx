package io.github.libfdx.tools.project.generator;

import java.util.List;

/**
 * Runs the bundled-sample project generator smoke checks.
 *
 * @author xpenatan
 */
public final class ProjectGeneratorSmokeTest {
    private ProjectGeneratorSmokeTest() {
    }

    /**
     * Runs the launcher entry point.
     *
     * @param args the args
     */
    public static void main(String[] args) {
        ProjectGenerator generator = new ProjectGenerator();
        List<ProjectSample> samples = generator.samples();
        require(samples.size() >= 5, "Expected the known repository samples to be bundled");
        require("base/starter-project".equals(samples.get(0).id()),
                "Starter Project is not first in the catalog");
        require(containsSample(samples, "base/starter-project"), "Starter Project sample missing");
        require(containsSample(samples, "2d/sprite-movement"), "Sprite Movement sample missing");
        require(containsSample(samples, "2d/platformer"), "Platformer sample missing");
        require(containsSample(samples, "2d/multiplayer-webrtc"), "WebRTC sample missing");
        require(containsSample(samples, "graphics/shader-graph"), "Shader Graph sample missing");
        require(generator.libfdxVersion().length() > 0, "Bundled libFDX version is empty");
        require("base/starter-project".equals(
                        ProjectGenerationSettings.builder().build().sampleId()),
                "Starter Project is not the default generated sample");

        GeneratedProject starterProject = generator.generate(ProjectGenerationSettings.builder()
                .packageName("com.acme.game")
                .build()).project();
        require(starterProject.containsFile(
                "core/src/main/java/com/acme/game/StarterProjectApplication.java"),
                "default portable application source missing");
        require(!starterProject.containsFile(
                "core/src/main/java/io/github/libfdx/samples/starter/StarterProjectApplication.java"),
                "starter source package was not rewritten");
        require(starterProject.file(
                        "core/src/main/java/com/acme/game/StarterProjectApplication.java")
                        .textContent().contains("package com.acme.game;"),
                "starter package declaration was not rewritten");
        require(starterProject.containsFile("assets/fdx_logo_dark.png"),
                "default project logo missing");
        require(!starterProject.file("assets/fdx_logo_dark.png").isText(),
                "default project logo was not preserved as binary");
        require(starterProject.containsFile("platform/desktop/build.gradle.kts"),
                "default desktop platform missing");
        require(starterProject.file("platform/desktop/build.gradle.kts").textContent()
                        .contains("id(\"io.github.libfdx\")"),
                "default desktop platform does not apply the libFDX plugin");
        require(!starterProject.containsFile("platform/plugin/build.gradle.kts"),
                "obsolete shared build-support module was exported");
        require(!starterProject.containsFile("platform/android/build.gradle.kts"),
                "unselected Android platform was exported");
        require(!starterProject.containsFile("platform/web/build.gradle.kts"),
                "unselected web platform was exported");
        require(!starterProject.file("settings.gradle.kts").textContent()
                        .contains("include(\":platform:plugin\")"),
                "obsolete shared build-support module was included");

        GeneratedProject androidOnly = generator.generate(ProjectGenerationSettings.builder()
                .projectName("android-game")
                .platforms(ProjectPlatform.ANDROID)
                .build()).project();
        require(androidOnly.containsFile("platform/android/build.gradle.kts"),
                "selected Android platform missing");
        require(androidOnly.file("platform/android/build.gradle.kts").textContent()
                        .contains("id(\"io.github.libfdx\")"),
                "selected Android platform does not apply the libFDX plugin");
        require(!androidOnly.containsFile("platform/desktop/build.gradle.kts"),
                "unselected desktop platform was exported");
        require(!androidOnly.containsFile("platform/plugin/build.gradle.kts"),
                "unneeded build-support module was exported");

        GeneratedProject allPlatforms = generator.generate(ProjectGenerationSettings.builder()
                .projectName("all-platforms")
                .platforms(ProjectPlatform.values())
                .build()).project();
        ProjectPlatform[] platformValues = ProjectPlatform.values();
        for (int i = 0; i < platformValues.length; i++) {
            require(allPlatforms.containsFile(
                            "platform/" + platformValues[i].directory() + "/build.gradle.kts"),
                    "selected " + platformValues[i].displayName() + " platform missing");
        }

        ProjectGenerationSettings settings = ProjectGenerationSettings.builder()
                .projectName("demo-game")
                .sampleId("2d/sprite-movement")
                .build();

        GeneratedProject project = generator.generate(settings).project();
        require(project.containsFile("settings.gradle.kts"), "settings.gradle.kts missing");
        require(project.containsFile("build.gradle.kts"), "root build.gradle.kts missing");
        require(project.containsFile("gradle/libs.versions.toml"), "version catalog missing");
        require(project.containsFile("PROJECT_GENERATOR.md"), "generator provenance missing");
        require(project.containsFile(
                "core/src/main/java/io/github/libfdx/samples/g2d/spritemovement/SpriteMovementApplication.java"),
                "bundled sample application source missing");
        require(project.containsFile(
                "platform/desktop/src/main/java/io/github/libfdx/samples/g2d/spritemovement/desktop/"
                        + "SpriteMovementDesktopLauncher.java"),
                "bundled desktop launcher missing");
        require(project.containsFile("assets/sprites/player.png"), "bundled binary sprite missing");
        require(!project.file("assets/sprites/player.png").isText(), "PNG was not preserved as binary");
        require(project.file("settings.gradle.kts").textContent().contains("include(\":core\")"),
                "generated core module missing");
        require(project.file("settings.gradle.kts").textContent().contains("include(\":platform:desktop\")"),
                "generated desktop module missing");
        require(!project.containsFile("platform/android/build.gradle.kts"),
                "unselected sprite-movement Android module was exported");
        require(project.file("gradle.properties").textContent()
                        .contains("libfdxVersion=" + generator.libfdxVersion()),
                "generated dependency version does not match the generator");
        require(project.file("build.gradle.kts").textContent()
                        .contains("libfdxDependencyVersion"),
                "standalone version bridge missing");
        require(project.file("core/build.gradle.kts").textContent()
                        .contains("$libfdxDependencyVersion"),
                "sample dependencies are not driven by the generator version");
        require(!project.containsFile("core/src/main/java/com/example/demo/DemoApplication.java"),
                "legacy hand-authored template source is still generated");

        GeneratedProject platformer = generator.generate(ProjectGenerationSettings.builder()
                .projectName("platformer")
                .sampleId("2d/platformer")
                .build()).project();
        require(platformer.containsFile(
                "assets/kenney/pixel-platformer/Tilemap/tilemap_packed.png"),
                "sample-owned platformer assets were not bundled");
        ProjectValidationResult validation = ProjectValidationResult.validate(ProjectGenerationSettings.builder()
                .projectName("bad name")
                .build());
        require(!validation.valid(), "invalid project name was accepted");
        require(!ProjectValidationResult.validate(ProjectGenerationSettings.builder()
                        .packageName("bad-package")
                        .build()).valid(),
                "invalid Java package was accepted");
        require(!ProjectValidationResult.validate(ProjectGenerationSettings.builder()
                        .platforms()
                        .build()).valid(),
                "empty platform selection was accepted");

        boolean unknownRejected = false;
        try {
            generator.generate(ProjectGenerationSettings.builder().sampleId("missing/sample").build());
        } catch (IllegalArgumentException expected) {
            unknownRejected = true;
        }
        require(unknownRejected, "unknown bundled sample was accepted");

        boolean unsupportedRejected = false;
        try {
            generator.generate(ProjectGenerationSettings.builder()
                    .sampleId("graphics/shader-graph")
                    .platforms(ProjectPlatform.WEB)
                    .build());
        } catch (IllegalArgumentException expected) {
            unsupportedRejected = true;
        }
        require(unsupportedRejected, "unsupported sample platform was accepted");
    }

    private static boolean containsSample(List<ProjectSample> samples, String id) {
        for (int i = 0; i < samples.size(); i++) {
            if (id.equals(samples.get(i).id())) {
                return true;
            }
        }
        return false;
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
