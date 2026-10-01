package io.github.libfdx.testsupport.desktopc;

import io.github.libfdx.application.ApplicationListener;
import io.github.libfdx.backend.desktopc.DesktopCAssetExecutor;
import io.github.libfdx.graphics.meshoptimizer.PortableMeshLodSimplifier;
import io.github.libfdx.tests.graphics.AssetLoadingTest;
import io.github.libfdx.tests.graphics.AudioPlaybackTest;
import io.github.libfdx.tests.graphics.ConcurrentGltfLoadingTest;
import io.github.libfdx.tests.graphics.FileStreamingTest;
import io.github.libfdx.tests.graphics.GltfLoadingTest;
import io.github.libfdx.tests.graphics.ModelBatchTest;
import io.github.libfdx.tests.graphics.ModelLodOptimizerTest;
import io.github.libfdx.tests.graphics.MusicStreamingTest;
import io.github.libfdx.tests.graphics.SceneShowcaseTest;
import io.github.libfdx.tests.graphics.TexturePackerTest;
import io.github.libfdx.tests.graphics.TiledMapTest;
import io.github.libfdx.testsupport.TestSelector;
import io.github.libfdx.testsupport.graphics.ConcurrentGltfObserver;
import io.github.libfdx.testsupport.graphics.GltfLoadingObserver;

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
