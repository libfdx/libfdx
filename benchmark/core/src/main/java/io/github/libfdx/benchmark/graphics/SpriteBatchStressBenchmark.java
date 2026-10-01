package io.github.libfdx.benchmark.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.Logger;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.FrameBuffer;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.graphics.LoadOp;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.Batch2D;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.SpriteBatch;
import io.github.libfdx.graphics.g2d.TextureRegion;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Random;

public final class SpriteBatchStressBenchmark extends ApplicationAdapter {
    public static final String NAME = "sprite_batch_stress";
    public static final String LIBFDX_NAME = "sprite_batch_libfdx";
    public static final String SPRITES_PROPERTY = "libfdx.benchmark.sprites";
    public static final int DEFAULT_SPRITE_COUNT = 1500000;
    private static final String SPRITE_ASSET = "benchmark/assets/fdx.png";
    private static final int INITIAL_BATCH_CAPACITY = 8191;
    // Bound even the largest (six vertices, eight floats each) per-sprite path to 1 GiB,
    // leaving room for the framework SpriteBatch's doubling capacities without signed-int overflow.
    private static final int MAX_SPRITE_COUNT = (1 << 30) / (6 * 8 * Float.BYTES);
    private static final int DRAW_SIZE = 32;
    private static final float ROTATION_SPEED = 20.0f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 1.0f;
    private static final long RANDOM_SEED = 0x51f15e2dL;
    private static final long REPORT_INTERVAL_NANOS = 1000000000L;

    private final long exitAfterNanos;
    private final long warmupNanos;
    private final BenchmarkFrameTiming timing;
    private final String resultPath;
    private final int spriteCount;
    private final boolean libfdxStyle;
    private CpuSprite[] cpuSprites;
    private CpuSpriteBatch cpuBatch;
    private final float[] spriteCenterX;
    private final float[] spriteCenterY;
    private Application application;
    private Display display;
    private AssetManager assets;
    private Runnable assetSetup;
    private GraphicsContext graphics;
    private Logger logger;
    private Batch2D batch;
    private TextureRegion sprite;
    private String graphicsApi;
    private String graphicsProvider;
    private boolean created;
    private long renderedFrames;
    private long lastReportNanos;
    private long lastReportFrame;
    private long startedAtNanos;
    private long lastRenderNanos;
    private boolean completedRunLimit;
    private float rotationDegrees;
    private float scale = MAX_SCALE;
    private float scaleSpeed = -1.0f;
    private int layoutWidth;
    private int layoutHeight;
    private String capturePath = System.getProperty("libfdx.benchmark.capture");
    private final String cpuDiagnostic = System.getProperty("libfdx.benchmark.cpuDiagnostic");
    private boolean cpuDiagnosticCompleted;
    private final CpuSpriteFrameProfile frameProfile;

    /**
     * Preallocates this run's sprite positions using {@link #SPRITES_PROPERTY}, or
     * {@link #DEFAULT_SPRITE_COUNT} when the property is absent. The count must be
     * an integer from 1 to {@value #MAX_SPRITE_COUNT}; later property changes do not affect this run.
     *
     * @throws FdxException if the configured sprite count is invalid
     */
    public SpriteBatchStressBenchmark(long exitAfterNanos, String resultPath) {
        this(exitAfterNanos, resultPath, false);
    }

    /** Selects the libFDX CPU sprite benchmark when {@code libfdxStyle} is true. */
    public SpriteBatchStressBenchmark(long exitAfterNanos, String resultPath, boolean libfdxStyle) {
        this.exitAfterNanos = exitAfterNanos;
        this.resultPath = resultPath;
        this.libfdxStyle = libfdxStyle;
        if (cpuDiagnostic != null && !libfdxStyle) {
            throw new FdxException("cpuDiagnostic requires sprite_batch_libfdx");
        }
        String configuredCount = System.getProperty(SPRITES_PROPERTY);
        spriteCount = libfdxStyle && configuredCount == null ? 8191 : parseSpriteCount(configuredCount);
        spriteCenterX = new float[libfdxStyle ? 0 : spriteCount];
        spriteCenterY = new float[libfdxStyle ? 0 : spriteCount];
        double warmupSeconds = Double.parseDouble(System.getProperty("libfdx.benchmark.warmupSeconds", "2"));
        if (!Double.isFinite(warmupSeconds) || warmupSeconds < 0.0
                || warmupSeconds * 1_000_000_000.0 >= Long.MAX_VALUE) {
            throw new FdxException("Benchmark warmupSeconds must be finite, nonnegative, and fit in nanoseconds");
        }
        warmupNanos = (long)(warmupSeconds * 1_000_000_000.0);
        timing = new BenchmarkFrameTiming(warmupNanos);
        boolean profile = Boolean.parseBoolean(System.getProperty("libfdx.benchmark.frameProfile", "false"));
        if (profile && (!libfdxStyle || spriteCount != INITIAL_BATCH_CAPACITY
                || cpuDiagnostic != null || capturePath != null)) {
            throw new FdxException("frameProfile requires sprite_batch_libfdx with 8191 sprites and no cpuDiagnostic/capture");
        }
        frameProfile = profile ? new CpuSpriteFrameProfile(warmupNanos) : null;
    }

    /** Returns the number of sprites preallocated for this run. */
    int spriteCount() {
        return spriteCount;
    }

    static int parseSpriteCount(String value) {
        if (value == null) {
            return DEFAULT_SPRITE_COUNT;
        }
        try {
            int count = Integer.parseInt(value.trim());
            if (count > 0 && count <= MAX_SPRITE_COUNT) {
                return count;
            }
        } catch (NumberFormatException ignored) {
            // Report the property and supported range for all invalid values.
        }
        throw new FdxException(SPRITES_PROPERTY + " must be an integer between 1 and " + MAX_SPRITE_COUNT
                + ": " + value);
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        graphicsApi = graphicsApiName(graphics);
        graphicsProvider = graphics.providerId().value();
        assets = new DefaultAssetManager(fdx.files());
        logger = fdx.logger();
        G2DAssetLoaders.register(assets, graphics);
        if (libfdxStyle) {
            cpuBatch = new CpuSpriteBatch(graphics, INITIAL_BATCH_CAPACITY, spriteCount);
            cpuBatch.frameProfile = frameProfile;
        }
        else batch = new SpriteBatch(graphics, INITIAL_BATCH_CAPACITY);

        assets.load(AssetDescriptor.of(spriteAsset(), Texture.class));
        assetSetup = this::createLoadedAssets;
    }

    private void createLoadedAssets() {
        Texture texture = assets.get(spriteAsset(), Texture.class);
        sprite = new TextureRegion(texture);
        if (libfdxStyle) {
            cpuSprites = new CpuSprite[spriteCount];
            for (int i = 0; i < spriteCount; i++) cpuSprites[i] = new CpuSprite(texture, 360f * i / spriteCount);
        }
        configureViewport(framebufferWidth(), framebufferHeight());

        created = true;
        logger.info(logPrefix() + " created with " + spriteCount
                + " sprites, " + texture.width() + "x" + texture.height()
                + " " + spriteAsset() + " texture drawn at " + DRAW_SIZE + "x" + DRAW_SIZE
                + runLimitDescription());
    }

    @Override
    public void resize(int width, int height) {
        if (created) {
            configureViewport(framebufferWidth(), framebufferHeight());
        }
    }

    @Override
    public void render() {
        boolean assetsFinished = assets.update(4, 1_000_000L);
        if (assetSetup != null) {
            if (!assetsFinished) {
                graphics.clear(0.02f, 0.025f, 0.04f, 1.0f);
                return;
            }
            Runnable setup = assetSetup;
            assetSetup = null;
            setup.run();
        }
        if (cpuDiagnostic != null) {
            if (!cpuDiagnosticCompleted) {
                CpuSpriteDiagnostic.run(cpuDiagnostic, cpuSprites, cpuBatch);
                cpuDiagnosticCompleted = true;
                application.requestExit();
            }
            return;
        }
        long startNanos = System.nanoTime();
        if (layoutWidth != framebufferWidth() || layoutHeight != framebufferHeight()) {
            configureViewport(framebufferWidth(), framebufferHeight());
        }

        if (startedAtNanos == 0L) {
            startedAtNanos = startNanos;
        }
        float deltaTime = application.deltaTime();
        rotationDegrees += ROTATION_SPEED * deltaTime;
        if (rotationDegrees >= 360.0f) {
            rotationDegrees -= 360.0f;
        }
        scale += scaleSpeed * deltaTime * (libfdxStyle ? 0.5f : 1f);
        if (scale <= MIN_SCALE) {
            scale = MIN_SCALE;
            scaleSpeed = 1.0f;
        } else if (scale >= MAX_SCALE) {
            scale = MAX_SCALE;
            scaleSpeed = -1.0f;
        }

        float width = spriteWidth() * scale;
        float height = spriteHeight() * scale;
        float originX = width * 0.5f;
        float originY = height * 0.5f;
        if (libfdxStyle) {
            long mark = frameProfile == null ? 0 : System.nanoTime();
            cpuBatch.begin();
            if (frameProfile != null) {
                long now = System.nanoTime();
                frameProfile.add(CpuSpriteFrameProfile.BEGIN, now - mark);
                mark = now;
            }
            for (int i = 0; i < cpuSprites.length; i++) {
                CpuSprite current = cpuSprites[i];
                current.rotate(ROTATION_SPEED * deltaTime);
                current.setScale(scale);
                current.draw(cpuBatch);
            }
            if (frameProfile != null) frameProfile.add(CpuSpriteFrameProfile.FILL, System.nanoTime() - mark);
            cpuBatch.end();
        } else {
            batch.begin(LoadOp.clear(0.0f, 0.0f, 0.0f, 1.0f));
            if (usesInstancedBatchPath()) {
                batch.draw(sprite, spriteCenterX, spriteCenterY, spriteCount, width, height, originX, originY,
                        rotationDegrees);
            } else {
                for (int i = 0; i < spriteCount; i++) {
                    batch.draw(sprite, spriteCenterX[i] - originX, spriteCenterY[i] - originY,
                            width, height, originX, originY, rotationDegrees);
                }
            }
            batch.end();
        }

        if (capturePath != null) {
            captureFrame(capturePath);
            capturePath = null;
            // Capture consumes this frame and is excluded from benchmark timing/warmup.
            startedAtNanos = 0;
            return;
        }
        long now = System.nanoTime();
        timing.recordFrame(startNanos, now);
        if (frameProfile != null) frameProfile.recordFrame(startNanos, now);
        lastRenderNanos = now;
        renderedFrames++;
        reportIfNeeded(now);

        if (exitAfterNanos > 0L && now - startedAtNanos >= exitAfterNanos) {
            completedRunLimit = true;
            application.requestExit();
        }
    }

    @Override
    public void dispose() {
        assetSetup = null;
        if (cpuBatch != null) cpuBatch.dispose();
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (assets != null) {
            assets.dispose();
            assets = null;
        }
        if (!created || cpuDiagnostic != null) {
            // A create/load failure is already propagating through the backend's
            // finally block. Do not replace its useful diagnostic with a cleanup error.
            return;
        }
        long elapsedNanos = elapsedTestNanos();
        if (exitAfterNanos > 0L && elapsedNanos < exitAfterNanos) {
            logger.warn(logPrefix() + " ran for " + format(nanosToSeconds(elapsedNanos))
                    + " of " + format(nanosToSeconds(exitAfterNanos)) + " required seconds");
        }
        double averageFrameFps = averageFrameFps(elapsedNanos);
        double averageSpriteDrawsPerSecond = averageFrameFps * spriteCount;
        logger.info(logPrefix() + " rendered " + renderedFrames + " frames in "
                + format(nanosToSeconds(elapsedNanos)) + " seconds with rotating/scaling "
                + spriteCount + " " + DRAW_SIZE + "x" + DRAW_SIZE
                + " sprites, average fps=" + format(averageFrameFps)
                + ", average sprite draws/s=" + format(averageSpriteDrawsPerSecond));
        if (timing.intervals().count() == 0L) {
            logger.warn(logPrefix() + " has no complete frame intervals after warm-up; increase duration");
        } else {
            logger.info(logPrefix() + " after warm-up: intervals=" + timing.intervals().count()
                    + ", fps=" + format(timing.framesPerSecond())
                    + ", p50/p95/p99 ms=" + format(timing.intervals().percentileNanos(0.5) / 1_000_000.0)
                    + "/" + format(timing.intervals().percentileNanos(0.95) / 1_000_000.0)
                    + "/" + format(timing.intervals().percentileNanos(0.99) / 1_000_000.0));
        }
        writeResult(elapsedNanos, averageFrameFps, averageSpriteDrawsPerSecond);
    }

    private void reportIfNeeded(long now) {
        if (lastReportNanos == 0L) {
            lastReportNanos = startedAtNanos;
            lastReportFrame = 0L;
        }
        long elapsedNanos = now - lastReportNanos;
        if (elapsedNanos < REPORT_INTERVAL_NANOS) {
            return;
        }
        long frameDelta = renderedFrames - lastReportFrame;
        double fps = frameDelta * 1000000000.0 / elapsedNanos;
        logger.info(logPrefix() + " elapsed=" + format(nanosToSeconds(now - startedAtNanos))
                + " seconds, fps=" + format(fps) + " at " + spriteCount
                + " " + DRAW_SIZE + "x" + DRAW_SIZE + " sprites");
        lastReportNanos = now;
        lastReportFrame = renderedFrames;
    }

    private void captureFrame(String path) {
        FrameBuffer frameBuffer = graphics.currentFrame().frameBuffer();
        if (!frameBuffer.supportsReadPixelsRgba8()) {
            throw new FdxException("Benchmark frame capture is unsupported by this graphics provider");
        }
        ByteBuffer pixels = frameBuffer.readPixelsRgba8();
        byte[] rgb = new byte[layoutWidth * layoutHeight * 3];
        for (int i = 0; i < layoutWidth * layoutHeight; i++) {
            rgb[i * 3] = pixels.get(i * 4);
            rgb[i * 3 + 1] = pixels.get(i * 4 + 1);
            rgb[i * 3 + 2] = pixels.get(i * 4 + 2);
        }
        try (FileOutputStream output = new FileOutputStream(path)) {
            output.write(("P6\n" + layoutWidth + " " + layoutHeight + "\n255\n").getBytes());
            output.write(rgb);
        } catch (IOException error) {
            throw new FdxException("Could not capture benchmark frame: " + path, error);
        }
    }

    private void writeResult(long elapsedNanos, double averageFrameFps, double averageSpriteDrawsPerSecond) {
        if (resultPath == null || resultPath.trim().length() == 0) {
            return;
        }
        File file = new File(resultPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new FdxException("Could not create benchmark result directory: " + parent);
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            writeProperty(output, "benchmark", libfdxStyle ? LIBFDX_NAME : NAME);
            writeProperty(output, "workload", libfdxStyle ? "libfdx-cpu-expanded" : "libfdx-stress");
            if (libfdxStyle) {
                writeProperty(output, "vertexBytesPerSprite", "80");
                writeProperty(output, "renderCalls", Integer.toString(cpuBatch.renderCalls()));
                writeProperty(output, "uploadedVertexBytes", Integer.toString(cpuBatch.uploadedBytes()));
            }
            writeProperty(output, "label", graphicsApi);
            writeProperty(output, "graphicsProvider", graphicsProvider);
            writeProperty(output, "renderer", graphics.frameMetrics().renderer());
            writeProperty(output, "device", System.getProperty("libfdx.benchmark.device", "unspecified"));
            writeProperty(output, "driver", System.getProperty("libfdx.benchmark.driver", "unspecified"));
            writeProperty(output, "revision", System.getProperty("libfdx.benchmark.revision", "unspecified"));
            writeProperty(output, "visible", System.getProperty("libfdx.benchmark.visible", ""));
            writeProperty(output, "vSync", System.getProperty("libfdx.benchmark.vsync", ""));
            writeProperty(output, "foregroundFps", System.getProperty("libfdx.benchmark.foregroundFps", ""));
            writeProperty(output, "sprites", Integer.toString(spriteCount));
            writeProperty(output, "spriteSize", DRAW_SIZE + "x" + DRAW_SIZE);
            writeProperty(output, "texture", spriteAsset());
            writeProperty(output, "textureFormat", "RGBA8_UNORM");
            writeProperty(output, "framebufferWidth", Integer.toString(layoutWidth));
            writeProperty(output, "framebufferHeight", Integer.toString(layoutHeight));
            writeProperty(output, "randomSeed", Long.toString(RANDOM_SEED));
            writeProperty(output, "instanced", Boolean.toString(usesInstancedBatchPath()));
            writeProperty(output, "frames", Long.toString(renderedFrames));
            writeProperty(output, "completed", Boolean.toString(completedRunLimit || exitAfterNanos <= 0L));
            writeProperty(output, "elapsedSeconds", format(nanosToSeconds(elapsedNanos)));
            writeProperty(output, "targetSeconds", format(nanosToSeconds(exitAfterNanos)));
            writeProperty(output, "averageFrameFps", format(averageFrameFps));
            writeProperty(output, "averageSpriteDrawsPerSecond", format(averageSpriteDrawsPerSecond));
            writeProperty(output, "warmupSeconds", format(nanosToSeconds(warmupNanos)));
            writeProperty(output, "warmupFrames", Long.toString(timing.warmupFrames()));
            writeProperty(output, "measuredIntervals", Long.toString(timing.intervals().count()));
            writeProperty(output, "measuredSeconds", format(timing.intervals().totalNanos() / 1_000_000_000.0));
            writeProperty(output, "measuredFrameFps", format(timing.framesPerSecond()));
            writeProperty(output, "measuredSpriteDrawsPerSecond", format(timing.framesPerSecond() * spriteCount));
            writeProperty(output, "frameTimeDefinition", "render-start-to-render-start; includes presentation and pacing");
            writeProperty(output, "cpuRenderDefinition", "render-start-to-batch-end; excludes presentation and reporting");
            writeProperty(output, "histogramFormat", "upperBoundNanos:count pairs; 32 subdivisions per power of two");
            writeProperty(output, "hitchThresholdMillis", format(FrameTimeHistogram.HITCH_NANOS / 1_000_000.0));
            writeTimings(output, "frameTime", timing.intervals());
            writeTimings(output, "cpuRender", timing.cpu());
            if (frameProfile != null) {
                writeProperty(output, "profileIntervals", Long.toString(frameProfile.intervals()));
                writeProperty(output, "profileOutsideDefinition", "backend/presentation/scheduling/reporting between render end and next render start");
                for (int i = 0; i < CpuSpriteFrameProfile.NAMES.length; i++) {
                    writeProperty(output, "profile" + CpuSpriteFrameProfile.NAMES[i] + "TotalNanos",
                            Long.toString(frameProfile.total(i)));
                }
            }
            writeProperty(output, "javaVersion", System.getProperty("java.version", ""));
            writeProperty(output, "javaVm", System.getProperty("java.vm.name", "") + " "
                    + System.getProperty("java.vm.version", ""));
            writeProperty(output, "os", System.getProperty("os.name", "") + " "
                    + System.getProperty("os.version", "") + " "
                    + System.getProperty("os.arch", ""));
            writeProperty(output, "multiRelease", System.getProperty("jdk.util.jar.enableMultiRelease", "true"));
            writeProperty(output, "generatedAtMillis", Long.toString(System.currentTimeMillis()));
        } catch (IOException error) {
            throw new FdxException("Could not write benchmark result: " + file, error);
        }
    }

    private void writeTimings(FileOutputStream output, String prefix, FrameTimeHistogram histogram) throws IOException {
        writeProperty(output, prefix + "MeanMillis", format(histogram.meanNanos() / 1_000_000.0));
        writeProperty(output, prefix + "P50Millis", format(histogram.percentileNanos(0.5) / 1_000_000.0));
        writeProperty(output, prefix + "P95Millis", format(histogram.percentileNanos(0.95) / 1_000_000.0));
        writeProperty(output, prefix + "P99Millis", format(histogram.percentileNanos(0.99) / 1_000_000.0));
        writeProperty(output, prefix + "WorstMillis", format(histogram.worstNanos() / 1_000_000.0));
        writeProperty(output, prefix + "Hitches", Long.toString(histogram.hitches()));
        writeProperty(output, prefix + "HistogramNanos", histogram.encode());
    }

    private void configureViewport(int framebufferWidth, int framebufferHeight) {
        layoutWidth = Math.max(1, framebufferWidth);
        layoutHeight = Math.max(1, framebufferHeight);
        if (batch != null) {
            batch.viewport(layoutWidth, layoutHeight);
        }
        if (cpuBatch != null) cpuBatch.viewport(layoutWidth, layoutHeight);
        generateSprites();
    }

    private void generateSprites() {
        Random random = new Random(RANDOM_SEED);
        if (libfdxStyle) {
            float availableWidth = Math.max(0, layoutWidth - DRAW_SIZE);
            float availableHeight = Math.max(0, layoutHeight - DRAW_SIZE);
            for (int i = 0; i < spriteCount; i++) {
                cpuSprites[i].setPosition(random.nextFloat() * availableWidth, random.nextFloat() * availableHeight);
            }
            return;
        }
        for (int i = 0; i < spriteCount; i++) {
            spriteCenterX[i] = toNormalizedX(random.nextInt(layoutWidth));
            spriteCenterY[i] = toNormalizedY(random.nextInt(layoutHeight));
        }
    }

    private float toNormalizedX(int pixelX) {
        return -1.0f + (2.0f * pixelX / layoutWidth);
    }

    private float toNormalizedY(int pixelY) {
        return -1.0f + (2.0f * pixelY / layoutHeight);
    }

    private float spriteWidth() {
        return 2.0f * DRAW_SIZE / layoutWidth;
    }

    private String spriteAsset() { return SPRITE_ASSET; }

    private float spriteHeight() {
        return 2.0f * DRAW_SIZE / layoutHeight;
    }

    private int framebufferWidth() {
        if (display == null) {
            return 1;
        }
        return display.framebufferWidth() > 0 ? display.framebufferWidth() : display.width();
    }

    private int framebufferHeight() {
        if (display == null) {
            return 1;
        }
        return display.framebufferHeight() > 0 ? display.framebufferHeight() : display.height();
    }

    private double averageFrameFps(long elapsedNanos) {
        if (renderedFrames == 0L || elapsedNanos <= 0L) {
            return 0.0;
        }
        return renderedFrames * 1000000000.0 / elapsedNanos;
    }

    private String graphicsApiName(GraphicsContext graphics) {
        String label = System.getProperty("libfdx.benchmark.graphicsLabel");
        if (label != null && label.trim().length() > 0) {
            return label.trim();
        }
        return graphics.providerId().value();
    }

    private boolean usesInstancedBatchPath() {
        if (libfdxStyle) return false;
        return "gl".equals(graphicsProvider) || "wgpu".equals(graphicsProvider) || "vulkan".equals(graphicsProvider);
    }

    private String logPrefix() {
        return (libfdxStyle ? "SpriteBatchBenchmark[" : "SpriteBatchStressBenchmark[") + graphicsApi + "]";
    }

    private String runLimitDescription() {
        if (exitAfterNanos <= 0L) {
            return "";
        }
        return ", duration=" + format(nanosToSeconds(exitAfterNanos)) + " seconds";
    }

    private long elapsedTestNanos() {
        if (startedAtNanos == 0L || lastRenderNanos == 0L) {
            return 0L;
        }
        return Math.max(0L, lastRenderNanos - startedAtNanos);
    }

    private double nanosToSeconds(long nanos) {
        return nanos / 1000000000.0;
    }

    private void writeProperty(FileOutputStream output, String key, String value) throws IOException {
        writePropertyText(output, key);
        output.write('=');
        writePropertyText(output, value != null ? value : "");
        output.write('\n');
    }

    private void writePropertyText(FileOutputStream output, String value) throws IOException {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '\\') {
                output.write('\\');
                output.write('\\');
            } else if (character == '\n') {
                output.write('\\');
                output.write('n');
            } else if (character == '\r') {
                output.write('\\');
                output.write('r');
            } else {
                output.write(character <= 0x7f ? character : '?');
            }
        }
    }

    private String format(double value) {
        boolean negative = value < 0.0;
        double absolute = negative ? -value : value;
        long scaled = (long) (absolute * 100.0 + 0.5);
        long integerPart = scaled / 100L;
        long fractionalPart = scaled % 100L;
        return (negative ? "-" : "") + integerPart + "." + (fractionalPart < 10L ? "0" : "") + fractionalPart;
    }
}
