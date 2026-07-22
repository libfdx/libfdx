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
        addText(files, "fdx-project.json", projectManifest(), values);
        addText(files, "scenes/main.fdxscene", defaultScene(), values);
        addText(files, "assets/.gitkeep", "", values);
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
        return "pluginManagement {\n"
                + "    repositories {\n"
                + "        gradlePluginPortal()\n"
                + "        mavenCentral()\n"
                + "        maven {\n"
                + "            url = uri(\"https://central.sonatype.com/repository/maven-snapshots/\")\n"
                + "        }\n"
                + "    }\n"
                + "    plugins {\n"
                + "        id(\"io.github.libfdx\") version providers.gradleProperty(\"libfdxVersion\")\n"
                + "            .orElse(\"{{libfdxVersion}}\")\n"
                + "            .get()\n"
                + "    }\n"
                + "}\n"
                + "\n"
                + "rootProject.name = \"{{projectName}}\"\n"
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
                + "Build the desktop editor project bundle:\n"
                + "\n"
                + "```powershell\n"
                + ".\\gradlew.bat :core:libfdx_ecs_project_bundle\n"
                + "```\n"
                + "\n"
                + "If this project does not include a Gradle wrapper yet, run the same task with your local `gradle` command.\n";
    }

    private static String projectManifest() {
        return "{\n"
                + "  \"format\": \"libfdx.ecs.project\",\n"
                + "  \"formatVersion\": 1,\n"
                + "  \"id\": \"{{packageName}}\",\n"
                + "  \"entryClass\": \"{{packageName}}.{{applicationClassName}}\",\n"
                + "  \"defaultScene\": \"scenes/main.fdxscene\",\n"
                + "  \"assetsDirectory\": \"assets\",\n"
                + "  \"gradleRoot\": \".\",\n"
                + "  \"gradleProject\": \":core\",\n"
                + "  \"desktopBundleTask\": \"libfdx_ecs_project_bundle\"\n"
                + "}\n";
    }

    private static String defaultScene() {
        return "{\n"
                + "  \"format\": \"libfdx.ecs.scene\",\n"
                + "  \"version\": 1,\n"
                + "  \"project\": \"{{packageName}}\",\n"
                + "  \"scene\": \"main\",\n"
                + "  \"entities\": []\n"
                + "}\n";
    }

    private static String coreBuildGradle() {
        return "plugins {\n"
                + "    id(\"java-library\")\n"
                + "    id(\"io.github.libfdx\")\n"
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
                + "    implementation(\"io.github.libfdx:ecs:$libfdxVersion\")\n"
                + "    implementation(\"io.github.libfdx:ecs_tooling:$libfdxVersion\")\n"
                + "}\n"
                + "\n"
                + "libfdx {\n"
                + "    ecsProject {\n"
                + "        projectId.set(\"{{packageName}}\")\n"
                + "        entryClass.set(\"{{packageName}}.{{applicationClassName}}\")\n"
                + "        projectRoot.set(rootProject.layout.projectDirectory)\n"
                + "        libfdxAbi.set(libfdxVersion)\n"
                + "    }\n"
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
                + "import io.github.libfdx.ecs.World;\n"
                + "import io.github.libfdx.ecs.component.Component;\n"
                + "import io.github.libfdx.ecs.tooling.EcsProject;\n"
                + "import io.github.libfdx.ecs.tooling.EcsProjectRuntime;\n"
                + "import io.github.libfdx.ecs.tooling.EcsRenderContext;\n"
                + "import io.github.libfdx.ecs.tooling.schema.EcsComponentDescriptor;\n"
                + "import io.github.libfdx.ecs.tooling.schema.EcsEntityAdapter;\n"
                + "import io.github.libfdx.ecs.tooling.schema.EcsProjectSchema;\n"
                + "import io.github.libfdx.graphics.LoadOp;\n"
                + "import io.github.libfdx.graphics.g2d.ShapeRenderer2D;\n"
                + "\n"
                + "public final class {{applicationClassName}} extends EcsProject {\n"
                + "    private static final EcsEntityAdapter ENTITIES = new EntityAdapter();\n"
                + "    private static final EcsProjectSchema SCHEMA = EcsProjectSchema.builder(ENTITIES)\n"
                + "            .component(EcsComponentDescriptor.builder(\n"
                + "                    \"{{packageName}}.entity-metadata\", \"Entity Metadata\",\n"
                + "                    EntityMetadata.class, EntityMetadata::new)\n"
                + "                    .transientComponent()\n"
                + "                    .build())\n"
                + "            .build();\n"
                + "\n"
                + "    public {{applicationClassName}}() {\n"
                + "        super(\"{{packageName}}\", \"{{projectName}}\", \"assets\", \"scenes/main.fdxscene\");\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public EcsProjectSchema schema() {\n"
                + "        return SCHEMA;\n"
                + "    }\n"
                + "\n"
                + "    @Override\n"
                + "    public EcsProjectRuntime createRuntime() {\n"
                + "        return new Runtime();\n"
                + "    }\n"
                + "\n"
                + "    private static final class EntityMetadata implements Component {\n"
                + "        long id;\n"
                + "        String name = \"Entity\";\n"
                + "        long parentId;\n"
                + "    }\n"
                + "\n"
                + "    private static final class EntityAdapter implements EcsEntityAdapter {\n"
                + "        public int create(World world, long id, String name) {\n"
                + "            int entity = world.createEntity();\n"
                + "            EntityMetadata metadata = new EntityMetadata();\n"
                + "            metadata.id = id;\n"
                + "            metadata.name = name;\n"
                + "            world.add(entity, metadata);\n"
                + "            return entity;\n"
                + "        }\n"
                + "\n"
                + "        public long persistentId(World world, int entity) {\n"
                + "            return world.require(entity, EntityMetadata.class).id;\n"
                + "        }\n"
                + "\n"
                + "        public String name(World world, int entity) {\n"
                + "            return world.require(entity, EntityMetadata.class).name;\n"
                + "        }\n"
                + "\n"
                + "        public void name(World world, int entity, String name) {\n"
                + "            world.require(entity, EntityMetadata.class).name = name;\n"
                + "        }\n"
                + "\n"
                + "        public long parentId(World world, int entity) {\n"
                + "            return world.require(entity, EntityMetadata.class).parentId;\n"
                + "        }\n"
                + "\n"
                + "        public void parentId(World world, int entity, long parentId) {\n"
                + "            world.require(entity, EntityMetadata.class).parentId = parentId;\n"
                + "        }\n"
                + "    }\n"
                + "\n"
                + "    private static final class Runtime implements EcsProjectRuntime {\n"
                + "        private final World world = new World();\n"
                + "        private ShapeRenderer2D shapes;\n"
                + "\n"
                + "        public void create(Fdx fdx) {\n"
                + "            shapes = new ShapeRenderer2D(fdx.graphics().main());\n"
                + "        }\n"
                + "\n"
                + "        public World world() {\n"
                + "            return world;\n"
                + "        }\n"
                + "\n"
                + "        public void render(EcsRenderContext context) {\n"
                + "            shapes.begin(LoadOp.clear(0.10f, 0.12f, 0.16f, 1.0f));\n"
                + "            shapes.filledTriangle(0.0f, 0.60f, -0.62f, -0.48f, 0.62f, -0.48f,\n"
                + "                    0.35f, 0.68f, 0.95f, 1.0f);\n"
                + "            shapes.end();\n"
                + "        }\n"
                + "\n"
                + "        public void dispose() {\n"
                + "            if (shapes != null) {\n"
                + "                shapes.dispose();\n"
                + "                shapes = null;\n"
                + "            }\n"
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
                + "import io.github.libfdx.ecs.tooling.EcsProjectApplication;\n"
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
                + "        new DesktopApplicationBackend().start(\n"
                + "                config, new EcsProjectApplication(new {{applicationClassName}}()));\n"
                + "    }\n"
                + "}\n";
    }
}
