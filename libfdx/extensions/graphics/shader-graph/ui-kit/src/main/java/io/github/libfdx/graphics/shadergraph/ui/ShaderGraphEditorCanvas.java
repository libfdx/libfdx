package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphWireRoute.Wire;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphPort;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerType;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.TextInputEvent;
import io.github.libfdx.ui.UiTextAlign;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive retained shader-node canvas. It owns only transient interaction
 * and drawing caches; semantic and persisted layout data remain in the editor
 * session.
 */
public final class ShaderGraphEditorCanvas implements UiCustomContent,
        UiDrawFunction, UiSurfaceInput {
    private static final float GRID_SIZE = 32.0f;
    private static final float PAN_THRESHOLD = 5.0f;
    private static final float HEADER_PADDING = 2.0f;
    private static final float TITLE_LINE_HEIGHT = 16.0f;
    private static final float DETAIL_LINE_HEIGHT = 14.0f;
    private static final float TYPE_TOP = HEADER_PADDING + TITLE_LINE_HEIGHT + HEADER_PADDING;
    private static final float HEADER_HEIGHT = TYPE_TOP + DETAIL_LINE_HEIGHT + HEADER_PADDING;
    private static final float PORT_TOP = 48.0f;
    private static final float PORT_STEP = 18.0f;
    private static final float PORT_RADIUS = 6.0f;
    private static final MaterialOutputChannel[] CHANNELS = MaterialOutputChannel.values();
    static final float OUTPUT_WIDTH = 182;
    static final float OUTPUT_HEIGHT = PORT_TOP + (CHANNELS.length - 1) * PORT_STEP + 16;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 2.5f;
    private static final double ZOOM_PER_NOTCH = 1.1;
    private static final UiTextStyle TITLE = UiTextStyle.text()
            .size(13.0f).lineHeight(TITLE_LINE_HEIGHT).color(ShaderGraphEditorTheme.TEXT).ellipsis(true);
    private static final UiTextStyle DETAIL = UiTextStyle.text()
            .size(11.0f).lineHeight(DETAIL_LINE_HEIGHT)
            .color(ShaderGraphEditorTheme.DETAIL).ellipsis(true);

    private final ShaderGraphEditorSession session;
    private final ShaderGraphCanvasMenu menu;
    private final ShaderGraphInlineValueEditor inlineValue;
    private final UiPath connectionPath = new UiPath(8, 32);
    private long cachedSemanticRevision = -1;
    private ShaderGraphEditorLayout cachedLayout;
    private ShaderGraph graph;
    private ShaderGraphEditorData layout;
    private ShaderNode[] nodes = new ShaderNode[0];
    private ShaderGraphEditorNode[] nodeLayouts =
            new ShaderGraphEditorNode[0];
    private float viewWidth, viewHeight;
    private float graphLeft, graphTop, graphRight, graphBottom;
    private float styleZoom;
    private UiTextStyle titleStyle = TITLE, detailStyle = DETAIL;
    private final UiIntState interactionRevision = Ui.state(0);
    private final Map<ShaderGraphId, ShaderGraphEditorPortLabels> sockets = new HashMap<>();
    private final Map<ShaderGraphId, ShaderGraphEditorNodeValue> values = new HashMap<>();
    private final Map<ShaderGraphId, Set<ShaderGraphId>> missingInputs = new HashMap<>();
    private ShaderGraph diagnosticGraph;
    private ShaderGraphEditorCompilation diagnosticCompilation;
    private UiTextStyle rightDetail, typeStyle, rightType, missingType;
    private ShaderGraphEditorData.Position materialOutput;
    private final PortPoint[] materialSources = new PortPoint[CHANNELS.length];
    private final List<ShaderGraphCanvasWire> wires = new ArrayList<>();
    private ShaderGraphCanvasWire selectedWire;
    private int selectedPin = -1;
    private int draggedPin = -1;
    private boolean clearWireOnPanClick;
    private boolean draggingMaterialOutput;
    private String dragNodeId = "";
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean panning;
    private boolean pendingContextClick;
    private float panPointerX;
    private float panPointerY;
    private ShaderGraphWireSocket draggedSocket;
    private PortPoint wireAnchor;
    private float connectingX;
    private float connectingY;
    private String lastInteractionError = "";

    public ShaderGraphEditorCanvas(ShaderGraphEditorSession session) {
        this(session, ShaderGraphEditorPalette.standard(), null);
    }

    ShaderGraphEditorCanvas(ShaderGraphEditorSession session, ShaderGraphEditorPalette palette, Runnable onValueCommit) {
        if (session == null) {
            throw new FdxException(
                    "Shader graph editor canvas requires a session");
        }
        this.session = session;
        menu = new ShaderGraphCanvasMenu(session, palette);
        inlineValue = new ShaderGraphInlineValueEditor(session, onValueCommit);
    }

    public ShaderGraphEditorSession session() {
        return session;
    }

    ShaderGraphWireRoute.Pin selectedRoutingPin() {
        interactionRevision.get();
        refresh();
        return selectedWire == null || selectedPin < 0 ? null : selectedWire.route.pins().get(selectedPin);
    }

    void rotateSelectedPin(float degrees) {
        if (selectedRoutingPin() == null) return;
        var route = selectedWire.route.withRotation(selectedPin, degrees);
        if (route != selectedWire.route) session.wireRoute("Rotate routing pin", selectedWire.key, route);
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

    /** Returns the visible shader node ID under the pointer, or an empty string for the canvas or material output. */
    public String nodeAt(float localX, float localY) {
        refresh();
        if (insideMaterialOutput(localX, localY)) return "";
        ShaderGraphEditorNode hit = nodeLayoutAt(localX, localY);
        return hit != null ? hit.nodeId().value() : "";
    }

    @Override
    public void build(UiCustomContext context) {
        context.draw(this);
        context.input(this);
        session.observeView();
        interactionRevision.get();
        refresh();
        refreshMissingInputs();
        updateTextStyles();
        context.content(ui -> {
            for (int i = 0; i < nodeLayouts.length; i++) buildNode(ui, nodes[i], nodeLayouts[i]);
            if (materialOutput != null) buildMaterialOutput(ui);
            if (!lastInteractionError.isEmpty() || selectedWire != null) {
                String message = lastInteractionError.isEmpty() ? selectedWire.label : lastInteractionError;
                ui.column(Ui.modifier().fill(), overlay -> {
                    overlay.spacer(Ui.modifier().weight(1));
                    overlay.selectableText(message, Ui.modifier().fillWidth().height(23).padding(10, 2)
                            .style(background(ShaderGraphEditorTheme.HEADER).text(DETAIL)
                                    .foreground(UiDrawable.color(ShaderGraphEditorTheme.WIRE))));
                });
            }
            inlineValue.build(ui, context);
            menu.build(ui, context);
        });
    }

    @Override
    public void draw(UiDrawContext draw, UiRect bounds) {
        refresh();
        viewWidth = bounds.width();
        viewHeight = bounds.height();
        draw.rect(bounds, ShaderGraphEditorTheme.CANVAS);
        drawGrid(draw, bounds);
        for (int i = 0; i < wires.size(); i++) {
            var wire = wires.get(i);
            if (wire != selectedWire) wire.draw(draw, connectionPath, bounds, false, selectedWire != null, -1);
        }
        if (selectedWire != null) selectedWire.draw(draw, connectionPath, bounds, true, false, selectedPin);
        var origin = draggedSocket != null ? draggedSocket : menu.wireOrigin();
        if (origin != null) drawPendingWire(draw, bounds, origin);

    }

    private void updateTextStyles() {
        if (styleZoom != layout.zoom()) {
            styleZoom = layout.zoom();
            titleStyle = TITLE.size(13 * styleZoom).lineHeight(TITLE_LINE_HEIGHT * styleZoom);
            detailStyle = DETAIL.size(11 * styleZoom).lineHeight(DETAIL_LINE_HEIGHT * styleZoom);
            rightDetail = detailStyle.align(UiTextAlign.END);
            typeStyle = DETAIL.size(10 * styleZoom).lineHeight(12 * styleZoom).color(ShaderGraphEditorTheme.TYPE_TEXT);
            rightType = typeStyle.align(UiTextAlign.END);
            missingType = typeStyle.color(ShaderGraphEditorTheme.TEXT);
        }
    }

    @Override
    public UiPointerResult pointer(UiPointerEvent event) {
        if (event.phase() == UiPointerPhase.DOWN || event.phase() == UiPointerPhase.UP) {
            interactionRevision.set(interactionRevision.get() + 1);
        }
        refresh();
        if (event.phase() == UiPointerPhase.DOWN && event.button() == MouseButton.RIGHT) {
            inlineValue.cancel();
            draggedSocket = null;
            if (panning || draggingMaterialOutput || draggedPin >= 0 || !dragNodeId.isEmpty()) finishTransaction(false);
            menu.close();
            lastInteractionError = "";
            pendingContextClick = true;
            clearWireOnPanClick = false;
            panPointerX = event.localX();
            panPointerY = event.localY();
            return UiPointerResult.CAPTURE;
        }
        if (menu.isOpen()) return UiPointerResult.HANDLED;
        if (event.phase() == UiPointerPhase.SCROLL) {
            inlineValue.cancel();
            if (event.scrollY() == 0 || !Float.isFinite(event.scrollY())) {
                return UiPointerResult.IGNORED;
            }
            viewWidth = event.surfaceWidth();
            viewHeight = event.surfaceHeight();
            if (pendingContextClick) beginPan();
            zoom(event.localX(), event.localY(), event.scrollY());
            return UiPointerResult.HANDLED;
        }
        if (event.phase() == UiPointerPhase.DOWN) {
            lastInteractionError = "";
            draggedSocket = socketAt(event.localX(), event.localY());
            if (draggedSocket != null) {
                inlineValue.cancel();
                selectedWire = null;
                wireAnchor = null;
                connectingX = event.localX();
                connectingY = event.localY();
                return UiPointerResult.CAPTURE;
            }
            if (insideMaterialOutput(event.localX(), event.localY())) {
                inlineValue.cancel();
                session.selectNode("");
                selectedWire = null;
                draggingMaterialOutput = true;
                dragOffsetX = graphX(event.localX()) - materialOutput.x();
                dragOffsetY = graphY(event.localY()) - materialOutput.y();
                session.beginTransaction("Move Material Output");
                return UiPointerResult.CAPTURE;
            }
            ShaderGraphEditorNode node = nodeLayoutAt(event.localX(), event.localY());
            inlineValue.press(event, node, node == null ? null : values.get(node.nodeId()),
                    graphX(event.localX()), graphY(event.localY()));
            if (node != null) {
                selectedWire = null;
                session.selectNode(node.nodeId().value());
                dragNodeId = node.nodeId().value();
                dragOffsetX = graphX(event.localX()) - node.x();
                dragOffsetY = graphY(event.localY()) - node.y();
                session.beginTransaction("Move node");
                return UiPointerResult.CAPTURE;
            }
            session.selectNode("");
            if (event.button() == MouseButton.LEFT) {
                var hit = wireAt(event.localX(), event.localY(), true);
                if (hit != null) {
                    selectedWire = hit.wire;
                    selectedPin = hit.pin;
                    draggedPin = selectedPin;
                    if (draggedPin >= 0) {
                        var pin = selectedWire.route.pins().get(draggedPin);
                        dragOffsetX = graphX(event.localX()) - pin.x();
                        dragOffsetY = graphY(event.localY()) - pin.y();
                    }
                    return UiPointerResult.CAPTURE;
                }
                if (event.pointerType() == PointerType.MOUSE) {
                    selectedWire = null;
                    return UiPointerResult.HANDLED;
                }
            }
            clearWireOnPanClick = event.button() == MouseButton.LEFT;
            panPointerX = event.localX();
            panPointerY = event.localY();
            beginPan();
            return UiPointerResult.CAPTURE;
        }
        if (event.phase() == UiPointerPhase.MOVE) {
            if (draggedPin >= 0 && selectedWire != null) {
                float x = graphX(event.localX()) - dragOffsetX;
                float y = graphY(event.localY()) - dragOffsetY;
                float angle = Math.abs(selectedWire.route.pins().get(draggedPin).rotationDegrees());
                if (angle == 90) {
                    x = graphX(snapToNeighbor(localX(x), selectedWire.x, draggedPin));
                } else if (angle == 0 || angle == 180) {
                    y = graphY(snapToNeighbor(localY(y), selectedWire.y, draggedPin));
                }
                selectedWire.x[draggedPin + 1] = localX(x);
                selectedWire.y[draggedPin + 1] = localY(y);
                return UiPointerResult.HANDLED;
            }
            if (draggedSocket != null) {
                connectingX = event.localX();
                connectingY = event.localY();
                return UiPointerResult.HANDLED;
            }
            if (draggingMaterialOutput) {
                session.moveMaterialOutput(graphX(event.localX()) - dragOffsetX, graphY(event.localY()) - dragOffsetY);
                return UiPointerResult.HANDLED;
            }
            if (!dragNodeId.isEmpty()) {
                if (inlineValue.pendingClick(event)) return UiPointerResult.HANDLED;
                session.moveNode(dragNodeId,
                        graphX(event.localX()) - dragOffsetX,
                        graphY(event.localY()) - dragOffsetY);
                return UiPointerResult.HANDLED;
            }
            if (pendingContextClick || panning) {
                updatePan(event.localX(), event.localY());
                return UiPointerResult.HANDLED;
            }
        }
        if (event.phase() == UiPointerPhase.UP) {
            if (pendingContextClick || panning) {
                updatePan(event.localX(), event.localY());
                boolean contextClick = pendingContextClick && event.button() == MouseButton.RIGHT && event.inside();
                if (panning && clearWireOnPanClick) selectedWire = null;
                finishTransaction(true);
                if (contextClick) openMenu(panPointerX, panPointerY);
                return UiPointerResult.RELEASE;
            }
            if (draggedSocket != null) {
                try {
                    if (event.inside()) dropWire(event.localX(), event.localY());
                } catch (RuntimeException failure) {
                    menu.close();
                    lastInteractionError = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
                }
                draggedSocket = null;
                return UiPointerResult.RELEASE;
            }
            finishTransaction(true);
            inlineValue.release(event);
            return UiPointerResult.RELEASE;
        }
        if (event.phase() == UiPointerPhase.CANCEL) {
            inlineValue.cancel();
            draggedSocket = null;
            finishTransaction(false);
            return UiPointerResult.RELEASE;
        }
        return UiPointerResult.IGNORED;
    }

    @Override
    public void focusChanged(boolean focused) {
        if (!focused) {
            inlineValue.cancel();
            menu.close();
            draggedSocket = null;
            finishTransaction(false);
        }
    }

    @Override
    public boolean keyDown(KeyEvent event) {
        if (event.key() == Key.ESCAPE && (draggedPin >= 0 || pendingContextClick || panning)) {
            finishTransaction(false);
            return true;
        }
        if (draggedSocket != null && event.key() == Key.ESCAPE) {
            draggedSocket = null;
            return true;
        }
        if (inlineValue.key(event.key())) {
            interactionRevision.set(interactionRevision.get() + 1);
            return true;
        }
        if (menu.key(event.key())) return true;
        if (event.key() == Key.ESCAPE && selectedWire != null) {
            interactionRevision.set(interactionRevision.get() + 1);
            selectedWire = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean textInput(TextInputEvent event) {
        return !event.composition() && menu.text(event.text());
    }

    /** Arranges the active graph and frames it in a single undo step. */
    public void organizeGraph() {
        session.beginTransaction("Organize graph");
        try {
            session.organizeGraph();
            fitGraph();
            session.commitTransaction();
        } finally {
            if (session.transactionActive()) session.cancelTransaction();
        }
    }

    /** Centers all node bounds in the current canvas, including nodes dragged outside the original viewport. */
    public void fitGraph() {
        refresh();
        if ((nodeLayouts.length == 0 && materialOutput == null) || viewWidth <= 16 || viewHeight <= 16) {
            return;
        }
        float zoom = fitZoom();
        session.viewport((viewWidth - (graphLeft + graphRight) * zoom) / 2,
                (viewHeight - (graphTop + graphBottom) * zoom) / 2, zoom);
    }

    /** Frames the surface output at readable scale with space for its incoming wires. */
    public void frameMaterialOutput() {
        refresh();
        if (materialOutput == null || viewWidth <= 16 || viewHeight <= 16) return;
        float zoom = Math.min(1f, Math.min((viewWidth - 16) / OUTPUT_WIDTH, (viewHeight - 16) / OUTPUT_HEIGHT));
        float rightMargin = Math.min(24, (viewWidth - OUTPUT_WIDTH * zoom) / 2);
        session.viewport(viewWidth - rightMargin - (materialOutput.x() + OUTPUT_WIDTH) * zoom,
                viewHeight / 2 - (materialOutput.y() + OUTPUT_HEIGHT / 2) * zoom, zoom);
    }

    private float fitZoom() {
        if ((nodeLayouts.length == 0 && materialOutput == null) || viewWidth <= 16 || viewHeight <= 16) {
            return 1.0f;
        }
        return Math.min(1.0f, Math.min((viewWidth - 16) / (graphRight - graphLeft),
                (viewHeight - 16) / (graphBottom - graphTop)));
    }

    private void drawGrid(UiDrawContext draw, UiRect bounds) {
        float spacing = GRID_SIZE * layout.zoom();
        while (spacing < 8.0f) {
            spacing *= 4.0f;
        }
        float startX = positiveModulo(layout.panX(), spacing);
        float startY = positiveModulo(layout.panY(), spacing);
        for (float x = startX; x <= bounds.width(); x += spacing) {
            draw.line(bounds.x() + x, bounds.y(),
                    bounds.x() + x, bounds.bottom(), 1.0f, ShaderGraphEditorTheme.GRID);
        }
        for (float y = startY; y <= bounds.height(); y += spacing) {
            draw.line(bounds.x(), bounds.y() + y,
                    bounds.right(), bounds.y() + y, 1.0f, ShaderGraphEditorTheme.GRID);
        }
    }

    private void drawConnection(UiDrawContext draw, float x1, float y1,
            float x2, float y2, UiColor color) {
        float control = ShaderGraphCanvasWire.control(x2 - x1, layout.zoom());
        connectionPath.clear().moveTo(x1, y1)
                .cubicTo(x1 + control, y1, x2 - control, y2, x2, y2);
        draw.path(connectionPath, Math.max(1.0f, 1.75f * layout.zoom()), color);
    }

    private void openMenu(float x, float y) {
        var actions = new ArrayList<ShaderGraphCanvasMenu.Action>();
        String title = "";
        var channel = materialSocketAt(x, y);
        if (channel != null || insideMaterialOutput(x, y)) {
            title = "Material Output";
            if (channel != null && channel.binding(graph.outputs()) != null) {
                actions.add(new ShaderGraphCanvasMenu.Action("Disconnect " + channel.label, () -> session.bindMaterialOutput(channel, null)));
            }
        } else {
            var visible = nodeLayoutAt(x, y);
            var hit = socketAt(x, y);
            if (hit != null) {
                title = portLabel(hit.endpoint(), hit.input()) + " · " + ShaderGraphEditorPortLabels.typeName(hit.type());
                socketActions(actions, hit.endpoint(), hit.input());
            } else if (visible != null) {
                String id = visible.nodeId().value();
                title = id;
                selectedWire = null;
                session.selectNode(id);
                actions.add(new ShaderGraphCanvasMenu.Action("Duplicate node", () -> duplicate(id)));
                if (hasWires(visible.nodeId())) actions.add(new ShaderGraphCanvasMenu.Action("Disconnect all wires",
                        () -> session.disconnectNodeWires(id)));
                actions.add(new ShaderGraphCanvasMenu.Action("Delete node", () -> session.removeNodes(id)));
            } else {
                var wire = wireAt(x, y, false);
                if (wire != null) {
                    title = wire.pin >= 0 ? "Routing pin" : "Wire";
                    session.selectNode("");
                    selectedWire = wire.wire;
                    selectedPin = wire.pin;
                    wireActions(actions, wire, graphX(x), graphY(y));
                }
            }
        }
        menu.open(x, y, graphX(x), graphY(y), title, actions);
    }

    private boolean hasWires(ShaderGraphId id) {
        for (var edge : graph.edges()) {
            if (id.equals(edge.source().nodeId()) || id.equals(edge.target().nodeId())) return true;
        }
        if (materialOutput == null) return false;
        var outputs = graph.outputs();
        for (var channel : CHANNELS) {
            var binding = channel.binding(outputs);
            if (binding != null && id.equals(binding.source().nodeId())) return true;
        }
        return false;
    }

    private void duplicate(String id) {
        String base = id + "_copy";
        String copy = base;
        for (int i = 2; graph.node(ShaderGraphId.of(copy)) != null; i++) copy = base + i;
        session.duplicateNode(id, copy, 28, 28);
    }

    private String portLabel(ShaderEndpoint endpoint, boolean input) {
        var labels = sockets.get(endpoint.nodeId());
        for (var label : input ? labels.inputs() : labels.outputs()) {
            if (label.port().id().equals(endpoint.portId())) return label.name();
        }
        return endpoint.portId().value();
    }

    private void socketActions(List<ShaderGraphCanvasMenu.Action> actions, ShaderEndpoint endpoint, boolean input) {
        for (var edge : graph.edges()) {
            var target = edge.target();
            if (input ? target.equals(endpoint) : edge.source().equals(endpoint)) {
                var peer = input ? edge.source() : target;
                String label = "Disconnect " + peer.nodeId() + " · " + portLabel(peer, !input);
                actions.add(new ShaderGraphCanvasMenu.Action(label, () -> session.disconnect(target)));
            }
        }
        if (input || materialOutput == null) return;
        var outputs = graph.outputs();
        for (var channel : CHANNELS) {
            var binding = channel.binding(outputs);
            if (binding != null && binding.source().equals(endpoint)) {
                actions.add(new ShaderGraphCanvasMenu.Action("Disconnect Material Output · " + channel.label,
                        () -> session.bindMaterialOutput(channel, null)));
            }
        }
    }

    private record WireHit(ShaderGraphCanvasWire wire, int segment, int pin) { }

    private WireHit wireAt(float x, float y, boolean cycle) {
        float radius2 = ShaderGraphCanvasWire.PICK_RADIUS * ShaderGraphCanvasWire.PICK_RADIUS;
        float bestPin = radius2, bestSegment = radius2;
        WireHit pin = null, segment = null;
        // Start after the selection when cycling, or at the selection for contextual actions on shared runs.
        int first = selectedWire == null ? 0 : wires.indexOf(selectedWire) + (cycle ? 1 : 0);
        for (int n = 0; n < wires.size(); n++) {
            var wire = wires.get((first + n) % wires.size());
            for (int i = 1; i < wire.x.length - 1; i++) {
                float dx = wire.x[i] - x, dy = wire.y[i] - y, distance = dx * dx + dy * dy;
                if (distance < bestPin || distance == bestPin && wire == selectedWire) {
                    bestPin = distance;
                    pin = new WireHit(wire, i - 1, i - 1);
                }
            }
            for (int i = 0; i < wire.x.length - 1; i++) {
                float distance = wire.segmentDistanceSquared(i, x, y);
                if (distance < bestSegment - 0.01f || segment == null && distance <= radius2) {
                    bestSegment = distance;
                    segment = new WireHit(wire, i, -1);
                }
            }
        }
        return pin != null ? pin : segment;
    }

    private void wireActions(List<ShaderGraphCanvasMenu.Action> actions, WireHit hit, float graphX, float graphY) {
        var wire = hit.wire;
        if (hit.pin >= 0) {
            actions.add(new ShaderGraphCanvasMenu.Action("Remove routing pin", () ->
                    session.wireRoute("Remove routing pin", wire.key, wire.route.remove(hit.pin))));
        } else {
            actions.add(new ShaderGraphCanvasMenu.Action("Add routing pin", () ->
                    session.wireRoute("Add routing pin", wire.key, wire.route.insert(hit.segment, graphX, graphY))));
            boolean straight = !wire.route.straight(hit.segment);
            String shape = straight ? "Make segment straight" : "Make segment curved";
            actions.add(new ShaderGraphCanvasMenu.Action(shape, () ->
                    session.wireRoute(shape, wire.key, wire.route.withStraight(hit.segment, straight))));
        }
        if (!wire.route.equals(ShaderGraphWireRoute.DEFAULT)) {
            actions.add(new ShaderGraphCanvasMenu.Action("Reset wire route", () ->
                    session.wireRoute("Reset wire route", wire.key, ShaderGraphWireRoute.DEFAULT)));
        }
        actions.add(new ShaderGraphCanvasMenu.Action("Show source", () -> frameSocket(wire, 0)));
        actions.add(new ShaderGraphCanvasMenu.Action("Show destination", () -> frameSocket(wire, wire.x.length - 1)));
        if (wire.channel == null) actions.add(new ShaderGraphCanvasMenu.Action("Disconnect wire", () -> session.disconnect(wire.key.target())));
        else actions.add(new ShaderGraphCanvasMenu.Action("Disconnect " + wire.channel.label, () -> session.bindMaterialOutput(wire.channel, null)));
    }

    private void frameSocket(ShaderGraphCanvasWire wire, int point) {
        float zoom = Math.max(1, layout.zoom());
        session.viewport(viewWidth / 2 - graphX(wire.x[point]) * zoom, viewHeight / 2 - graphY(wire.y[point]) * zoom, zoom);
    }

    private static UiStyle background(UiColor color) {
        return UiStyle.style().background(UiDrawable.color(color));
    }

    private static UiModifier box(float x, float y, float width, float height) {
        return Ui.modifier().offset(x, y).size(width, height);
    }

    private void label(UiScope card, String text, float x, float y, float width, float height, UiTextStyle style) {
        float zoom = layout.zoom();
        card.text(text, box(x * zoom, y * zoom, width * zoom, height * zoom).style(UiStyle.style().text(style)));
    }

    private void refreshMissingInputs() {
        var result = session.latestCompilation();
        if (result == diagnosticCompilation && graph == diagnosticGraph) return;
        diagnosticCompilation = result;
        diagnosticGraph = graph;
        missingInputs.clear();
        if (result == null || result.success()) return;
        for (var diagnostic : result.diagnostics()) {
            if (diagnostic.graphId().equals(graph.id()) && diagnostic.code().equals("FDXG_INPUT_MISSING")
                    && diagnostic.nodeId() != null && diagnostic.portId() != null) {
                missingInputs.computeIfAbsent(diagnostic.nodeId(), ignored -> new HashSet<>()).add(diagnostic.portId());
            }
        }
    }

    ShaderGraphEditorNodeValue selectedValue() {
        refresh();
        String id = session.selectedNodeId();
        return id.isEmpty() ? null : values.get(ShaderGraphId.of(id));
    }

    private void buildNode(UiScope ui, ShaderNode node, ShaderGraphEditorNode placement) {
        ShaderEndpoint source = selectedWire == null ? null : selectedWire.key.source();
        ShaderEndpoint target = selectedWire == null ? null : selectedWire.key.target();
        boolean sourceNode = source != null && source.nodeId().equals(node.id());
        boolean targetNode = target != null && target.nodeId().equals(node.id());
        boolean selected = sourceNode || targetNode || session.selectedNodeId().equals(node.id().value());
        var ports = sockets.get(node.id());
        var value = values.get(node.id());
        buildCard(ui, placement.x(), placement.y(), placement.width(), placement.height(), selected,
                node.id().value(), value == null ? node.definitionId().value() : value.source(), card -> {
                    if (value != null) label(card, value.readout(), ShaderGraphInlineValueEditor.INSET,
                            placement.height() - ShaderGraphInlineValueEditor.BOTTOM,
                            placement.width() - 2 * ShaderGraphInlineValueEditor.INSET, ShaderGraphInlineValueEditor.HEIGHT, titleStyle);
                    buildPorts(card, placement.width(), ports.inputs(), ports.outputs().length, false, targetNode ? target : null,
                            missingInputs.getOrDefault(node.id(), Set.of()));
                    buildPorts(card, placement.width(), ports.outputs(), ports.inputs().length, true, sourceNode ? source : null, Set.of());
                });
    }

    private void buildCard(UiScope ui, float x, float y, float width, float height, boolean selected,
            String title, String subtitle, UiContent content) {
        float zoom = layout.zoom();
        float border = selected ? Math.max(2, 2 * zoom) : Math.max(0.75f, zoom);
        ui.stack(box(localX(x), localY(y), width * zoom, height * zoom)
                .style(background(selected ? ShaderGraphEditorTheme.ACCENT : ShaderGraphEditorTheme.EDGE)), card -> {
            card.spacer(box(border, border, width * zoom - 2 * border, height * zoom - 2 * border)
                    .style(background(ShaderGraphEditorTheme.NODE)));
            card.spacer(box(border, border, width * zoom - 2 * border, HEADER_HEIGHT * zoom - border)
                    .style(background(ShaderGraphEditorTheme.HEADER)));
            label(card, title, 10, HEADER_PADDING, width - 20, TITLE_LINE_HEIGHT, titleStyle);
            label(card, subtitle, 10, TYPE_TOP, width - 20, DETAIL_LINE_HEIGHT, detailStyle);
            content.build(card);
        });
    }

    private void buildMaterialOutput(UiScope ui) {
        buildCard(ui, materialOutput.x(), materialOutput.y(), OUTPUT_WIDTH, OUTPUT_HEIGHT,
                draggingMaterialOutput || selectedWire != null && selectedWire.channel != null, "Material Output", "Surface", card -> {
                    card.custom("material-sockets", Ui.modifier().fill(), custom -> custom.draw(this::drawMaterialSockets));
                    if (layout.zoom() >= 0.60f) for (int i = 0; i < CHANNELS.length; i++) {
                        float y = PORT_TOP + (i - 0.5f) * PORT_STEP;
                        label(card, CHANNELS[i].label, 10, y, OUTPUT_WIDTH - 60, PORT_STEP, detailStyle);
                        label(card, CHANNELS[i].typeLabel, OUTPUT_WIDTH - 40, y, 36, PORT_STEP, detailStyle);
                    }
                });
    }

    private void drawMaterialSockets(UiDrawContext draw, UiRect bounds) {
        float zoom = layout.zoom();
        float x = bounds.x();
        var hover = draggedSocket == null || draggedSocket.input() ? null : materialSocketAt(connectingX, connectingY);
        for (int i = 0; i < CHANNELS.length; i++) {
            var channel = CHANNELS[i];
            float cy = bounds.y() + (PORT_TOP + i * PORT_STEP) * zoom;
            float radius = 5 * zoom;
            boolean compatible = draggedSocket == null || draggedSocket.channel() == channel
                    || (!draggedSocket.input() && channel.type.equals(draggedSocket.type()));
            if (channel == hover && compatible) {
                draw.rect(x + zoom, cy - PORT_STEP * zoom / 2, (OUTPUT_WIDTH - 2) * zoom, PORT_STEP * zoom, ShaderGraphEditorTheme.HEADER);
            }
            if (selectedWire != null && selectedWire.channel == channel) socketRing(draw, x, cy, radius);
            draw.circle(x, cy, radius, compatible ? ShaderGraphEditorTheme.ACCENT : ShaderGraphEditorTheme.EDGE);
            if (materialSources[i] == null) draw.circle(x, cy, radius * 0.58f, ShaderGraphEditorTheme.NODE);
        }
    }

    private float materialPortY(int index) {
        return localY(materialOutput.y() + materialSocketY(index));
    }

    static float materialSocketY(int index) {
        return PORT_TOP + index * PORT_STEP;
    }

    private boolean insideMaterialOutput(float x, float y) {
        if (materialOutput == null) return false;
        float dx = graphX(x) - materialOutput.x();
        float dy = graphY(y) - materialOutput.y();
        return dx >= 0 && dx <= OUTPUT_WIDTH && dy >= 0 && dy <= OUTPUT_HEIGHT;
    }

    private MaterialOutputChannel materialSocketAt(float x, float y) {
        if (materialOutput == null) return null;
        float left = localX(materialOutput.x());
        if (x < left - Math.max(8, PORT_RADIUS * layout.zoom()) || x > left + OUTPUT_WIDTH * layout.zoom()) return null;
        int index = Math.round((graphY(y) - materialOutput.y() - PORT_TOP) / PORT_STEP);
        return index >= 0 && index < CHANNELS.length ? CHANNELS[index] : null;
    }

    private void buildPorts(UiScope card, float width, ShaderGraphEditorPortLabels.Socket[] ports,
            int oppositeCount, boolean output, ShaderEndpoint selectedPort, Set<ShaderGraphId> missing) {
        float zoom = layout.zoom();
        card.custom(output ? "output-sockets" : "input-sockets", Ui.modifier().fill(), custom -> custom.draw((draw, bounds) -> {
            float x = output ? bounds.right() : bounds.x();
            float radius = 5 * zoom;
            for (int i = 0; i < ports.length; i++) {
                float y = bounds.y() + ShaderGraphEditorLayout.socketY(i) * zoom;
                if (selectedPort != null && selectedPort.portId().equals(ports[i].port().id())) socketRing(draw, x, y, radius);
                if (missing.contains(ports[i].port().id())) {
                    draw.circle(x, y, radius + zoom, ShaderGraphEditorTheme.TEXT);
                    draw.rect(x - zoom, y - 4 * zoom, 2 * zoom, 5 * zoom, ShaderGraphEditorTheme.NODE);
                    draw.rect(x - zoom, y + 2 * zoom, 2 * zoom, 2 * zoom, ShaderGraphEditorTheme.NODE);
                } else {
                    draw.circle(x, y, radius, output ? ShaderGraphEditorTheme.ACCENT : ShaderGraphEditorTheme.DETAIL);
                    if (!output) draw.circle(x, y, radius * 0.58f, ShaderGraphEditorTheme.NODE);
                }
            }
        }));
        if (zoom < 0.60f) return;
        for (int i = 0; i < ports.length; i++) {
            float textWidth = Math.max(0, (i < oppositeCount ? width / 2 : width) - 13);
            float left = output ? width - 10 - textWidth : 10;
            float y = ShaderGraphEditorLayout.socketY(i);
            label(card, ports[i].name(), left, y - 7, textWidth, 14, output ? rightDetail : detailStyle);
            boolean missingInput = missing.contains(ports[i].port().id());
            String detail = missingInput ? "Missing · " + ports[i].type() : ports[i].type();
            var style = output ? rightType : typeStyle;
            label(card, detail, left, y + 7, textWidth, 12, missingInput ? missingType : style);
        }
    }

    private static void socketRing(UiDrawContext draw, float x, float y, float radius) {
        draw.circle(x, y, radius + 4, ShaderGraphEditorTheme.TEXT);
        draw.circle(x, y, radius + 2, ShaderGraphEditorTheme.CANVAS);
    }

    private void refresh() {
        if (!session.selectedNodeId().isEmpty()) selectedWire = null;
        if (graph != null
                && cachedSemanticRevision == session.semanticRevision()
                && cachedLayout == session.layout()) {
            return;
        }
        ShaderGraph active = session.activeGraph();
        wireAnchor = null;
        if (active != graph || cachedSemanticRevision != session.semanticRevision()) {
            draggedSocket = null;
            sockets.clear();
            values.clear();
            for (ShaderNode node : active.nodes()) {
                sockets.put(node.id(), ShaderGraphEditorPortLabels.of(node, session.document()));
                var value = ShaderGraphEditorNodeValue.of(node, active);
                if (value != null) values.put(node.id(), value);
            }
        }
        if (graph != null && !graph.id().equals(active.id())) selectedWire = null;
        graph = active;
        layout = session.layout().activeGraph();
        ShaderGraphEditorNode[] layouts = layout.nodes();
        nodes = new ShaderNode[layouts.length];
        nodeLayouts = new ShaderGraphEditorNode[layouts.length];
        graphLeft = graphTop = Float.POSITIVE_INFINITY;
        graphRight = graphBottom = Float.NEGATIVE_INFINITY;
        int count = 0;
        for (ShaderGraphEditorNode item : layouts) {
            ShaderNode node = graph.node(item.nodeId());
            if (node != null) {
                nodes[count] = node;
                nodeLayouts[count] = item;
                graphLeft = Math.min(graphLeft, item.x());
                graphTop = Math.min(graphTop, item.y());
                graphRight = Math.max(graphRight, item.x() + item.width());
                graphBottom = Math.max(graphBottom, item.y() + item.height());
                count++;
            }
        }
        if (count != nodes.length) {
            nodes = java.util.Arrays.copyOf(nodes, count);
            nodeLayouts = java.util.Arrays.copyOf(nodeLayouts, count);
        }
        cachedSemanticRevision = session.semanticRevision();
        cachedLayout = session.layout();
        // A new layout supersedes any in-progress pin gesture. The preview lives only in its wire geometry.
        draggedPin = -1;
        var selectedKey = selectedWire == null ? null : selectedWire.key;
        if (selectedKey == null || selectedWire.route.pins().size()
                != layout.routes().getOrDefault(selectedKey, ShaderGraphWireRoute.DEFAULT).pins().size()) selectedPin = -1;
        selectedWire = null;
        wires.clear();
        for (var edge : graph.edges()) {
            cacheWire(Wire.of(edge), null, outputPoint(edge.source()), inputPoint(edge.target()), selectedKey);
        }
        materialOutput = layout.materialOutput();
        if (materialOutput != null) {
            graphLeft = Math.min(graphLeft, materialOutput.x());
            graphTop = Math.min(graphTop, materialOutput.y());
            graphRight = Math.max(graphRight, materialOutput.x() + OUTPUT_WIDTH);
            graphBottom = Math.max(graphBottom, materialOutput.y() + OUTPUT_HEIGHT);
            var outputs = graph.outputs();
            for (int i = 0; i < CHANNELS.length; i++) {
                var binding = CHANNELS[i].binding(outputs);
                materialSources[i] = binding == null ? null : outputPoint(binding.source());
                if (binding != null) cacheWire(Wire.of(binding), CHANNELS[i], materialSources[i],
                        new PortPoint(localX(materialOutput.x()), materialPortY(i)), selectedKey);
            }
        }
    }

    private void cacheWire(Wire key, MaterialOutputChannel channel, PortPoint start, PortPoint end, Wire selectedKey) {
        if (start == null || end == null) return;
        var source = key.source();
        String destination = channel == null ? key.target().nodeId() + " · " + portLabel(key.target(), true)
                : "Material Output · " + channel.label;
        String label = source.nodeId() + " · " + portLabel(source, false) + "  to  " + destination;
        var wire = new ShaderGraphCanvasWire(key, channel, label, layout, start.x, start.y, end.x, end.y);
        wires.add(wire);
        if (key.equals(selectedKey)) selectedWire = wire;
        for (var pin : wire.route.pins()) {
            graphLeft = Math.min(graphLeft, pin.x());
            graphTop = Math.min(graphTop, pin.y());
            graphRight = Math.max(graphRight, pin.x());
            graphBottom = Math.max(graphBottom, pin.y());
        }
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

    private void drawPendingWire(UiDrawContext draw, UiRect bounds, ShaderGraphWireSocket origin) {
        if (wireAnchor == null) {
            var channel = origin.channel();
            wireAnchor = channel == null ? portPoint(origin.endpoint(), origin.input())
                    : new PortPoint(localX(materialOutput.x()), materialPortY(channel.ordinal()));
            if (wireAnchor == null) return;
        }
        float x = bounds.x(), y = bounds.y();
        if (origin.input()) drawConnection(draw, x + connectingX, y + connectingY, x + wireAnchor.x, y + wireAnchor.y, ShaderGraphEditorTheme.ACCENT);
        else drawConnection(draw, x + wireAnchor.x, y + wireAnchor.y, x + connectingX, y + connectingY, ShaderGraphEditorTheme.ACCENT);
    }

    private void dropWire(float x, float y) {
        var target = socketAt(x, y);
        if (target != null) {
            if (target.input() != draggedSocket.input()) session.connectSockets(draggedSocket, target);
        } else if (!insideMaterialOutput(x, y) && nodeLayoutAt(x, y) == null && wireAt(x, y, false) == null) {
            connectingX = x;
            connectingY = y;
            menu.openForWire(x, y, graphX(x), graphY(y), draggedSocket);
        }
    }

    private ShaderGraphWireSocket socketAt(float x, float y) {
        var channel = materialSocketAt(x, y);
        if (channel != null) return ShaderGraphWireSocket.material(channel);
        if (insideMaterialOutput(x, y)) return null;
        float radius = Math.max(8.0f, PORT_RADIUS * layout.zoom() + 3.0f);
        for (int i = nodeLayouts.length - 1; i >= 0; i--) {
            var node = nodeLayouts[i];
            float left = localX(node.x()), right = localX(node.x() + node.width());
            boolean input = Math.abs(x - left) <= Math.abs(x - right);
            if (Math.abs(x - (input ? left : right)) <= radius) {
                var labels = sockets.get(node.nodeId());
                var ports = input ? labels.inputs() : labels.outputs();
                int row = Math.round((graphY(y) - node.y() - ShaderGraphEditorLayout.SOCKET_TOP) / ShaderGraphEditorLayout.SOCKET_STEP);
                if (ports.length > 0) {
                    row = Math.clamp(row, 0, ports.length - 1);
                    if (Math.abs(y - localY(node.y() + ShaderGraphEditorLayout.socketY(row))) <= radius)
                        return ShaderGraphWireSocket.port(node.nodeId(), ports[row].port(), input);
                }
            }
            if (x >= left && x <= right && y >= localY(node.y()) && y <= localY(node.y() + node.height())) return null;
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
                            localY(nodeLayouts[i].y() + ShaderGraphEditorLayout.socketY(port)));
                }
            }
        }
        return null;
    }

    private void zoom(float pointerX, float pointerY, float amount) {
        float oldZoom = layout.zoom();
        // Fit can frame a wide graph below the usual minimum. Saved views can also start outside the limits.
        float minimum = Math.min(oldZoom, Math.min(MIN_ZOOM, fitZoom()));
        float maximum = Math.max(oldZoom, MAX_ZOOM);
        double requested = oldZoom * Math.pow(ZOOM_PER_NOTCH, -amount);
        float newZoom = (float) Math.max(minimum, Math.min(maximum, requested));
        if (newZoom == oldZoom) {
            return;
        }
        double ratio = (double) newZoom / oldZoom;
        float panX = (float) (pointerX + ((double) layout.panX() - pointerX) * ratio);
        float panY = (float) (pointerY + ((double) layout.panY() - pointerY) * ratio);
        session.viewport(panX, panY, newZoom);
    }

    private void beginPan() {
        pendingContextClick = false;
        panning = true;
        session.beginTransaction("Move viewport");
    }

    private void updatePan(float x, float y) {
        float dx = x - panPointerX;
        float dy = y - panPointerY;
        if (pendingContextClick) {
            if (dx * dx + dy * dy < PAN_THRESHOLD * PAN_THRESHOLD) return;
            beginPan();
        }
        if (dx == 0 && dy == 0) return;
        clearWireOnPanClick = false;
        panPointerX = x;
        panPointerY = y;
        session.viewport(layout.panX() + dx, layout.panY() + dy, layout.zoom());
    }

    private static float snapToNeighbor(float coordinate, float[] points, int pin) {
        if (Math.abs(coordinate - points[pin]) <= 5) return points[pin];
        if (Math.abs(coordinate - points[pin + 2]) <= 5) return points[pin + 2];
        return coordinate;
    }

    private void finishTransaction(boolean commit) {
        boolean ownsTransaction = panning || draggingMaterialOutput || !dragNodeId.isEmpty();
        if (draggedPin >= 0 && selectedWire != null) {
            var wire = selectedWire;
            int index = draggedPin;
            draggedPin = -1;
            var original = wire.route.pins().get(index);
            float x = wire.x[index + 1], y = wire.y[index + 1];
            wire.x[index + 1] = localX(original.x());
            wire.y[index + 1] = localY(original.y());
            if (commit && (wire.x[index + 1] != x || wire.y[index + 1] != y)) {
                session.wireRoute("Move routing pin", wire.key, wire.route.move(index, graphX(x), graphY(y)));
            }
        }
        draggedPin = -1;
        draggingMaterialOutput = false;
        dragNodeId = "";
        panning = false;
        pendingContextClick = false;
        if (ownsTransaction && session.transactionActive()) {
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

    private record PortPoint(float x, float y) {
    }
}
