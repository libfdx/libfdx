package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shadergraph.model.ShaderEdge;
import io.github.libfdx.graphics.shadergraph.model.ShaderEndpoint;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphDependency;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphOutput;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameter;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphResource;
import io.github.libfdx.graphics.shadergraph.node.ShaderNode;
import io.github.libfdx.graphics.shadergraph.node.ShaderNodeProperty;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * UI-thread editor session with bounded undo/redo and worker-safe compilation
 * snapshots.
 *
 * <p>Semantic and layout revisions are separate. Pan, zoom, selection, and
 * node positions never alter the document semantic hash or invalidate a
 * successful canonical compilation. Drag transactions may contain many
 * layout updates but create one undo record.</p>
 */
public final class ShaderGraphEditorSession {
    public static final int DEFAULT_HISTORY_CAPACITY = 256;
    private static final float DEFAULT_NODE_WIDTH = 180.0f;
    private static final float DEFAULT_NODE_HEIGHT = 104.0f;

    private final int historyCapacity;
    private final Deque<HistoryEntry> undo = new ArrayDeque<>();
    private final Deque<HistoryEntry> redo = new ArrayDeque<>();
    private final List<ShaderGraphEditorSessionListener> listeners =
            new ArrayList<>();
    private ShaderGraphEditorState state;
    private long semanticRevision;
    private long layoutRevision;
    private long nextCompilationGeneration;
    private long latestRequestedGeneration;
    private ShaderGraphEditorCompilation latestCompilation;
    private ShaderGraphEditorCompilation lastGoodCompilation;
    private String selectedNodeId = "";
    private String transactionName;
    private ShaderGraphEditorState transactionStart;

    public ShaderGraphEditorSession(ShaderGraphEditorDocument document) {
        this(new ShaderGraphEditorState(document,
                ShaderGraphEditorLayout.forDocument(document)),
                DEFAULT_HISTORY_CAPACITY);
    }

    public ShaderGraphEditorSession(ShaderGraphEditorDocument document,
            ShaderGraphEditorLayout layout) {
        this(new ShaderGraphEditorState(document, layout),
                DEFAULT_HISTORY_CAPACITY);
    }

    public ShaderGraphEditorSession(ShaderGraphEditorState state,
            int historyCapacity) {
        if (state == null || historyCapacity <= 0) {
            throw new FdxException("Shader graph editor session is incomplete");
        }
        this.state = state;
        this.historyCapacity = historyCapacity;
    }

    public ShaderGraphEditorState state() {
        return state;
    }

    public ShaderGraphEditorDocument document() {
        return state.document();
    }

    public ShaderGraphEditorLayout layout() {
        return state.layout();
    }

    public ShaderGraph activeGraph() {
        return document().graph(layout().activeGraphId());
    }

    public long semanticRevision() {
        return semanticRevision;
    }

    public long layoutRevision() {
        return layoutRevision;
    }

    public String selectedNodeId() {
        return selectedNodeId;
    }

    /**
     * Retains a listener until {@link #removeListener} is called.
     */
    public void addListener(ShaderGraphEditorSessionListener listener) {
        if (listener == null) {
            throw new FdxException(
                    "Shader graph editor session listener cannot be null");
        }
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(ShaderGraphEditorSessionListener listener) {
        listeners.remove(listener);
    }

    public void selectNode(String nodeId) {
        String requested = nodeId != null ? nodeId.trim() : "";
        if (!requested.isEmpty()
                && activeGraph().node(
                        io.github.libfdx.graphics.shadergraph.model.ShaderGraphId
                                .of(requested)) == null) {
            throw new FdxException(
                    "Active shader graph has no node " + requested);
        }
        if (!selectedNodeId.equals(requested)) {
            selectedNodeId = requested;
            notifyListeners();
        }
    }

    public void selectGraph(String graphId) {
        ShaderGraphEditorLayout next = layout().activeGraph(graphId);
        applyState(new ShaderGraphEditorState(document(), next));
        if (!selectedNodeId.isEmpty()) {
            selectedNodeId = "";
            notifyListeners();
        }
    }

    public boolean canUndo() {
        return !undo.isEmpty() && transactionStart == null;
    }

    public boolean canRedo() {
        return !redo.isEmpty() && transactionStart == null;
    }

    public int undoCount() {
        return undo.size();
    }

    public int redoCount() {
        return redo.size();
    }

    public String nextUndoName() {
        return canUndo() ? undo.peekLast().name : "";
    }

    public String nextRedoName() {
        return canRedo() ? redo.peekLast().name : "";
    }

    public void execute(ShaderGraphEditorCommand command) {
        if (command == null || command.name() == null
                || command.name().trim().isEmpty()) {
            throw new FdxException("Shader graph editor command is incomplete");
        }
        ShaderGraphEditorState before = state;
        ShaderGraphEditorState after = command.apply(before);
        if (after == null) {
            throw new FdxException(
                    "Shader graph editor command returned null state");
        }
        if (before.equals(after)) {
            return;
        }
        applyState(after);
        if (transactionStart == null) {
            push(undo, new HistoryEntry(command.name().trim(), before, state));
            redo.clear();
        }
    }

    public boolean undo() {
        requireNoTransaction("undo");
        HistoryEntry entry = undo.pollLast();
        if (entry == null) {
            return false;
        }
        applyState(entry.before);
        push(redo, entry);
        validateSelection();
        return true;
    }

    public boolean redo() {
        requireNoTransaction("redo");
        HistoryEntry entry = redo.pollLast();
        if (entry == null) {
            return false;
        }
        applyState(entry.after);
        push(undo, entry);
        validateSelection();
        return true;
    }

    public void beginTransaction(String name) {
        if (transactionStart != null || name == null
                || name.trim().isEmpty()) {
            throw new FdxException(
                    "Shader graph editor transaction is already active or unnamed");
        }
        transactionName = name.trim();
        transactionStart = state;
    }

    public boolean transactionActive() {
        return transactionStart != null;
    }

    public boolean commitTransaction() {
        if (transactionStart == null) {
            return false;
        }
        ShaderGraphEditorState before = transactionStart;
        String name = transactionName;
        transactionStart = null;
        transactionName = null;
        if (before.equals(state)) {
            return false;
        }
        push(undo, new HistoryEntry(name, before, state));
        redo.clear();
        return true;
    }

    public boolean cancelTransaction() {
        if (transactionStart == null) {
            return false;
        }
        ShaderGraphEditorState before = transactionStart;
        transactionStart = null;
        transactionName = null;
        applyState(before);
        validateSelection();
        return true;
    }

    public void replaceDocument(String name,
            ShaderGraphEditorDocument replacement) {
        execute(command(name, current -> new ShaderGraphEditorState(
                replacement, current.layout().reconcile(replacement))));
    }

    /**
     * Adopts editor/compiled sections returned by persistence without creating
     * an undo entry or changing semantic/layout revisions.
     *
     * <p>The semantic source must be byte-identical to the current document.
     * Semantic replacement remains an explicit editor command.</p>
     */
    public void adoptSavedDocument(
            ShaderGraphEditorDocument savedDocument) {
        requireNoTransaction("adopt a saved document");
        if (savedDocument == null
                || !document().semanticSource().equals(
                        savedDocument.semanticSource())) {
            throw new FdxException(
                    "Saved shader graph semantics do not match "
                            + "the editor session");
        }
        state = new ShaderGraphEditorState(
                savedDocument, state.layout());
    }

    public void replaceGraph(String name, ShaderGraph replacement) {
        execute(command(name, current -> {
            ShaderGraphEditorDocument document =
                    current.document().withGraph(replacement);
            return new ShaderGraphEditorState(document,
                    current.layout().reconcile(document));
        }));
    }

    public void editActiveGraph(String name,
            UnaryOperator<ShaderGraph> operation) {
        if (operation == null) {
            throw new FdxException(
                    "Shader graph editor operation cannot be null");
        }
        ShaderGraph replacement = operation.apply(activeGraph());
        if (replacement == null) {
            throw new FdxException(
                    "Shader graph editor operation returned null");
        }
        replaceGraph(name, replacement);
    }

    public void addNode(ShaderNode node, float x, float y) {
        if (node == null) {
            throw new FdxException("Shader graph editor node cannot be null");
        }
        String graphId = activeGraph().id().value();
        execute(command("Add " + node.definitionId(), current -> {
            ShaderGraph graph = current.document().graph(graphId);
            ShaderGraph replacement =
                    ShaderGraphSemanticEdits.addNode(graph, node);
            ShaderGraphEditorDocument document =
                    current.document().withGraph(replacement);
            ShaderGraphEditorLayout layout =
                    current.layout().reconcile(document).withNode(graphId,
                            ShaderGraphEditorNode.of(node.id().value(), x, y,
                                    DEFAULT_NODE_WIDTH,
                                    DEFAULT_NODE_HEIGHT, false));
            return new ShaderGraphEditorState(document, layout);
        }));
        selectNode(node.id().value());
    }

    public void duplicateNode(String nodeId, String duplicateId,
            float offsetX, float offsetY) {
        String graphId = activeGraph().id().value();
        execute(command("Duplicate node", current -> {
            ShaderGraph graph = current.document().graph(graphId);
            ShaderGraph replacement = ShaderGraphSemanticEdits
                    .duplicateNode(graph, nodeId, duplicateId);
            ShaderGraphEditorDocument document =
                    current.document().withGraph(replacement);
            ShaderGraphEditorData graphLayout =
                    current.layout().graph(graphId);
            ShaderGraphEditorNode source = layoutNode(graphLayout, nodeId);
            ShaderGraphEditorNode duplicate = ShaderGraphEditorNode.of(
                    duplicateId, source.x() + offsetX,
                    source.y() + offsetY, source.width(), source.height(),
                    source.collapsed());
            ShaderGraphEditorLayout layout =
                    current.layout().reconcile(document)
                            .withNode(graphId, duplicate);
            return new ShaderGraphEditorState(document, layout);
        }));
        selectNode(duplicateId);
    }

    public void removeNodes(String... nodeIds) {
        String graphId = activeGraph().id().value();
        execute(command("Remove nodes", current -> {
            ShaderGraph replacement = ShaderGraphSemanticEdits.removeNodes(
                    current.document().graph(graphId), nodeIds);
            ShaderGraphEditorDocument document =
                    current.document().withGraph(replacement);
            return new ShaderGraphEditorState(document,
                    current.layout().reconcile(document));
        }));
        validateSelection();
    }

    public void connect(ShaderEndpoint source, ShaderEndpoint target) {
        editActiveGraph("Connect nodes",
                graph -> ShaderGraphSemanticEdits.connect(
                        graph, source, target));
    }

    public void disconnect(ShaderEndpoint target) {
        editActiveGraph("Disconnect input",
                graph -> ShaderGraphSemanticEdits.disconnect(graph, target));
    }

    public void setNodeProperty(String nodeId,
            ShaderNodeProperty property) {
        editActiveGraph("Edit node property",
                graph -> ShaderGraphSemanticEdits.setNodeProperty(
                        graph, nodeId, property));
    }

    public void renameNode(String nodeId, String replacementId) {
        String graphId = activeGraph().id().value();
        execute(command("Rename node", current -> {
            ShaderGraph replacement = ShaderGraphSemanticEdits.renameNode(
                    current.document().graph(graphId),
                    nodeId, replacementId);
            ShaderGraphEditorDocument document =
                    current.document().withGraph(replacement);
            ShaderGraphEditorLayout layout =
                    current.layout().reconcile(document);
            ShaderGraphEditorNode old = layoutNode(
                    current.layout().graph(graphId), nodeId);
            layout = layout.withNode(graphId, ShaderGraphEditorNode.of(
                    replacementId, old.x(), old.y(), old.width(),
                    old.height(), old.collapsed()));
            return new ShaderGraphEditorState(document, layout);
        }));
        if (selectedNodeId.equals(nodeId)) {
            selectedNodeId = replacementId;
            notifyListeners();
        }
    }

    public void parameters(ShaderGraphParameter... values) {
        editActiveGraph("Edit parameters",
                graph -> ShaderGraphSemanticEdits.parameters(graph, values));
    }

    public void resources(ShaderGraphResource... values) {
        editActiveGraph("Edit resources",
                graph -> ShaderGraphSemanticEdits.resources(graph, values));
    }

    public void outputs(ShaderGraphOutput... values) {
        editActiveGraph("Edit outputs",
                graph -> ShaderGraphSemanticEdits.outputs(graph, values));
    }

    public void dependencies(ShaderGraphDependency... values) {
        editActiveGraph("Edit dependencies",
                graph -> ShaderGraphSemanticEdits.dependencies(graph, values));
    }

    public void moveNode(String nodeId, float x, float y) {
        String graphId = activeGraph().id().value();
        execute(command("Move node", current -> {
            ShaderGraphEditorNode node = layoutNode(
                    current.layout().graph(graphId), nodeId);
            ShaderGraphEditorNode moved = ShaderGraphEditorNode.of(
                    nodeId, x, y, node.width(), node.height(),
                    node.collapsed());
            return new ShaderGraphEditorState(current.document(),
                    current.layout().withNode(graphId, moved));
        }));
    }

    public void setNodeCollapsed(String nodeId, boolean collapsed) {
        String graphId = activeGraph().id().value();
        execute(command("Toggle node", current -> {
            ShaderGraphEditorNode node = layoutNode(
                    current.layout().graph(graphId), nodeId);
            ShaderGraphEditorNode replacement = ShaderGraphEditorNode.of(
                    nodeId, node.x(), node.y(), node.width(), node.height(),
                    collapsed);
            return new ShaderGraphEditorState(current.document(),
                    current.layout().withNode(graphId, replacement));
        }));
    }

    public void viewport(float panX, float panY, float zoom) {
        String graphId = activeGraph().id().value();
        execute(command("Move viewport", current ->
                new ShaderGraphEditorState(current.document(),
                        current.layout().withViewport(
                                graphId, panX, panY, zoom))));
    }

    public ShaderGraphEditorCompileRequest beginCompilation(
            ShaderGraphEditorCompileSettings settings) {
        if (settings == null) {
            throw new FdxException(
                    "Shader graph editor compile settings cannot be null");
        }
        long generation = ++nextCompilationGeneration;
        latestRequestedGeneration = generation;
        return new ShaderGraphEditorCompileRequest(generation,
                semanticRevision, document(), settings);
    }

    /**
     * Accepts only the newest generation for the current semantic revision and
     * hash. A failed accepted result updates diagnostics but not last-good
     * preview state.
     *
     * @return true when the result was accepted
     */
    public boolean completeCompilation(
            ShaderGraphEditorCompilation compilation) {
        if (compilation == null
                || compilation.generation() != latestRequestedGeneration
                || compilation.semanticRevision() != semanticRevision
                || !compilation.semanticHash().equals(
                        document().semanticHash())) {
            return false;
        }
        latestCompilation = compilation;
        if (compilation.success()) {
            lastGoodCompilation = compilation;
        }
        notifyListeners();
        return true;
    }

    public boolean completeCompilation(
            ShaderGraphEditorCompilation compilation,
            ShaderGraphEditorPreviewHost previewHost,
            ShaderGraphEditorPreviewMode previewMode) {
        boolean accepted = completeCompilation(compilation);
        if (accepted && compilation.success() && previewHost != null) {
            ShaderGraphEditorPreviewMode mode = previewMode != null
                    ? previewMode : ShaderGraphEditorPreviewMode
                            .defaultFor(document());
            if (!mode.supports(document())) {
                throw new FdxException("Preview mode " + mode
                        + " does not support " + document().type());
            }
            previewHost.present(compilation, mode);
        }
        return accepted;
    }

    public ShaderGraphEditorCompilation latestCompilation() {
        return latestCompilation;
    }

    public ShaderGraphEditorCompilation lastGoodCompilation() {
        return lastGoodCompilation;
    }

    private void applyState(ShaderGraphEditorState replacement) {
        ShaderGraphEditorState reconciled = new ShaderGraphEditorState(
                replacement.document(), replacement.layout());
        boolean semanticChanged =
                !state.document().equals(reconciled.document());
        boolean layoutChanged = !state.layout().equals(reconciled.layout());
        state = reconciled;
        if (semanticChanged) {
            semanticRevision++;
            latestCompilation = null;
        }
        if (layoutChanged) {
            layoutRevision++;
        }
        if (semanticChanged || layoutChanged) {
            notifyListeners();
        }
    }

    private void push(Deque<HistoryEntry> history, HistoryEntry entry) {
        history.addLast(entry);
        while (history.size() > historyCapacity) {
            history.removeFirst();
        }
    }

    private void requireNoTransaction(String operation) {
        if (transactionStart != null) {
            throw new FdxException("Cannot " + operation
                    + " during shader graph editor transaction "
                    + transactionName);
        }
    }

    private void validateSelection() {
        if (!selectedNodeId.isEmpty()
                && activeGraph().node(
                        io.github.libfdx.graphics.shadergraph.model.ShaderGraphId
                                .of(selectedNodeId)) == null) {
            selectedNodeId = "";
            notifyListeners();
        }
    }

    private void notifyListeners() {
        ShaderGraphEditorSessionListener[] snapshot = listeners.toArray(
                ShaderGraphEditorSessionListener[]::new);
        for (ShaderGraphEditorSessionListener listener : snapshot) {
            listener.changed(this);
        }
    }

    private static ShaderGraphEditorCommand command(String name,
            UnaryOperator<ShaderGraphEditorState> operation) {
        if (name == null || name.trim().isEmpty() || operation == null) {
            throw new FdxException(
                    "Shader graph editor command is incomplete");
        }
        return new ShaderGraphEditorCommand() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ShaderGraphEditorState apply(
                    ShaderGraphEditorState state) {
                return operation.apply(state);
            }
        };
    }

    private static ShaderGraphEditorNode layoutNode(
            ShaderGraphEditorData data, String nodeId) {
        if (data != null) {
            for (ShaderGraphEditorNode node : data.nodes()) {
                if (node.nodeId().value().equals(nodeId)) {
                    return node;
                }
            }
        }
        throw new FdxException("Shader graph editor layout has no node "
                + nodeId);
    }

    private record HistoryEntry(String name,
            ShaderGraphEditorState before,
            ShaderGraphEditorState after) {
    }
}
