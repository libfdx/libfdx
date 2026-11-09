package io.github.libfdx.tools.uipreview;

import static org.junit.jupiter.api.Assertions.*;

import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.ProviderId;
import io.github.libfdx.files.DefaultFileSystem;
import io.github.libfdx.graphics.*;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiToolkit;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.InflaterInputStream;
import org.junit.jupiter.api.Test;

final class PreviewRuntimeTest {
    private static Path temporary() throws Exception {
        Path root = Path.of("build/tmp/preview-runtime").toAbsolutePath();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, "case-");
    }

    @Test
    void rejectsInvalidConfigurationBeforeOpeningNativeRuntime() {
        assertThrows(IllegalArgumentException.class, () -> new UiPreviewConfig(0, 100));
        UiPreviewConfig config = new UiPreviewConfig(320, 240);
        assertThrows(IllegalArgumentException.class, () -> config.uiScale(Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> config.uiScale(0));
        assertThrows(IllegalArgumentException.class, () -> config.theme(null));
        assertThrows(IllegalArgumentException.class, () -> config.title(" "));
        assertThrows(IllegalArgumentException.class, () -> config.exitAfterFrames(-1));
        assertThrows(IllegalArgumentException.class, () -> UiPreview.open(config, (UiPreviewFactory)null));
    }

    @Test
    void resolvesNamedTargetsBeforeOpeningTheWindow() {
        UiPreviewTarget showcase = UiPreviewTarget.of("showcase", "Showcase", () -> ui -> {});
        UiPreviewTarget editor = UiPreviewTarget.of("editor", "Editor", () -> ui -> {});
        List<UiPreviewTarget> targets = List.of(showcase, editor);
        assertEquals(0, UiPreview.initialTarget(targets, "", ""));
        assertEquals(1, UiPreview.initialTarget(targets, "editor", ""));
        assertEquals(0, UiPreview.initialTarget(targets, "editor", "showcase"));
        assertEquals(0, UiPreview.initialTarget(targets, "editor", "removed"));
        assertThrows(IllegalArgumentException.class, () -> UiPreview.initialTarget(targets, "missing", ""));
        assertThrows(IllegalArgumentException.class, () -> UiPreviewTarget.of("two words", "Invalid", () -> ui -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> UiPreview.openTargets(new UiPreviewConfig(320, 240), showcase, showcase));
    }

    @Test
    void discoversConcreteTypedEntriesWithoutInitializingThem() throws Exception {
        Path classes = Path.of(System.getProperty("preview.testClasses")).toAbsolutePath().normalize();
        List<String> entries = PreviewEntries.discover(List.of(classes.toString()), classes.getParent());
        assertEquals(List.of("io.github.libfdx.tools.uipreview.support.PreviewEntryFixtures$Discovered"), entries);
    }

    @Test
    void sessionDisposesDistinctResourcesInReverseOrderAndSurvivesDisposalFailures() throws Exception {
        Path root = temporary();
        DefaultFileSystem files = new DefaultFileSystem(root.toFile(), root.toFile(), root.toFile());
        GraphicsContext graphics = new GraphicsContext() {
            public GraphicsDevice device() { throw new AssertionError("No graphics operation expected"); }

            public TextureFormat surfaceFormat() { return TextureFormat.RGBA8_UNORM; }

            public GraphicsFrame currentFrame() { throw new AssertionError("No frame expected"); }

            public void clear(float r, float g, float b, float a) { throw new AssertionError("No clear expected"); }

            public ProviderId providerId() { return ProviderId.of("test"); }

            public <T> T as() { return null; }

        };
        UiRoot uiRoot = new UiToolkit(files).root(null, null);
        UiPreviewContext context = new UiPreviewContext(uiRoot, graphics);
        List<Integer> disposed = new ArrayList<>();
        class Resource implements Disposable {
            final int id;
            Resource(int id) { this.id = id; }

            public void dispose() { disposed.add(id); throw new IllegalStateException("dispose " + id); }

            public boolean isDisposed() { return disposed.contains(id); }

            public boolean equals(Object other) { return other instanceof Resource; }

            public int hashCode() { return 0; }

        }
        Resource one = context.own(new Resource(1));
        assertSame(one, context.own(one));
        context.own(new Resource(2));
        assertSame(files, context.files());
        assertSame(uiRoot, context.root());
        assertSame(graphics, context.graphics());
        assertTrue(context.updateAssets());
        RuntimeException failure = assertThrows(RuntimeException.class, context::dispose);
        assertEquals("dispose 2", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertEquals(List.of(2, 1), disposed);
        context.dispose();
        assertEquals(List.of(2, 1), disposed);
        assertThrows(IllegalStateException.class, context::assets);
        assertThrows(IllegalStateException.class, context::root);
        assertThrows(IllegalStateException.class, () -> context.own(one));
        uiRoot.dispose();
        PreviewSnapshot.deleteOwned(root.getParent(), root);
    }

    @Test
    void captureFlipsFramebufferRowsAndPreservesRgbaAndValidChunkChecksums() throws Exception {
        Path root = temporary();
        Path output = root.resolve("nested/capture.png");
        ByteBuffer rgba = ByteBuffer.wrap(new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
        PreviewPng.write(output, 1, 2, rgba);
        assertEquals(0, rgba.position());
        try (DataInputStream png = new DataInputStream(Files.newInputStream(output))) {
            assertArrayEquals(new byte[] {(byte)137, 80, 78, 71, 13, 10, 26, 10}, png.readNBytes(8));
            List<String> types = new ArrayList<>();
            while (png.available() > 0) {
                int length = png.readInt();
                byte[] type = png.readNBytes(4), data = png.readNBytes(length);
                String name = new String(type, java.nio.charset.StandardCharsets.US_ASCII);
                types.add(name);
                CRC32 checksum = new CRC32(); checksum.update(type); checksum.update(data);
                assertEquals((int)checksum.getValue(), png.readInt());
                if (name.equals("IHDR")) {
                    DataInputStream header = new DataInputStream(new ByteArrayInputStream(data));
                    assertEquals(1, header.readInt()); assertEquals(2, header.readInt());
                    assertArrayEquals(new byte[] {8, 6, 0, 0, 0}, header.readAllBytes());
                } else if (name.equals("IDAT")) {
                    try (var pixels = new InflaterInputStream(new ByteArrayInputStream(data))) {
                        assertArrayEquals(new byte[] {0, 5, 6, 7, 8, 0, 1, 2, 3, 4}, pixels.readAllBytes());
                    }
                }
            }
            assertEquals(List.of("IHDR", "IDAT", "IEND"), types);
        }
        assertThrows(IllegalArgumentException.class, () -> PreviewPng.write(output, 2, 2, rgba));
        PreviewSnapshot.deleteOwned(root.getParent(), root);
    }

    @Test
    void stopMarkerIsObservedOffTheRenderThread() throws Exception {
        Path root = temporary();
        Path stop = root.resolve("stop");
        try (PreviewStopSignal signal = new PreviewStopSignal(stop)) {
            assertFalse(signal.requested);
            Files.writeString(stop, "stop");
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (!signal.requested && System.nanoTime() < deadline) Thread.sleep(10);
            assertTrue(signal.requested);
        }
        PreviewSnapshot.deleteOwned(root.getParent(), root);
    }

}
