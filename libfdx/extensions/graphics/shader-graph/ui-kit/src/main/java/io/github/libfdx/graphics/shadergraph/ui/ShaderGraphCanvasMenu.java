package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shadergraph.model.ShaderGraphId;
import io.github.libfdx.input.Key;
import io.github.libfdx.ui.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Transient pointer-anchored actions and keyboard-searchable node picker on a focused canvas. */
final class ShaderGraphCanvasMenu {
    record Action(String label, Runnable command) { }
    private record Entry(String key, String category, String keywords, Action action) { }

    private static final int MAX_ROWS = 11;
    private static final UiModifier SEARCH_FIELD = Ui.modifier().fillWidth().height(30).semanticLabel("Search nodes");
    private final ShaderGraphEditorSession session;
    private final ShaderGraphEditorPalette palette;
    private final TreeMap<String, List<Entry>> categories = new TreeMap<>();
    private final Map<String, Entry> entries = new HashMap<>();
    private final List<Action> actions = new ArrayList<>();
    private final UiState<String> query = Ui.state("");
    private final UiBooleanState visible = Ui.state(false);
    private final UiBooleanState searching = Ui.state(false);
    private UiTreeState unfiltered, tree;
    private ShaderGraphWireSocket origin;
    private float anchorX, anchorY, graphX, graphY;
    private int nextId = 1;
    private String heading, appliedQuery;
    private long revision;
    private ShaderGraphEditorLayout anchorLayout;

    ShaderGraphCanvasMenu(ShaderGraphEditorSession session, ShaderGraphEditorPalette palette) {
        this.session = session;
        this.palette = palette;
    }

    void open(float x, float y, float graphX, float graphY, String title, List<Action> actions) {
        begin(x, y, graphX, graphY);
        if (title.isEmpty()) openPicker();
        else {
            heading = title;
            this.actions.addAll(actions);
            this.actions.add(new Action("Add node…", this::openPicker));
        }
    }

    private void begin(float x, float y, float graphX, float graphY) {
        close();
        this.graphX = graphX;
        this.graphY = graphY;
        anchorX = x;
        anchorY = y;
        revision = session.semanticRevision();
        anchorLayout = session.layout();
        visible.set(true);
    }

    void openForWire(float x, float y, float graphX, float graphY, ShaderGraphWireSocket from) {
        begin(x, y, graphX, graphY);
        origin = from;
        String id = availableId();
        // Inspect each concrete factory result once. The node offered here is the very one inserted on selection.
        for (var template : palette.forGraph(session.activeGraph())) {
            var node = template.create(id);
            var labels = ShaderGraphEditorPortLabels.of(node, session.document());
            var opposite = from.input() ? labels.outputs() : labels.inputs();
            for (int i = 0; i < opposite.length; i++) {
                var socket = opposite[i];
                if (!socket.port().type().equals(from.type())) continue;
                var port = ShaderGraphWireSocket.port(node.id(), socket.port(), !from.input());
                float nodeX = from.input() ? graphX - ShaderGraphEditorLayout.newNode(node, 0, 0).width() : graphX;
                float nodeY = graphY - ShaderGraphEditorLayout.socketY(i);
                String label = template.label() + " (" + socket.name() + ")";
                var action = new Action(label, () -> {
                    session.addConnectedNode(node, nodeX, nodeY, from, port);
                    nextId++;
                });
                addEntry(template, action);
                break;
            }
        }
        openPicker();
    }

    ShaderGraphWireSocket wireOrigin() {
        return isOpen() ? origin : null;
    }

    boolean isOpen() {
        if (visible.get() && (revision != session.semanticRevision() || anchorLayout != session.layout())) close();
        return visible.get();
    }

    void close() {
        visible.set(false);
        anchorLayout = null;
        searching.set(false);
        actions.clear();
        categories.clear();
        entries.clear();
        tree = unfiltered = null;
        origin = null;
    }

    private void addNode(ShaderGraphEditorNodeTemplate template, float x, float y) {
        session.addNode(template.create(availableId()), x, y);
        nextId++;
    }

    private String availableId() {
        while (session.activeGraph().node(ShaderGraphId.of("node_" + nextId)) != null) nextId++;
        return "node_" + nextId;
    }

    private void addEntry(ShaderGraphEditorNodeTemplate template, Action action) {
        String category = template.category();
        String keywords = (category + " " + action.label() + " " + template.definitionId()).toLowerCase(Locale.ROOT);
        Entry entry = new Entry("node-" + entries.size(), category, keywords.replace(":", ""), action);
        entries.put(entry.key(), entry);
        categories.computeIfAbsent(category, ignored -> new ArrayList<>()).add(entry);
    }

    private void openPicker() {
        visible.set(true);
        searching.set(true);
        query.set("");
        heading = "Search nodes…";
        if (origin == null) {
            for (var template : palette.forGraph(session.activeGraph())) {
                addEntry(template, new Action(template.label(), () -> addNode(template, graphX, graphY)));
            }
        } else {
            heading = "Search nodes with " + ShaderGraphEditorPortLabels.typeName(origin.type())
                    + (origin.input() ? " output…" : " input…");
        }
        unfiltered = makeTree("", origin != null && entries.size() + categories.size() <= MAX_ROWS);
        tree = unfiltered;
        appliedQuery = "";
    }

    private UiTreeState makeTree(String filter, boolean expand) {
        List<UiTreeItem> roots = new ArrayList<>();
        String firstMatch = null;
        for (var group : categories.entrySet()) {
            List<UiTreeItem> matches = new ArrayList<>();
            for (Entry entry : group.getValue()) {
                if (!entry.keywords().contains(filter)) continue;
                matches.add(new UiTreeItem(entry.key(), entry.action().label()));
                if (firstMatch == null) firstMatch = entry.key();
            }
            if (!matches.isEmpty()) roots.add(new UiTreeItem(group.getKey(), group.getKey(), matches.toArray(UiTreeItem[]::new)));
        }
        UiTreeState state = new UiTreeState(roots.toArray(UiTreeItem[]::new)).animatePlacement(false);
        if (!expand) for (UiTreeItem group : roots) state.setExpanded(group.key(), false);
        if (!roots.isEmpty()) state.select(expand ? firstMatch : roots.get(0).key());
        return state;
    }

    private void syncQuery() {
        String filter = query.get().trim().toLowerCase(Locale.ROOT).replace(":", "");
        if (filter.equals(appliedQuery)) return;
        appliedQuery = filter;
        tree = filter.isEmpty() ? unfiltered : makeTree(filter, true);
    }

    void build(UiScope ui, UiCustomContext context) {
        if (!isOpen()) return;
        boolean picker = searching.get();
        if (picker) syncQuery();
        ui.popup(Ui.popup("graph-actions").at(anchorX, anchorY).blockingInput(true).onDismiss(this::close),
                popup -> popup.panel(Ui.modifier().width(284).padding(4).gap(2), panel -> {
                    panel.text(heading, Ui.modifier().fillWidth().height(20));
                    if (picker) {
                        context.initialFocus(panel.textField(SEARCH_FIELD, query));
                        int count = tree.visibleCount();
                        if (count == 0) panel.text("No matching nodes");
                        else panel.tree(Ui.modifier().fillWidth().height(26 * Math.min(MAX_ROWS, count)), tree, this::choose);
                    } else {
                        for (Action action : actions) {
                            var button = panel.button(action.label(), Ui.modifier().fillWidth().height(26), () -> run(action));
                            if (action == actions.get(0)) context.initialFocus(button);
                        }
                    }
                }));
    }

    private void run(Action action) {
        close();
        action.command().run();
    }

    private void choose(String key) {
        if (key == null) return;
        Entry entry = entries.get(key);
        if (entry != null) run(entry.action());
        else {
            tree.select(key);
            tree.setExpanded(key, !tree.isExpanded(key));
        }
    }

    boolean text(String text) {
        if (!isOpen() || searching.get() || text == null || text.isEmpty()
                || text.codePoints().anyMatch(Character::isISOControl)) return false;
        openPicker();
        query.set(text);
        return true;
    }

    boolean key(Key key) {
        if (!isOpen()) return false;
        if (key == Key.ESCAPE) {
            close();
            return true;
        }
        if (!searching.get()) return false;
        syncQuery();
        String selected = tree.selectedKey();
        if (key == Key.ENTER) choose(selected);
        else if (key == Key.UP || key == Key.DOWN) tree.select(tree.nextVisibleKey(selected, key == Key.UP));
        else if ((key == Key.LEFT || key == Key.RIGHT) && query.get().isEmpty() && selected != null) {
            Entry entry = entries.get(selected);
            if (entry == null) {
                if (key == Key.RIGHT && tree.isExpanded(selected)) tree.select(tree.nextVisibleKey(selected, false));
                else tree.setExpanded(selected, key == Key.RIGHT);
            } else if (key == Key.LEFT) tree.select(entry.category());
        } else return false;
        return true;
    }

}
