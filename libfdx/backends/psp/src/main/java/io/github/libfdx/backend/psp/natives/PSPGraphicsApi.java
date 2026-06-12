package io.github.libfdx.backend.psp.natives;

import io.github.libfdx.backend.psp.natives.types.ScePspFMatrix4;
import io.github.libfdx.backend.psp.natives.types.ScePspFQuaternion;
import io.github.libfdx.backend.psp.natives.types.ScePspFVector3;
import io.github.libfdx.backend.psp.natives.types.ScePspIMatrix4;
import java.nio.ByteBuffer;
import org.teavm.interop.Address;
import org.teavm.interop.Function;
import org.teavm.interop.Import;
import org.teavm.interop.c.Include;

/**
 * Exposes API access for PSP graphics.
 *
 * @author xpenatan
 */
@Include("PSPGraphicsApi.h")
public class PSPGraphicsApi {

    // pspgu.h

    /* PI, float-sized */
    public static final float GU_PI = 3.141593f;

    /* Boolean values for convenience */
    public static final int GU_FALSE = 0;
    public static final int GU_TRUE = 1;

    /* Primitive types */
    public static final int GU_POINTS = 0;
    public static final int GU_LINES = 1;
    public static final int GU_LINE_STRIP = 2;
    public static final int GU_TRIANGLES = 3;
    public static final int GU_TRIANGLE_STRIP = 4;
    public static final int GU_TRIANGLE_FAN = 5;
    public static final int GU_SPRITES = 6;

    /* States */
    public static final int GU_ALPHA_TEST = 0;
    public static final int GU_DEPTH_TEST = 1;
    public static final int GU_SCISSOR_TEST = 2;
    public static final int GU_STENCIL_TEST = 3;
    public static final int GU_BLEND = 4;
    public static final int GU_CULL_FACE = 5;
    public static final int GU_DITHER = 6;
    public static final int GU_FOG = 7;
    public static final int GU_CLIP_PLANES = 8;
    public static final int GU_TEXTURE_2D = 9;
    public static final int GU_LIGHTING = 10;
    public static final int GU_LIGHT0 = 11;
    public static final int GU_LIGHT1 = 12;
    public static final int GU_LIGHT2 = 13;
    public static final int GU_LIGHT3 = 14;
    public static final int GU_LINE_SMOOTH = 15;
    public static final int GU_PATCH_CULL_FACE = 16;
    public static final int GU_COLOR_TEST = 17;
    public static final int GU_COLOR_LOGIC_OP = 18;
    public static final int GU_FACE_NORMAL_REVERSE = 19;
    public static final int GU_PATCH_FACE = 20;
    public static final int GU_FRAGMENT_2X = 21;
    public static final int GU_MAX_STATUS = 22;

    /* Matrix modes */
    public static final int GU_PROJECTION = 0;
    public static final int GU_VIEW = 1;
    public static final int GU_MODEL = 2;
    public static final int GU_TEXTURE = 3;

    /* Vertex Declarations Begin */
    public static final int GU_TEXTURE_8BIT = 1 << 0;
    public static final int GU_TEXTURE_16BIT = 2 << 0;
    public static final int GU_TEXTURE_32BITF = 3 << 0;
    public static final int GU_TEXTURE_BITS = 3 << 0;

    public static final int GU_COLOR_5650 = 4 << 2;
    public static final int GU_COLOR_5551 = 5 << 2;
    public static final int GU_COLOR_4444 = 6 << 2;
    public static final int GU_COLOR_8888 = 7 << 2;
    public static final int GU_COLOR_BITS = 7 << 2;

    public static final int GU_NORMAL_8BIT = 1 << 5;
    public static final int GU_NORMAL_16BIT = 2 << 5;
    public static final int GU_NORMAL_32BITF = 3 << 5;
    public static final int GU_NORMAL_BITS = 3 << 5;

    public static final int GU_VERTEX_8BIT = 1 << 7;
    public static final int GU_VERTEX_16BIT = 2 << 7;
    public static final int GU_VERTEX_32BITF = 3 << 7;
    public static final int GU_VERTEX_BITS = 3 << 7;

    public static final int GU_WEIGHT_8BIT = 1 << 9;
    public static final int GU_WEIGHT_16BIT = 2 << 9;
    public static final int GU_WEIGHT_32BITF = 3 << 9;
    public static final int GU_WEIGHT_BITS = 3 << 9;

    public static final int GU_INDEX_8BIT = 1 << 11;
    public static final int GU_INDEX_16BIT = 2 << 11;
    public static final int GU_INDEX_BITS = 3 << 11;

    public static final int GU_WEIGHTS_BITS = 7 << 14;
    public static final int GU_VERTICES_BITS = 7 << 18;

    public static final int GU_TRANSFORM_3D = 0 << 23;
    public static final int GU_TRANSFORM_2D = 1 << 23;
    public static final int GU_TRANSFORM_BITS = 1 << 23;
    /* Vertex Declarations End */

    /* display ON/OFF switch */
    public static final int GU_DISPLAY_OFF = 0;
    public static final int GU_DISPLAY_ON = 1;

    /* screen size */
    public static final int GU_SCR_WIDTH = 480;
    public static final int GU_SCR_HEIGHT = 272;
    public static final float GU_SCR_ASPECT = ((float)GU_SCR_WIDTH / (float)GU_SCR_HEIGHT);
    public static final int GU_SCR_OFFSETX = ((4096 - GU_SCR_WIDTH) / 2);
    public static final int GU_SCR_OFFSETY = ((4096 - GU_SCR_HEIGHT) / 2);

    /* Frame buffer */
    public static final int GU_VRAM_TOP = 0x00000000;
    public static final int GU_VRAM_WIDTH = 512;
    /* 16bit mode */
    public static final int GU_VRAM_BUFSIZE = (GU_VRAM_WIDTH*GU_SCR_HEIGHT*2);
    public static final Address GU_VRAM_BP_0 = Address.fromInt(GU_VRAM_TOP);
    public static final Address GU_VRAM_BP_1 = Address.fromInt(GU_VRAM_TOP+GU_VRAM_BUFSIZE);
    public static final Address GU_VRAM_BP_2 = Address.fromInt(GU_VRAM_TOP+(GU_VRAM_BUFSIZE*2));
    /* 32bit mode */
    public static final int GU_VRAM_BUFSIZE32 = (GU_VRAM_WIDTH*GU_SCR_HEIGHT*4);
    public static final Address GU_VRAM_BP32_0 = Address.fromInt(GU_VRAM_TOP);
    public static final Address GU_VRAM_BP32_1 = Address.fromInt(GU_VRAM_TOP+GU_VRAM_BUFSIZE32);
    public static final Address GU_VRAM_BP32_2 = Address.fromInt(GU_VRAM_TOP+(GU_VRAM_BUFSIZE32*2));

    /* Pixel Formats */
    public static final int GU_PSM_5650 = 0; /* Display, Texture, Palette */
    public static final int GU_PSM_5551 = 1; /* Display, Texture, Palette */
    public static final int GU_PSM_4444 = 2; /* Display, Texture, Palette */
    public static final int GU_PSM_8888 = 3; /* Display, Texture, Palette */
    public static final int GU_PSM_T4 = 4; /* Texture */
    public static final int GU_PSM_T8 = 5; /* Texture */
    public static final int GU_PSM_T16 = 6; /* Texture */
    public static final int GU_PSM_T32 = 7; /* Texture */
    public static final int GU_PSM_DXT1 = 8; /* Texture */
    public static final int GU_PSM_DXT3 = 9; /* Texture */
    public static final int GU_PSM_DXT5 = 10; /* Texture */

    /* Spline Mode */
    public static final int GU_FILL_FILL = 0;
    public static final int GU_OPEN_FILL = 1;
    public static final int GU_FILL_OPEN = 2;
    public static final int GU_OPEN_OPEN = 3;

    /* Shading Model */
    public static final int GU_FLAT = 0;
    public static final int GU_SMOOTH = 1;

    /* Logical operation */
    public static final int GU_CLEAR = 0;
    public static final int GU_AND = 1;
    public static final int GU_AND_REVERSE = 2;
    public static final int GU_COPY = 3;
    public static final int GU_AND_INVERTED = 4;
    public static final int GU_NOOP = 5;
    public static final int GU_XOR = 6;
    public static final int GU_OR = 7;
    public static final int GU_NOR = 8;
    public static final int GU_EQUIV = 9;
    public static final int GU_INVERTED = 10;
    public static final int GU_OR_REVERSE = 11;
    public static final int GU_COPY_INVERTED = 12;
    public static final int GU_OR_INVERTED = 13;
    public static final int GU_NAND = 14;
    public static final int GU_SET = 15;

    /* Texture Filter */
    public static final int GU_NEAREST = 0;
    public static final int GU_LINEAR = 1;
    public static final int GU_NEAREST_MIPMAP_NEAREST = 4;
    public static final int GU_LINEAR_MIPMAP_NEAREST = 5;
    public static final int GU_NEAREST_MIPMAP_LINEAR = 6;
    public static final int GU_LINEAR_MIPMAP_LINEAR = 7;

    /* Texture Map Mode */
    public static final int GU_TEXTURE_COORDS = 0;
    public static final int GU_TEXTURE_MATRIX = 1;
    public static final int GU_ENVIRONMENT_MAP = 2;

    /* Texture Level Mode */
    public static final int GU_TEXTURE_AUTO = 0;
    public static final int GU_TEXTURE_CONST = 1;
    public static final int GU_TEXTURE_SLOPE = 2;

    /* Texture Projection Map Mode */
    public static final int GU_POSITION = 0;
    public static final int GU_UV = 1;
    public static final int GU_NORMALIZED_NORMAL = 2;
    public static final int GU_NORMAL = 3;

    /* Wrap Mode */
    public static final int GU_REPEAT = 0;
    public static final int GU_CLAMP = 1;

    /* Front Face Direction */
    public static final int GU_CW = 0;
    public static final int GU_CCW = 1;

    /* Test Function */
    public static final int GU_NEVER = 0;
    public static final int GU_ALWAYS = 1;
    public static final int GU_EQUAL = 2;
    public static final int GU_NOTEQUAL = 3;
    public static final int GU_LESS = 4;
    public static final int GU_LEQUAL = 5;
    public static final int GU_GREATER = 6;
    public static final int GU_GEQUAL = 7;

    /* Clear Buffer Mask */
    public static final int GU_COLOR_BUFFER_BIT = 1;
    public static final int GU_STENCIL_BUFFER_BIT = 2;
    public static final int GU_DEPTH_BUFFER_BIT = 4;
    public static final int GU_FAST_CLEAR_BIT = 16;

    /* Texture Effect */
    public static final int GU_TFX_MODULATE = 0;
    public static final int GU_TFX_DECAL = 1;
    public static final int GU_TFX_BLEND = 2;
    public static final int GU_TFX_REPLACE = 3;
    public static final int GU_TFX_ADD = 4;

    /* Texture Color Component */
    public static final int GU_TCC_RGB = 0;
    public static final int GU_TCC_RGBA = 1;

    /* Blending Op */
    public static final int GU_ADD = 0;
    public static final int GU_SUBTRACT = 1;
    public static final int GU_REVERSE_SUBTRACT = 2;
    public static final int GU_MIN = 3;
    public static final int GU_MAX = 4;
    public static final int GU_ABS = 5;

    /* Blending Factor */
    public static final int GU_OTHER_COLOR = 0;
    public static final int GU_ONE_MINUS_OTHER_COLOR = 1;
    public static final int GU_SRC_ALPHA = 2;
    public static final int GU_ONE_MINUS_SRC_ALPHA = 3;
    public static final int GU_DST_ALPHA = 4;
    public static final int GU_ONE_MINUS_DST_ALPHA = 5;
    public static final int GU_DOUBLE_SRC_ALPHA = 6;
    public static final int GU_ONE_MINUS_DOUBLE_SRC_ALPHA = 7;
    public static final int GU_DOUBLE_DST_ALPHA = 8;
    public static final int GU_ONE_MINUS_DOUBLE_DST_ALPHA = 9;
    public static final int GU_FIX = 10; /* Note: behavior of 11-15 blend factors is identical to GU_FIX */
    public static final int GU_SRC_COLOR = 0; /* Deprecated */
    public static final int GU_ONE_MINUS_SRC_COLOR = 1; /* Deprecated */
    public static final int GU_DST_COLOR = 0; /* Deprecated */
    public static final int GU_ONE_MINUS_DST_COLOR = 1; /* Deprecated */

    /* Stencil Operations */
    public static final int GU_KEEP = 0;
    public static final int GU_ZERO = 1;
    public static final int GU_REPLACE = 2;
    public static final int GU_INVERT = 3;
    public static final int GU_INCR = 4;
    public static final int GU_DECR = 5;

    /* Light Components */
    public static final int GU_AMBIENT = 1;
    public static final int GU_DIFFUSE = 2;
    public static final int GU_SPECULAR = 4;
    public static final int GU_AMBIENT_AND_DIFFUSE = (GU_AMBIENT|GU_DIFFUSE);
    public static final int GU_DIFFUSE_AND_SPECULAR = (GU_DIFFUSE|GU_SPECULAR);
    public static final int GU_POWERED_DIFFUSE = 8;

    /* Light modes */
    public static final int GU_SINGLE_COLOR = 0;
    public static final int GU_SEPARATE_SPECULAR_COLOR = 1;

    /* Light Type */
    public static final int GU_DIRECTIONAL = 0;
    public static final int GU_POINTLIGHT = 1;
    public static final int GU_SPOTLIGHT = 2;

    /* Contexts */
    public static final int GU_DIRECT = 0;
    public static final int GU_CALL = 1;
    public static final int GU_SEND = 2;

    /* List Queue */
    public static final int GU_TAIL = 0;
    public static final int GU_HEAD = 1;

    /* Sync behavior (mode) */
    public static final int GU_SYNC_FINISH = 0;
    public static final int GU_SYNC_SIGNAL = 1;
    public static final int GU_SYNC_DONE = 2;
    public static final int GU_SYNC_LIST = 3;
    public static final int GU_SYNC_SEND = 4;

    /* behavior (what) */
    public static final int GU_SYNC_WAIT = 0;
    public static final int GU_SYNC_NOWAIT = 1;

    /* Sync behavior (what) [see pspge.h] */
    public static final int GU_SYNC_WHAT_DONE = 0;
    public static final int GU_SYNC_WHAT_QUEUED = 1;
    public static final int GU_SYNC_WHAT_DRAW = 2;
    public static final int GU_SYNC_WHAT_STALL = 3;
    public static final int GU_SYNC_WHAT_CANCEL = 4;

    /* Call mode */
    public static final int GU_CALL_NORMAL = 0;
    public static final int GU_CALL_SIGNAL = 1;

    /* Signal models */
    public static final int GU_SIGNAL_WAIT = 1;
    public static final int GU_SIGNAL_NOWAIT = 2;
    public static final int GU_SIGNAL_PAUSE = 3;

    /* Signals */
    public static final int GU_CALLBACK_SIGNAL = 1;
    public static final int GU_CALLBACK_FINISH = 4;

    /* Signal behavior (deprecated) */
    public static final int GU_BEHAVIOR_SUSPEND = 1;
    public static final int GU_BEHAVIOR_CONTINUE = 2;

    /* Break mode */
    public static final int GU_BREAK_PAUSE = 0;
    public static final int GU_BREAK_CANCEL = 1;

    /* Color Macros, maps 8 bit unsigned channels into one 32-bit value */
    /**
     * Runs the GU ABGR step.
     *
     * @param a the a
     * @param b the b
     * @param g the g
     * @return the GU ABGR
     */
    public static int GU_ABGR(int a, int b, int g, int r) { return (((a) << 24)|((b) << 16)|((g) << 8)|(r)); }
    /**
     * Runs the GU ARGB step.
     *
     * @param a the a
     * @param r the r
     * @param g the g
     * @return the GU ARGB
     */
    public static int GU_ARGB(int a, int r, int g, int b) { return GU_ABGR((a),(b),(g),(r)); }
    /**
     * Runs the GU RGBA step.
     *
     * @param r the r
     * @param g the g
     * @param b the b
     * @return the GU RGBA
     */
    public static int GU_RGBA(int r, int g, int b, int a) { return GU_ARGB((a),(r),(g),(b)); }

    /**
     * Calls the sce gu depth buffer native function.
     *
     * @param zbp the zbp
     * @param zbw the zbw
     */
    @Import(name = "sceGuDepthBuffer")
    public static native void sceGuDepthBuffer(Address zbp, int zbw);

    /**
     * Calls the sce gu disp buffer native function.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param dispbp the dispbp
     * @param dispbw the dispbw
     */
    @Import(name = "sceGuDispBuffer")
    public static native void sceGuDispBuffer(int width, int height, Address dispbp, int dispbw);

    /**
     * Calls the sce gu draw buffer native function.
     *
     * @param psm the psm
     * @param fbp the fbp
     * @param fbw the fbw
     */
    @Import(name = "sceGuDrawBuffer")
    public static native void sceGuDrawBuffer(int psm, Address fbp, int fbw);

    /**
     * Calls the sce gu draw buffer list native function.
     *
     * @param psm the psm
     * @param fbp the fbp
     * @param fbw the fbw
     */
    @Import(name = "sceGuDrawBufferList")
    public static native void sceGuDrawBufferList(int psm, Address fbp, int fbw);

    /**
     * Calls the sce gu display native function.
     *
     * @param state the state
     * @return the sce gu display
     */
    @Import(name = "sceGuDisplay")
    public static native int sceGuDisplay(int state);

    /**
     * Calls the sce gu depth func native function.
     *
     * @param function the function
     */
    @Import(name = "sceGuDepthFunc")
    public static native void sceGuDepthFunc(int function);

    /**
     * Calls the sce gu depth mask native function.
     *
     * @param mask the mask
     */
    @Import(name = "sceGuDepthMask")
    public static native void sceGuDepthMask(int mask);

    /**
     * Calls the sce gu depth offset native function.
     *
     * @param offset the offset
     */
    @Import(name = "sceGuDepthOffset")
    public static native void sceGuDepthOffset(int offset);

    /**
     * Calls the sce gu depth range native function.
     *
     * @param near the near
     * @param far the far
     */
    @Import(name = "sceGuDepthRange")
    public static native void sceGuDepthRange(int near, int far);

    /**
     * Calls the sce gu fog native function.
     *
     * @param near the near
     * @param far the far
     * @param color the color
     */
    @Import(name = "sceGuFog")
    public static native void sceGuFog(float near, float far, int color);

    /**
     * Calls the sce gu init native function.
     *
     * @return the sce gu init
     */
    @Import(name = "sceGuInit")
    public static native int sceGuInit();

    /**
     * Calls the sce gu term native function.
     */
    @Import(name = "sceGuTerm")
    public static native void sceGuTerm();

    /**
     * Calls the sce gu break native function.
     *
     * @param mode the mode
     * @return the sce gu break
     */
    @Import(name = "sceGuBreak")
    public static native int sceGuBreak(int mode);

    /**
     * Calls the sce gu continue native function.
     *
     * @return the sce gu continue
     */
    @Import(name = "sceGuContinue")
    public static native int sceGuContinue();

    /**
     * Calls the sce gu set callback native function.
     *
     * @param signal the signal
     * @param callback the callback to invoke
     * @return the sce gu set callback
     */
    @Import(name = "sceGuSetCallback")
    public static native Address sceGuSetCallback(int signal, GuSetCallback callback);

    /**
     * Calls the sce gu signal native function.
     *
     * @param mode the mode
     * @param id the identifier
     */
    @Import(name = "sceGuSignal")
    public static native void sceGuSignal(int mode, int id);

    /**
     * Calls the sce gu send commandf native function.
     *
     * @param cmd the cmd
     * @param argument the argument
     */
    @Import(name = "sceGuSendCommandf")
    public static native void sceGuSendCommandf(int cmd, float argument);

    /**
     * Calls the sce gu send commandi native function.
     *
     * @param cmd the cmd
     * @param argument the argument
     */
    @Import(name = "sceGuSendCommandi")
    public static native void sceGuSendCommandi(int cmd, int argument);

    /**
     * Calls the sce gu get memory native function.
     *
     * @param size the size
     * @return the sce gu get memory
     */
    @Import(name = "sceGuGetMemory")
    public static native Address sceGuGetMemory(int size);

    /**
     * Calls the sce gu start native function.
     *
     * @param ctype the ctype
     * @param list the list
     * @return the sce gu start
     */
    @Import(name = "sceGuStart")
    public static native int sceGuStart(int ctype, Address list);

    /**
     * Calls the sce gu finish native function.
     *
     * @return the sce gu finish
     */
    @Import(name = "sceGuFinish")
    public static native int sceGuFinish();

    /**
     * Calls the sce gu finish ID native function.
     *
     * @param id the identifier
     * @return the sce gu finish ID
     */
    @Import(name = "sceGuFinishId")
    public static native int sceGuFinishId(int id);

    /**
     * Calls the sce gu call list native function.
     *
     * @param list the list
     * @return the sce gu call list
     */
    @Import(name = "sceGuCallList")
    public static native int sceGuCallList(Address list);

    /**
     * Calls the sce gu call mode native function.
     *
     * @param mode the mode
     */
    @Import(name = "sceGuCallMode")
    public static native void sceGuCallMode(int mode);

    /**
     * Calls the sce gu check list native function.
     *
     * @return the sce gu check list
     */
    @Import(name = "sceGuCheckList")
    public static native int sceGuCheckList();

    /**
     * Calls the sce gu send list native function.
     *
     * @param mode the mode
     * @param list the list
     * @param context the context
     * @return the sce gu send list
     */
    @Import(name = "sceGuSendList")
    public static native int sceGuSendList(int mode, Address list, Address context);

    /**
     * Calls the sce gu swap buffers native function.
     *
     * @return the sce gu swap buffers
     */
    @Import(name = "sceGuSwapBuffers")
    public static native Address sceGuSwapBuffers();

    /**
     * Calls the sce gu sync native function.
     *
     * @param mode the mode
     * @param what the what
     * @return the sce gu sync
     */
    @Import(name = "sceGuSync")
    public static native int sceGuSync(int mode, int what);

    /**
     * Calls the sce gu draw array native function.
     *
     * @param prim the prim
     * @param vtype the vtype
     * @param count the count
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGuDrawArray")
    public static native void sceGuDrawArray(int prim, int vtype, int count, Address indices, Address vertices);

    /**
     * Calls the sce gu draw array native function.
     *
     * @param prim the prim
     * @param vtype the vtype
     * @param count the count
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGuDrawArray")
    public static native void sceGuDrawArray(int prim, int vtype, int count, Address indices, ByteBuffer vertices);

    /**
     * Calls the sce gu begin object native function.
     *
     * @param vtype the vtype
     * @param count the count
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGuBeginObject")
    public static native void sceGuBeginObject(int vtype, int count, Address indices, Address vertices);

    /**
     * Calls the sce gu end object native function.
     *
     * @return the sce gu end object
     */
    @Import(name = "sceGuEndObject")
    public static native int sceGuEndObject();

    /**
     * Calls the sce gu set status native function.
     *
     * @param state the state
     * @param status the status
     */
    @Import(name = "sceGuSetStatus")
    public static native void sceGuSetStatus(int state, int status);

    /**
     * Calls the sce gu get status native function.
     *
     * @param state the state
     * @return the sce gu get status
     */
    @Import(name = "sceGuGetStatus")
    public static native int sceGuGetStatus(int state);

    /**
     * Calls the sce gu set all status native function.
     *
     * @param status the status
     */
    @Import(name = "sceGuSetAllStatus")
    public static native void sceGuSetAllStatus(int status);

    /**
     * Calls the sce gu get all status native function.
     *
     * @return the sce gu get all status
     */
    @Import(name = "sceGuGetAllStatus")
    public static native int sceGuGetAllStatus();

    /**
     * Calls the sce gu enable native function.
     *
     * @param state the state
     */
    @Import(name = "sceGuEnable")
    public static native void sceGuEnable(int state);

    /**
     * Calls the sce gu disable native function.
     *
     * @param state the state
     */
    @Import(name = "sceGuDisable")
    public static native void sceGuDisable(int state);

    /**
     * Calls the sce gu light native function.
     *
     * @param light the light
     * @param type the expected Java type
     * @param components the components
     * @param position the position
     */
    @Import(name = "sceGuLight")
    public static native void sceGuLight(int light, int type, int components, Address position);

    /**
     * Calls the sce gu light att native function.
     *
     * @param light the light
     * @param atten0 the atten0
     * @param atten1 the atten1
     * @param atten2 the atten2
     */
    @Import(name = "sceGuLightAtt")
    public static native void sceGuLightAtt(int light, float atten0, float atten1, float atten2);

    /**
     * Calls the sce gu light color native function.
     *
     * @param light the light
     * @param component the component
     * @param color the color
     */
    @Import(name = "sceGuLightColor")
    public static native void sceGuLightColor(int light, int component, int color);

    /**
     * Calls the sce gu light mode native function.
     *
     * @param mode the mode
     */
    @Import(name = "sceGuLightMode")
    public static native void sceGuLightMode(int mode);

    /**
     * Calls the sce gu light spot native function.
     *
     * @param light the light
     * @param direction the direction
     * @param exponent the exponent
     * @param cutoff the cutoff
     */
    @Import(name = "sceGuLightSpot")
    public static native void sceGuLightSpot(int light, Address direction, float exponent, float cutoff);

    /**
     * Calls the sce gu clear native function.
     *
     * @param flags the flags
     */
    @Import(name = "sceGuClear")
    public static native void sceGuClear(int flags);

    /**
     * Calls the sce gu clear color native function.
     *
     * @param color the color
     */
    @Import(name = "sceGuClearColor")
    public static native void sceGuClearColor(int color);

    /**
     * Calls the sce gu clear depth native function.
     *
     * @param depth the depth
     */
    @Import(name = "sceGuClearDepth")
    public static native void sceGuClearDepth(int depth);

    /**
     * Calls the sce gu clear stencil native function.
     *
     * @param stencil the stencil
     */
    @Import(name = "sceGuClearStencil")
    public static native void sceGuClearStencil(int stencil);

    /**
     * Calls the sce gu pixel mask native function.
     *
     * @param mask the mask
     */
    @Import(name = "sceGuPixelMask")
    public static native void sceGuPixelMask(int mask);

    /**
     * Calls the sce gu color native function.
     *
     * @param color the color
     */
    @Import(name = "sceGuColor")
    public static native void sceGuColor(int color);

    /**
     * Calls the sce gu color func native function.
     *
     * @param func the func
     * @param color the color
     * @param mask the mask
     */
    @Import(name = "sceGuColorFunc")
    public static native void sceGuColorFunc(int func, int color, int mask);

    /**
     * Calls the sce gu color material native function.
     *
     * @param components the components
     */
    @Import(name = "sceGuColorMaterial")
    public static native void sceGuColorMaterial(int components);

    /**
     * Calls the sce gu alpha func native function.
     *
     * @param func the func
     * @param value the value
     * @param mask the mask
     */
    @Import(name = "sceGuAlphaFunc")
    public static native void sceGuAlphaFunc(int func, int value, int mask);

    /**
     * Calls the sce gu ambient native function.
     *
     * @param color the color
     */
    @Import(name = "sceGuAmbient")
    public static native void sceGuAmbient(int color);

    /**
     * Calls the sce gu ambient color native function.
     *
     * @param color the color
     */
    @Import(name = "sceGuAmbientColor")
    public static native void sceGuAmbientColor(int color);

    /**
     * Calls the sce gu blend func native function.
     *
     * @param op the op
     * @param src the src
     * @param dest the dest
     * @param srcfix the srcfix
     * @param destfix the destfix
     */
    @Import(name = "sceGuBlendFunc")
    public static native void sceGuBlendFunc(int op, int src, int dest, int srcfix, int destfix);

    /**
     * Calls the sce gu material native function.
     *
     * @param mode the mode
     * @param color the color
     */
    @Import(name = "sceGuMaterial")
    public static native void sceGuMaterial(int mode, int color);

    /**
     * Calls the sce gu model color native function.
     *
     * @param emissive the emissive
     * @param ambient the ambient
     * @param diffuse the diffuse
     * @param specular the specular
     */
    @Import(name = "sceGuModelColor")
    public static native void sceGuModelColor(int emissive, int ambient, int diffuse, int specular);

    /**
     * Calls the sce gu stencil func native function.
     *
     * @param func the func
     * @param ref the ref
     * @param mask the mask
     */
    @Import(name = "sceGuStencilFunc")
    public static native void sceGuStencilFunc(int func, int ref, int mask);

    /**
     * Calls the sce gu stencil op native function.
     *
     * @param fail the fail
     * @param zfail the zfail
     * @param zpass the zpass
     */
    @Import(name = "sceGuStencilOp")
    public static native void sceGuStencilOp(int fail, int zfail, int zpass);

    /**
     * Calls the sce gu specular native function.
     *
     * @param power the power
     */
    @Import(name = "sceGuSpecular")
    public static native void sceGuSpecular(float power);

    /**
     * Calls the sce gu front face native function.
     *
     * @param order the order
     */
    @Import(name = "sceGuFrontFace")
    public static native void sceGuFrontFace(int order);

    /**
     * Calls the sce gu logical op native function.
     *
     * @param op the op
     */
    @Import(name = "sceGuLogicalOp")
    public static native void sceGuLogicalOp(int op);

    /**
     * Calls the sce gu set dither native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGuSetDither")
    public static native void sceGuSetDither(Address matrix4);

    /**
     * Calls the sce gu set dither native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGuSetDither")
    public static native void sceGuSetDither(ScePspIMatrix4 matrix4);

    /**
     * Calls the sce gu shade model native function.
     *
     * @param mode the mode
     */
    @Import(name = "sceGuShadeModel")
    public static native void sceGuShadeModel(int mode);

    /**
     * Calls the sce gu copy image native function.
     *
     * @param psm the psm
     * @param sx the sx
     * @param sy the sy
     * @param width the width in pixels
     * @param height the height in pixels
     * @param srcw the srcw
     * @param src the src
     * @param dx the dx
     * @param dy the dy
     * @param destw the destw
     * @param dest the dest
     */
    @Import(name = "sceGuCopyImage")
    public static native void sceGuCopyImage(int psm, int sx, int sy, int width, int height, int srcw, Address src, int dx, int dy, int destw, Address dest);

    /**
     * Calls the sce gu tex env color native function.
     *
     * @param color the color
     */
    @Import(name = "sceGuTexEnvColor")
    public static native void sceGuTexEnvColor(int color);

    /**
     * Calls the sce gu tex filter native function.
     *
     * @param min the min
     * @param mag the mag
     */
    @Import(name = "sceGuTexFilter")
    public static native void sceGuTexFilter(int min, int mag);

    /**
     * Calls the sce gu tex flush native function.
     */
    @Import(name = "sceGuTexFlush")
    public static native void sceGuTexFlush();

    /**
     * Calls the sce gu tex func native function.
     *
     * @param tfx the tfx
     * @param tcc the tcc
     */
    @Import(name = "sceGuTexFunc")
    public static native void sceGuTexFunc(int tfx, int tcc);

    /**
     * Calls the sce gu tex image native function.
     *
     * @param mipmap the mipmap
     * @param width the width in pixels
     * @param height the height in pixels
     * @param tbw the tbw
     * @param tbp the tbp
     */
    @Import(name = "sceGuTexImage")
    public static native void sceGuTexImage(int mipmap, int width, int height, int tbw, Address tbp);

    /**
     * Calls the sce gu tex image native function.
     *
     * @param mipmap the mipmap
     * @param width the width in pixels
     * @param height the height in pixels
     * @param tbw the tbw
     * @param tbp the tbp
     */
    @Import(name = "sceGuTexImage")
    public static native void sceGuTexImage(int mipmap, int width, int height, int tbw, ByteBuffer tbp);

    /**
     * Calls the sce gu tex level mode native function.
     *
     * @param mode the mode
     * @param bias the bias
     */
    @Import(name = "sceGuTexLevelMode")
    public static native void sceGuTexLevelMode(int mode, float bias);

    /**
     * Calls the sce gu tex map mode native function.
     *
     * @param mode the mode
     * @param lu the lu
     * @param lv the lv
     */
    @Import(name = "sceGuTexMapMode")
    public static native void sceGuTexMapMode(int mode, int lu, int lv);

    /**
     * Calls the sce gu tex mode native function.
     *
     * @param tpsm the tpsm
     * @param maxmips the maxmips
     * @param mc the mc
     * @param swizzle the swizzle
     */
    @Import(name = "sceGuTexMode")
    public static native void sceGuTexMode(int tpsm, int maxmips, int mc, int swizzle);

    /**
     * Calls the sce gu tex offset native function.
     *
     * @param u the u
     * @param v the v
     */
    @Import(name = "sceGuTexOffset")
    public static native void sceGuTexOffset(float u, float v);

    /**
     * Calls the sce gu tex proj map mode native function.
     *
     * @param mode the mode
     */
    @Import(name = "sceGuTexProjMapMode")
    public static native void sceGuTexProjMapMode(int mode);

    /**
     * Calls the sce gu tex scale native function.
     *
     * @param u the u
     * @param v the v
     */
    @Import(name = "sceGuTexScale")
    public static native void sceGuTexScale(float u, float v);

    /**
     * Calls the sce gu tex slope native function.
     *
     * @param slope the slope
     */
    @Import(name = "sceGuTexSlope")
    public static native void sceGuTexSlope(float slope);

    /**
     * Calls the sce gu tex sync native function.
     */
    @Import(name = "sceGuTexSync")
    public static native void sceGuTexSync();

    /**
     * Calls the sce gu tex wrap native function.
     *
     * @param u the u
     * @param v the v
     */
    @Import(name = "sceGuTexWrap")
    public static native void sceGuTexWrap(int u, int v);

    /**
     * Calls the sce gu clut load native function.
     *
     * @param num_blocks the num blocks
     * @param cbp the cbp
     */
    @Import(name = "sceGuClutLoad")
    public static native void sceGuClutLoad(int num_blocks, Address cbp);

    /**
     * Calls the sce gu clut mode native function.
     *
     * @param cpsm the cpsm
     * @param shift the shift
     * @param mask the mask
     * @param csa the csa
     */
    @Import(name = "sceGuClutMode")
    public static native void sceGuClutMode(int cpsm, int shift, int mask, int csa);

    /**
     * Calls the sce gu offset native function.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    @Import(name = "sceGuOffset")
    public static native void sceGuOffset(int x, int y);

    /**
     * Calls the sce gu scissor native function.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     * @param w the w
     * @param h the h
     */
    @Import(name = "sceGuScissor")
    public static native void sceGuScissor(int x, int y, int w, int h);

    /**
     * Calls the sce gu viewport native function.
     *
     * @param cx the cx
     * @param cy the cy
     * @param width the width in pixels
     * @param height the height in pixels
     */
    @Import(name = "sceGuViewport")
    public static native void sceGuViewport(int cx, int cy, int width, int height);

    /**
     * Calls the sce gu draw bezier native function.
     *
     * @param vtype the vtype
     * @param ucount the ucount
     * @param vcount the vcount
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGuDrawBezier")
    public static native void sceGuDrawBezier(int vtype, int ucount, int vcount, Address indices, Address vertices);

    /**
     * Calls the sce gu patch divide native function.
     *
     * @param ulevel the ulevel
     * @param vlevel the vlevel
     */
    @Import(name = "sceGuPatchDivide")
    public static native void sceGuPatchDivide(int ulevel, int vlevel);

    /**
     * Calls the sce gu patch front face native function.
     *
     * @param mode the mode
     */
    @Import(name = "sceGuPatchFrontFace")
    public static native void sceGuPatchFrontFace(int mode);

    /**
     * Calls the sce gu patch prim native function.
     *
     * @param prim the prim
     */
    @Import(name = "sceGuPatchPrim")
    public static native void sceGuPatchPrim(int prim);

    /**
     * Calls the sce gu draw spline native function.
     *
     * @param vtype the vtype
     * @param ucount the ucount
     * @param vcount the vcount
     * @param uedge the uedge
     * @param vedge the vedge
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGuDrawSpline")
    public static native void sceGuDrawSpline(int vtype, int ucount, int vcount, int uedge, int vedge, Address indices, Address vertices);

    /**
     * Calls the sce gu set matrix native function.
     *
     * @param type the expected Java type
     * @param matrix the matrix
     */
    @Import(name = "sceGuSetMatrix")
    public static native void sceGuSetMatrix(int type, Address matrix);

    /**
     * Calls the sce gu set matrix native function.
     *
     * @param type the expected Java type
     * @param matrix the matrix
     */
    @Import(name = "sceGuSetMatrix")
    public static native void sceGuSetMatrix(int type, ScePspFMatrix4 matrix);

    /**
     * Calls the sce gu bone matrix native function.
     *
     * @param index the index
     * @param matrix the matrix
     */
    @Import(name = "sceGuBoneMatrix")
    public static native void sceGuBoneMatrix(int index, Address matrix);

    /**
     * Calls the sce gu bone matrix native function.
     *
     * @param index the index
     * @param matrix the matrix
     */
    @Import(name = "sceGuBoneMatrix")
    public static native void sceGuBoneMatrix(int index, ScePspFMatrix4 matrix);

    /**
     * Calls the sce gu morph weight native function.
     *
     * @param index the index
     * @param weight the weight
     */
    @Import(name = "sceGuMorphWeight")
    public static native void sceGuMorphWeight(int index, float weight);

    /**
     * Calls the sce gu draw array n native function.
     *
     * @param primitive_type the primitive type
     * @param vertex_type the vertex type
     * @param vcount the vcount
     * @param primcount the primcount
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGuDrawArrayN")
    public static native void sceGuDrawArrayN(int primitive_type, int vertex_type, int vcount, int primcount, Address indices, Address vertices);

    /**
     * Calls the gu swap buffers behaviour native function.
     *
     * @param behaviour the behaviour
     */
    @Import(name = "guSwapBuffersBehaviour")
    public static native void guSwapBuffersBehaviour(int behaviour);

    /**
     * Calls the gu swap buffers callback native function.
     *
     * @param callback the callback to invoke
     */
    @Import(name = "guSwapBuffersCallback")
    public static native void guSwapBuffersCallback(GuSwapBuffersCallback callback);

    /**
     * Calls the gu get static vram buffer native function.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param psm the psm
     * @return the gu get static vram buffer
     */
    @Import(name = "guGetStaticVramBuffer")
    public static native Address guGetStaticVramBuffer(int width, int height, int psm);

    /**
     * Calls the gu get static vram texture native function.
     *
     * @param width the width in pixels
     * @param height the height in pixels
     * @param psm the psm
     * @return the gu get static vram texture
     */
    @Import(name = "guGetStaticVramTexture")
    public static native Address guGetStaticVramTexture(int width, int height, int psm);

    /**
     * Calls the gu get display state native function.
     *
     * @return the gu get display state
     */
    @Import(name = "guGetDisplayState")
    public static native int guGetDisplayState();

    /**
     * Represents a gu swap buffers callback.
     *
     * @author xpenatan
     */
    public static abstract class GuSwapBuffersCallback extends Function {
        /**
         * Runs the invoke step.
         *
         * @param display the display
         * @param render the render
         */
        public abstract void invoke(Address display, Address render);
    }

    /**
     * Represents a gu set callback.
     *
     * @author xpenatan
     */
    public static abstract class GuSetCallback extends Function {
        /**
         * Runs the invoke step.
         *
         * @param value the value
         */
        public abstract void invoke(int value);
    }

    // pspgum.h

    /**
     * Calls the sce gum draw array native function.
     *
     * @param prim the prim
     * @param vtype the vtype
     * @param count the count
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGumDrawArray")
    public static native void sceGumDrawArray(int prim, int vtype, int count, Address indices, Address vertices);

    /**
     * Calls the sce gum draw array native function.
     *
     * @param prim the prim
     * @param vtype the vtype
     * @param count the count
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGumDrawArray")
    public static native void sceGumDrawArray(int prim, int vtype, int count, Address indices, ByteBuffer vertices);

    /**
     * Calls the sce gum draw array n native function.
     *
     * @param prim the prim
     * @param vtype the vtype
     * @param count the count
     * @param a3 the a3
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGumDrawArrayN")
    public static native void sceGumDrawArrayN(int prim, int vtype, int count, int a3, Address indices, Address vertices);

    /**
     * Calls the sce gum draw bezier native function.
     *
     * @param vtype the vtype
     * @param ucount the ucount
     * @param vcount the vcount
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGumDrawBezier")
    public static native void sceGumDrawBezier(int vtype, int ucount, int vcount, Address indices, Address vertices);

    /**
     * Calls the sce gum draw spline native function.
     *
     * @param vtype the vtype
     * @param ucount the ucount
     * @param vcount the vcount
     * @param uedge the uedge
     * @param vedge the vedge
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGumDrawSpline")
    public static native void sceGumDrawSpline(int vtype, int ucount, int vcount, int uedge, int vedge, Address indices, Address vertices);

    /**
     * Calls the sce gum load identity native function.
     */
    @Import(name = "sceGumLoadIdentity")
    public static native void sceGumLoadIdentity();

    /**
     * Calls the sce gum load matrix native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGumLoadMatrix")
    public static native void sceGumLoadMatrix(Address matrix4);

    /**
     * Calls the sce gum load matrix native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGumLoadMatrix")
    public static native void sceGumLoadMatrix(ScePspFMatrix4 matrix4);

    /**
     * Calls the sce gum look at native function.
     *
     * @param eye_Vector3 the eye vector3
     * @param center_Vector3 the center vector3
     * @param up_Vector3 the up vector3
     */
    @Import(name = "sceGumLookAt")
    public static native void sceGumLookAt(Address eye_Vector3, Address center_Vector3, Address up_Vector3);

    /**
     * Calls the sce gum look at native function.
     *
     * @param eye_Vector3 the eye vector3
     * @param center_Vector3 the center vector3
     * @param up_Vector3 the up vector3
     */
    @Import(name = "sceGumLookAt")
    public static native void sceGumLookAt(ScePspFVector3 eye_Vector3, ScePspFVector3 center_Vector3, ScePspFVector3 up_Vector3);

    /**
     * Calls the sce gum matrix mode native function.
     *
     * @param mode the mode
     */
    @Import(name = "sceGumMatrixMode")
    public static native void sceGumMatrixMode(int mode);

    /**
     * Calls the sce gum mult matrix native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGumMultMatrix")
    public static native void sceGumMultMatrix(Address matrix4);

    /**
     * Calls the sce gum mult matrix native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGumMultMatrix")
    public static native void sceGumMultMatrix(ScePspFMatrix4 matrix4);

    /**
     * Calls the sce gum ortho native function.
     *
     * @param left the left
     * @param right the right
     * @param bottom the bottom
     * @param top the top
     * @param near the near
     * @param far the far
     */
    @Import(name = "sceGumOrtho")
    public static native void sceGumOrtho(float left, float right, float bottom, float top, float near, float far);

    /**
     * Calls the sce gum perspective native function.
     *
     * @param fovy the fovy
     * @param aspect the aspect
     * @param near the near
     * @param far the far
     */
    @Import(name = "sceGumPerspective")
    public static native void sceGumPerspective(float fovy, float aspect, float near, float far);

    /**
     * Calls the sce gum pop matrix native function.
     */
    @Import(name = "sceGumPopMatrix")
    public static native void sceGumPopMatrix();

    /**
     * Calls the sce gum push matrix native function.
     */
    @Import(name = "sceGumPushMatrix")
    public static native void sceGumPushMatrix();

    /**
     * Calls the sce gum rotate x native function.
     *
     * @param angle the angle
     */
    @Import(name = "sceGumRotateX")
    public static native void sceGumRotateX(float angle);

    /**
     * Calls the sce gum rotate y native function.
     *
     * @param angle the angle
     */
    @Import(name = "sceGumRotateY")
    public static native void sceGumRotateY(float angle);

    /**
     * Calls the sce gum rotate z native function.
     *
     * @param angle the angle
     */
    @Import(name = "sceGumRotateZ")
    public static native void sceGumRotateZ(float angle);

    /**
     * Calls the sce gum rotate XYZ native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumRotateXYZ")
    public static native void sceGumRotateXYZ(Address vector3);

    /**
     * Calls the sce gum rotate XYZ native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumRotateXYZ")
    public static native void sceGumRotateXYZ(ScePspFVector3 vector3);

    /**
     * Calls the sce gum rotate ZYX native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumRotateZYX")
    public static native void sceGumRotateZYX(Address vector3);

    /**
     * Calls the sce gum rotate ZYX native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumRotateZYX")
    public static native void sceGumRotateZYX(ScePspFVector3 vector3);

    /**
     * Calls the sce gum rotate native function.
     *
     * @param quaternion the quaternion
     */
    @Import(name = "sceGumRotate")
    public static native void sceGumRotate(Address quaternion);

    /**
     * Calls the sce gum rotate native function.
     *
     * @param quaternion the quaternion
     */
    @Import(name = "sceGumRotate")
    public static native void sceGumRotate(ScePspFQuaternion quaternion);

    /**
     * Calls the sce gum scale native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumScale")
    public static native void sceGumScale(Address vector3);

    /**
     * Calls the sce gum scale native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumScale")
    public static native void sceGumScale(ScePspFVector3 vector3);

    /**
     * Calls the sce gum store matrix native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGumStoreMatrix")
    public static native void sceGumStoreMatrix(Address matrix4);

    /**
     * Calls the sce gum store matrix native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "sceGumStoreMatrix")
    public static native void sceGumStoreMatrix(ScePspFMatrix4 matrix4);

    /**
     * Calls the sce gum translate native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumTranslate")
    public static native void sceGumTranslate(Address vector3);

    /**
     * Calls the sce gum translate native function.
     *
     * @param vector3 the vector3
     */
    @Import(name = "sceGumTranslate")
    public static native void sceGumTranslate(ScePspFVector3 vector3);

    /**
     * Calls the sce gum update matrix native function.
     */
    @Import(name = "sceGumUpdateMatrix")
    public static native void sceGumUpdateMatrix();

    /**
     * Calls the sce gum full inverse native function.
     */
    @Import(name = "sceGumFullInverse")
    public static native void sceGumFullInverse();

    /**
     * Calls the sce gum fast inverse native function.
     */
    @Import(name = "sceGumFastInverse")
    public static native void sceGumFastInverse();

    /**
     * Calls the sce gum begin object native function.
     *
     * @param vtype the vtype
     * @param count the count
     * @param indices the indices
     * @param vertices the vertices
     */
    @Import(name = "sceGumBeginObject")
    public static native void sceGumBeginObject(int vtype, int count, Address indices, Address vertices);

    /**
     * Calls the sce gum end object native function.
     */
    @Import(name = "sceGumEndObject")
    public static native void sceGumEndObject();

    /**
     * Calls the gum init native function.
     */
    @Import(name = "gumInit")
    public static native void gumInit();

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "gumInit")
    public static native void gumLoadIdentity(Address matrix4);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     */
    @Import(name = "gumInit")
    public static native void gumLoadIdentity(ScePspFMatrix4 matrix4);

    /**
     * Calls the gum init native function.
     *
     * @param rMatrix4 the r matrix4
     * @param quaternion the quaternion
     */
    @Import(name = "gumInit")
    public static native void gumLoadQuaternion(Address rMatrix4, Address quaternion);

    /**
     * Calls the gum init native function.
     *
     * @param rMatrix4 the r matrix4
     * @param quaternion the quaternion
     */
    @Import(name = "gumInit")
    public static native void gumLoadQuaternion(ScePspFMatrix4 rMatrix4, ScePspFQuaternion quaternion);

    /**
     * Calls the gum init native function.
     *
     * @param rMatrix4 the r matrix4
     * @param aMatrix4 the a matrix4
     */
    @Import(name = "gumInit")
    public static native void gumLoadMatrix(Address rMatrix4, Address aMatrix4);

    /**
     * Calls the gum init native function.
     *
     * @param rMatrix4 the r matrix4
     * @param aMatrix4 the a matrix4
     */
    @Import(name = "gumInit")
    public static native void gumLoadMatrix(ScePspFMatrix4 rMatrix4, ScePspFMatrix4 aMatrix4);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     * @param eyeVector3 the eye vector3
     * @param centerVector3 the center vector3
     * @param upVector3 the up vector3
     */
    @Import(name = "gumInit")
    public static native void gumLookAt(Address matrix4, Address eyeVector3, Address centerVector3, Address upVector3);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     * @param eyeVector3 the eye vector3
     * @param centerVector3 the center vector3
     * @param upVector3 the up vector3
     */
    @Import(name = "gumInit")
    public static native void gumLookAt(ScePspFMatrix4 matrix4, ScePspFVector3 eyeVector3, ScePspFVector3 centerVector3, ScePspFVector3 upVector3);

    /**
     * Calls the gum init native function.
     *
     * @param resultMatrix4 the result matrix4
     * @param aMatrix4 the a matrix4
     * @param bMatrix4 the b matrix4
     */
    @Import(name = "gumInit")
    public static native void gumMultMatrix(Address resultMatrix4, Address aMatrix4, Address bMatrix4);

    /**
     * Calls the gum init native function.
     *
     * @param resultMatrix4 the result matrix4
     * @param aMatrix4 the a matrix4
     * @param bMatrix4 the b matrix4
     */
    @Import(name = "gumInit")
    public static native void gumMultMatrix(ScePspFMatrix4 resultMatrix4, ScePspFMatrix4 aMatrix4, ScePspFMatrix4 bMatrix4);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     * @param left the left
     * @param right the right
     * @param bottom the bottom
     * @param top the top
     * @param near the near
     * @param far the far
     */
    @Import(name = "gumInit")
    public static native void gumOrtho(Address matrix4, float left, float right, float bottom, float top, float near, float far);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     * @param left the left
     * @param right the right
     * @param bottom the bottom
     * @param top the top
     * @param near the near
     * @param far the far
     */
    @Import(name = "gumInit")
    public static native void gumOrtho(ScePspFMatrix4 matrix4, float left, float right, float bottom, float top, float near, float far);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     * @param fovy the fovy
     * @param aspect the aspect
     * @param near the near
     * @param far the far
     */
    @Import(name = "gumInit")
    public static native void gumPerspective(Address matrix4, float fovy, float aspect, float near, float far);

    /**
     * Calls the gum init native function.
     *
     * @param matrix4 the matrix4
     * @param fovy the fovy
     * @param aspect the aspect
     * @param near the near
     * @param far the far
     */
    @Import(name = "gumInit")
    public static native void gumPerspective(ScePspFMatrix4 matrix4, float fovy, float aspect, float near, float far);

    /**
     * Calls the gum rotate x native function.
     *
     * @param matrix4 the matrix4
     * @param angle the angle
     */
    @Import(name = "gumRotateX")
    public static native void gumRotateX(Address matrix4, float angle);

    /**
     * Calls the gum rotate x native function.
     *
     * @param matrix4 the matrix4
     * @param angle the angle
     */
    @Import(name = "gumRotateX")
    public static native void gumRotateX(ScePspFMatrix4 matrix4, float angle);

    /**
     * Calls the gum rotate XYZ native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumRotateXYZ")
    public static native void gumRotateXYZ(Address matrix4, Address vector3);

    /**
     * Calls the gum rotate XYZ native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumRotateXYZ")
    public static native void gumRotateXYZ(ScePspFMatrix4 matrix4, ScePspFVector3 vector3);

    /**
     * Calls the gum rotate y native function.
     *
     * @param matrix4 the matrix4
     * @param angle the angle
     */
    @Import(name = "gumRotateY")
    public static native void gumRotateY(Address matrix4, float angle);

    /**
     * Calls the gum rotate y native function.
     *
     * @param matrix4 the matrix4
     * @param angle the angle
     */
    @Import(name = "gumRotateY")
    public static native void gumRotateY(ScePspFMatrix4 matrix4, float angle);

    /**
     * Calls the gum rotate z native function.
     *
     * @param matrix4 the matrix4
     * @param angle the angle
     */
    @Import(name = "gumRotateZ")
    public static native void gumRotateZ(Address matrix4, float angle);

    /**
     * Calls the gum rotate z native function.
     *
     * @param matrix4 the matrix4
     * @param angle the angle
     */
    @Import(name = "gumRotateZ")
    public static native void gumRotateZ(ScePspFMatrix4 matrix4, float angle);

    /**
     * Calls the gum rotate ZYX native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumRotateZYX")
    public static native void gumRotateZYX(Address matrix4, Address vector3);

    /**
     * Calls the gum rotate ZYX native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumRotateZYX")
    public static native void gumRotateZYX(ScePspFMatrix4 matrix4, ScePspFVector3 vector3);

    /**
     * Calls the gum rotate matrix native function.
     *
     * @param matrix4 the matrix4
     * @param quaternion the quaternion
     */
    @Import(name = "gumRotateMatrix")
    public static native void gumRotateMatrix(Address matrix4, Address quaternion);

    /**
     * Calls the gum rotate matrix native function.
     *
     * @param matrix4 the matrix4
     * @param quaternion the quaternion
     */
    @Import(name = "gumRotateMatrix")
    public static native void gumRotateMatrix(ScePspFMatrix4 matrix4, ScePspFQuaternion quaternion);

    /**
     * Calls the gum scale native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumScale")
    public static native void gumScale(Address matrix4, Address vector3);

    /**
     * Calls the gum scale native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumScale")
    public static native void gumScale(ScePspFMatrix4 matrix4, ScePspFVector3 vector3);

    /**
     * Calls the gum translate native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumTranslate")
    public static native void gumTranslate(Address matrix4, Address vector3);

    /**
     * Calls the gum translate native function.
     *
     * @param matrix4 the matrix4
     * @param vector3 the vector3
     */
    @Import(name = "gumTranslate")
    public static native void gumTranslate(ScePspFMatrix4 matrix4, ScePspFVector3 vector3);

    /**
     * Calls the gum full inverse native function.
     *
     * @param rMatrix4 the r matrix4
     * @param aMatrix4 the a matrix4
     */
    @Import(name = "gumFullInverse")
    public static native void gumFullInverse(Address rMatrix4, Address aMatrix4);

    /**
     * Calls the gum full inverse native function.
     *
     * @param rMatrix4 the r matrix4
     * @param aMatrix4 the a matrix4
     */
    @Import(name = "gumFullInverse")
    public static native void gumFullInverse(ScePspFMatrix4 rMatrix4, ScePspFMatrix4 aMatrix4);

    /**
     * Calls the gum fast inverse native function.
     *
     * @param rMatrix4 the r matrix4
     * @param aMatrix4 the a matrix4
     */
    @Import(name = "gumFastInverse")
    public static native void gumFastInverse(Address rMatrix4, Address aMatrix4);

    /**
     * Calls the gum fast inverse native function.
     *
     * @param rMatrix4 the r matrix4
     * @param aMatrix4 the a matrix4
     */
    @Import(name = "gumFastInverse")
    public static native void gumFastInverse(ScePspFMatrix4 rMatrix4, ScePspFMatrix4 aMatrix4);

    /**
     * Calls the gum cross product native function.
     *
     * @param rVector3 the r vector3
     * @param aVector3 the a vector3
     * @param bVector3 the b vector3
     */
    @Import(name = "gumCrossProduct")
    public static native void gumCrossProduct(Address rVector3, Address aVector3, Address bVector3);

    /**
     * Calls the gum cross product native function.
     *
     * @param rVector3 the r vector3
     * @param aVector3 the a vector3
     * @param bVector3 the b vector3
     */
    @Import(name = "gumCrossProduct")
    public static native void gumCrossProduct(ScePspFVector3 rVector3, ScePspFVector3 aVector3, ScePspFVector3 bVector3);

    /**
     * Calls the gum dot product native function.
     *
     * @param aVector3 the a vector3
     * @param bVector3 the b vector3
     * @return the gum dot product
     */
    @Import(name = "gumDotProduct")
    public static native float gumDotProduct(Address aVector3, Address bVector3);

    /**
     * Calls the gum dot product native function.
     *
     * @param aVector3 the a vector3
     * @param bVector3 the b vector3
     * @return the gum dot product
     */
    @Import(name = "gumDotProduct")
    public static native float gumDotProduct(ScePspFVector3 aVector3, ScePspFVector3 bVector3);

    /**
     * Calls the gum normalize native function.
     *
     * @param vVector3 the v vector3
     */
    @Import(name = "gumNormalize")
    public static native void gumNormalize(Address vVector3);

    /**
     * Calls the gum normalize native function.
     *
     * @param vVector3 the v vector3
     */
    @Import(name = "gumNormalize")
    public static native void gumNormalize(ScePspFVector3 vVector3);

    /**
     * Calls the gum rotate vector native function.
     *
     * @param rVector3 the r vector3
     * @param quaternion the quaternion
     * @param vector3 the vector3
     */
    @Import(name = "gumRotateVector")
    public static native void gumRotateVector(Address rVector3, Address quaternion, Address vector3);

    /**
     * Calls the gum rotate vector native function.
     *
     * @param rVector3 the r vector3
     * @param quaternion the quaternion
     * @param vector3 the vector3
     */
    @Import(name = "gumRotateVector")
    public static native void gumRotateVector(ScePspFVector3 rVector3, ScePspFQuaternion quaternion, ScePspFVector3 vector3);

    /**
     * Calls the gum normalize quaternion native function.
     *
     * @param quaternion the quaternion
     */
    @Import(name = "gumNormalizeQuaternion")
    public static native void gumNormalizeQuaternion(Address quaternion);

    /**
     * Calls the gum normalize quaternion native function.
     *
     * @param quaternion the quaternion
     */
    @Import(name = "gumNormalizeQuaternion")
    public static native void gumNormalizeQuaternion(ScePspFQuaternion quaternion);

    /**
     * Calls the gum load axis angle native function.
     *
     * @param rQuaternion the r quaternion
     * @param axisVector3 the axis vector3
     * @param t the t
     */
    @Import(name = "gumLoadAxisAngle")
    public static native void gumLoadAxisAngle(Address rQuaternion, Address axisVector3, float t);

    /**
     * Calls the gum load axis angle native function.
     *
     * @param rQuaternion the r quaternion
     * @param axisVector3 the axis vector3
     * @param t the t
     */
    @Import(name = "gumLoadAxisAngle")
    public static native void gumLoadAxisAngle(ScePspFQuaternion rQuaternion, ScePspFVector3 axisVector3, float t);

    /**
     * Calls the gum mult quaternion native function.
     *
     * @param resultQuaternion the result quaternion
     * @param aQuaternion the a quaternion
     * @param bQuaternion the b quaternion
     */
    @Import(name = "gumMultQuaternion")
    public static native void gumMultQuaternion(Address resultQuaternion, Address aQuaternion, Address bQuaternion);

    /**
     * Calls the gum mult quaternion native function.
     *
     * @param resultQuaternion the result quaternion
     * @param aQuaternion the a quaternion
     * @param bQuaternion the b quaternion
     */
    @Import(name = "gumMultQuaternion")
    public static native void gumMultQuaternion(ScePspFQuaternion resultQuaternion, ScePspFQuaternion aQuaternion, ScePspFQuaternion bQuaternion);

    // CUSTOM METHODS

    /**
     * Calls the init graphics native function.
     */
    @Import(name = "initGraphics")
    public static native void initGraphics();

    /**
     * Calls the begin frame native function.
     *
     * @param dialog the dialog
     */
    @Import(name = "beginFrame")
    public static native void beginFrame(int dialog);

    /**
     * Calls the end frame native function.
     *
     * @param vsync the vsync
     * @param dialog the dialog
     */
    @Import(name = "endFrame")
    public static native void endFrame(int vsync, int dialog);

    /**
     * Calls the libfdx PSP dcache writeback invalidate native function.
     *
     * @param data the data
     * @param size the size
     */
    @Import(name = "libfdx_psp_dcache_writeback_invalidate")
    public static native void dcacheWritebackInvalidate(ByteBuffer data, int size);

    /**
     * Calls the libfdx PSP dcache writeback invalidate native function.
     *
     * @param data the data
     * @param size the size
     */
    @Import(name = "libfdx_psp_dcache_writeback_invalidate")
    public static native void dcacheWritebackInvalidate(Address data, int size);

    /**
     * Calls the libfdx PSP copy texture data native function.
     *
     * @param target the target value
     * @param source the source value
     * @param size the size
     */
    @Import(name = "libfdx_psp_copy_texture_data")
    public static native void copyTextureData(Address target, ByteBuffer source, int size);
}
