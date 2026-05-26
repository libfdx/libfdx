package io.github.libfdx.backend.desktopnative;

import io.github.libfdx.backend.teavm.shared.BuilderException;
import io.github.libfdx.backend.teavm.shared.TeaVMBuildRunner;
import io.github.libfdx.backend.teavm.shared.TeaVMOptimization;
import org.teavm.tooling.TeaVMTargetType;
import org.teavm.tooling.TeaVMToolLog;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public final class NativeBuilder {
    private static final int BYTES_PER_MEGABYTE = 1024 * 1024;

    private final ArrayList<Path> classpath = new ArrayList<>();
    private final ArrayList<Path> nativeResourceClasspath = new ArrayList<>();
    private final LinkedHashMap<String, String> properties = new LinkedHashMap<>();
    private Path buildRoot;
    private Path generatedSourcesDirectory;
    private Path releaseDirectory;
    private Path cacheDirectory;
    private String mainClass;
    private String targetFileName = "app";
    private String buildType = "Debug";
    private TeaVMOptimization optimization = TeaVMOptimization.AGGRESSIVE;
    private boolean showConsole = true;
    private boolean obfuscated = true;
    private boolean debugInformation;
    private boolean fastDependencyAnalysis;
    private boolean incremental;
    private boolean heapDump;
    private boolean shortFileNames = true;
    private int minHeapSize = 4;
    private int maxHeapSize = 128;
    private TeaVMToolLog log;

    private NativeBuilder() {
    }

    public static NativeBuilder desktop() {
        return new NativeBuilder();
    }

    public NativeBuilder classpath(Path entry) {
        classpath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    public NativeBuilder classpath(Collection<Path> entries) {
        classpath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    public NativeBuilder classpathFromCurrentJvm() {
        addCurrentJvmClasspath(classpath);
        return this;
    }

    public NativeBuilder nativeResourceClasspath(Path entry) {
        nativeResourceClasspath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    public NativeBuilder nativeResourceClasspath(Collection<Path> entries) {
        nativeResourceClasspath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    public NativeBuilder nativeResourceClasspathFromCurrentJvm() {
        addCurrentJvmClasspath(nativeResourceClasspath);
        return this;
    }

    public NativeBuilder buildRoot(Path buildRoot) {
        this.buildRoot = buildRoot;
        return this;
    }

    public NativeBuilder outputDirectory(Path outputDirectory) {
        return buildRoot(outputDirectory);
    }

    public NativeBuilder generatedSourcesDirectory(Path generatedSourcesDirectory) {
        this.generatedSourcesDirectory = generatedSourcesDirectory;
        return this;
    }

    public NativeBuilder releaseDirectory(Path releaseDirectory) {
        this.releaseDirectory = releaseDirectory;
        return this;
    }

    public NativeBuilder cacheDirectory(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        return this;
    }

    public NativeBuilder mainClass(String mainClass) {
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
        return this;
    }

    public NativeBuilder targetFileName(String targetFileName) {
        this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
        return this;
    }

    public NativeBuilder buildType(String buildType) {
        this.buildType = Objects.requireNonNull(buildType, "buildType");
        return this;
    }

    public NativeBuilder optimization(TeaVMOptimization optimization) {
        this.optimization = Objects.requireNonNull(optimization, "optimization");
        return this;
    }

    public NativeBuilder showConsole(boolean showConsole) {
        this.showConsole = showConsole;
        return this;
    }

    public NativeBuilder obfuscated(boolean obfuscated) {
        this.obfuscated = obfuscated;
        return this;
    }

    public NativeBuilder debugInformation(boolean debugInformation) {
        this.debugInformation = debugInformation;
        return this;
    }

    public NativeBuilder fastDependencyAnalysis(boolean fastDependencyAnalysis) {
        this.fastDependencyAnalysis = fastDependencyAnalysis;
        return this;
    }

    public NativeBuilder incremental(boolean incremental) {
        this.incremental = incremental;
        return this;
    }

    public NativeBuilder heapDump(boolean heapDump) {
        this.heapDump = heapDump;
        return this;
    }

    public NativeBuilder shortFileNames(boolean shortFileNames) {
        this.shortFileNames = shortFileNames;
        return this;
    }

    public NativeBuilder minHeapSize(int minHeapSize) {
        this.minHeapSize = minHeapSize;
        return this;
    }

    public NativeBuilder maxHeapSize(int maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
        return this;
    }

    public NativeBuilder property(String key, String value) {
        properties.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    public NativeBuilder properties(Map<String, String> properties) {
        this.properties.putAll(Objects.requireNonNull(properties, "properties"));
        return this;
    }

    public NativeBuilder log(TeaVMToolLog log) {
        this.log = log;
        return this;
    }

    public NativeBuildResult build() {
        Path root = requirePath(buildRoot, "buildRoot");
        Path sources = generatedSourcesDirectory != null
                ? generatedSourcesDirectory.toAbsolutePath().normalize()
                : root.resolve("c/src").toAbsolutePath().normalize();
        Path release = releaseDirectory != null
                ? releaseDirectory.toAbsolutePath().normalize()
                : root.resolve("c/release").toAbsolutePath().normalize();
        String actualMainClass = requireText(mainClass, "mainClass");
        String actualTargetFileName = requireText(targetFileName, "targetFileName");
        List<Path> actualClasspath = normalized(classpath);
        if (actualClasspath.isEmpty()) {
            throw new BuilderException("Classpath is empty. Add game/framework jars or call classpathFromCurrentJvm().");
        }
        Properties teaVMProperties = new Properties();
        teaVMProperties.putAll(properties);
        teaVMProperties.setProperty("libfdx.native.backend", "desktop_native");
        Set<Path> generatedFiles = TeaVMBuildRunner.build(new TeaVMBuildRunner.Request(
                TeaVMTargetType.C,
                actualClasspath,
                sources,
                cacheDirectory != null ? cacheDirectory.toAbsolutePath().normalize() : null,
                actualMainClass,
                actualTargetFileName,
                null,
                optimization,
                teaVMProperties,
                log,
                obfuscated,
                false,
                debugInformation,
                false,
                false,
                fastDependencyAnalysis,
                incremental
        ), strategy -> {
            strategy.setMinHeapSize(minHeapSize * BYTES_PER_MEGABYTE);
            strategy.setMaxHeapSize(maxHeapSize * BYTES_PER_MEGABYTE);
            strategy.setHeapDump(heapDump);
            strategy.setShortFileNames(shortFileNames);
        }, "TeaVM native build failed for " + actualMainClass);
        try {
            Set<Path> projectFiles = NativeProjectWriter.write(NativeProject.builder()
                    .buildRoot(root)
                    .generatedSourcesDirectory(sources)
                    .releaseDirectory(release)
                    .projectName(actualTargetFileName)
                    .buildType(buildType)
                    .showConsole(showConsole)
                    .nativeResourceClasspath(normalized(nativeResourceClasspath))
                    .build());
            return new NativeBuildResult(root, sources, release, generatedFiles, projectFiles);
        } catch (java.io.IOException error) {
            throw new BuilderException("Could not write native project to " + root, error);
        }
    }

    private static void addCurrentJvmClasspath(ArrayList<Path> target) {
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isBlank()) {
            for (String entry : classPath.split(java.io.File.pathSeparator)) {
                if (!entry.isBlank()) {
                    target.add(Path.of(entry));
                }
            }
        }
    }

    private static List<Path> normalized(List<Path> paths) {
        return paths.stream().map(path -> path.toAbsolutePath().normalize()).toList();
    }

    private static Path requirePath(Path path, String name) {
        if (path == null) {
            throw new BuilderException(name + " must be set");
        }
        return path.toAbsolutePath().normalize();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BuilderException(name + " must be set");
        }
        return value;
    }
}
