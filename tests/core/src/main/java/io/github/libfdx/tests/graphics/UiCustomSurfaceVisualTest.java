package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiCustomContent;
import io.github.libfdx.ui.UiCustomContext;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiDrawFunction;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiPath;
import io.github.libfdx.ui.UiPointerEvent;
import io.github.libfdx.ui.UiPointerPhase;
import io.github.libfdx.ui.UiPointerResult;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiSurfaceInput;
import io.github.libfdx.ui.UiToolkit;

/**
 * Renders the generic custom-surface, clipping, line, and path primitives
 * through the active graphics provider.
 */
public final class UiCustomSurfaceVisualTest extends GraphicsParityTest
        implements UiCustomContent, UiDrawFunction, UiSurfaceInput {
    private static final UiModifier ROOT = UiModifier.none().fill().padding(24.0f).gap(12.0f);
    private static final UiModifier CANVAS = UiModifier.none().fillWidth().height(430.0f)
            .focusable(true).clip();
    private static final UiColor CANVAS_COLOR = UiColor.rgba8888(0x111827ff);
    private static final UiColor GRID_COLOR = UiColor.rgba8888(0x263449ff);
    private static final UiColor NODE_COLOR = UiColor.rgba8888(0x25324aff);
    private static final UiColor NODE_ACCENT = UiColor.rgba8888(0x60a5faff);
    private static final UiColor NODE_OUTPUT = UiColor.rgba8888(0x34d399ff);
    private static final UiColor NODE_INPUT = UiColor.rgba8888(0xfbbf24ff);
    private static final UiColor CONNECTION = UiColor.rgba8888(0x7dd3fcff);
    private static final UiColor ACTIVE_CONNECTION = UiColor.rgba8888(0xf472b6ff);
    private final UiPath path = new UiPath(8, 32);
    private final UiPath secondPath = new UiPath(8, 32);
    private UiRoot root;
    private float pointerX = -1.0f;
    private float pointerY = -1.0f;

    /**
     * Creates the provider-rendered custom-surface test.
     *
     * @param exitAfterFrames the number of frames before exit
     */
    public UiCustomSurfaceVisualTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "UiCustomSurfaceVisualTest");
        root = new UiToolkit(fdx.files()).root(display, graphics).input(fdx.input());
        root.setContent(this::buildUi);
        markCreated();
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        graphics.clear(0.035f, 0.045f, 0.065f, 1.0f);
        root.update(application.deltaTime());
        root.render();
        finishFrame();
    }

    @Override
    public void dispose() {
        dispose(root);
        root = null;
        verifyDisposed();
    }

    private void buildUi(UiScope ui) {
        ui.column(ROOT, column -> {
            column.text("UI Kit custom surface", UiModifier.none().height(34.0f));
            column.text("Provider-rendered clipping, retained paths, and interactive pointer capture",
                    UiModifier.none().height(28.0f));
            column.custom("phase-11-canvas", CANVAS, this);
            column.text("The grid and curves intentionally extend past the canvas and are clipped.",
                    UiModifier.none().height(26.0f));
        });
    }

    @Override
    public void build(UiCustomContext context) {
        context.draw(this);
        context.input(this);
    }

    @Override
    public void draw(UiDrawContext draw, UiRect bounds) {
        draw.rect(bounds, CANVAS_COLOR);
        for (float x = bounds.x() - 40.0f; x <= bounds.right() + 40.0f; x += 24.0f) {
            draw.line(x, bounds.y() - 40.0f, x, bounds.bottom() + 40.0f, 1.0f, GRID_COLOR);
        }
        for (float y = bounds.y() - 40.0f; y <= bounds.bottom() + 40.0f; y += 24.0f) {
            draw.line(bounds.x() - 40.0f, y, bounds.right() + 40.0f, y, 1.0f, GRID_COLOR);
        }

        float nodeY = bounds.y() + 105.0f;
        float firstX = bounds.x() + 95.0f;
        float secondX = bounds.x() + bounds.width() * 0.5f - 80.0f;
        float thirdX = bounds.right() - 255.0f;
        drawNode(draw, firstX, nodeY, 160.0f, 120.0f, NODE_INPUT);
        drawNode(draw, secondX, nodeY + 115.0f, 160.0f, 120.0f, NODE_ACCENT);
        drawNode(draw, thirdX, nodeY + 20.0f, 160.0f, 120.0f, NODE_OUTPUT);

        path.clear()
                .moveTo(firstX + 160.0f, nodeY + 60.0f)
                .cubicTo(firstX + 245.0f, nodeY + 60.0f,
                        secondX - 85.0f, nodeY + 175.0f,
                        secondX, nodeY + 175.0f);
        draw.path(path, 3.0f, CONNECTION);

        secondPath.clear()
                .moveTo(secondX + 160.0f, nodeY + 175.0f)
                .cubicTo(secondX + 255.0f, nodeY + 175.0f,
                        thirdX - 95.0f, nodeY + 80.0f,
                        thirdX, nodeY + 80.0f);
        draw.path(secondPath, 4.0f, ACTIVE_CONNECTION);

        draw.line(bounds.x() - 80.0f, bounds.y() + 36.0f,
                bounds.right() + 80.0f, bounds.y() + 36.0f, 2.0f, NODE_ACCENT);
        if (pointerX >= 0.0f) {
            draw.line(pointerX - 8.0f, pointerY, pointerX + 8.0f, pointerY, 2.0f, NODE_OUTPUT);
            draw.line(pointerX, pointerY - 8.0f, pointerX, pointerY + 8.0f, 2.0f, NODE_OUTPUT);
        }
    }

    private void drawNode(UiDrawContext draw, float x, float y, float width, float height, UiColor accent) {
        draw.rect(x, y, width, height, NODE_COLOR);
        draw.rect(x, y, width, 5.0f, accent);
        draw.rect(x + 14.0f, y + 28.0f, width - 28.0f, 8.0f, accent);
        draw.rect(x + 14.0f, y + 52.0f, width - 48.0f, 6.0f, NODE_ACCENT);
        draw.rect(x + 14.0f, y + 74.0f, width - 64.0f, 6.0f, NODE_ACCENT);
    }

    @Override
    public UiPointerResult pointer(UiPointerEvent event) {
        pointerX = event.x();
        pointerY = event.y();
        if (event.phase() == UiPointerPhase.DOWN) {
            return UiPointerResult.CAPTURE;
        }
        if (event.phase() == UiPointerPhase.UP || event.phase() == UiPointerPhase.CANCEL) {
            return UiPointerResult.RELEASE;
        }
        return UiPointerResult.HANDLED;
    }
}
