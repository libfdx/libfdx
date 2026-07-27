package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiCustomContent;
import io.github.libfdx.ui.UiCustomContext;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiDrawFunction;
import io.github.libfdx.ui.UiPath;
import io.github.libfdx.ui.UiPointerEvent;
import io.github.libfdx.ui.UiPointerPhase;
import io.github.libfdx.ui.UiPointerResult;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiSurfaceInput;
import io.github.libfdx.ui.UiTextStyle;

/**
 * Interactive retained shader-node canvas. It owns only transient interaction
 * and drawing caches; semantic and persisted layout data remain in the editor
 * session.
 */
public final class ShaderGraphEditorCanvas implements UiCustomContent,
        UiDrawFunction, UiSurfaceInput {
    private static final float GRID_SIZE = 32.0f;
    private static final float HEADER_HEIGHT = 30.0f;
    private static final float PORT_TOP = 48.0f;
    private static final float PORT_STEP = 18.0f;
    private static final float PORT_RADIUS = 6.0f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.5f;
    private static final UiColor BACKGROUND =
            UiColor.rgba8888(0x0b1220ff);
    private static final UiColor GRID = UiColor.rgba8888(0x1c2940ff);
    private static final UiColor NODE = UiColor.rgba8888(0x1f2c44ff);
    private static final UiColor NODE_HEADER =
            UiColor.rgba8888(0x334766ff);
    private static final UiColor NODE_SELECTED =
            UiColor.rgba8888(0x60a5faff);
    private static final UiColor INPUT = UiColor.rgba8888(0xfbbf24ff);
    private static final UiColor OUTPUT = UiColor.rgba8888(0x34d399ff);
    private static final UiColor CONNECTION =
            UiColor.rgba8888(0x7dd3fcff);
    private static final UiColor CONNECTING =
            UiColor.rgba8888(0xf472b6ff);
    private static final UiTextStyle TITLE = UiTextStyle.text()
            .size(13.0f).lineHeight(16.0f).ellipsis(true);
    private static final UiTextStyle DETAIL = UiTextStyle.text()
            .size(11.0f).lineHeight(14.0f)
            .color(UiColor.rgba8888(0xb8c5d9ff)).ellipsis(true);

    private final ShaderGraphEditorSession session;
    private final UiPath connectionPath = new UiPath(8, 32);
    private long cachedSemanticRevision = -1;
    private long cachedLayoutRevision = -1;
    private ShaderGraph graph;
    private ShaderGraphEditorData layout;
    private ShaderNode[] nodes = new ShaderNode[0];
    private ShaderGraphEditorNode[] nodeLayouts =
            new ShaderGraphEditorNode[0];
    private String dragNodeId = "";
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean panning;
    private float panPointerX;
    private float panPointerY;
    private float panStartX;
    private float panStartY;
    private ShaderEndpoint connectingSource;
    private float connectingX;
    private float connectingY;
    private String lastInteractionError = "";

    public ShaderGraphEditorCanvas(ShaderGraphEditorSession session) {
        if (session == null) {
            throw new FdxException(
                    "Shader graph editor canvas requires a session");
        }
        this.session = session;
    }

    public ShaderGraphEditorSession session() {
        return session;
    }

    public String lastInteractionError() {
        return lastInteractionError;
    }

    public float graphX(float localX) {
        refresh();
        return (localX - layout.panX()) / layout.zoom();
    }

    public float graphY(float localY) {
        refresh();
        return (localY - layout.panY()) / layout.zoom();
    }

    public float localX(float graphX) {
        refresh();
        return layout.panX() + graphX * layout.zoom();
    }

    public float localY(float graphY) {
        refresh();
        return layout.panY() + graphY * layout.zoom();
    }

    public String nodeAt(float localX, float localY) {
        refresh();
        ShaderGraphEditorNode hit = nodeLayoutAt(localX, localY);
        return hit != null ? hit.nodeId().value() : "";
    }

    @Override
    public void build(UiCustomContext context) {
        context.draw(this);
        context.input(this);
    }

    @Override
    public void draw(UiDrawContext draw, UiRect bounds) {
        refresh();
        draw.rect(bounds, BACKGROUND);
        drawGrid(draw, bounds);
        for (ShaderEdge edge : graph.edges()) {
            drawEdge(draw, bounds, edge, CONNECTION);
        }
        if (connectingSource != null) {
            PortPoint start = outputPoint(connectingSource);
            if (start != null) {
                drawConnection(draw, bounds.x() + start.x,
                        bounds.y() + start.y,
                        bounds.x() + connectingX,
                        bounds.y() + connectingY, CONNECTING);
            }
        }
        for (int i = 0; i < nodeLayouts.length; i++) {
            drawNode(draw, bounds, nodes[i], nodeLayouts[i]);
        }
    }

    @Override
    public UiPointerResult pointer(UiPointerEvent event) {
        refresh();
        lastInteractionError = "";
        if (event.phase() == UiPointerPhase.SCROLL) {
            zoom(event.localX(), event.localY(), event.scrollY());
            return UiPointerResult.HANDLED;
        }
        if (event.phase() == UiPointerPhase.DOWN) {
            PortHit port = portAt(event.localX(), event.localY(), false);
            if (port != null) {
                connectingSource = ShaderEndpoint.of(
                        port.node.nodeId(), port.port.id());
                connectingX = event.localX();
                connectingY = event.localY();
                return UiPointerResult.CAPTURE;
            }
            ShaderGraphEditorNode node =
                    nodeLayoutAt(event.localX(), event.localY());
            if (node != null) {
                session.selectNode(node.nodeId().value());
                dragNodeId = node.nodeId().value();
                dragOffsetX = graphX(event.localX()) - node.x();
                dragOffsetY = graphY(event.localY()) - node.y();
                session.beginTransaction("Move node");
                return UiPointerResult.CAPTURE;
            }
            session.selectNode("");
            panning = true;
            panPointerX = event.localX();
            panPointerY = event.localY();
            panStartX = layout.panX();
            panStartY = layout.panY();
            session.beginTransaction("Move viewport");
            return UiPointerResult.CAPTURE;
        }
        if (event.phase() == UiPointerPhase.MOVE) {
            if (connectingSource != null) {
                connectingX = event.localX();
                connectingY = event.localY();
                return UiPointerResult.HANDLED;
            }
            if (!dragNodeId.isEmpty()) {
                session.moveNode(dragNodeId,
                        graphX(event.localX()) - dragOffsetX,
                        graphY(event.localY()) - dragOffsetY);
                return UiPointerResult.HANDLED;
            }
            if (panning) {
                session.viewport(
                        panStartX + event.localX() - panPointerX,
                        panStartY + event.localY() - panPointerY,
                        layout.zoom());
                return UiPointerResult.HANDLED;
            }
        }
        if (event.phase() == UiPointerPhase.UP) {
            if (connectingSource != null) {
                PortHit target =
                        portAt(event.localX(), event.localY(), true);
                if (target != null) {
                    try {
                        session.connect(connectingSource,
                                ShaderEndpoint.of(target.node.nodeId(),
                                        target.port.id()));
                    } catch (RuntimeException failure) {
                        lastInteractionError = failure.getMessage() != null
                                ? failure.getMessage()
                                : failure.getClass().getSimpleName();
                    }
                }
                connectingSource = null;
                return UiPointerResult.RELEASE;
            }
            finishTransaction(true);
            return UiPointerResult.RELEASE;
        }
        if (event.phase() == UiPointerPhase.CANCEL) {
            connectingSource = null;
            finishTransaction(false);
            return UiPointerResult.RELEASE;
        }
        return UiPointerResult.IGNORED;
    }

    @Override
    public void focusChanged(boolean focused) {
        if (!focused) {
            connectingSource = null;
            finishTransaction(false);
        }
    }

    private void drawGrid(UiDrawContext draw, UiRect bounds) {
        float spacing = GRID_SIZE * layout.zoom();
        if (spacing < 8.0f) {
            spacing *= 4.0f;
        }
        float startX = positiveModulo(layout.panX(), spacing);
        float startY = positiveModulo(layout.panY(), spacing);
        for (float x = startX; x <= bounds.width(); x += spacing) {
            draw.line(bounds.x() + x, bounds.y(),
                    bounds.x() + x, bounds.bottom(), 1.0f, GRID);
        }
        for (float y = startY; y <= bounds.height(); y += spacing) {
            draw.line(bounds.x(), bounds.y() + y,
                    bounds.right(), bounds.y() + y, 1.0f, GRID);
        }
    }

    private void drawEdge(UiDrawContext draw, UiRect bounds,
            ShaderEdge edge, UiColor color) {
        PortPoint source = outputPoint(edge.source());
        PortPoint target = inputPoint(edge.target());
        if (source != null && target != null) {
            drawConnection(draw, bounds.x() + source.x,
                    bounds.y() + source.y,
                    bounds.x() + target.x,
                    bounds.y() + target.y, color);
        }
    }

    private void drawConnection(UiDrawContext draw, float x1, float y1,
            float x2, float y2, UiColor color) {
        float control = Math.max(36.0f, Math.abs(x2 - x1) * 0.45f);
        connectionPath.clear().moveTo(x1, y1)
                .cubicTo(x1 + control, y1, x2 - control, y2, x2, y2);
        draw.path(connectionPath, 2.5f, color);
    }

    private void drawNode(UiDrawContext draw, UiRect bounds,
            ShaderNode node, ShaderGraphEditorNode nodeLayout) {
        float x = bounds.x() + localX(nodeLayout.x());
        float y = bounds.y() + localY(nodeLayout.y());
        float width = nodeLayout.width() * layout.zoom();
        float height = nodeLayout.height() * layout.zoom();
        draw.rect(x, y, width, height, NODE);
        draw.rect(x, y, width, HEADER_HEIGHT * layout.zoom(),
                NODE_HEADER);
        if (session.selectedNodeId().equals(node.id().value())) {
            float border = Math.max(2.0f, 2.0f * layout.zoom());
            draw.rect(x, y, width, border, NODE_SELECTED);
            draw.rect(x, y + height - border, width, border,
                    NODE_SELECTED);
            draw.rect(x, y, border, height, NODE_SELECTED);
            draw.rect(x + width - border, y, border, height,
                    NODE_SELECTED);
        }
        draw.text(node.id().value(),
                new UiRect(x + 10.0f * layout.zoom(),
                        y + 4.0f * layout.zoom(),
                        Math.max(0.0f, width - 20.0f * layout.zoom()),
                        HEADER_HEIGHT * layout.zoom()),
                TITLE);
        draw.text(node.definitionId().value(),
                new UiRect(x + 10.0f * layout.zoom(),
                        y + 31.0f * layout.zoom(),
                        Math.max(0.0f, width - 20.0f * layout.zoom()),
                        18.0f * layout.zoom()),
                DETAIL);
        drawPorts(draw, x, y, width, node.inputs(), true);
        drawPorts(draw, x, y, width, node.outputs(), false);
    }

    private void drawPorts(UiDrawContext draw, float x, float y,
            float width, ShaderGraphPort[] ports, boolean input) {
        float size = PORT_RADIUS * 2.0f * layout.zoom();
        float centerX = input ? x : x + width;
        UiColor color = input ? INPUT : OUTPUT;
        for (int i = 0; i < ports.length; i++) {
            float centerY = y
                    + (PORT_TOP + i * PORT_STEP) * layout.zoom();
            draw.rect(centerX - size * 0.5f, centerY - size * 0.5f,
                    size, size, color);
        }
    }

    private void refresh() {
        if (graph != null
                && cachedSemanticRevision == session.semanticRevision()
                && cachedLayoutRevision == session.layoutRevision()
                && graph.id().value().equals(
                        session.layout().activeGraphId())) {
            return;
        }
        graph = session.activeGraph();
        layout = session.layout().activeGraph();
        ShaderNode[] semanticNodes = graph.nodes();
        ShaderGraphEditorNode[] layouts = layout.nodes();
        nodes = new ShaderNode[layouts.length];
        nodeLayouts = new ShaderGraphEditorNode[layouts.length];
        int count = 0;
        for (ShaderGraphEditorNode item : layouts) {
            ShaderNode node = graph.node(item.nodeId());
            if (node != null) {
                nodes[count] = node;
                nodeLayouts[count] = item;
                count++;
            }
        }
        if (count != nodes.length) {
            nodes = java.util.Arrays.copyOf(nodes, count);
            nodeLayouts = java.util.Arrays.copyOf(nodeLayouts, count);
        }
        cachedSemanticRevision = session.semanticRevision();
        cachedLayoutRevision = session.layoutRevision();
    }

    private ShaderGraphEditorNode nodeLayoutAt(float x, float y) {
        for (int i = nodeLayouts.length - 1; i >= 0; i--) {
            ShaderGraphEditorNode node = nodeLayouts[i];
            float localX = localX(node.x());
            float localY = localY(node.y());
            if (x >= localX && x <= localX + node.width() * layout.zoom()
                    && y >= localY
                    && y <= localY + node.height() * layout.zoom()) {
                return node;
            }
        }
        return null;
    }

    private PortHit portAt(float x, float y, boolean input) {
        float radius = Math.max(8.0f, PORT_RADIUS * layout.zoom() + 3.0f);
        for (int nodeIndex = nodeLayouts.length - 1;
                nodeIndex >= 0; nodeIndex--) {
            ShaderGraphEditorNode node = nodeLayouts[nodeIndex];
            ShaderGraphPort[] ports = input
                    ? nodes[nodeIndex].inputs() : nodes[nodeIndex].outputs();
            float portX = localX(node.x()
                    + (input ? 0.0f : node.width()));
            for (int portIndex = 0; portIndex < ports.length;
                    portIndex++) {
                float portY = localY(node.y() + PORT_TOP
                        + portIndex * PORT_STEP);
                if (Math.abs(x - portX) <= radius
                        && Math.abs(y - portY) <= radius) {
                    return new PortHit(node, ports[portIndex]);
                }
            }
        }
        return null;
    }

    private PortPoint outputPoint(ShaderEndpoint endpoint) {
        return portPoint(endpoint, false);
    }

    private PortPoint inputPoint(ShaderEndpoint endpoint) {
        return portPoint(endpoint, true);
    }

    private PortPoint portPoint(ShaderEndpoint endpoint, boolean input) {
        for (int i = 0; i < nodeLayouts.length; i++) {
            if (!nodeLayouts[i].nodeId().equals(endpoint.nodeId())) {
                continue;
            }
            ShaderGraphPort[] ports = input
                    ? nodes[i].inputs() : nodes[i].outputs();
            for (int port = 0; port < ports.length; port++) {
                if (ports[port].id().equals(endpoint.portId())) {
                    return new PortPoint(localX(nodeLayouts[i].x()
                            + (input ? 0.0f
                                    : nodeLayouts[i].width())),
                            localY(nodeLayouts[i].y() + PORT_TOP
                                    + port * PORT_STEP));
                }
            }
        }
        return null;
    }

    private void zoom(float pointerX, float pointerY, float amount) {
        float oldZoom = layout.zoom();
        float factor = amount > 0.0f ? 0.9f : 1.1f;
        float newZoom = clamp(oldZoom * factor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == oldZoom) {
            return;
        }
        float graphX = (pointerX - layout.panX()) / oldZoom;
        float graphY = (pointerY - layout.panY()) / oldZoom;
        session.viewport(pointerX - graphX * newZoom,
                pointerY - graphY * newZoom, newZoom);
    }

    private void finishTransaction(boolean commit) {
        dragNodeId = "";
        panning = false;
        if (session.transactionActive()) {
            if (commit) {
                session.commitTransaction();
            } else {
                session.cancelTransaction();
            }
        }
    }

    private static float positiveModulo(float value, float modulus) {
        float result = value % modulus;
        return result < 0.0f ? result + modulus : result;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record PortHit(ShaderGraphEditorNode node,
            ShaderGraphPort port) {
    }

    private record PortPoint(float x, float y) {
    }
}
