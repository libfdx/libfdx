package io.github.libfdx.tests.utils.uikittest.validation;

import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.clamp;
import static io.github.libfdx.tests.utils.uikittest.validation.UiKitTestInput.find;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcase;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;

/** Checks floating window movement, resize bounds, and stacking. */
final class UiKitWindowChecks {
    private final UiKitShowcase showcase;
    private final UiRoot root;
    private final DefaultInput input;
    private final UiKitTestInput interaction;

    private boolean windowMoveChecked;
    private boolean windowResizeChecked;
    private boolean edgeMoveChecked;
    private boolean edgeResizeChecked;

    UiKitWindowChecks(UiKitShowcase showcase, UiRoot root, DefaultInput input, UiKitTestInput interaction) {
        this.showcase = showcase;
        this.root = root;
        this.input = input;
        this.interaction = interaction;
    }

    void verifyCoverage(long renderedFrames) {
        if (!windowMoveChecked) {
            throw new FdxException("UiKitTest movable window did not move after title-bar drag");
        }
        if (!windowResizeChecked) {
            throw new FdxException("UiKitTest resizable window did not resize from the corner handle");
        }
        if (showcase.windows().statsWindow.zOrder() <= showcase.windows().toolsWindow.zOrder()) {
            throw new FdxException("UiKitTest resizable window did not render above the overlapped tools window");
        }
        if (!edgeMoveChecked) {
            throw new FdxException("UiKitTest did not run the edge-bounded window move check after "
                    + renderedFrames + " rendered frames; tools=" + showcase.windows().toolsWindow.x() + "," + showcase.windows().toolsWindow.y()
                    + " " + showcase.windows().toolsWindow.width() + "x" + showcase.windows().toolsWindow.height());
        }
        if (!edgeResizeChecked) {
            throw new FdxException("UiKitTest did not run the edge-bounded window resize check after "
                    + renderedFrames + " rendered frames; stats=" + showcase.windows().statsWindow.x() + "," + showcase.windows().statsWindow.y()
                    + " " + showcase.windows().statsWindow.width() + "x" + showcase.windows().statsWindow.height());
        }
    }

    void validateWindowDrag(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the movable window");
        }
        float beforeX = showcase.windows().toolsWindow.x();
        float beforeY = showcase.windows().toolsWindow.y();
        UiRect area = root.rootNode().bounds();
        float expectedX = clamp(beforeX + deltaX, area.x(), Math.max(area.x(), area.right() - showcase.windows().toolsWindow.width()));
        float expectedY = clamp(beforeY + deltaY, area.y(), Math.max(area.y(), area.bottom() - showcase.windows().toolsWindow.height()));
        dragWindow(window, deltaX, deltaY);
        if (Math.abs(showcase.windows().toolsWindow.x() - expectedX) > 0.5f || Math.abs(showcase.windows().toolsWindow.y() - expectedY) > 0.5f) {
            throw new FdxException("UiKitTest movable window did not clamp to the expected drag position: actual="
                    + showcase.windows().toolsWindow.x() + "," + showcase.windows().toolsWindow.y() + ", expected=" + expectedX + "," + expectedY);
        }
        if (Math.abs(showcase.windows().toolsWindow.x() - beforeX) <= 0.5f && Math.abs(showcase.windows().toolsWindow.y() - beforeY) <= 0.5f) {
            throw new FdxException("UiKitTest movable window did not move after title-bar drag");
        }
        windowMoveChecked = true;
    }

    void validateWindowResize(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the resizable window");
        }
        float beforeWidth = showcase.windows().statsWindow.width();
        float beforeHeight = showcase.windows().statsWindow.height();
        UiRect area = root.rootNode().bounds();
        float expectedWidth = clamp(beforeWidth + deltaX, showcase.windows().statsWindow.minWidth(),
                Math.max(showcase.windows().statsWindow.minWidth(), area.right() - showcase.windows().statsWindow.x()));
        float expectedHeight = clamp(beforeHeight + deltaY, showcase.windows().statsWindow.minHeight(),
                Math.max(showcase.windows().statsWindow.minHeight(), area.bottom() - showcase.windows().statsWindow.y()));
        resizeWindow(window, deltaX, deltaY);
        if (Math.abs(showcase.windows().statsWindow.width() - expectedWidth) > 0.5f
                || Math.abs(showcase.windows().statsWindow.height() - expectedHeight) > 0.5f) {
            throw new FdxException("UiKitTest resizable window did not clamp to the expected size: actual="
                    + showcase.windows().statsWindow.width() + "x" + showcase.windows().statsWindow.height()
                    + ", expected=" + expectedWidth + "x" + expectedHeight);
        }
        if (Math.abs(showcase.windows().statsWindow.width() - beforeWidth) <= 0.5f
                && Math.abs(showcase.windows().statsWindow.height() - beforeHeight) <= 0.5f) {
            throw new FdxException("UiKitTest resizable window did not resize from the corner handle");
        }
        windowResizeChecked = true;
    }

    void dragWindowPastEdge(UiNode window) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the movable window for the edge move check");
        }
        edgeMoveChecked = true;
        UiRect area = root.rootNode().bounds();
        float maxX = Math.max(area.x(), area.right() - showcase.windows().toolsWindow.width());
        float maxY = Math.max(area.y(), area.bottom() - showcase.windows().toolsWindow.height());
        dragWindow(window, 2000.0f, 2000.0f);
        if (showcase.windows().toolsWindow.x() > maxX + 0.5f || showcase.windows().toolsWindow.y() > maxY + 0.5f) {
            throw new FdxException("UiKitTest edge move grew beyond the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + showcase.windows().toolsWindow.x() + "," + showcase.windows().toolsWindow.y() + " "
                    + showcase.windows().toolsWindow.width() + "x" + showcase.windows().toolsWindow.height()
                    + ", max=" + maxX + "," + maxY);
        }
        if (showcase.windows().toolsWindow.x() < maxX - 1.0f || showcase.windows().toolsWindow.y() < maxY - 1.0f) {
            throw new FdxException("UiKitTest edge move did not reach the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + showcase.windows().toolsWindow.x() + "," + showcase.windows().toolsWindow.y() + " "
                    + showcase.windows().toolsWindow.width() + "x" + showcase.windows().toolsWindow.height()
                    + ", max=" + maxX + "," + maxY);
        }
    }

    void moveWindowToLayoutOrigin(UiNode window) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the movable window for the resize setup");
        }
        UiRect area = root.rootNode().bounds();
        dragWindow(window, area.x() - showcase.windows().toolsWindow.x(), area.y() - showcase.windows().toolsWindow.y());
        if (Math.abs(showcase.windows().toolsWindow.x() - area.x()) > 0.5f || Math.abs(showcase.windows().toolsWindow.y() - area.y()) > 0.5f) {
            throw new FdxException("UiKitTest could not move the front window away from the resize target: actual="
                    + showcase.windows().toolsWindow.x() + "," + showcase.windows().toolsWindow.y() + ", expected=" + area.x() + "," + area.y());
        }
    }

    void resizeWindowPastEdge(UiNode window) {
        if (window == null) {
            throw new FdxException("UiKitTest could not find the resizable window for the edge resize check");
        }
        edgeResizeChecked = true;
        float startX = showcase.windows().statsWindow.x();
        float startY = showcase.windows().statsWindow.y();
        UiRect area = root.rootNode().bounds();
        float maxWidth = Math.max(showcase.windows().statsWindow.minWidth(), area.right() - startX);
        float maxHeight = Math.max(showcase.windows().statsWindow.minHeight(), area.bottom() - startY);
        resizeWindow(window, 2000.0f, 2000.0f);
        if (Math.abs(showcase.windows().statsWindow.x() - startX) > 0.5f || Math.abs(showcase.windows().statsWindow.y() - startY) > 0.5f) {
            throw new FdxException("UiKitTest edge resize moved the anchored window origin");
        }
        if (showcase.windows().statsWindow.width() > maxWidth + 0.5f || showcase.windows().statsWindow.height() > maxHeight + 0.5f) {
            throw new FdxException("UiKitTest edge resize grew beyond the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + showcase.windows().statsWindow.x() + "," + showcase.windows().statsWindow.y() + " "
                    + showcase.windows().statsWindow.width() + "x" + showcase.windows().statsWindow.height()
                    + ", max=" + maxWidth + "x" + maxHeight);
        }
        if (showcase.windows().statsWindow.width() < maxWidth - 1.0f || showcase.windows().statsWindow.height() < maxHeight - 1.0f) {
            throw new FdxException("UiKitTest edge resize did not reach the available layout area: area="
                    + area.x() + "," + area.y() + " " + area.width() + "x" + area.height()
                    + ", window=" + showcase.windows().statsWindow.x() + "," + showcase.windows().statsWindow.y() + " "
                    + showcase.windows().statsWindow.width() + "x" + showcase.windows().statsWindow.height()
                    + ", max=" + maxWidth + "x" + maxHeight);
        }
    }

    private void dragWindow(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            return;
        }
        UiRect bounds = window.bounds();
        float startX = bounds.x() + Math.min(92.0f, bounds.width() * 0.35f);
        float startY = bounds.y() + 14.0f;
        int downX = root.displayX(startX);
        int downY = root.displayY(startY);
        int moveX = root.displayX(startX + deltaX);
        int moveY = root.displayY(startY + deltaY);
        input.dispatchPointerMoved(downX, downY);
        input.dispatchPointerDown(MouseButton.LEFT, downX, downY);
        input.dispatchPointerMoved(moveX, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, moveX, moveY);
        interaction.settleValidationLayout();
    }

    private void resizeWindow(UiNode window, float deltaX, float deltaY) {
        if (window == null) {
            return;
        }
        UiRect bounds = window.bounds();
        float startX = bounds.right() - 6.0f;
        float startY = bounds.bottom() - 6.0f;
        int downX = root.displayX(startX);
        int downY = root.displayY(startY);
        int moveX = root.displayX(startX + deltaX);
        int moveY = root.displayY(startY + deltaY);
        input.dispatchPointerMoved(downX, downY);
        input.dispatchPointerDown(MouseButton.LEFT, downX, downY);
        input.dispatchPointerMoved(moveX, moveY);
        input.dispatchPointerUp(MouseButton.LEFT, moveX, moveY);
    }

}
