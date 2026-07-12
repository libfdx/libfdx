package io.github.libfdx.ui;

import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class UiChildOrderingTest {
    private static final UiContent STABLE_COLUMN_CONTENT = scope -> scope.text("stable");
    private static final UiContent STABLE_ROOT_CONTENT = scope -> scope.column(STABLE_COLUMN_CONTENT);
    private static final UiContent STABLE_WINDOW_CONTENT = scope -> scope.text("window body");
    private static final UiContent STABLE_ANIMATED_CONTENT = scope -> scope.text("animated body");
    private static final List<String> STABLE_ITEMS = List.of("first", "second", "third");
    private static final UiSize STABLE_CUSTOM_SIZE = new UiSize(80.0f, 24.0f);
    private static final UiMeasureFunction STABLE_MEASURE = constraints -> STABLE_CUSTOM_SIZE;
    private static final UiDrawFunction STABLE_DRAW = (draw, bounds) -> { };
    private static final UiCustomContent STABLE_CUSTOM = context -> {
        context.measure(STABLE_MEASURE);
        context.draw(STABLE_DRAW);
    };
    private static final UiItemContent<String> STABLE_ITEM = (scope, item) -> scope.text(item);
    private static final UiTooltip STABLE_TOOLTIP = UiTooltip.text("stable tooltip");
    private static final UiModifier HIT_MODIFIER = UiModifier.none().size(120.0f, 40.0f).focusable(true);
    private static final Runnable NO_OP = () -> { };
    private static final UiModifier STABLE_CONTROL_MODIFIER = UiModifier.none().size(140.0f, 42.0f);
    private static final UiContent HIT_CONTENT = scope -> scope.button("hit", HIT_MODIFIER, NO_OP);

    @Test
    void ordersLayersAndInvalidatesWhenWindowMovesToFront() {
        UiRoot root = new UiRoot(null, null, null, null);
        UiNode parent = node(UiNodeType.ROOT, "root");
        UiNode content = node(UiNodeType.PANEL, "content");
        UiWindowState firstState = new UiWindowState(0.0f, 0.0f, 100.0f, 100.0f);
        UiWindowState secondState = new UiWindowState(0.0f, 0.0f, 100.0f, 100.0f);
        UiNode firstWindow = window("first-window", firstState);
        UiNode secondWindow = window("second-window", secondState);
        UiNode popup = node(UiNodeType.POPUP, "popup");
        UiNode modal = node(UiNodeType.MODAL, "modal");
        addChildren(parent, modal, secondWindow, content, popup, firstWindow);

        root.ensureWindowZOrder(firstState);
        root.ensureWindowZOrder(secondState);
        List<UiNode> ordered = root.renderChildren(parent);

        assertEquals(List.of(content, firstWindow, secondWindow, popup, modal), ordered);
        assertSame(ordered, root.renderChildren(parent));
        assertEquals(List.of(modal, popup, secondWindow, firstWindow, content), parent.orderedChildren(true));

        root.bringWindowToFront(firstState);

        assertEquals(List.of(content, secondWindow, firstWindow, popup, modal), root.renderChildren(parent));
        assertEquals(List.of(modal, popup, firstWindow, secondWindow, content), parent.orderedChildren(true));
        root.dispose();
    }

    @Test
    void warmedCompositionOrderingAllocatesNoPerFrameObjects() {
        UiRoot root = new UiRoot(null, null, null, null);
        UiNode parent = node(UiNodeType.ROOT, "root");
        UiNode content = node(UiNodeType.PANEL, "content");
        UiWindowState firstState = new UiWindowState(0.0f, 0.0f, 100.0f, 100.0f);
        UiWindowState secondState = new UiWindowState(0.0f, 0.0f, 100.0f, 100.0f);
        UiNode firstWindow = window("first-window", firstState);
        UiNode secondWindow = window("second-window", secondState);
        UiNode popup = node(UiNodeType.POPUP, "popup");
        UiNode modal = node(UiNodeType.MODAL, "modal");
        root.ensureWindowZOrder(firstState);
        root.ensureWindowZOrder(secondState);

        for (int i = 0; i < 2_000; i++) {
            composeAndOrder(root, parent, modal, secondWindow, content, popup, firstWindow);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int checksum = 0;
        for (int i = 0; i < 2_000; i++) {
            checksum += composeAndOrder(root, parent, modal, secondWindow, content, popup, firstWindow);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(20_000, checksum);
        assertTrue(allocated <= 512L, "Expected no post-warm-up child-order churn, allocated " + allocated
                + " bytes");
        root.dispose();
    }

    @Test
    void warmedRootUpdateAllocatesNoIteratorObjects() {
        UiRoot root = new UiRoot(null, null, null, null);
        root.animatable("object", "ready");
        root.floatAnimatable("float", 1.0f);
        for (int i = 0; i < 2_000; i++) {
            root.update(0.0f);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 2_000; i++) {
            root.update(0.001f);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertTrue(root.elapsedSeconds() > 1.9f);
        assertTrue(allocated <= 512L, "Expected no post-warm-up UI update iterator churn, allocated " + allocated
                + " bytes");
        root.dispose();
    }

    @Test
    void warmedStableRecompositionAllocatesNoFrameworkObjects() {
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(800, 600);
        root.setContent(STABLE_ROOT_CONTENT);
        for (int i = 0; i < 10_000; i++) {
            root.requestCompose();
            root.update(0.0f);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 2_000; i++) {
            root.requestCompose();
            root.update(0.0f);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertTrue(allocated <= 512L, "Expected no post-warm-up stable-composition churn, allocated " + allocated
                + " bytes");
        root.dispose();
    }

    @Test
    void warmedStableWidgetRecompositionAllocatesNoFrameworkObjects() {
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(1280, 720);
        root.setContent(new StableWidgetContent());
        for (int i = 0; i < 10_000; i++) {
            root.requestCompose();
            root.update(0.0f);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 2_000; i++) {
            root.requestCompose();
            root.update(0.0f);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertTrue(allocated <= 512L, "Expected no post-warm-up built-in widget churn, allocated " + allocated
                + " bytes");
        root.dispose();
    }

    @Test
    void warmedStableInfrastructureRecompositionAllocatesNoFrameworkObjects() {
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(1280, 720);
        root.setContent(new StableInfrastructureContent());
        for (int i = 0; i < 10_000; i++) {
            root.requestCompose();
            root.update(0.0f);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 2_000; i++) {
            root.requestCompose();
            root.update(0.0f);
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertTrue(allocated <= 512L, "Expected no post-warm-up UI infrastructure churn, allocated " + allocated
                + " bytes");
        root.dispose();
    }

    @Test
    void widgetDescriptorReuseTracksChangedInputs() {
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(1280, 720);
        MutableWidgetContent content = new MutableWidgetContent();
        root.setContent(content);
        root.update(0.0f);

        UiNode sliderNode = content.sliderNode;
        UiNode progressNode = content.progressNode;
        UiNode tabsNode = content.tabsNode;
        UiNode windowNode = content.windowNode;
        UiSliderModel sliderModel = (UiSliderModel) sliderNode.descriptor();
        UiProgressBarModel progressModel = (UiProgressBarModel) progressNode.descriptor();
        UiTabsModel tabsModel = (UiTabsModel) tabsNode.descriptor();
        UiWindowModel windowModel = (UiWindowModel) windowNode.descriptor();

        content.slider = Ui.state(4.0f);
        content.progress = Ui.state(8.0f);
        content.activeTab = Ui.state(1);
        content.minimum = 2.0f;
        content.maximum = 10.0f;
        content.labels = new String[] {"Alpha", "Beta"};
        content.window = new UiWindowState(48.0f, 64.0f, 360.0f, 240.0f);
        root.requestCompose();
        root.update(0.0f);

        assertSame(sliderNode, content.sliderNode);
        assertSame(progressNode, content.progressNode);
        assertSame(tabsNode, content.tabsNode);
        assertSame(windowNode, content.windowNode);
        assertNotSame(sliderModel, content.sliderNode.descriptor());
        assertNotSame(progressModel, content.progressNode.descriptor());
        assertSame(tabsModel, content.tabsNode.descriptor());
        assertNotSame(windowModel, content.windowNode.descriptor());
        assertEquals(2.0f, content.sliderNode.sliderMinimum());
        assertEquals(10.0f, content.sliderNode.sliderMaximum());
        assertEquals(2, tabsModel.count());
        assertEquals("Alpha", tabsModel.label(0));
        assertEquals("Beta", tabsModel.label(1));
        assertSame(content.activeTab, tabsModel.activeIndexState());
        assertSame(content.window, ((UiWindowModel) content.windowNode.descriptor()).state());
        root.dispose();
    }

    @Test
    void observableStateListenersRemainUniqueAndMutationSafe() {
        UiFloatState state = Ui.state(0.0f);
        int[] calls = new int[2];
        UiStateListener[] selfRemoving = new UiStateListener[1];
        selfRemoving[0] = ignored -> {
            calls[0]++;
            state.removeListener(selfRemoving[0]);
        };
        UiStateListener retained = ignored -> calls[1]++;
        state.addListener(selfRemoving[0]);
        state.addListener(selfRemoving[0]);
        state.addListener(retained);

        state.set(1.0f);
        state.set(2.0f);

        assertEquals(1, calls[0]);
        assertEquals(2, calls[1]);
    }

    @Test
    void immutableModifierReturnsItselfForNoOpChanges() {
        UiModifier modifier = UiModifier.none().focusable(true).alpha(0.75f);
        UiAnimationSpec animation = UiAnimationSpec.defaultSpec().fade();

        assertSame(modifier, modifier.focusable(true));
        assertSame(modifier, modifier.alpha(0.75f));
        assertSame(animation, animation.fade());
        assertSame(animation, animation.durationMillis(animation.durationMillis()));
    }

    @Test
    void warmedHitTestingAllocatesNoResultObjects() {
        UiRoot root = new UiRoot(null, null, null, null);
        root.resize(800, 600);
        root.setContent(HIT_CONTENT);
        root.update(0.0f);
        for (int i = 0; i < 10_000; i++) {
            root.hitTest(10.0f, 10.0f);
        }

        java.lang.management.ThreadMXBean platformBean = ManagementFactory.getThreadMXBean();
        assumeTrue(platformBean instanceof ThreadMXBean);
        ThreadMXBean bean = (ThreadMXBean)platformBean;
        assumeTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) {
            bean.setThreadAllocatedMemoryEnabled(true);
        }
        long threadId = Thread.currentThread().threadId();
        long before = bean.getThreadAllocatedBytes(threadId);
        int hits = 0;
        for (int i = 0; i < 2_000; i++) {
            if (root.hitTest(10.0f, 10.0f) != null) {
                hits++;
            }
        }
        long allocated = bean.getThreadAllocatedBytes(threadId) - before;

        assertEquals(2_000, hits);
        assertTrue(allocated <= 512L, "Expected no post-warm-up hit-result churn, allocated " + allocated
                + " bytes");
        root.dispose();
    }

    private static final class StableWidgetContent implements UiContent {
        private final UiFloatState slider = Ui.state(0.5f);
        private final UiFloatState progress = Ui.state(0.75f);
        private final UiIntState activeTab = Ui.state(0);
        private final UiState<String> text = Ui.state("stable text");
        private final UiWindowState window = new UiWindowState(640.0f, 32.0f, 320.0f, 220.0f);
        private final String[] labels = {"One", "Two", "Three"};

        @Override
        public void build(UiScope scope) {
            scope.slider(slider, 0.0f, 1.0f);
            scope.progressBar(progress, 0.0f, 1.0f);
            scope.tabs(activeTab, labels);
            scope.textArea(text);
            scope.button("Stable button", STABLE_CONTROL_MODIFIER, NO_OP);
            scope.window("Stable window", window, STABLE_WINDOW_CONTENT);
        }
    }

    private static final class StableInfrastructureContent implements UiContent {
        @Override
        public void build(UiScope scope) {
            scope.animatable("object", "stable");
            scope.floatAnimatable("float", 0.5f);
            scope.scrollState("scroll");
            scope.listState("list");
            scope.items(STABLE_ITEMS, null, STABLE_ITEM);
            scope.custom("stable-custom", UiModifier.none(), STABLE_CUSTOM);
            scope.animateContentSize(STABLE_ANIMATED_CONTENT);
            scope.animatedVisibility(false, UiAnimationSpec.defaultSpec(), STABLE_ANIMATED_CONTENT);
            scope.tooltip(STABLE_TOOLTIP, STABLE_ANIMATED_CONTENT);
        }
    }

    private static final class MutableWidgetContent implements UiContent {
        private UiFloatState slider = Ui.state(0.5f);
        private UiFloatState progress = Ui.state(0.75f);
        private UiIntState activeTab = Ui.state(0);
        private UiWindowState window = new UiWindowState(640.0f, 32.0f, 320.0f, 220.0f);
        private float minimum;
        private float maximum = 1.0f;
        private String[] labels = {"One", "Two", "Three"};
        private UiNode sliderNode;
        private UiNode progressNode;
        private UiNode tabsNode;
        private UiNode windowNode;

        @Override
        public void build(UiScope scope) {
            sliderNode = scope.slider(slider, minimum, maximum);
            progressNode = scope.progressBar(progress, minimum, maximum);
            tabsNode = scope.tabs(activeTab, labels);
            windowNode = scope.window("Mutable window", window, STABLE_WINDOW_CONTENT);
        }
    }

    private static int composeAndOrder(UiRoot root, UiNode parent, UiNode modal, UiNode secondWindow,
            UiNode content, UiNode popup, UiNode firstWindow) {
        parent.begin(null, UiModifier.none());
        parent.addChild(modal);
        parent.addChild(secondWindow);
        parent.addChild(content);
        parent.addChild(popup);
        parent.addChild(firstWindow);
        List<UiNode> backToFront = root.renderChildren(parent);
        List<UiNode> frontToBack = parent.orderedChildren(true);
        return backToFront.size() + frontToBack.size();
    }

    private static UiNode node(UiNodeType type, String identity) {
        UiNode node = new UiNode(type, identity);
        node.begin(null, UiModifier.none());
        return node;
    }

    private static UiNode window(String identity, UiWindowState state) {
        UiNode node = node(UiNodeType.WINDOW, identity);
        node.descriptor(new UiWindowModel(state));
        return node;
    }

    private static void addChildren(UiNode parent, UiNode... children) {
        for (int i = 0; i < children.length; i++) {
            parent.addChild(children[i]);
        }
    }
}
