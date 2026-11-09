package io.github.libfdx.tests.utils.desktopc;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCAssetExecutor;
import io.github.libfdx.graphics.meshoptimizer.PortableMeshLodSimplifier;
import io.github.libfdx.tests.AssetLoadingTest;
import io.github.libfdx.tests.AudioPlaybackTest;
import io.github.libfdx.tests.ConcurrentGltfLoadingTest;
import io.github.libfdx.tests.FileStreamingTest;
import io.github.libfdx.tests.GltfLoadingTest;
import io.github.libfdx.tests.ModelBatchTest;
import io.github.libfdx.tests.ModelLodOptimizerTest;
import io.github.libfdx.tests.MusicStreamingTest;
import io.github.libfdx.tests.SceneShowcaseTest;
import io.github.libfdx.tests.TexturePackerTest;
import io.github.libfdx.tests.TiledMapTest;
import io.github.libfdx.tests.utils.testselector.TestSelector;
import io.github.libfdx.tests.utils.concurrentgltfloadingtest.ConcurrentGltfObserver;
import io.github.libfdx.tests.utils.gltfloadingtest.GltfLoadingObserver;

/** Supplies application-owned cooperative workers to tests that support asynchronous acquisition. */
public final class DesktopCTestFactory {
    private DesktopCTestFactory() {}

    public static ApplicationListener create(String name, long frames) {
        return switch (TestSelector.normalize(name)) {
            case "AssetLoadingTest" -> new AssetLoadingTest(frames, workers());
            case "AudioPlaybackTest" -> new AudioPlaybackTest(frames, workers());
            case "MusicStreamingTest" -> new MusicStreamingTest(frames, workers());
            case "GltfLoadingTest" ->
                    new GltfLoadingTest(frames, GltfLoadingObserver.NONE, workers());
            case "ConcurrentGltfLoadingTest" ->
                    new ConcurrentGltfLoadingTest(frames, ConcurrentGltfObserver.NONE, workers());
            case "FileStreamingTest" -> new FileStreamingTest(frames, workers());
            case "TiledMapTest" -> new TiledMapTest(frames, workers());
            case "TexturePackerTest" -> new TexturePackerTest(frames, workers());
            case "ModelBatchTest" ->
                    new ModelBatchTest(
                            frames,
                            System.getProperty(
                                    "libfdx.test.modelAsset", ModelBatchTest.DEFAULT_GLTF_ASSET),
                            null,
                            null,
                            workers());
            case "ModelLodOptimizerTest" ->
                    new ModelLodOptimizerTest(frames, new PortableMeshLodSimplifier(), workers());
            case "SceneShowcaseTest" -> new SceneShowcaseTest(frames, workers());
            default -> TestSelector.create(name, frames);
        };
    }

    private static DesktopCAssetExecutor workers() {
        return new DesktopCAssetExecutor(2, 8);
    }
}
