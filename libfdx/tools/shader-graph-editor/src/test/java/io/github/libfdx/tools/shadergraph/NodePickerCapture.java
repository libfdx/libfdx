package io.github.libfdx.tools.shadergraph;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.backend.desktop.DesktopApplicationBackend;
import io.github.libfdx.backend.desktop.DesktopApplicationConfig;
import io.github.libfdx.backend.desktop.DesktopOpenGLProvider;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.MouseButton;
import java.nio.file.Path;
import java.util.List;

/** Exercises the editor host and captures the result of a real pointer and text input sequence. */
public final class NodePickerCapture extends ApplicationAdapter {
    private final ShaderGraphEditorApplication editor = new ShaderGraphEditorApplication(
            List.of(new ShaderGraphDesktopEditor.Effect("Dissolve and rim", "shaders/surface-effects.fdxgraph")), 0, 0);
    private final Path destination;
    private Fdx fdx;
    private int frame;

    private NodePickerCapture(Path destination) {
        this.destination = destination;
    }

    public static void main(String[] args) {
        var config = new DesktopApplicationConfig().size(1280, 720).visible(false).vSync(false)
                .graphics(new DesktopOpenGLProvider());
        new DesktopApplicationBackend().start(config, new NodePickerCapture(Path.of(args[0])));
    }

    @Override
    public void create(Fdx fdx) {
        this.fdx = fdx;
        editor.create(fdx);
    }

    @Override
    public void resize(int width, int height) {
        editor.resize(width, height);
    }

    @Override
    public void render() {
        DefaultInput input = (DefaultInput) fdx.input();
        if (frame == 3) {
            var display = fdx.displays().main();
            int x = display.width() / 3;
            int y = display.height() * 3 / 4;
            input.dispatchPointerDown(MouseButton.RIGHT, x, y);
            input.dispatchPointerUp(MouseButton.RIGHT, x, y);
        }
        if (frame == 4 && !input.dispatchTextInput("noise")) {
            throw new AssertionError("The editor's node search did not receive typed input");
        }
        editor.render();
    }

    @Override
    public void onFrameEnd() {
        if (++frame == 7) {
            ShaderGraphDesktopEditor.writeCapture(destination, fdx.graphics().main().currentFrame().frameBuffer());
            fdx.app().requestExit();
        }
    }

    @Override
    public void dispose() {
        editor.dispose();
    }

}
