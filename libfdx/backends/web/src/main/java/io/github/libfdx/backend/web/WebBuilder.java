package io.github.libfdx.backend.web;

import io.github.libfdx.backend.teavm.shared.BuilderException;
import io.github.libfdx.backend.teavm.shared.TeaVMBuildRunner;
import io.github.libfdx.backend.teavm.shared.TeaVMOptimization;
import io.github.libfdx.tools.font.BitmapFontGenerator;
import io.github.libfdx.tools.font.BitmapFontSpec;
import org.teavm.tooling.TeaVMToolLog;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds web instances and related output.
 *
 * @author xpenatan
 */
public final class WebBuilder {
    private final WebTarget target;
    private final ArrayList<Path> classpath = new ArrayList<>();
    private final ArrayList<Path> runtimeClasspath = new ArrayList<>();
    private final ArrayList<Path> assets = new ArrayList<>();
    private final ArrayList<BitmapFontSpec> bitmapFonts = new ArrayList<>();
    private final LinkedHashMap<String, String> properties = new LinkedHashMap<>();
    private Path webappDirectory;
    private Path cacheDirectory;
    private String mainClass;
    private String targetFileName;
    private String entryPointName = "main";
    private String title = "libfdx";
    private String canvasId = "libfdx-canvas";
    private int width = 640;
    private int height = 480;
    private String mainClassArgs = "";
    private TeaVMOptimization optimization = TeaVMOptimization.BALANCED;
    private boolean obfuscated = true;
    private boolean strict;
    private boolean debugInformation;
    private boolean sourceMaps;
    private boolean sourceFilesCopied;
    private boolean fastDependencyAnalysis;
    private boolean incremental;
    private boolean wasmRuntimeModular;
    private TeaVMToolLog log;

    private WebBuilder(WebTarget target) {
        this.target = Objects.requireNonNull(target, "target");
        this.targetFileName = target.getDefaultTargetFileName();
    }

    /**
     * Creates a web builder.
     *
     * @return a new web builder
     */
    public static WebBuilder javascript() {
        return new WebBuilder(WebTarget.JAVASCRIPT);
    }

    /**
     * Creates a web builder.
     *
     * @return a new web builder
     */
    public static WebBuilder wasm() {
        return new WebBuilder(WebTarget.WASM);
    }

    /**
     * Sets the classpath and returns this web builder.
     *
     * @param entry the entry
     * @return this web builder for chaining
     */
    public WebBuilder classpath(Path entry) {
        classpath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Sets the classpath and returns this web builder.
     *
     * @param entries the entries
     * @return this web builder for chaining
     */
    public WebBuilder classpath(Collection<Path> entries) {
        classpath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    /**
     * Returns the classpath from current jvm.
     *
     * @return this web builder for chaining
     */
    public WebBuilder classpathFromCurrentJvm() {
        String classPath = System.getProperty("java.class.path", "");
        if (!classPath.isBlank()) {
            for (String entry : classPath.split(java.io.File.pathSeparator)) {
                if (!entry.isBlank()) {
                    classpath.add(Path.of(entry));
                }
            }
        }
        return this;
    }

    /**
     * Sets the runtime classpath and returns this web builder.
     *
     * @param entry the entry
     * @return this web builder for chaining
     */
    public WebBuilder runtimeClasspath(Path entry) {
        runtimeClasspath.add(Objects.requireNonNull(entry, "entry"));
        return this;
    }

    /**
     * Sets the runtime classpath and returns this web builder.
     *
     * @param entries the entries
     * @return this web builder for chaining
     */
    public WebBuilder runtimeClasspath(Collection<Path> entries) {
        runtimeClasspath.addAll(Objects.requireNonNull(entries, "entries"));
        return this;
    }

    /**
     * Sets the asset and returns this web builder.
     *
     * @param asset the asset
     * @return this web builder for chaining
     */
    public WebBuilder asset(Path asset) {
        assets.add(Objects.requireNonNull(asset, "asset"));
        return this;
    }

    /**
     * Sets the assets and returns this web builder.
     *
     * @param assets the assets
     * @return this web builder for chaining
     */
    public WebBuilder assets(Collection<Path> assets) {
        this.assets.addAll(Objects.requireNonNull(assets, "assets"));
        return this;
    }

    /**
     * Sets the bitmap font and returns this web builder.
     *
     * @param bitmapFont the bitmap font
     * @return this web builder for chaining
     */
    public WebBuilder bitmapFont(BitmapFontSpec bitmapFont) {
        bitmapFonts.add(Objects.requireNonNull(bitmapFont, "bitmapFont"));
        return this;
    }

    /**
     * Sets the bitmap fonts and returns this web builder.
     *
     * @param bitmapFonts the bitmap fonts
     * @return this web builder for chaining
     */
    public WebBuilder bitmapFonts(Collection<BitmapFontSpec> bitmapFonts) {
        this.bitmapFonts.addAll(Objects.requireNonNull(bitmapFonts, "bitmapFonts"));
        return this;
    }

    /**
     * Sets the bitmap font and returns this web builder.
     *
     * @param sourceFile the source file
     * @param name the name
     * @param size the size
     * @return this web builder for chaining
     */
    public WebBuilder bitmapFont(Path sourceFile, String name, int size) {
        return bitmapFont(sourceFile, name, size, BitmapFontSpec.DEFAULT_ASSET_PATH);
    }

    /**
     * Sets the bitmap font and returns this web builder.
     *
     * @param sourceFile the source file
     * @param name the name
     * @param size the size
     * @param assetPath the asset path
     * @return this web builder for chaining
     */
    public WebBuilder bitmapFont(Path sourceFile, String name, int size, String assetPath) {
        return bitmapFont(BitmapFontSpec.builder()
                .sourceFile(sourceFile)
                .name(name)
                .size(size)
                .assetPath(assetPath)
                .build());
    }

    /**
     * Sets the webapp directory and returns this web builder.
     *
     * @param webappDirectory the webapp directory
     * @return this web builder for chaining
     */
    public WebBuilder webappDirectory(Path webappDirectory) {
        this.webappDirectory = webappDirectory;
        return this;
    }

    /**
     * Sets the cache directory and returns this web builder.
     *
     * @param cacheDirectory the cache directory
     * @return this web builder for chaining
     */
    public WebBuilder cacheDirectory(Path cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
        return this;
    }

    /**
     * Sets the main class and returns this web builder.
     *
     * @param mainClass the main class
     * @return this web builder for chaining
     */
    public WebBuilder mainClass(String mainClass) {
        this.mainClass = Objects.requireNonNull(mainClass, "mainClass");
        return this;
    }

    /**
     * Sets the target file name and returns this web builder.
     *
     * @param targetFileName the target file name
     * @return this web builder for chaining
     */
    public WebBuilder targetFileName(String targetFileName) {
        this.targetFileName = Objects.requireNonNull(targetFileName, "targetFileName");
        return this;
    }

    /**
     * Sets the entry point name and returns this web builder.
     *
     * @param entryPointName the entry point name
     * @return this web builder for chaining
     */
    public WebBuilder entryPointName(String entryPointName) {
        this.entryPointName = Objects.requireNonNull(entryPointName, "entryPointName");
        return this;
    }

    /**
     * Sets the title and returns this web builder.
     *
     * @param title the title
     * @return this web builder for chaining
     */
    public WebBuilder title(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /**
     * Returns whether this instance can vas ID.
     *
     * @param canvasId the canvas ID
     * @return this web builder for chaining
     */
    public WebBuilder canvasId(String canvasId) {
        this.canvasId = Objects.requireNonNull(canvasId, "canvasId");
        return this;
    }

    /**
     * Sets the size and returns this web builder.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @return this web builder for chaining
     */
    public WebBuilder size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * Returns the fill window.
     *
     * @return this web builder for chaining
     */
    public WebBuilder fillWindow() {
        this.width = 0;
        this.height = 0;
        return this;
    }

    /**
     * Sets the main class args and returns this web builder.
     *
     * @param args the args
     * @return this web builder for chaining
     */
    public WebBuilder mainClassArgs(String... args) {
        Objects.requireNonNull(args, "args");
        this.mainClassArgs = java.util.Arrays.stream(args)
                .map(value -> "\"" + WebAppWriter.js(value) + "\"")
                .collect(Collectors.joining(", "));
        return this;
    }

    /**
     * Sets the raw main class args and returns this web builder.
     *
     * @param mainClassArgs the main class args
     * @return this web builder for chaining
     */
    public WebBuilder rawMainClassArgs(String mainClassArgs) {
        this.mainClassArgs = Objects.requireNonNull(mainClassArgs, "mainClassArgs");
        return this;
    }

    /**
     * Sets the optimization and returns this web builder.
     *
     * @param optimization the optimization
     * @return this web builder for chaining
     */
    public WebBuilder optimization(TeaVMOptimization optimization) {
        this.optimization = Objects.requireNonNull(optimization, "optimization");
        return this;
    }

    /**
     * Sets the obfuscated and returns this web builder.
     *
     * @param obfuscated the obfuscated
     * @return this web builder for chaining
     */
    public WebBuilder obfuscated(boolean obfuscated) {
        this.obfuscated = obfuscated;
        return this;
    }

    /**
     * Sets the strict and returns this web builder.
     *
     * @param strict the strict
     * @return this web builder for chaining
     */
    public WebBuilder strict(boolean strict) {
        this.strict = strict;
        return this;
    }

    /**
     * Sets the debug information and returns this web builder.
     *
     * @param debugInformation the debug information
     * @return this web builder for chaining
     */
    public WebBuilder debugInformation(boolean debugInformation) {
        this.debugInformation = debugInformation;
        return this;
    }

    /**
     * Sets the source maps and returns this web builder.
     *
     * @param sourceMaps the source maps
     * @return this web builder for chaining
     */
    public WebBuilder sourceMaps(boolean sourceMaps) {
        this.sourceMaps = sourceMaps;
        return this;
    }

    /**
     * Sets the source files copied and returns this web builder.
     *
     * @param sourceFilesCopied the source files copied
     * @return this web builder for chaining
     */
    public WebBuilder sourceFilesCopied(boolean sourceFilesCopied) {
        this.sourceFilesCopied = sourceFilesCopied;
        return this;
    }

    /**
     * Sets the fast dependency analysis and returns this web builder.
     *
     * @param fastDependencyAnalysis the fast dependency analysis
     * @return this web builder for chaining
     */
    public WebBuilder fastDependencyAnalysis(boolean fastDependencyAnalysis) {
        this.fastDependencyAnalysis = fastDependencyAnalysis;
        return this;
    }

    /**
     * Sets the incremental and returns this web builder.
     *
     * @param incremental the incremental
     * @return this web builder for chaining
     */
    public WebBuilder incremental(boolean incremental) {
        this.incremental = incremental;
        return this;
    }

    /**
     * Sets the Wasm runtime modular and returns this web builder.
     *
     * @param wasmRuntimeModular the Wasm runtime modular
     * @return this web builder for chaining
     */
    public WebBuilder wasmRuntimeModular(boolean wasmRuntimeModular) {
        this.wasmRuntimeModular = wasmRuntimeModular;
        return this;
    }

    /**
     * Sets the property and returns this web builder.
     *
     * @param key the key
     * @param value the value
     * @return this web builder for chaining
     */
    public WebBuilder property(String key, String value) {
        properties.put(Objects.requireNonNull(key, "key"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /**
     * Sets the properties and returns this web builder.
     *
     * @param properties the properties
     * @return this web builder for chaining
     */
    public WebBuilder properties(Map<String, String> properties) {
        this.properties.putAll(Objects.requireNonNull(properties, "properties"));
        return this;
    }

    /**
     * Sets the log and returns this web builder.
     *
     * @param log the log
     * @return this web builder for chaining
     */
    public WebBuilder log(TeaVMToolLog log) {
        this.log = log;
        return this;
    }

    /**
     * Returns the build.
     *
     * @return the created value
     */
    public WebBuildResult build() {
        Path output = requirePath(webappDirectory, "webappDirectory");
        String actualMainClass = requireText(mainClass, "mainClass");
        List<Path> actualClasspath = normalized(classpath);
        if (actualClasspath.isEmpty()) {
            throw new BuilderException("Classpath is empty. Add game/framework jars or call classpathFromCurrentJvm().");
        }
        List<Path> actualRuntimeClasspath = runtimeClasspath.isEmpty() ? actualClasspath : normalized(runtimeClasspath);
        List<Path> actualAssets = new ArrayList<>(normalized(assets));
        actualAssets.addAll(generateBitmapFontAssets(output.resolve("generated-assets/bitmap-fonts")));
        List<WebAsset> assetEntries = WebAssets.collect(actualAssets);
        Properties teaVMProperties = new Properties();
        teaVMProperties.putAll(properties);
        TeaVMAssetProperties.putInto(teaVMProperties, assetEntries);
        Set<Path> generatedFiles = TeaVMBuildRunner.build(new TeaVMBuildRunner.Request(
                target.teaVMTargetType(),
                actualClasspath,
                output,
                cacheDirectory != null ? cacheDirectory.toAbsolutePath().normalize() : null,
                actualMainClass,
                targetFileName,
                entryPointName,
                optimization,
                teaVMProperties,
                log,
                obfuscated,
                strict,
                debugInformation,
                sourceMaps,
                sourceFilesCopied,
                fastDependencyAnalysis,
                incremental
        ), strategy -> {
        }, "TeaVM web build failed for " + actualMainClass);
        try {
            if (target.isWasm()) {
                WebAppWriter.copyWasmRuntime(output.resolve(targetFileName + "-runtime.js"), wasmRuntimeModular,
                        obfuscated);
            }
            List<WebAsset> copiedAssets = WebAppWriter.write(WebApp.builder()
                    .webappDirectory(output)
                    .title(title)
                    .width(width)
                    .height(height)
                    .canvasId(canvasId)
                    .entryPointName(entryPointName)
                    .mainClassArgs(mainClassArgs)
                    .targetFileName(targetFileName)
                    .wasm(target.isWasm())
                    .assets(actualAssets)
                    .runtimeClasspath(actualRuntimeClasspath)
                    .build());
            return new WebBuildResult(target, output, output.resolve(targetFileName), copiedAssets, generatedFiles);
        } catch (IOException error) {
            throw new BuilderException("Could not write libfdx web application to " + output, error);
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
