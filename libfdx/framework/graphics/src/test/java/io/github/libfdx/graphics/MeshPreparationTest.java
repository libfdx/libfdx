package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MeshPreparationTest {
    @Test
    void packingReusesScratchAcrossVertexBatchesWithoutTruncatingTheUpload() {
        int count = 2051;
        float[] positions = new float[count * 3], colors = new float[count * 4];
        for (int i = 0; i < count; i++) {
            positions[i*3] = i; positions[i*3+1] = i+1; positions[i*3+2] = i+2;
            colors[i*4] = .25f; colors[i*4+1] = .5f; colors[i*4+2] = .75f; colors[i*4+3] = 1;
        }
        var preparation = Mesh.preparePositionColor3D(positions,colors,null,null,null,null,null,null,null,
                null,null,null,false,null,null);
        int steps = 0;
        while (!preparation.step(1024)) assertTrue(++steps < 16);
        Device device = new Device();
        Mesh mesh = preparation.upload(device.graphics,"multiple batches");
        float[] packed = device.writes.getFirst();
        assertEquals(count*7, packed.length);
        for (int i = 0; i < count; i++) {
            assertEquals(i, packed[i*7]); assertEquals(i+1, packed[i*7+1]); assertEquals(i+2, packed[i*7+2]);
            assertEquals(.25f, packed[i*7+3]); assertEquals(.5f, packed[i*7+4]);
            assertEquals(.75f, packed[i*7+5]); assertEquals(1, packed[i*7+6]);
        }
        mesh.dispose();
    }

    @Test
    void rangeUploadsRespectBudgetsTransferOnlyWhenCompleteAndCleanPartialResources() {
        Device device = new Device(); device.ranges = true;
        var preparation = Mesh.preparePositionColor3D(new float[]{1,2,3}, new float[]{1,1,1,1},
                null,null,null,null,null,null,null,null,null,null,false,null,null);
        finish(preparation);
        var upload = preparation.beginUpload(device.graphics, "chunked");
        assertThrows(FdxException.class, upload::take);
        assertThrows(FdxException.class, () -> upload.step(3));
        assertFalse(upload.step(13));
        assertEquals(java.util.List.of(0), device.offsets);
        assertEquals(3, device.writes.getFirst().length);
        assertFalse(upload.step(12));
        assertTrue(upload.step(12));
        assertEquals(java.util.List.of(0,12,24), device.offsets);
        assertArrayEquals(new float[]{1,2,3}, device.writes.get(0));
        Mesh mesh = upload.take();
        upload.dispose(); assertEquals(0, device.disposed);
        assertThrows(FdxException.class, upload::take);
        mesh.dispose(); assertEquals(1, device.disposed);

        var cancelled = preparation.beginUpload(device.graphics, "cancelled");
        assertFalse(cancelled.step(4)); cancelled.dispose(); cancelled.dispose();
        assertEquals(2, device.disposed);
        assertThrows(FdxException.class, () -> cancelled.step(4));

        var failed = preparation.beginUpload(device.graphics, "failed");
        assertFalse(failed.step(4)); device.failWrite = true;
        assertThrows(FdxException.class, () -> failed.step(4)); failed.dispose();
        assertEquals(3, device.disposed);
    }

    @Test
    void boundedPackingPreservesExtendedSkinnedLayoutAndCopiesRetainedSources() {
        float[] positions = {1, 2, 3, 4, 5, 6};
        float[] colors = {1, .5f, .25f, 1, .2f, .4f, .6f, 1};
        var preparation = Mesh.preparePositionColor3D(positions, colors, null,
                new float[]{0, 0, 1, 0, 1, 0}, new float[]{.1f, .2f, .3f, .4f},
                new float[]{1, .5f, .2f, 1, .3f, .4f}, null, new float[]{.2f, .1f, 0, 0, .1f, .2f}, null,
                new int[]{0, 1, 2, 3, 3, 2, 1, 0}, new float[]{1, 0, 0, 0, .5f, .5f, 0, 0}, null, true,
                new float[]{.6f, .7f, .8f, .9f}, new float[]{1, 0, 0, -1, 0, 0, 1, 1});
        Device device = new Device();
        assertThrows(FdxException.class, () -> preparation.step(0));
        assertThrows(FdxException.class, () -> preparation.upload(device.graphics, "early"));
        assertFalse(preparation.step(1));
        assertEquals(0, device.created);
        finish(preparation);
        assertTrue(preparation.step(1));
        Mesh mesh = preparation.upload(device.graphics, "prepared");
        Mesh second = preparation.upload(device.graphics, "independent");
        mesh.sourcePositions()[0] = 7;
        assertEquals(1, second.sourcePositions()[0]);
        mesh.sourcePositions()[0] = 1;
        second.dispose();
        assertArrayEquals(new float[]{1,2,3, 0,0,1, .1f,.2f, 1,.5f,.25f,1, 1,.5f,.2f, .2f,.1f,0,
                0,1,2,3, 1,0,0,0, .6f,.7f, 1,0,0,-1,
                4,5,6, 0,1,0, .3f,.4f, .2f,.4f,.6f,1, 1,.3f,.4f, 0,.1f,.2f,
                3,2,1,0, .5f,.5f,0,0, .8f,.9f, 0,0,1,1}, device.writes.getFirst());
        positions[0] = 99; colors[0] = 99;
        assertEquals(1, mesh.sourcePositions()[0]);
        assertEquals(1, mesh.sourceColors()[0]);
        mesh.dispose();
        assertEquals(2, device.disposed);
    }

    @Test
    void failedUploadDisposesItsBufferAndPreparationHasNoGpuOwnership() {
        var preparation = Mesh.preparePositionColor3D(new float[]{0,0,0}, new float[]{1,1,1,1},
                null,null,null,null,null,null,null,null,null,null,false,null,null);
        finish(preparation);
        Device device = new Device();
        device.failWrite = true;
        assertThrows(FdxException.class, () -> preparation.upload(device.graphics, "failure"));
        assertEquals(device.created, device.disposed);
        device.failWrite = false;
        preparation.upload(device.graphics, "retry").dispose();
        assertEquals(device.created, device.disposed);
    }

    private static void finish(Mesh.PositionColor3DPreparation preparation) {
        int steps = 0;
        while (!preparation.step(1)) assertTrue(++steps < 32);
    }

    private static final class Device {
        final ArrayList<float[]> writes = new ArrayList<>();
        final ArrayList<Integer> offsets = new ArrayList<>();
        int created, disposed;
        boolean failWrite;
        boolean ranges;
        final GraphicsDevice device = (GraphicsDevice) Proxy.newProxyInstance(GraphicsDevice.class.getClassLoader(),
                new Class<?>[]{GraphicsDevice.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "supportsBufferRangeInitialization" -> ranges;
                    case "createBuffer" -> {
                        created++;
                        yield Proxy.newProxyInstance(Buffer.class.getClassLoader(), new Class<?>[]{Buffer.class},
                                (p, m, a) -> { if (m.getName().equals("dispose")) { disposed++; return null; }
                                    throw new AssertionError(m.getName()); });
                    }
                    case "writeBuffer", "initializeBufferRange" -> {
                        if (failWrite) throw new FdxException("write rejected");
                        boolean range = method.getName().equals("initializeBufferRange");
                        if (range) offsets.add((Integer)args[1]);
                        var data = ((ByteBuffer)args[range ? 2 : 1]).duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
                        float[] values = new float[data.remaining()]; data.get(values); writes.add(values); yield null;
                    }
                    default -> throw new AssertionError(method.getName());
                });
        final GraphicsContext graphics = (GraphicsContext) Proxy.newProxyInstance(GraphicsContext.class.getClassLoader(),
                new Class<?>[]{GraphicsContext.class}, (proxy, method, args) -> {
                    if (method.getName().equals("device")) return device;
                    throw new AssertionError(method.getName());
                });
    }
}
