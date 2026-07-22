package io.github.libfdx.tools.project.generator;

/**
 * Runs the project generator smoke test scenario.
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
        String expectedVersion = System.getProperty("libfdx.expectedVersion");
        require(expectedVersion != null && expectedVersion.length() > 0, "Expected libFDX version was not provided");
        require(expectedVersion.equals(ProjectGenerationSettings.builder().build().libfdxVersion()),
                "Default libFDX version does not match libfdx.toml");

        ProjectGenerationSettings settings = ProjectGenerationSettings.builder()
                .projectName("demo-game")
                .packageName("com.example.demo")
                .applicationClassName("DemoApplication")
                .desktopLauncherClassName("DemoDesktopLauncher")
                .libfdxVersion("0.0.1")
                .build();

        GeneratedProject project = new ProjectGenerator().generate(settings).project();
        require(project.containsFile("settings.gradle.kts"), "settings.gradle.kts missing");
        require(project.containsFile("core/src/main/java/com/example/demo/DemoApplication.java"),
                "application source missing");
        require(project.containsFile("platform/desktop/src/main/java/com/example/demo/desktop/DemoDesktopLauncher.java"),
                "desktop launcher source missing");
        require(project.file("platform/desktop/build.gradle.kts").textContent().contains("run_gl"),
                "desktop run_gl task missing");
        require(project.file("core/build.gradle.kts").textContent().contains("ecs_tooling"),
                "ecs_tooling dependency missing");
        require(project.file("core/build.gradle.kts").textContent().contains("ecsProject"),
                "ecsProject configuration missing");
        require(project.file("core/src/main/java/com/example/demo/DemoApplication.java")
                        .textContent().contains("extends EcsProject"),
                "portable EcsProject entry missing");
        require(project.file("platform/desktop/src/main/java/com/example/demo/desktop/DemoDesktopLauncher.java")
                        .textContent().contains("EcsProjectApplication"),
                "desktop launcher does not use EcsProjectApplication");
        require(project.containsFile("fdx-project.json"), "fdx-project.json missing");
        require(project.containsFile("scenes/main.fdxscene"), "default scene missing");
        require(project.containsFile("assets/.gitkeep"), "assets directory marker missing");

        ProjectValidationResult validation = ProjectValidationResult.validate(ProjectGenerationSettings.builder()
                .projectName("bad name")
                .build());
        require(!validation.valid(), "invalid project name was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) {
            throw new AssertionError(message);
        }
    }
}
