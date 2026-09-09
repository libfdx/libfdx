package io.github.libfdx.tests.graphics;

import io.github.libfdx.testsupport.graphics.GraphicsParityTest;
import io.github.libfdx.testsupport.graphics.AssetLoadingScene;
import io.github.libfdx.testsupport.graphics.AssetLoadingFixtures.*;
import io.github.libfdx.input.*;

import io.github.libfdx.Fdx;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetExecutor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.AssetLease;
import io.github.libfdx.assets.AssetScope;
import io.github.libfdx.assets.AssetStatus;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.G2DAssetLoaders;
import io.github.libfdx.graphics.g2d.TextureRegion;

import java.nio.ByteBuffer;

/** Delayed shared acquisition, budgeted finalization, and independent level scope release. */
public final class AssetLoadingTest extends GraphicsParityTest {
    private static final String IMAGE = "fdx_logo_dark.png";
    private static final int COUNT = 24;
    private static final int TASK_BUDGET = 3;
    private static final long TIME_BUDGET = 1_000_000L;
    private final AssetExecutor executor;
    private final AssetHandle<?>[] cards = new AssetHandle<?>[COUNT];
    private final AssetLease<?>[] previousCards = new AssetLease<?>[COUNT];
    private final Card[] releasedCards = new Card[COUNT];
    private DefaultAssetManager assets;
    private AssetScope previousLevel;
    private AssetScope currentLevel;
    private DelayedImage image;
    private AssetLoadingScene scene;
    private Input input;
    private boolean advance;
    private float elapsedTime, updateClock;
    private final InputAdapter controls = new InputAdapter() {
        @Override public boolean keyDown(KeyEvent event) {
            if (event.key() != Key.SPACE) return false;
            if (loaded == COUNT) advance = true;
            return true;
        }
    };
    private Thread applicationThread;
    private long frames;
    private int loaded;
    private int finalizationFrames;
    private int maxSteps;
    private int overruns;
    private long maxStepNanos;
    private long maxUpdateNanos;
    private long maxFrameNanos;
    private long loadingFrameNanos;
    private int loadingFrames;
    private int finalized;
    private boolean reported;
    private boolean loadingMeasured;
    private long firstLevelReleasedFrame;
    private boolean scopesReleased;
    private volatile boolean preparedOnWorker;

    /** Creates the cooperative scenario used on event-loop platforms. */
    public AssetLoadingTest(long exitAfterFrames) {
        this(exitAfterFrames, null);
    }

    /** Takes ownership of the optional executor and disposes it after the manager. */
    public AssetLoadingTest(long exitAfterFrames, AssetExecutor executor) {
        super(exitAfterFrames);
        this.executor = executor;
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "AssetLoadingTest");
        applicationThread = Thread.currentThread();
        input = fdx.input();
        input.addProcessor(controls);
        scene = new AssetLoadingScene(graphics, fdx.files());
        image = new DelayedImage(fdx.files().internal(IMAGE));
        assets = new DefaultAssetManager(new DelayedFiles(fdx.files(), image), executor);
        previousLevel = assets.createScope();
        currentLevel = assets.createScope();
        G2DAssetLoaders.register(assets, graphics);
        assets.registerLoader(Card.class, new AssetLoader<Card>() {
            @Override public Class<Card> type() { return Card.class; }
            @Override public FdxFuture<Card> load(AssetLoadContext context, AssetDescriptor<Card> descriptor) {
                FdxFuture<TextureRegion> logo = context.dependency(AssetDescriptor.of(IMAGE, TextureRegion.class));
                FdxFuture<Card> result = FdxFuture.pending();
                context.async(() -> {
                    if (Thread.currentThread() != applicationThread) { preparedOnWorker = true; }
                    int index = Integer.parseInt(descriptor.path());
                    ByteBuffer pixels = rgba8(1, 1);
                    pixels.put((byte)(30 + index * 5)).put((byte)(125 + index * 3)).put((byte)190).put((byte)255);
                    pixels.flip();
                    return pixels;
                }).onSuccess(pixels -> context.completeOnUpdate(() -> {
                    requireApplicationThread();
                    Texture tile = graphics.device().createTexture(TextureDescriptor.rgba8(descriptor.path(), 1, 1));
                    try {
                        graphics.device().writeTexture(tile, pixels);
                        finalized++;
                        return new Card(tile, logo.get());
                    } catch (RuntimeException | Error error) {
                        tile.dispose();
                        throw error;
                    }
                }).onSuccess(result::complete).onFailure(result::completeExceptionally))
                        .onFailure(result::completeExceptionally);
                return result;
            }
        });
        for (int i = 0; i < cards.length; i++) {
            AssetDescriptor<Card> descriptor = AssetDescriptor.of(Integer.toString(i), Card.class);
            previousCards[i] = previousLevel.load(descriptor);
            cards[i] = currentLevel.load(descriptor);
            cards[i].future().onSuccess(card -> { requireApplicationThread(); loaded++; });
            cards[i].future().onFailure(error -> {
                if (!assets.isDisposed()) { throw new FdxException("Loading scenario failed", error); }
            });
        }
        markCreated();
    }

    @Override
    public void render() {
        long start = System.nanoTime();
        float delta = Math.max(0, Math.min(.1f, application.deltaTime()));
        elapsedTime += delta;
        updateClock += delta;
        image.pump(requiresCompletion() ? frames >= 12 : elapsedTime >= 1.5f);
        int before = finalized;
        // Only the interactive demonstration is paced; automated runs exercise every frame.
        if (requiresCompletion() || updateClock >= .08f) {
            assets.update(TASK_BUDGET, TIME_BUDGET);
            updateClock = 0;
            if (finalized > before) { finalizationFrames++; }
            if (assets.lastUpdateTaskCount() > TASK_BUDGET) { throw new FdxException("Task budget exceeded"); }
            maxSteps = Math.max(maxSteps, assets.lastUpdateTaskCount());
            maxStepNanos = Math.max(maxStepNanos, assets.lastUpdateMaxTaskNanos());
            maxUpdateNanos = Math.max(maxUpdateNanos, assets.lastUpdateNanos());
            if (assets.lastUpdateMaxTaskNanos() > TIME_BUDGET) { overruns++; }
        }
        if (frames < 12 && loaded != 0) { throw new FdxException("Parent published before delayed I/O"); }
        if (reported && !scopesReleased && (requiresCompletion() ? frames - firstLevelReleasedFrame >= 24 : advance)) {
            releaseFinalLevel();
            advance = false;
        }

        scene.draw(framebufferWidth(), framebufferHeight(), cards, loaded, reported,
                scopesReleased, elapsedTime, requiresCompletion());
        long elapsed = System.nanoTime() - start;
        if (!loadingMeasured) {
            maxFrameNanos = Math.max(maxFrameNanos, elapsed);
            loadingFrameNanos += elapsed;
            loadingFrames++;
            loadingMeasured = loaded == COUNT;
        }
        if (loaded == COUNT && !reported && (requiresCompletion() || advance)) {
            advance = false;
            verifyLoading();
            for (int i = 0; i < cards.length; i++) {
                if (previousCards[i].asset() != cards[i].asset()) {
                    throw new FdxException("Levels did not share the parent asset");
                }
            }
            previousLevel.dispose();
            assets.unload(IMAGE); // Direct unload cannot invalidate scoped dependencies.
            for (int i = 0; i < cards.length; i++) {
                Card card = (Card)cards[i].asset();
                if (!previousCards[i].isDisposed() || card == null || card.tile.isDisposed() || card.logo.texture().isDisposed()) {
                    throw new FdxException("Closing the first level invalidated the second level");
                }
            }
            firstLevelReleasedFrame = frames;
            logger.info("AssetLoadingTest complete: cards=" + loaded + ", imageReads=" + image.reads
                    + ", frames=" + loadingFrames + ", finalizationFrames=" + finalizationFrames
                    + ", maxSteps=" + maxSteps + ", maxStepNs=" + maxStepNanos + ", maxUpdateNs=" + maxUpdateNanos
                    + ", stepOverruns=" + overruns + ", meanRenderNs=" + (loadingFrameNanos / loadingFrames)
                    + ", maxRenderNs=" + maxFrameNanos + ", workerPreparation=" + preparedOnWorker
                    + ", firstScopeClosed=true, secondScopeValid=true");
            reported = true;
        }
        frames++;
        finishFrame();
    }

    private void verifyLoading() {
        if (loaded != COUNT || image.reads != 1 || finalizationFrames <= 1 || executor != null && !preparedOnWorker) {
            throw new FdxException("Incomplete loading, duplicate I/O, or finalization was not spread across frames");
        }
        Texture shared = ((Card)cards[0].asset()).logo.texture();
        for (AssetHandle<?> handle : cards) {
            if (((Card)handle.asset()).logo.texture() != shared) {
                throw new FdxException("Shared dependency was duplicated");
            }
        }
    }

    private void releaseFinalLevel() {
        Texture shared = ((Card)cards[0].asset()).logo.texture();
        for (int i = 0; i < cards.length; i++) { releasedCards[i] = (Card)cards[i].asset(); }
        currentLevel.dispose();
        for (int i = 0; i < cards.length; i++) {
            if (cards[i].status() != AssetStatus.UNLOADED || cards[i].asset() != null || !releasedCards[i].isDisposed()) {
                throw new FdxException("Final level release leaked a parent resource");
            }
        }
        if (!shared.isDisposed() || assets.find(IMAGE, Texture.class) != null || finalized != COUNT) {
            throw new FdxException("Final level release leaked or duplicated a dependency");
        }
        scopesReleased = true;
        logger.info("AssetLoadingTest scopes released: parents=" + COUNT + ", sharedTextureDisposed=true");
    }

    @Override
    public void dispose() {
        try {
            if (requiresCompletion() && !scopesReleased) {
                throw new FdxException("AssetLoadingTest did not complete both level transitions");
            }
        } finally {
            if (input != null) input.removeProcessor(controls);
            dispose(scene);
            try { dispose(assets); } finally { dispose(executor); }
        }
        verifyDisposed();
    }

    private void requireApplicationThread() {
        if (Thread.currentThread() != applicationThread) { throw new FdxException("Graphics/callback ran on worker"); }
    }

}
