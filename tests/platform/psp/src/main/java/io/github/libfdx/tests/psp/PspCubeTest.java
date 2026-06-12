package io.github.libfdx.tests.psp;

import io.github.libfdx.backend.psp.natives.types.ScePspFVector3;
import org.teavm.interop.Address;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_CCW;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_COLOR_8888;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_COLOR_BUFFER_BIT;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_CULL_FACE;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_DEPTH_BUFFER_BIT;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_DEPTH_TEST;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_MODEL;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_PI;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_PROJECTION;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TEXTURE_2D;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TRANSFORM_3D;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_TRIANGLES;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_VERTEX_32BITF;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.GU_VIEW;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuClear;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuClearColor;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuClearDepth;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuDisable;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuEnable;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGuFrontFace;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGumDrawArray;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGumLoadIdentity;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGumMatrixMode;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGumPerspective;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGumRotateXYZ;
import static io.github.libfdx.backend.psp.natives.PSPGraphicsApi.sceGumTranslate;

/**
 * Runs the psp cube test scenario.
 *
 * @author xpenatan
 */
final class PspCubeTest implements PspTest {
    private static final int VERTEX_COUNT = 36;
    private static final int VERTEX_SIZE = Integer.BYTES + 3 * Float.BYTES;
    private static final int RED_ABGR = 0xff0000ff;
    private static final int GREEN_ABGR = 0xff00ff00;
    private static final int BLUE_ABGR = 0xffff0000;
    private static final int CYAN_ABGR = 0xffffff00;
    private static final int MAGENTA_ABGR = 0xffff00ff;
    private static final int YELLOW_ABGR = 0xff00ffff;

    private final Address nullAddress = Address.fromInt(0);
    private ScePspFVector3 position;
    private ScePspFVector3 rotation;
    private ByteBuffer vertices;

    /**
     * Initializes this instance.
     */
    @Override
    public void create() {
        position = ScePspFVector3.malloc();
        rotation = ScePspFVector3.malloc();
        vertices = ByteBuffer.allocateDirect(VERTEX_COUNT * VERTEX_SIZE);
        vertices.order(ByteOrder.LITTLE_ENDIAN);

        face(RED_ABGR,
                -1.0f, -1.0f, 1.0f,
                1.0f, -1.0f, 1.0f,
                1.0f, 1.0f, 1.0f,
                -1.0f, 1.0f, 1.0f);
        face(GREEN_ABGR,
                1.0f, -1.0f, -1.0f,
                -1.0f, -1.0f, -1.0f,
                -1.0f, 1.0f, -1.0f,
                1.0f, 1.0f, -1.0f);
        face(BLUE_ABGR,
                1.0f, -1.0f, 1.0f,
                1.0f, -1.0f, -1.0f,
                1.0f, 1.0f, -1.0f,
                1.0f, 1.0f, 1.0f);
        face(CYAN_ABGR,
                -1.0f, -1.0f, -1.0f,
                -1.0f, -1.0f, 1.0f,
                -1.0f, 1.0f, 1.0f,
                -1.0f, 1.0f, -1.0f);
        face(MAGENTA_ABGR,
                -1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, 1.0f,
                1.0f, 1.0f, -1.0f,
                -1.0f, 1.0f, -1.0f);
        face(YELLOW_ABGR,
                -1.0f, -1.0f, -1.0f,
                1.0f, -1.0f, -1.0f,
                1.0f, -1.0f, 1.0f,
                -1.0f, -1.0f, 1.0f);
        vertices.flip();
    }

    /**
     * Renders the current content.
     */
    @Override
    public void render() {
        sceGuClearColor(0xffffffff);
        sceGuClearDepth(0);
        sceGuClear(GU_COLOR_BUFFER_BIT | GU_DEPTH_BUFFER_BIT);
        sceGuEnable(GU_DEPTH_TEST);
        sceGuEnable(GU_CULL_FACE);
        sceGuDisable(GU_TEXTURE_2D);
        sceGuFrontFace(GU_CCW);

        sceGumMatrixMode(GU_PROJECTION);
        sceGumLoadIdentity();
        sceGumPerspective(75.0f, 16.0f / 9.0f, 0.5f, 1000.0f);

        sceGumMatrixMode(GU_VIEW);
        sceGumLoadIdentity();

        sceGumMatrixMode(GU_MODEL);
        sceGumLoadIdentity();
        ScePspFVector3.set(position, 0.0f, 0.0f, -4.0f);
        ScePspFVector3.set(rotation,
                25.0f * (GU_PI / 180.0f),
                -35.0f * (GU_PI / 180.0f),
                0.0f);
        sceGumTranslate(position);
        sceGumRotateXYZ(rotation);

        vertices.position(0);
        sceGumDrawArray(GU_TRIANGLES, GU_COLOR_8888 | GU_VERTEX_32BITF | GU_TRANSFORM_3D,
                VERTEX_COUNT, nullAddress, vertices);
    }

    private void face(int color, float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2,
            float z2, float x3, float y3, float z3) {
        vertex(color, x0, y0, z0);
        vertex(color, x1, y1, z1);
        vertex(color, x2, y2, z2);
        vertex(color, x0, y0, z0);
        vertex(color, x2, y2, z2);
        vertex(color, x3, y3, z3);
    }

    private void vertex(int color, float x, float y, float z) {
        vertices.putInt(color);
        vertices.putFloat(x);
        vertices.putFloat(y);
        vertices.putFloat(z);
    }
}
