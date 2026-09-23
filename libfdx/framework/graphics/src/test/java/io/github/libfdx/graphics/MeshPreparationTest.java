package io.github.libfdx.graphics;

import io.github.libfdx.core.FdxException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MeshPreparationTest {
    @Test void boundedPackingPreservesExtendedSkinnedLayoutAndCopiesRetainedSources() {
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
        assertTrue(preparation.step(1));
        assertTrue(preparation.step(1));
        Mesh mesh = preparation.upload(device.graphics, "prepared");
        assertArrayEquals(new float[]{1,2,3, 0,0,1, .1f,.2f, 1,.5f,.25f,1, 1,.5f,.2f, .2f,.1f,0,
                0,1,2,3, 1,0,0,0, .6f,.7f, 1,0,0,-1,
                4,5,6, 0,1,0, .3f,.4f, .2f,.4f,.6f,1, 1,.3f,.4f, 0,.1f,.2f,
                3,2,1,0, .5f,.5f,0,0, .8f,.9f, 0,0,1,1}, device.writes.getFirst());
        positions[0] = 99; colors[0] = 99;
        assertEquals(1, mesh.sourcePositions()[0]);
        assertEquals(1, mesh.sourceColors()[0]);
        mesh.dispose();
        assertEquals(1, device.disposed);
    }

    @Test void failedUploadDisposesItsBufferAndPreparationHasNoGpuOwnership() {
        var preparation = Mesh.preparePositionColor3D(new float[]{0,0,0}, new float[]{1,1,1,1},
                null,null,null,null,null,null,null,null,null,null,false,null,null);
        assertTrue(preparation.step(1));
        Device device = new Device();
        device.failWrite = true;
        assertThrows(FdxException.class, () -> preparation.upload(device.graphics, "failure"));
        assertEquals(device.created, device.disposed);
        device.failWrite = false;
        preparation.upload(device.graphics, "retry").dispose();
        assertEquals(device.created, device.disposed);
    }

    private static final class Device {
        final ArrayList<float[]> writes = new ArrayList<>();
        int created, disposed;
        boolean failWrite;
        final GraphicsDevice device = (GraphicsDevice) Proxy.newProxyInstance(GraphicsDevice.class.getClassLoader(),
                new Class<?>[]{GraphicsDevice.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "createBuffer" -> {
                        created++;
                        yield Proxy.newProxyInstance(Buffer.class.getClassLoader(), new Class<?>[]{Buffer.class},
                                (p, m, a) -> { if (m.getName().equals("dispose")) { disposed++; return null; }
                                    throw new AssertionError(m.getName()); });
                    }
                    case "writeBuffer" -> {
                        if (failWrite) throw new FdxException("write rejected");
                        var data = ((ByteBuffer)args[1]).duplicate().order(ByteOrder.nativeOrder()).asFloatBuffer();
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
