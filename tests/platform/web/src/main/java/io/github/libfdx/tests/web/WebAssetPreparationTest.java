package io.github.libfdx.tests.web;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.web.WebAssetPreparation;
import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.assets.loaders.ImageAssetLoader;
import io.github.libfdx.assets.loaders.ImageDecoder;
import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetLoader;
import io.github.libfdx.assets.AssetLoadContext;
import io.github.libfdx.assets.DefaultAssetManager;
import io.github.libfdx.core.FdxFuture;
import io.github.libfdx.graphics.TextureMipmaps;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Base64;

/** Actual worker transfer, pixel parity, failure/fallback, queue bounds and disposal checks. */
public final class WebAssetPreparationTest extends ApplicationAdapter {
    private final boolean automatic;
    public WebAssetPreparationTest(boolean automatic) { this.automatic = automatic; }
    private WebAssetPreparation worker, crashing;
    private DefaultAssetManager defaultAssets;
    private final ArrayList<WebAssetPreparation> defaults = new ArrayList<>();
    private FdxFuture<ImageData> managedImage;
    private final ArrayList<FdxFuture<ImageData>> burst = new ArrayList<>();
    private FdxFuture<ByteBuffer[]> overflowMips;
    private FdxFuture<ImageData> image, fallback, invalid;
    private final ArrayList<FdxFuture<ByteBuffer[]>> mips = new ArrayList<>();
    private final ArrayList<ByteBuffer[]> expected = new ArrayList<>();
    private ByteBuffer source;
    private byte[] encoded, original;
    private Fdx fdx;
    private long deadline;
    private boolean passed;

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        deadline = System.currentTimeMillis() + 15000;
        worker = new WebAssetPreparation();
        source = ByteBuffer.allocateDirect(66);
        for (int i = 0; i < source.capacity(); i++) source.put(i, (byte)(i * 47));
        source.position(3).limit(63);
        check(worker.prepare(source,Integer.MAX_VALUE,Integer.MAX_VALUE,false,false).isFailed(), "Oversized dimensions did not fail");
        for (int flags = 0; flags < 4; flags++) {
            boolean srgb = (flags & 1) != 0, alpha = (flags & 2) != 0;
            expected.add(TextureMipmaps.rgba8(source,3,5,srgb,alpha));
            mips.add(worker.prepare(source,3,5,srgb,alpha));
        }
        // Two RGBA pixels, including hidden RGB at alpha zero and partial alpha.
        encoded = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAABCAYAAAD0In+KAAAAEUlEQVR4nGPgEpFj0DCyqQcAA8YBUmk+QdcAAAAASUVORK5CYII=");
        original = encoded.clone();
        AssetLoader<ImageData> defaultLoader = new AssetLoader<>() {
            private final ImageAssetLoader delegate = new ImageAssetLoader();
            @Override
            public Class<ImageData> type() { return ImageData.class; }
            @Override
            public FdxFuture<ImageData> load(AssetLoadContext context, AssetDescriptor<ImageData> descriptor) {
                FdxFuture<ImageData> result = delegate.load(context, descriptor);
                ImageDecoder decoder = ImageDecoder.platformDefault(context);
                check(decoder instanceof WebAssetPreparation, "Default image decoder was not bound to web worker");
                defaults.add((WebAssetPreparation) decoder);
                return result;
            }
        };
        var imagePath = AssetDescriptor.of("data/g3d/gltf/Ducky/textures/palette.png", ImageData.class);
        DefaultAssetManager cancelledAssets = new DefaultAssetManager(fdx.files());
        cancelledAssets.registerLoader(ImageData.class, defaultLoader);
        var cancelledImage = cancelledAssets.loadAsync(imagePath);
        ArrayList<FdxFuture<ImageData>> cancelledBurst = new ArrayList<>();
        for (int i=0;i<9;i++) cancelledBurst.add(defaults.getFirst().decodeAsync(null, encoded));
        var cancelledMips = defaults.getFirst().prepare(source,3,5,true,true);
        cancelledAssets.dispose();
        check(cancelledImage.isFailed() && defaults.getFirst().isDisposed(), "Manager did not cancel/dispose its default worker");
        for (var future : cancelledBurst) check(future.isFailed(), "Manager left worker/overflow preparation pending");
        check(cancelledMips.isFailed(), "Manager left cooperative mipmaps pending");
        defaultAssets = new DefaultAssetManager(fdx.files());
        defaultAssets.registerLoader(ImageData.class, defaultLoader);
        managedImage = defaultAssets.loadAsync(imagePath);
        check(defaults.get(0) != defaults.get(1), "Separate managers shared a worker lifetime");
        for (int i=0;i<9;i++) burst.add(defaults.get(1).decodeAsync(null, encoded));
        overflowMips = defaults.get(1).prepare(source,3,5,true,true);
        image = worker.decodeAsync(null, encoded);
        invalid = worker.decodeAsync(null, new byte[] {1,2,3});
        // A real Worker error after startup exercises fallback of an accepted in-flight job.
        crashing = new WebAssetPreparation("data:text/javascript,postMessage({ready:true});onmessage=function(){throw new Error('injected worker crash');}");
        fallback = crashing.decodeAsync(null, encoded);

        WebAssetPreparation cancelled = new WebAssetPreparation();
        ArrayList<FdxFuture<ImageData>> accepted = new ArrayList<>();
        for (int i = 0; i < 8; i++) accepted.add(cancelled.decodeAsync(null, encoded));
        check(cancelled.decodeAsync(null, encoded).isFailed(), "Queue saturation did not reject");
        cancelled.dispose(); cancelled.dispose();
        for (var pending : accepted) check(pending.isFailed(), "Disposal did not cancel queued work");
        check(cancelled.decodeAsync(null, encoded).isFailed(), "Disposed worker accepted work");
    }

    @Override
    public void render() {
        fdx.graphics().main().clear(.02f,.04f,.07f,1);
        if (passed) return;
        defaultAssets.update(8, 2_000_000L);
        check(System.currentTimeMillis() < deadline, "Asset worker checks timed out");
        if (!image.isDone() || !fallback.isDone() || !invalid.isDone() || !managedImage.isDone() || !overflowMips.isDone()) return;
        for (var pending : mips) if (!pending.isDone()) return;
        for (var pending : burst) if (!pending.isDone()) return;
        for (int i = 0; i < mips.size(); i++) {
            ByteBuffer[] actual = mips.get(i).get(), reference = expected.get(i);
            check(actual.length == reference.length, "Mip count changed");
            for (int level = 0; level < actual.length; level++) check(actual[level].equals(reference[level]), "Worker mip pixels differ at flags="+i+" level="+level);
        }
        check(source.position() == 3 && source.limit() == 63, "Borrowed input range changed");
        for (int i = 3; i < 63; i++) check(source.get(i) == (byte)(i * 47), "Borrowed input bytes changed");
        check(java.util.Arrays.equals(encoded,original), "Encoded input detached or mutated");
        checkPixels(image.get()); checkPixels(fallback.get());
        check(invalid.isFailed(), "Malformed image did not fail");
        check(worker.completedWorkerJobs() == 5 && worker.fallbackJobs() == 0, "Expected real worker execution");
        check(crashing.fallbackJobs() == 1, "Worker crash did not select fallback");
        check(managedImage.get().width() > 0 && defaults.get(1).completedWorkerJobs() >= 8
                        && defaults.get(1).fallbackJobs() >= 1,
                "Manager worker burst did not finish through bounded worker/cooperative overflow");
        for (var pending : burst) checkPixels(pending.get());
        ByteBuffer[] overflowLevels = overflowMips.get(), expectedLevels = expected.get(3);
        check(overflowLevels.length == expectedLevels.length, "Overflow mip count changed");
        for (int i=0;i<overflowLevels.length;i++) check(overflowLevels[i].equals(expectedLevels[i]), "Overflow mip pixels changed");
        defaultAssets.dispose();
        check(defaults.get(1).isDisposed(), "Completed manager did not release its worker");
        passed = true;
        System.out.println("WEB_ASSET_PREPARATION_PASS worker=5 rgba=exact mips=exact fallback=1 bounds=1 disposal=1 default=1 isolation=1 overflow=1");
        if (!automatic) fdx.app().requestExit();
    }

    private static void checkPixels(ImageData image) {
        check(image.width()==2 && image.height()==1, "PNG dimensions changed");
        check(image.rgba().equals(ByteBuffer.wrap(new byte[]{10,20,30,0,40,50,60,127})), "PNG lost hidden RGB/alpha");
    }
    private static void check(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }
    @Override
    public void dispose() {
        if (worker != null) worker.dispose();
        if (crashing != null) crashing.dispose();
        if (defaultAssets != null) defaultAssets.dispose();
        check(passed, "Asset worker test ended before completion");
    }
}
