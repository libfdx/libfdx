package io.github.libfdx.backend.psp;

import io.github.libfdx.backend.teavm.shared.BuilderException;
import io.github.libfdx.backend.teavm.shared.TeaVMBuildRunner;
import io.github.libfdx.backend.teavm.shared.TeaVMOptimization;
import io.github.libfdx.tools.font.BitmapFontGenerator;
import io.github.libfdx.tools.font.BitmapFontSpec;
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
 * Builds psp instances and related output.
 *
 * @author xpenatan
 */
public final class PspBuilder {
    private static final int BYTES_PER_MEGABYTE = 1024 * 1024;

    private final ArrayList<Path> classpath = new ArrayList<>();
    private final ArrayList<Path> nativeResourceClasspath = new ArrayList<>();
    private final ArrayList<Path> assets = new ArrayList<>();
    private final ArrayList<BitmapFontSpec> bitmapFonts = new ArrayList<>();
    private final LinkedHashMap<String, String> properties = new LinkedHashMap<>();
    private Path buildRoot;
    private Path generatedSourcesDirectory;
    private Path releaseDirectory;
    private Path cacheDirectory;
    private String mainClass;
    private String targetFileName = "app";
    private TeaVMOptimization optimization = TeaVMOptimization.BALANCED;
    private boolean obfuscated;
    private boolean debugInformation;
    private boolean fastDependencyAnalysis;
    private boolean incremental;
    private boolean heapDump;
    private boolean shortFileNames = true;
    private boolean debugMemory;
    private int minHeapSize = 2;
    private int maxHeapSize = 8;
    private TeaVMToolLog log;

    private PspBuilder() {
    }

    /**
     * Creates a PSP builder.
     *
     * @return a new PSP builder
     */
    public static PspBuilder psp() {
        return new PspBuilder();
    }

    /**
     * Sets the classpath and returns this PSP builder.
     *
     * @param entry the entry
     * @return this PSP builder for chaining
     */
    public PspBuilder classpath(Path entry) {
        classpath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Sets the classpath and returns this PSP builder.
     *
     * @param entries the entries
     * @return this PSP builder for chaining
     */
    public PspBuilder classpath(Collection<Path> entries) {
        classpath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    /**
     * Returns the classpath from current jvm.
     *
     * @return this PSP builder for chaining
     */
    public PspBuilder classpathFromCurrentJvm() {
        addCurrentJvmClasspath(classpath);
        return this;
    }

    /**
     * Sets the native resource classpath and returns this PSP builder.
     *
     * @param entry the entry
     * @return this PSP builder for chaining
     */
    public PspBuilder nativeResourceClasspath(Path entry) {
        nativeResourceClasspath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Sets the native resource classpath and returns this PSP builder.
     *
     * @param entries the entries
     * @return this PSP builder for chaining
     */
    public PspBuilder nativeResourceClasspath(Collection<Path> entries) {
        nativeResourceClasspath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    /**
     * Returns the native resource classpath from current jvm.
     *
     * @return this PSP builder for chaining
     */
    public PspBuilder nativeResourceClasspathFromCurrentJvm() {
        addCurrentJvmClasspath(nativeResourceClasspath);
        return this;
    }

    /**
     * Sets the asset and returns this PSP builder.
     *
     * @param asset the asset
     * @return this PSP builder for chaining
     */
    public PspBuilder asset(Path asset) {
        assets.add(Objects.requireNonNull(asset, "asset"));
        return this;
    }

    /**
     * Sets the assets and returns this PSP builder.
     *
     * @param assets the assets
     * @return this PSP builder for chaining
     */
    public PspBuilder assets(Collection<Path> assets) {
        this.assets.addAll(Objects.requireNonNull(assets, "assets"));
        return this;
    }

    /**
     * Sets the bitmap font and returns this PSP builder.
     *
     * @param bitmapFont the bitmap font
     * @return this PSP builder for chaining
     */
    public PspBuilder bitmapFont(BitmapFontSpec bitmapFont) {
        bitmapFonts.add(Objects.requireNonNull(bitmapFont, "bitmapFont"));
        return this;
    }

    /**
     * Sets the bitmap fonts and returns this PSP builder.
     *
     * @param bitmapFonts the bitmap fonts
     * @return this PSP builder for chaining
     */
    public PspBuilder bitmapFonts(Collection<BitmapFontSpec> bitmapFonts) {
        this.bitmapFonts.addAll(Objects.requireNonNull(bitmapFonts, "bitmapFonts"));
        return this;
    }

    /**
     * Sets the bitmap font and returns this PSP builder.
     *
     * @param sourceFile the source file
     * @param name the name
     * @param size the size
     * @return this PSP builder for chaining
     */
    public PspBuilder bitmapFont(Path sourceFile, String name, int size) {
        return bitmapFont(sourceFile, name, size, BitmapFontSpec.DEFAULT_ASSET_PATH);
    }

    /**
     * Sets the bitmap font and returns this PSP builder.
     *
     * @param sourceFile the source file
     * @param name the name
     * @param size the size
     * @param assetPath the asset path
     * @return this PSP builder for chaining
     */
    public PspBuilder bitmapFont(Path sourceFile, String name, int size, String assetPath) {
        return bitmapFont(BitmapFontSpec.builder()
                .sourceFile(sourceFile)
                .name(name)
                .size(size)
                .assetPath(assetPath)
                .build());
    }

    /**
     * Sets the build root and returns this PSP builder.
     *
     * @param buildRoot the build root
     * @return this PSP builder for chaining
     */
    public PspBuilder buildRoot(Path buildRoot) {
        this.buildRoot = buildRoot;
        return this;
    }

    /**
     * Sets the output directory and returns this PSP builder.
     *
     * @param outputDirectory the output directory
     * @return this PSP builder for chaining
     */
    public PspBuilder outputDirectory(Path outputDirectory) {
        return buildRoot(outputDirectory);
    }

    /**
     * Sets the generated sources directory and returns this PSP builder.
     *
     * @param generatedSourcesDirectory the generated sources directory
     * @return this PSP builder for chaining
     */
    public PspBuilder generatedSourcesDirectory(Path generatedSourcesDirectory) {
        this.generatedSourcesDirectory = generatedSourcesDirectory;
        return this;
    }

    /**
     * Sets the release directory and returns this PSP builder.
     *
     * @param releaseDirectory the release directory
     * @return this PSP builder for chaining
     */
    public PspBuilder releaseDirectory(Path releaseDirectory) {
        this.releaseDirectory = releaseDirectory;
        return this;
    }

    /**
     * Sets the cache directory and returns this PSP builder.
     *
     * @param cacheDirectory the cache directory
     * @return this PSP builder for chaining
     */
    public PspBuilder cacheDirectory(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        return this;
    }

    /**
     * Sets the main class and returns this PSP builder.
     *
     * @param mainClass the main class
     * @return this PSP builder for chaining
     */
    public PspBuilder mainClass(String mainClass) {
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
        return this;
    }

    /**
     * Sets the target file name and returns this PSP builder.
     *
     * @param targetFileName the target file name
     * @return this PSP builder for chaining
     */
    public PspBuilder targetFileName(String targetFileName) {
        this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
        return this;
    }

    /**
     * Sets the optimization and returns this PSP builder.
     *
     * @param optimization the optimization
     * @return this PSP builder for chaining
     */
    public PspBuilder optimization(TeaVMOptimization optimization) {
        this.optimization = Objects.requireNonNull(optimization, "optimization");
        return this;
    }

    /**
     * Sets the obfuscated and returns this PSP builder.
     *
     * @param obfuscated the obfuscated
     * @return this PSP builder for chaining
     */
    public PspBuilder obfuscated(boolean obfuscated) {
        this.obfuscated = obfuscated;
        return this;
    }

    /**
     * Sets the debug information and returns this PSP builder.
     *
     * @param debugInformation the debug information
     * @return this PSP builder for chaining
     */
    public PspBuilder debugInformation(boolean debugInformation) {
        this.debugInformation = debugInformation;
        return this;
    }

    /**
     * Sets the fast dependency analysis and returns this PSP builder.
     *
     * @param fastDependencyAnalysis the fast dependency analysis
     * @return this PSP builder for chaining
     */
    public PspBuilder fastDependencyAnalysis(boolean fastDependencyAnalysis) {
        this.fastDependencyAnalysis = fastDependencyAnalysis;
        return this;
    }

    /**
     * Sets the incremental and returns this PSP builder.
     *
     * @param incremental the incremental
     * @return this PSP builder for chaining
     */
    public PspBuilder incremental(boolean incremental) {
        this.incremental = incremental;
        return this;
    }

    /**
     * Sets the heap dump and returns this PSP builder.
     *
     * @param heapDump the heap dump
     * @return this PSP builder for chaining
     */
    public PspBuilder heapDump(boolean heapDump) {
        this.heapDump = heapDump;
        return this;
    }

    /**
     * Sets the short file names and returns this PSP builder.
     *
     * @param shortFileNames the short file names
     * @return this PSP builder for chaining
     */
    public PspBuilder shortFileNames(boolean shortFileNames) {
        this.shortFileNames = shortFileNames;
        return this;
    }

    /**
     * Sets the debug memory and returns this PSP builder.
     *
     * @param debugMemory the debug memory
     * @return this PSP builder for chaining
     */
    public PspBuilder debugMemory(boolean debugMemory) {
        this.debugMemory = debugMemory;
        return this;
    }

    /**
     * Sets the min heap size and returns this PSP builder.
     *
     * @param minHeapSize the min heap size
     * @return this PSP builder for chaining
     */
    public PspBuilder minHeapSize(int minHeapSize) {
        this.minHeapSize = minHeapSize;
        return this;
    }

    /**
     * Sets the max heap size and returns this PSP builder.
     *
     * @param maxHeapSize the max heap size
     * @return this PSP builder for chaining
     */
    public PspBuilder maxHeapSize(int maxHeapSize) {
        this.maxHeapSize = maxHeapSize;
        return this;
    }

    /**
     * Sets the property and returns this PSP builder.
     *
     * @param key the key
     * @param value the value
     * @return this PSP builder for chaining
     */
    public PspBuilder property(String key, String value) {
        properties.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Sets the properties and returns this PSP builder.
     *
     * @param properties the properties
     * @return this PSP builder for chaining
     */
    public PspBuilder properties(Map<String, String> properties) {
        this.properties.putAll(Objects.requireNonNull(properties, "properties"));
        return this;
    }

    /**
     * Sets the log and returns this PSP builder.
     *
     * @param log the log
     * @return this PSP builder for chaining
     */
    public PspBuilder log(TeaVMToolLog log) {
        this.log = log;
        return this;
    }

    /**
     * Returns the build.
     *
     * @return the created value
     */
    public PspBuildResult build() {
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
        teaVMProperties.setProperty("libfdx.native.backend", "psp");
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
        }, "TeaVM PSP build failed for " + actualMainClass);
        try {
            List<Path> actualAssets = new ArrayList<>(normalized(assets));
            actualAssets.addAll(generateBitmapFontAssets(root.resolve("generated-assets/bitmap-fonts")));
            Set<Path> projectFiles = PspProjectWriter.write(PspProject.builder()
                    .buildRoot(root)
                    .generatedSourcesDirectory(sources)
                    .releaseDirectory(release)
                    .projectName(actualTargetFileName)
                    .debugMemory(debugMemory)
                    .nativeResourceClasspath(normalized(nativeResourceClasspath))
                    .assets(actualAssets)
                    .build());
            return new PspBuildResult(root, sources, release, generatedFiles, projectFiles);
        } catch (java.io.IOException error) {
            throw new BuilderException("Could not write PSP project to " + root, error);
        }
    }

    private List<Path> generateBitmapFontAssets(Path defaultRoot) {
        ArrayList<Path> generated = new ArrayList<>();
        for (BitmapFontSpec bitmapFont : bitmapFonts) {
            BitmapFontSpec actual = bitmapFont.outputDirectory() != null
                    ? bitmapFont
                    : bitmapFont.withOutputDirectory(defaultRoot.resolve(bitmapFont.name()));
            generated.add(BitmapFontGenerator.generate(actual).assetRoot());
        }
        return generated;
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
