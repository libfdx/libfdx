package io.github.libfdx.backend.android;

import android.opengl.GLES30;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.gl.GLProgramBinary;
import io.github.libfdx.graphics.gl.GLShaderType;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/** Explicit native binding/capability probe; all driver work occurs during startup. */
public final class AndroidGlesProgramBinaryTest extends ApplicationAdapter {
    private Fdx fdx;

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        AndroidGlesApi gl = new AndroidGlesApi(1);
        System.out.println("[info] GLES_PROGRAM_BINARY_DRIVER vendor=" + GLES30.glGetString(GLES30.GL_VENDOR)
                + " renderer=" + GLES30.glGetString(GLES30.GL_RENDERER)
                + " version=" + GLES30.glGetString(GLES30.GL_VERSION));
        int[] count = new int[1];
        GLES30.glGetIntegerv(GLES30.GL_NUM_PROGRAM_BINARY_FORMATS, count, 0);
        String identity = gl.programBinaryIdentity();
        require(GLES30.glGetError() == GLES30.GL_NO_ERROR, "Binary capability query failed");
        System.out.println("[info] GLES_PROGRAM_BINARY formats=" + count[0] + " identity=" + identity
                + " capabilities=" + fdx.graphics().main().device().shaderPreparationCapabilities());
        if (count[0] == 0) {
            require(identity == null, "Unsupported native binary capability was advertised");
            require(!fdx.graphics().main().device().shaderPreparationCapabilities().pipelineCache(), "Unsupported program cache advertised");
            System.out.println("[info] GLES_PROGRAM_BINARY_PASS unsupported_zero_formats=true");
            return;
        }
        int vertex = gl.createShader(GLShaderType.VERTEX), fragment = gl.createShader(GLShaderType.FRAGMENT);
        int program = gl.createProgram(), restored = gl.createProgram();
        try {
            gl.shaderSource(vertex, "#version 300 es\nvoid main() { gl_Position = vec4(0.0, 0.0, 0.0, 1.0); }");
            gl.shaderSource(fragment, "#version 300 es\nprecision highp float; out vec4 color; void main() { color = vec4(1.0); }");
            gl.compileShader(vertex); gl.compileShader(fragment);
            gl.attachShader(program, vertex); gl.attachShader(program, fragment);
            gl.hintProgramBinaryRetrievable(program); gl.linkProgram(program);
            require(gl.programLinkStatus(program), gl.programInfoLog(program));
            GLProgramBinary binary = gl.exportProgramBinary(program, 1024 * 1024);
            require(binary != null, "Driver advertised binaries but export failed");
            boolean restoredBinary = gl.restoreProgramBinary(restored, binary);
            boolean linked = gl.programLinkStatus(restored);
            System.out.println("[info] GLES_PROGRAM_BINARY_RESTORE bytes=" + binary.bytes().length + " format=" + binary.format()
                    + " accepted=" + restoredBinary + " linked=" + linked + " log=" + gl.programInfoLog(restored));
            if (!restoredBinary || !linked) {
                // Independent SDK call control: retain the native output buffer unchanged between calls.
                int[] size = new int[1];
                GLES30.glGetProgramiv(program, GLES30.GL_PROGRAM_BINARY_LENGTH, size, 0);
                ByteBuffer nativeBytes = ByteBuffer.allocateDirect(size[0]).order(ByteOrder.nativeOrder());
                IntBuffer length = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
                IntBuffer format = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
                GLES30.glGetProgramBinary(program, size[0], length, format, nativeBytes);
                int exportError = GLES30.glGetError();
                int control = GLES30.glCreateProgram();
                try {
                    GLES30.glProgramBinary(control, format.get(0), nativeBytes, length.get(0));
                    int importError = GLES30.glGetError();
                    GLES30.glGetProgramiv(control, GLES30.GL_LINK_STATUS, size, 0);
                    System.out.println("[info] GLES_PROGRAM_BINARY_SDK_CONTROL bytes=" + length.get(0) + " format=" + format.get(0)
                            + " position=" + nativeBytes.position() + " export_error=" + exportError + " import_error=" + importError
                            + " linked=" + size[0] + " log=" + GLES30.glGetProgramInfoLog(control));
                } finally { GLES30.glDeleteProgram(control); }
            }
            require(restoredBinary && linked, "Native restore failed");
            require(!gl.restoreProgramBinary(restored, new GLProgramBinary(-1, binary.bytes())), "Invalid format accepted");
            System.out.println("[info] GLES_PROGRAM_BINARY_PASS bytes=" + binary.bytes().length + " format=" + binary.format());
        } finally {
            gl.deleteProgram(restored); gl.deleteProgram(program); gl.deleteShader(vertex); gl.deleteShader(fragment);
        }
    }
    @Override
    public void render() { fdx.app().requestExit(); }
    private static void require(boolean condition, String message) { if (!condition) throw new FdxException(message); }
}
