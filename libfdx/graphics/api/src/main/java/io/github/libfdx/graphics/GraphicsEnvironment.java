package io.github.libfdx.graphics;

import io.github.libfdx.display.Display;

public interface GraphicsEnvironment {
    Display display();

    NativeWindow nativeWindow();
}
