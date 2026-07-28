package io.github.libfdx.tools.project.generator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Copies a bundled repository sample into a standalone, version-pinned Gradle project.
 *
 * @author xpenatan
 */
public final class ProjectGenerator {
    private static final String STARTER_SAMPLE_ID = "base/starter-project";
    private static final String STARTER_SOURCE_PACKAGE = "io.github.libfdx.samples.starter";

    private final List<ProjectSample> samples;
    private final Map<String, ProjectSample> samplesById;

    /**
     * Creates a generator backed by the samples embedded at build time.
     */
    public ProjectGenerator() {
        ProjectSample[] bundled = BundledSampleCatalogData.samples();
        ArrayList<ProjectSample> available = new ArrayList<ProjectSample>(bundled.length);
        LinkedHashMap<String, ProjectSample> byId = new LinkedHashMap<String, ProjectSample>();
        for (int i = 0; i < bundled.length; i++) {
            ProjectSample sample = bundled[i];
            available.add(sample);
            if (byId.put(sample.id(), sample) != null) {
                throw new IllegalStateException("Duplicate bundled sample identifier: " + sample.id());
            }
        }
        samples = Collections.unmodifiableList(available);
        samplesById = Collections.unmodifiableMap(byId);
    }

    /**
     * Returns the repository samples embedded in this generator build.
     *
     * @return the bundled samples
     */
    public List<ProjectSample> samples() {
        return samples;
    }

    /**
     * Returns the exact libFDX dependency version selected when this generator was built.
     *
     * @return the libFDX version
     */
    public String libfdxVersion() {
        return BundledSampleCatalogData.LIBFDX_VERSION;
    }

    /**
     * Returns the generator dependency channel: snapshot, release, or custom.
     *
     * @return the channel
     */
    public String channel() {
        return BundledSampleCatalogData.CHANNEL;
    }

    /**
     * Generates a standalone project by copying the selected bundled sample.
     *
     * @param settings the generation settings
     * @return the generation result
     */
    public ProjectGenerationResult generate(ProjectGenerationSettings settings) {
        ProjectGenerationSettings resolved =
                settings != null ? settings : ProjectGenerationSettings.builder().build();
        ProjectValidationResult validation = ProjectValidationResult.validate(resolved);
        if (!validation.valid()) {
            throw new IllegalArgumentException(validation.joinedErrors());
        }
        ProjectSample sample = samplesById.get(resolved.sampleId());
        if (sample == null) {
            throw new IllegalArgumentException("Unknown bundled sample '" + resolved.sampleId()
                    + "'. Available samples: " + availableSampleIds());
        }
        validatePlatforms(sample, resolved.platforms());

        LinkedHashMap<String, GeneratedFile> files = unpackSample(sample);
        files = selectPlatforms(files, resolved.platforms());
        if (STARTER_SAMPLE_ID.equals(sample.id())) {
            files = rewritePackage(files, resolved.packageName());
        }
        addEnvelope(files, resolved, sample);
        return new ProjectGenerationResult(resolved,
                new GeneratedProject(resolved.projectName(), new ArrayList<GeneratedFile>(files.values())));
    }

    private LinkedHashMap<String, GeneratedFile> unpackSample(ProjectSample sample) {
        LinkedHashMap<String, GeneratedFile> files = new LinkedHashMap<String, GeneratedFile>();
        byte[] archive = BundledSampleCatalogData.archive(sample.id());
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String path = normalizedArchivePath(entry.getName());
                    byte[] content = readEntry(zip);
                    GeneratedFile generated = isText(path)
                            ? GeneratedFile.text(path, new String(content, StandardCharsets.UTF_8))
                            : GeneratedFile.binary(path, content);
                    if (files.put(path, generated) != null) {
                        throw new IllegalStateException("Bundled sample contains duplicate path: " + path);
                    }
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        } catch (IOException error) {
            throw new IllegalStateException("Could not read bundled sample '" + sample.id() + "'.", error);
        }
        return files;
    }

    private void addEnvelope(Map<String, GeneratedFile> files, ProjectGenerationSettings settings,
            ProjectSample sample) {
        addGenerated(files, GeneratedFile.text("settings.gradle.kts", settingsGradle(settings, files)));
        addGenerated(files, GeneratedFile.text("build.gradle.kts", rootBuildGradle()));
        addGenerated(files, GeneratedFile.text("gradle.properties", gradleProperties()));
        addGenerated(files, GeneratedFile.text("gradle/libs.versions.toml",
                BundledSampleCatalogData.versionCatalog()));
        if (!files.containsKey(".gitignore")) {
            addGenerated(files, GeneratedFile.text(".gitignore", gitignore()));
        }
        addGenerated(files, GeneratedFile.text("PROJECT_GENERATOR.md", generatorReadme(settings, sample, files)));
    }

    private String settingsGradle(ProjectGenerationSettings settings, Map<String, GeneratedFile> files) {
        StringBuilder text = new StringBuilder();
        text.append("pluginManagement {\n");
        text.append("    val libfdxVersion = providers.gradleProperty(\"libfdxVersion\").get()\n");
        text.append("    plugins {\n");
        text.append("        id(\"io.github.libfdx\") version libfdxVersion\n");
        text.append("    }\n");
        text.append("    repositories {\n");
        text.append("        google()\n");
        text.append("        mavenCentral()\n");
        text.append("        maven {\n");
        text.append("            url = uri(\"https://central.sonatype.com/repository/maven-snapshots/\")\n");
        text.append("        }\n");
        text.append("        gradlePluginPortal()\n");
        text.append("    }\n");
        text.append("}\n");
        text.append("\n");
        text.append("rootProject.name = \"").append(kotlinString(settings.projectName())).append("\"\n");
        appendModule(text, files, "core");
        appendModule(text, files, "editor");
        ProjectPlatform[] platforms = ProjectPlatform.values();
        for (int i = 0; i < platforms.length; i++) {
            appendModule(text, files, "platform/" + platforms[i].directory());
        }
        appendModule(text, files, "platform/plugin");
        return text.toString();
    }

    private void appendModule(StringBuilder text, Map<String, GeneratedFile> files, String directory) {
        if (files.containsKey(directory + "/build.gradle.kts")) {
            text.append("include(\":").append(directory.replace('/', ':')).append("\")\n");
        }
    }

    private String rootBuildGradle() {
        return "plugins {\n"
                + "    base\n"
                + "}\n"
                + "\n"
                + "val libfdxVersion = providers.gradleProperty(\"libfdxVersion\").get()\n"
                + "gradle.extensions.extraProperties.set(\"libfdxUsePublishedLibfdx\", true)\n"
                + "gradle.extensions.extraProperties.set(\"libfdxDependencyVersion\", libfdxVersion)\n"
                + "\n"
                + "allprojects {\n"
                + "    repositories {\n"
                + "        google()\n"
                + "        mavenCentral()\n"
                + "        maven {\n"
                + "            url = uri(\"https://central.sonatype.com/repository/maven-snapshots/\")\n"
                + "        }\n"
                + "    }\n"
                + "    configurations.configureEach {\n"
                + "        resolutionStrategy.cacheChangingModulesFor(0, \"seconds\")\n"
                + "    }\n"
                + "}\n";
    }

    private String gradleProperties() {
        return "org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8\n"
                + "org.gradle.configuration-cache=true\n"
                + "libfdxVersion=" + libfdxVersion() + "\n"
                + "libfdxGeneratorChannel=" + channel() + "\n"
                + "android.useAndroidX=true\n"
                + "androidCompileSdk=36\n"
                + "androidMinSdk=23\n"
                + "androidTargetSdk=36\n";
    }

    private String gitignore() {
        return ".gradle/\n"
                + "**/build/\n"
                + ".idea/\n"
                + "*.iml\n"
                + "local.properties\n";
    }

    private String generatorReadme(ProjectGenerationSettings settings, ProjectSample sample,
            Map<String, GeneratedFile> files) {
        StringBuilder text = new StringBuilder();
        text.append("# Generated from ").append(sample.displayName()).append("\n\n");
        text.append("This project copies the `").append(sample.id())
                .append("` sample bundled in the libFDX project generator. ")
                .append("Its Java code, assets, scenes, and sample-owned Gradle files come from that sample; ")
                .append("the root Gradle envelope pins them to libFDX `").append(libfdxVersion()).append("` (")
                .append(channel()).append(").\n\n");
        text.append("The generated root project is `").append(settings.projectName()).append("`. ")
                .append("To inspect available application tasks, run:\n\n");
        text.append("```powershell\n");
        text.append("./gradlew tasks --group application\n");
        text.append("```\n\n");
        text.append("Included modules:\n\n");
        if (files.containsKey("core/build.gradle.kts")) {
            text.append("- `:core`\n");
        }
        if (files.containsKey("editor/build.gradle.kts")) {
            text.append("- `:editor`\n");
        }
        ProjectPlatform[] platforms = ProjectPlatform.values();
        for (int i = 0; i < platforms.length; i++) {
            String module = "platform/" + platforms[i].directory();
            if (files.containsKey(module + "/build.gradle.kts")) {
                text.append("- `:").append(module.replace('/', ':')).append("` (")
                        .append(platforms[i].displayName()).append(")\n");
            }
        }
        if (files.containsKey("platform/plugin/build.gradle.kts")) {
            text.append("- `:platform:plugin` (build tasks for the selected platforms)\n");
        }
        if (STARTER_SAMPLE_ID.equals(sample.id())) {
            text.append("\nJava package: `").append(settings.packageName()).append("`\n");
        }
        text.append("\nOnly the selected platform modules were exported.\n");
        return text.toString();
    }

    private void validatePlatforms(ProjectSample sample, Set<ProjectPlatform> platforms) {
        for (ProjectPlatform platform : platforms) {
            if (!sample.supports(platform)) {
                throw new IllegalArgumentException("'" + sample.displayName() + "' does not provide "
                        + platform.displayName() + ". Available platforms: " + platformNames(sample.platforms()));
            }
        }
    }

    private String platformNames(List<ProjectPlatform> platforms) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < platforms.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(platforms.get(i).displayName());
        }
        return text.toString();
    }

    private LinkedHashMap<String, GeneratedFile> selectPlatforms(
            LinkedHashMap<String, GeneratedFile> source, Set<ProjectPlatform> selected) {
        boolean keepPlugin = source.containsKey("platform/plugin/build.gradle.kts")
                && needsBuildPlugin(source, selected);
        LinkedHashMap<String, GeneratedFile> filtered = new LinkedHashMap<String, GeneratedFile>();
        for (Map.Entry<String, GeneratedFile> entry : source.entrySet()) {
            String path = entry.getKey();
            if (path.startsWith("platform/")) {
                int directoryEnd = path.indexOf('/', "platform/".length());
                String directory = directoryEnd >= 0
                        ? path.substring("platform/".length(), directoryEnd)
                        : path.substring("platform/".length());
                if ("plugin".equals(directory)) {
                    if (!keepPlugin) {
                        continue;
                    }
                } else {
                    ProjectPlatform platform = ProjectPlatform.fromDirectory(directory);
                    if (platform != null && !selected.contains(platform)) {
                        continue;
                    }
                }
            }
            filtered.put(path, entry.getValue());
        }
        return filtered;
    }

    private boolean needsBuildPlugin(Map<String, GeneratedFile> files, Set<ProjectPlatform> selected) {
        for (ProjectPlatform platform : selected) {
            if (platform != ProjectPlatform.ANDROID
                    && files.containsKey("platform/" + platform.directory() + "/build.gradle.kts")) {
                return true;
            }
        }
        return false;
    }

    private LinkedHashMap<String, GeneratedFile> rewritePackage(
            LinkedHashMap<String, GeneratedFile> source, String packageName) {
        String sourcePath = STARTER_SOURCE_PACKAGE.replace('.', '/');
        String targetPath = packageName.replace('.', '/');
        LinkedHashMap<String, GeneratedFile> rewritten = new LinkedHashMap<String, GeneratedFile>();
        for (GeneratedFile file : source.values()) {
            String path = file.path().replace("/" + sourcePath + "/", "/" + targetPath + "/");
            GeneratedFile replacement = file.isText()
                    ? GeneratedFile.text(path,
                            file.textContent().replace(STARTER_SOURCE_PACKAGE, packageName))
                    : GeneratedFile.binary(path, file.binaryContent());
            if (rewritten.put(path, replacement) != null) {
                throw new IllegalStateException("Package rewrite produced duplicate path: " + path);
            }
        }
        return rewritten;
    }

    private String availableSampleIds() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(samples.get(i).id());
        }
        return text.toString();
    }

    private static void addGenerated(Map<String, GeneratedFile> files, GeneratedFile file) {
        if (files.containsKey(file.path())) {
            throw new IllegalStateException("Bundled sample conflicts with generated project file: " + file.path());
        }
        files.put(file.path(), file);
    }

    private static String normalizedArchivePath(String value) {
        String path = value != null ? value.replace('\\', '/') : "";
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.length() == 0 || path.equals("..") || path.startsWith("../") || path.contains("/../")) {
            throw new IllegalStateException("Unsafe bundled sample path: " + value);
        }
        return path;
    }

    private static byte[] readEntry(ZipInputStream zip) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int read;
        while ((read = zip.read(buffer)) != -1) {
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }

    private static boolean isText(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".kts")
                || lower.endsWith(".md")
                || lower.endsWith(".txt")
                || lower.endsWith(".xml")
                || lower.endsWith(".json")
                || lower.endsWith(".properties")
                || lower.endsWith(".toml")
                || lower.endsWith(".fnt")
                || lower.endsWith(".fdxscene")
                || lower.endsWith(".fdxgraph")
                || lower.endsWith(".gitignore")
                || lower.endsWith(".gitattributes");
    }

    private static String kotlinString(String value) {
        String text = value != null ? value : "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("$", "\\$");
    }
}
