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

/**
 * Builds native instances and related output.
 *
 * @author xpenatan
 */
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

    /**
     * Creates a native builder.
     *
     * @return a new native builder
     */
    public static NativeBuilder desktop() {
        return new NativeBuilder();
    }

    /**
     * Sets the classpath and returns this native builder.
     *
     * @param entry the entry
     * @return this native builder for chaining
     */
    public NativeBuilder classpath(Path entry) {
        classpath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Sets the classpath and returns this native builder.
     *
     * @param entries the entries
     * @return this native builder for chaining
     */
    public NativeBuilder classpath(Collection<Path> entries) {
        classpath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    /**
     * Returns the classpath from current jvm.
     *
     * @return this native builder for chaining
     */
    public NativeBuilder classpathFromCurrentJvm() {
        addCurrentJvmClasspath(classpath);
        return this;
    }

    /**
     * Sets the native resource classpath and returns this native builder.
     *
     * @param entry the entry
     * @return this native builder for chaining
     */
    public NativeBuilder nativeResourceClasspath(Path entry) {
        nativeResourceClasspath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Sets the native resource classpath and returns this native builder.
     *
     * @param entries the entries
     * @return this native builder for chaining
     */
    public NativeBuilder nativeResourceClasspath(Collection<Path> entries) {
        nativeResourceClasspath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    /**
     * Returns the native resource classpath from current jvm.
     *
     * @return this native builder for chaining
     */
    public NativeBuilder nativeResourceClasspathFromCurrentJvm() {
        addCurrentJvmClasspath(nativeResourceClasspath);
        return this;
    }

    /**
     * Sets the build root and returns this native builder.
     *
     * @param buildRoot the build root
     * @return this native builder for chaining
     */
    public NativeBuilder buildRoot(Path buildRoot) {
        this.buildRoot = buildRoot;
        return this;
    }

    /**
     * Sets the output directory and returns this native builder.
     *
     * @param outputDirectory the output directory
     * @return this native builder for chaining
     */
    public NativeBuilder outputDirectory(Path outputDirectory) {
        return buildRoot(outputDirectory);
    }

    /**
     * Sets the generated sources directory and returns this native builder.
     *
     * @param generatedSourcesDirectory the generated sources directory
     * @return this native builder for chaining
     */
    public NativeBuilder generatedSourcesDirectory(Path generatedSourcesDirectory) {
        this.generatedSourcesDirectory = generatedSourcesDirectory;
        return this;
    }

    /**
     * Sets the release directory and returns this native builder.
     *
     * @param releaseDirectory the release directory
     * @return this native builder for chaining
     */
    public NativeBuilder releaseDirectory(Path releaseDirectory) {
        this.releaseDirectory = releaseDirectory;
        return this;
    }

    /**
     * Sets the cache directory and returns this native builder.
     *
     * @param cacheDirectory the cache directory
     * @return this native builder for chaining
     */
    public NativeBuilder cacheDirectory(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        return this;
    }

    /**
     * Sets the main class and returns this native builder.
     *
     * @param mainClass the main class
     * @return this native builder for chaining
     */
    public NativeBuilder mainClass(String mainClass) {
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
        return this;
    }

    /**
     * Sets the target file name and returns this native builder.
     *
     * @param targetFileName the target file name
     * @return this native builder for chaining
     */
    public NativeBuilder targetFileName(String targetFileName) {
        this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
        return this;
    }

    /**
     * Sets the build type and returns this native builder.
     *
     * @param buildType the build type
     * @return this native builder for chaining
     */
    public NativeBuilder buildType(String buildType) {
        this.buildType = Objects.requireNonNull(buildType, "buildType");
        return this;
    }

    /**
     * Sets the optimization and returns this native builder.
     *
     * @param optimization the optimization
     * @return this native builder for chaining
     */
    public NativeBuilder optimization(TeaVMOptimization optimization) {
        this.optimization = Objects.requireNonNull(optimization, "optimization");
        return this;
    }

    /**
     * Sets the show console and returns this native builder.
     *
     * @param showConsole the show console
     * @return this native builder for chaining
     */
    public NativeBuilder showConsole(boolean showConsole) {
        this.showConsole = showConsole;
        return this;
    }

    /**
     * Sets the obfuscated and returns this native builder.
     *
     * @param obfuscated the obfuscated
     * @return this native builder for chaining
     */
    public NativeBuilder obfuscated(boolean obfuscated) {
        this.obfuscated = obfuscated;
        return this;
    }

    /**
     * Sets the debug information and returns this native builder.
     *
     * @param debugInformation the debug information
     * @return this native builder for chaining
     */
    public NativeBuilder debugInformation(boolean debugInformation) {
        this.debugInformation = debugInformation;
        return this;
    }

    /**
     * Sets the fast dependency analysis and returns this native builder.
     *
     * @param fastDependencyAnalysis the fast dependency analysis
     * @return this native builder for chaining
     */
    public NativeBuilder fastDependencyAnalysis(boolean fastDependencyAnalysis) {
        this.fastDependencyAnalysis = fastDependencyAnalysis;
        return this;
    }

    /**
     * Sets the incremental and returns this native builder.
     *
     * @param incremental the incremental
     * @return this native builder for chaining
     */
    public NativeBuilder incremental(boolean incremental) {
        this.incremental = incremental;
        return this;
    }

    /**
     * Sets the heap dump and returns this native builder.
     *
     * @param heapDump the heap dump
     * @return this native builder for chaining
     */
    public NativeBuilder heapDump(boolean heapDump) {
        this.heapDump = heapDump;
        return this;
    }

    /**
     * Sets the short file names and returns this native builder.
     *
     * @param shortFileNames the short file names
     * @return this native builder for chaining
     */
    public NativeBuilder shortFileNames(boolean shortFileNames) {
        this.shortFileNames = shortFileNames;
        return this;
    }

    /**
     * Sets the min heap size and returns this native builder.
     *
     * @param minHeapSize the min heap size
     * @return this native builder for chaining
     */
    public NativeBuilder minHeapSize(int minHeapSize) {
        this.minHeapSize = minHeapSize;
        return this;
    }

    /**
     * Sets the max heap size and returns this native builder.
     *
     * @param maxHeapSize the max heap size
     * @return this native builder for chaining
     */
    public NativeBuilder maxHeapSize(int maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
        return this;
    }

    /**
     * Sets the property and returns this native builder.
     *
     * @param key the key
     * @param value the value
     * @return this native builder for chaining
     */
    public NativeBuilder property(String key, String value) {
        properties.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Sets the properties and returns this native builder.
     *
     * @param properties the properties
     * @return this native builder for chaining
     */
    public NativeBuilder properties(Map<String, String> properties) {
        this.properties.putAll(Objects.requireNonNull(properties, "properties"));
        return this;
    }

    /**
     * Sets the log and returns this native builder.
     *
     * @param log the log
     * @return this native builder for chaining
     */
    public NativeBuilder log(TeaVMToolLog log) {
        this.log = log;
        return this;
    }

    /**
     * Returns the build.
     *
     * @return the created value
     */
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
