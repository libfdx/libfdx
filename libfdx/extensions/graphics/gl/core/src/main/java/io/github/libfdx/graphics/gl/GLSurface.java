package io.github.libfdx.graphics.gl;

public interface GLSurface {
    void makeCurrent();

    void swapBuffers();

    void releaseCurrent();
}
