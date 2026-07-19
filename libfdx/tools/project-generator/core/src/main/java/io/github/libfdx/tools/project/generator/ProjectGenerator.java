package io.github.libfdx.tools.project.generator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a project generator.
 *
 * @author xpenatan
 */
public final class ProjectGenerator {
    private final TemplateRenderer renderer = new TemplateRenderer();

    /**
     * Runs the generate step.
     *
     * @param settings the settings
     * @return the generate
     */
    public ProjectGenerationResult generate(ProjectGenerationSettings settings) {
        ProjectGenerationSettings resolved = settings != null ? settings : ProjectGenerationSettings.builder().build();
        ProjectValidationResult validation = ProjectValidationResult.validate(resolved);
        if (!validation.valid()) {
            throw new ProjectGenerationException(validation.joinedErrors());
        }

        Map<String, String> values = placeholders(resolved);
        ArrayList<GeneratedFile> files = new ArrayList<GeneratedFile>();
        addText(files, "settings.gradle.kts", settingsGradle(), values);
        addText(files, "build.gradle.kts", rootBuildGradle(), values);
        addText(files, "gradle.properties", gradleProperties(), values);
        addText(files, "README.md", readme(), values);
        addText(files, "core/build.gradle.kts", coreBuildGradle(), values);
        addText(files, "core/src/main/java/{{packagePath}}/{{applicationClassName}}.java",
                applicationJava(), values);
        if (resolved.desktopPlatform()) {
            addText(files, "platform/desktop/build.gradle.kts", desktopBuildGradle(), values);
            addText(files,
                    "platform/desktop/src/main/java/{{desktopPackagePath}}/{{desktopLauncherClassName}}.java",
                    desktopLauncherJava(), values);
        }
        return new ProjectGenerationResult(resolved, new GeneratedProject(resolved.projectName(), files));
    }

    private void addText(List<GeneratedFile> files, String path, String template, Map<String, String> values) {
        files.add(GeneratedFile.text(renderer.render(path, values), renderer.render(template, values)));
    }

    private Map<String, String> placeholders(ProjectGenerationSettings settings) {
        LinkedHashMap<String, String> values = new LinkedHashMap<String, String>();
        values.put("projectName", settings.projectName());
        values.put("packageName", settings.packageName());
        values.put("packagePath", settings.packagePath());
        values.put("applicationClassName", settings.applicationClassName());
        values.put("desktopPackageName", settings.desktopPackageName());
        values.put("desktopPackagePath", settings.desktopPackagePath());
        values.put("desktopLauncherClassName", settings.desktopLauncherClassName());
        values.put("libfdxVersion", settings.libfdxVersion());
        return values;
    }

    private static String settingsGradle() {
        return "rootProject.name = \"{{projectName}}\"\n"
                + "\n"
                + "include(\":core\")\n"
                + "include(\":platform:desktop\")\n";
    }

    private static String rootBuildGradle() {
        return "plugins {\n"
                + "    id(\"base\")\n"
                + "}\n"
                + "\n"
                + "extra[\"libfdxVersion\"] = providers.gradleProperty(\"libfdxVersion\")\n"
                + "    .orElse(\"{{libfdxVersion}}\")\n"
                + "    .get()\n"
                + "\n"
                + "allprojects {\n"
                + "    group = \"{{packageName}}\"\n"
                + "    version = \"0.1.0\"\n"
                + "\n"
                + "    repositories {\n"
                + "        mavenCentral()\n"
                + "        maven {\n"
                + "            url = uri(\"https://central.sonatype.com/repository/maven-snapshots/\")\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private static String gradleProperties() {
        return "org.gradle.jvmargs=-Xmx2g\n";
    }

    private static String readme() {
        return "# {{projectName}}\n"
                + "\n"
                + "Generated libfdx project.\n"
                + "\n"
                + "Run the desktop GL target:\n"
                + "\n"
                + "```powershell\n"
                + ".\\gradlew.bat :platform:desktop:run_gl\n"
                + "```\n"
                + "\n"
                + "If this project does not include a Gradle wrapper yet, run the same task with your local `gradle` command.\n";
    }

    private static String coreBuildGradle() {
        return "plugins {\n"
                + "    id(\"java-library\")\n"
                + "}\n"
                + "\n"
                + "java {\n"
                + "    sourceCompatibility = JavaVersion.toVersion(25)\n"
                + "    targetCompatibility = JavaVersion.toVersion(25)\n"
                + "}\n"
                + "\n"
                + "val libfdxVersion = rootProject.extra[\"libfdxVersion\"] as String\n"
                + "\n"
                + "dependencies {\n"
                + "    implementation(\"io.github.libfdx:fdx:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:application:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:display:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:files:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:input:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:graphics:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:g2d:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:ui_kit:$libfdxVersion\")\n"
                + "}\n";
    }

    private static String desktopBuildGradle() {
        return "import org.gradle.jvm.toolchain.JavaLanguageVersion\n"
                + "\n"
                + "plugins {\n"
                + "    id(\"java\")\n"
                + "}\n"
                + "\n"
                + "java {\n"
                + "    sourceCompatibility = JavaVersion.toVersion(25)\n"
                + "    targetCompatibility = JavaVersion.toVersion(25)\n"
                + "}\n"
                + "\n"
                + "val libfdxVersion = rootProject.extra[\"libfdxVersion\"] as String\n"
                + "\n"
                + "dependencies {\n"
                + "    implementation(project(\":core\"))\n"
                + "    implementation(\"io.github.libfdx:backend_desktop:$libfdxVersion\")\n"
                + "    runtimeOnly(\"io.github.libfdx:gl_desktop:$libfdxVersion\")\n"
                + "}\n"
                + "\n"
                + "val desktopMainClass = \"{{desktopPackageName}}.{{desktopLauncherClassName}}\"\n"
                + "\n"
                + "fun JavaExec.configureDesktopRun() {\n"
                + "    group = \"application\"\n"
                + "    description = \"Runs the desktop GL target.\"\n"
                + "    classpath = sourceSets[\"main\"].runtimeClasspath\n"
                + "    mainClass.set(desktopMainClass)\n"
                + "    workingDir = rootProject.projectDir\n"
                + "    javaLauncher.set(javaToolchains.launcherFor {\n"
                + "        languageVersion.set(JavaLanguageVersion.of(25))\n"
                + "    })\n"
                + "    jvmArgs(\"-Dorg.lwjgl.system.stackSize=1024\")\n"
                + "    jvmArgs(\"--enable-native-access=ALL-UNNAMED\")\n"
                + "}\n"
                + "\n"
                + "tasks.register<JavaExec>(\"run_gl\") {\n"
                + "    configureDesktopRun()\n"
                + "}\n";
    }

    private static String applicationJava() {
        return "package {{packageName}};\n"
                + "\n"
                + "import io.github.libfdx.Fdx;\n"
                + "import io.github.libfdx.application.Application;\n"
                + "import io.github.libfdx.application.ApplicationAdapter;\n"
                + "import io.github.libfdx.graphics.GraphicsContext;\n"
                + "import io.github.libfdx.graphics.LoadOp;\n"
                + "import io.github.libfdx.graphics.g2d.ShapeRenderer2D;\n"
                + "import io.github.libfdx.ui.Ui;\n"
                + "import io.github.libfdx.ui.UiBooleanState;\n"
                + "import io.github.libfdx.ui.UiRoot;\n"
                + "import io.github.libfdx.ui.UiToolkit;\n"
                + "\n"
                + "public final class {{applicationClassName}} extends ApplicationAdapter {\n"
                + "    private Application application;\n"
                + "    private GraphicsContext graphics;\n"
                + "    private ShapeRenderer2D shapes;\n"
                + "    private UiRoot ui;\n"
                + "    private UiBooleanState showDetails;\n"
                + "\n"
                + "    @Override\n"
                + "    public void create(Fdx fdx) {\n"
                + "        application = fdx.app();\n"
                + "        graphics = fdx.graphics().main();\n"
                + "        shapes = new ShapeRenderer2D(graphics);\n"
                + "        showDetails = Ui.state(true);\n"
                + "        ui = new UiToolkit(fdx.files()).root(fdx.displays().main(), graphics).input(fdx.input());\n"
                + "        ui.setContent(root -> {\n"
                + "            root.panel(Ui.modifier().width(300.0f).padding(12.0f).gap(8.0f), panel -> {\n"
                + "                panel.text(\"{{projectName}}\");\n"
                + "                panel.checkbox(\"Show details\", showDetails);\n"
                + "                if (showDetails.get()) {\n"
                + "                    panel.text(\"Graphics: \" + graphics.providerId());\n"
                + "                }\n"
                + "            });\n"
                + "        });\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void resize(int width, int height) {\n"
                + "        if (ui != null) {\n"
                + "            ui.resize(width, height);\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void render() {\n"
                + "        shapes.begin(LoadOp.clear(0.10f, 0.12f, 0.16f, 1.0f));\n"
                + "        shapes.filledTriangle(0.0f, 0.60f, -0.62f, -0.48f, 0.62f, -0.48f,\n"
                + "                0.35f, 0.68f, 0.95f, 1.0f);\n"
                + "        shapes.end();\n"
                + "        ui.update(application.deltaTime());\n"
                + "        ui.render();\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public void dispose() {\n"
                + "        if (ui != null) {\n"
                + "            ui.dispose();\n"
                + "            ui = null;\n"
                + "        }\n"
                + "        if (shapes != null) {\n"
                + "            shapes.dispose();\n"
                + "            shapes = null;\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
    }

    private static String desktopLauncherJava() {
        return "package {{desktopPackageName}};\n"
                + "\n"
                + "import io.github.libfdx.backend.desktop.DesktopApplicationBackend;\n"
                + "import io.github.libfdx.backend.desktop.DesktopApplicationConfig;\n"
                + "import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;\n"
                + "import {{packageName}}.{{applicationClassName}};\n"
                + "\n"
                + "public final class {{desktopLauncherClassName}} {\n"
                + "    private {{desktopLauncherClassName}}() {\n"
                + "    }\n"
                + "\n"
                + "    public static void main(String[] args) {\n"
                + "        DesktopApplicationConfig config = new DesktopApplicationConfig()\n"
                + "                .title(\"{{projectName}}\")\n"
                + "                .size(960, 540)\n"
                + "                .vSync(true)\n"
                + "                .foregroundFps(60)\n"
                + "                .graphics(new DesktopOpenGLProvider());\n"
                + "\n"
                + "        new DesktopApplicationBackend().start(config, new {{applicationClassName}}());\n"
                + "    }\n"
                + "}\n";
    }
}
