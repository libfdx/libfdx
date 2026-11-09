package io.github.libfdx.tests.utils.uikittest.showcase;

import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcaseThemes.THEME_NAMES;

import io.github.libfdx.assets.AssetDescriptor;
import io.github.libfdx.assets.AssetHandle;
import io.github.libfdx.assets.AssetManager;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.core.FdxException;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.InputAdapter;
import io.github.libfdx.input.Key;
import io.github.libfdx.input.KeyEvent;
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.input.PointerEvent;
import io.github.libfdx.tests.utils.uikittest.compose.UiKitSettingsPanel;
import io.github.libfdx.tests.utils.uikittest.compose.UiKitShowcaseContent;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitButtonsSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitCheckboxesSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitDisclosureSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitDrawingSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitListsSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitLoadingSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitOverlaysSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitProgressBarsSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitScrollViewSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitSlidersSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTabsSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTextFieldsSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitThemesSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTogglesSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTooltipsSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitWindowsSection;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTheme;

/** Owns the showcase state, assets, root setup, and preview navigation. */
public final class UiKitShowcase {
    private static final String LOGO_ASSET = "fdx_logo_dark.png";
    private static final String PATCH_ASSET = "ui_panel_patch.png";
    private static final AssetDescriptor<Texture> LOGO_TEXTURE = AssetDescriptor.of(LOGO_ASSET, Texture.class);
    private static final AssetDescriptor<Texture> PANEL_PATCH = AssetDescriptor.of(PATCH_ASSET, Texture.class);
    private static final float DEFAULT_SAFE_AREA = 0.0f;
    private static final float UI_SCALE_INITIAL = 1.0f;
    public static final int SECTION_BUTTONS = 0;
    public static final int SECTION_CHECKBOXES = 1;
    public static final int SECTION_SLIDERS = 2;
    public static final int SECTION_PROGRESS_BARS = 3;
    public static final int SECTION_TABS = 4;
    public static final int SECTION_TEXT_FIELDS = 5;
    public static final int SECTION_LISTS = 6;
    public static final int SECTION_DRAWING = 7;
    public static final int SECTION_WINDOWS = 8;
    public static final int SECTION_SCROLLVIEW = 9;
    private static final int SECTION_OVERLAYS = 10;
    public static final int SECTION_TOOLTIPS = 11;
    public static final int SECTION_TOGGLES = 12;
    public static final int SECTION_LOADING = 13;
    public static final int SECTION_COLLAPSE = 14;
    public static final int SECTION_THEMES = 15;
    private final boolean previewMode;
    private Display display;
    private UiRoot root;
    private Input previewNavigationInput;
    private InputAdapter previewNavigation;
    private UiIntState clickCount;
    private UiBooleanState checked;
    private UiBooleanState visualDebug;
    private UiBooleanState showDetails;
    private UiBooleanState scaleGlobally;
    private UiBooleanState compactChecked;
    private UiFloatState uiScale;
    private UiFloatState volume;
    private UiIntState activeSection;
    private UiIntState activeTheme;
    private UiState<String> name;
    private UiScrollState navScroll;
    private TextureRegion panelPatchRegion;
    private UiTheme[] demoThemes;
    private float initialUiScale;
    private boolean scaleGloballyActive;
    private Section[] sections;
    private UiContent content;
    private UiKitButtonsSection buttons;
    private UiKitListsSection lists;
    private UiKitTabsSection tabs;
    private UiKitTextFieldsSection textFields;
    private UiKitWindowsSection windows;
    private UiKitScrollViewSection scrollView;
    private UiKitOverlaysSection overlays;
    private UiKitTogglesSection toggles;

    public UiKitShowcase(boolean previewMode) {
        this.previewMode = previewMode;
    }

    public static void loadAssets(AssetManager assets) {
        assets.load(LOGO_TEXTURE);
        assets.load(PANEL_PATCH);
    }

    /**
     * Prepares the showcase for a host-owned root and asset manager.
     * The host drives asset loading before composition and disposes the preview binding.
     */
    public static PreviewContent previewContent(UiRoot root, AssetManager assets) {
        return new PreviewContent(root, assets);
    }

    /** Owns one preview's showcase state and navigation. The root and assets are borrowed. */
    public static final class PreviewContent implements Disposable {
        private final UiKitShowcase showcase = new UiKitShowcase(true);
        private final UiContent content = UiKitShowcaseContent.deferred(this::preparedContent);
        private boolean prepared;
        private boolean disposed;

        private PreviewContent(UiRoot root, AssetManager assets) {
            AssetHandle<Texture> logo = assets.load(LOGO_TEXTURE);
            AssetHandle<Texture> patch = assets.load(PANEL_PATCH);
            Runnable prepare = () -> {
                if (disposed || prepared || !logo.isLoaded() || !patch.isLoaded()) {
                    return;
                }
                showcase.initialize(assets, root.display(), false);
                showcase.configureRoot(root);
                showcase.installPreviewNavigation(root.input());
                prepared = true;
            };
            logo.future().onSuccess(texture -> prepare.run());
            patch.future().onSuccess(texture -> prepare.run());
        }

        public UiContent content() {
            return content;
        }

        private UiContent preparedContent() {
            if (!prepared || disposed) {
                throw new FdxException("UI Kit showcase assets are unavailable");
            }
            return showcase.content();
        }

        @Override
        public void dispose() {
            if (disposed) return;
            disposed = true;
            showcase.removePreviewNavigation();
            showcase.detachRoot();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

    }

    public void initialize(AssetManager loadedAssets, Display display, boolean validationActive) {
        this.display = display;
        Texture logo = loadedAssets.get(LOGO_ASSET, Texture.class);
        Texture patch = loadedAssets.get(PATCH_ASSET, Texture.class);
        panelPatchRegion = new TextureRegion(patch);
        demoThemes = createThemes();

        clickCount = Ui.state(0);
        checked = Ui.state(false);
        visualDebug = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.uiDebugLines", "false")));
        showDetails = Ui.state(true);
        scaleGlobally = Ui.state(Boolean.parseBoolean(System.getProperty("libfdx.test.scaleGlobally", "false")));
        compactChecked = Ui.state(true);
        initialUiScale = Float.parseFloat(System.getProperty("libfdx.test.uiScale",
                String.valueOf(UI_SCALE_INITIAL)));
        uiScale = Ui.state(initialUiScale);
        volume = Ui.state(0.35f);
        activeTheme = Ui.state(initialTheme());
        name = Ui.state("Player");
        navScroll = new UiScrollState();
        initializeSections(new TextureRegion(logo), validationActive);
        activeSection = Ui.state(initialSection());
    }

    private void initializeSections(TextureRegion logoRegion, boolean validationActive) {
        Runnable incrementClick = this::incrementClick;
        UiKitSlidersSection sliders = new UiKitSlidersSection(volume, this::compactViewport);
        lists = new UiKitListsSection();
        tabs = new UiKitTabsSection(volume, clickCount, lists.inventoryList, uiScale);
        textFields = new UiKitTextFieldsSection(name);
        UiKitDrawingSection drawing = new UiKitDrawingSection(sliders.sliderNormal, logoRegion);
        windows = new UiKitWindowsSection(checked, showDetails, incrementClick,
                () -> UiKitShowcaseContent.statusText(this), compactViewport());
        scrollView = new UiKitScrollViewSection(name, incrementClick);
        overlays = new UiKitOverlaysSection(incrementClick);
        buttons = new UiKitButtonsSection(clickCount, showDetails, incrementClick, overlays.showPopup::toggle);
        UiKitTooltipsSection tooltips = new UiKitTooltipsSection(this::compactViewport, incrementClick, compactChecked, name);
        toggles = new UiKitTogglesSection();
        UiKitSettingsPanel settings = new UiKitSettingsPanel(display, visualDebug, scaleGlobally, uiScale);

        sections = new Section[SECTION_THEMES + 1];
        sections[SECTION_BUTTONS] = new Section("Buttons", buttons);
        sections[SECTION_CHECKBOXES] = new Section("Checkboxes",
                new UiKitCheckboxesSection(checked, compactChecked, visualDebug));
        sections[SECTION_SLIDERS] = new Section("Sliders", sliders);
        sections[SECTION_PROGRESS_BARS] = new Section("Progress bars",
                new UiKitProgressBarsSection(volume, sliders.sliderWide, sliders.sliderDisabled));
        sections[SECTION_TABS] = new Section("Tabs", tabs);
        sections[SECTION_TEXT_FIELDS] = new Section("Text fields", textFields);
        sections[SECTION_LISTS] = new Section("Lists", lists);
        sections[SECTION_DRAWING] = new Section("Drawing", drawing);
        sections[SECTION_WINDOWS] = new Section("Windows", windows);
        sections[SECTION_SCROLLVIEW] = new Section("Scroll view", scrollView);
        sections[SECTION_OVERLAYS] = new Section("Overlays", overlays);
        sections[SECTION_TOOLTIPS] = new Section("Tooltips", tooltips);
        sections[SECTION_TOGGLES] = new Section("Toggles & choices", toggles);
        sections[SECTION_LOADING] = new Section("Loading & status", new UiKitLoadingSection(volume));
        sections[SECTION_COLLAPSE] = new Section("Disclosure panels", new UiKitDisclosureSection());
        sections[SECTION_THEMES] = new Section("Themes", new UiKitThemesSection(activeTheme, this::selectTheme));
        content = new UiKitShowcaseContent(this, drawing, settings, tooltips, incrementClick, previewMode,
                validationActive);
    }

    public void configureRoot(UiRoot root) {
        this.root = root;
        root.theme(theme(activeTheme.get()));
        root.safeArea(Ui.insets(Float.parseFloat(System.getProperty("libfdx.test.safeArea",
                        String.valueOf(DEFAULT_SAFE_AREA)))))
                .debugLines(visualDebug.get());
        scaleGloballyActive = scaleGlobally.get();
        if (scaleGloballyActive) {
            root.uiScale(uiScale.get());
        }
    }

    public void detachRoot() {
        root = null;
    }

    private void installPreviewNavigation(Input backendInput) {
        if (backendInput == null) {
            return;
        }
        previewNavigationInput = backendInput;
        previewNavigation = new InputAdapter() {
            public boolean keyDown(KeyEvent event) {
                if (event.key() == Key.PAGE_UP) {
                    activeSection.set(Math.floorMod(activeSection.get() - 1, sections.length));
                    return true;
                }
                if (event.key() == Key.PAGE_DOWN) {
                    activeSection.set((activeSection.get() + 1) % sections.length);
                    return true;
                }
                return false;
            }

            public boolean scrolled(PointerEvent event) {
                UiScrollState scroll = activeSectionScroll();
                float before = scroll.y();
                scroll.scrollBy(0, event.scrollY() * 24.0f);
                if (scroll.y() != before) {
                    root.requestCompose();
                }
                return true;
            }

        };
        previewNavigationInput.addProcessor(previewNavigation);
    }

    private void removePreviewNavigation() {
        if (previewNavigationInput != null) {
            previewNavigationInput.removeProcessor(previewNavigation);
            previewNavigationInput = null;
            previewNavigation = null;
        }
    }

    public void applyGlobalScale(Input input) {
        if (root == null || scaleGlobally == null || uiScale == null) {
            return;
        }
        boolean currentlyGlobal = scaleGlobally.get();
        if (scaleGloballyActive != currentlyGlobal) {
            if (!currentlyGlobal) {
                root.uiScale(1.0f);
            } else {
                root.uiScale(uiScale.get());
            }
            scaleGloballyActive = currentlyGlobal;
            return;
        }
        if (!scaleGloballyActive) {
            return;
        }
        if (input != null && input.isMouseButtonPressed(MouseButton.LEFT)) {
            return;
        }
        scaleGloballyActive = currentlyGlobal;
        if (Math.abs(root.uiScale() - uiScale.get()) > 0.0001f) {
            root.uiScale(uiScale.get());
        }
    }

    public UiContent content() {
        return content;
    }

    public int sectionCount() {
        return sections.length;
    }

    public String sectionLabel(int index) {
        return sections[index].label();
    }

    public String activeSectionName() {
        int index = activeSection != null ? activeSection.get() : SECTION_BUTTONS;
        if (index < 0 || index >= sections.length) {
            return sections[SECTION_BUTTONS].label();
        }
        return sections[index].label();
    }

    public boolean compactViewport() {
        if (display == null || display.width() <= 0) {
            return false;
        }
        float scale = 1.0f;
        if (root != null) {
            scale = root.uiScale();
            if (root.autoUiScale()) {
                scale *= display.contentScale();
            }
        } else {
            if (scaleGlobally != null && scaleGlobally.get() && uiScale != null) {
                scale = uiScale.get();
            }
            scale *= display.contentScale();
        }
        scale = Math.max(0.25f, Math.min(4.0f, scale));
        return display.width() / scale < 1100.0f;
    }

    public UiScrollState activeSectionScroll() {
        int index = activeSection != null ? activeSection.get() : SECTION_BUTTONS;
        if (index < 0 || index >= sections.length) {
            index = SECTION_BUTTONS;
        }
        return sections[index].scroll();
    }

    private int initialSection() {
        String requested = System.getProperty(previewMode ? "libfdx.uiPreview.section" : "libfdx.test.uiSection", "");
        if (requested == null || requested.length() == 0) {
            return SECTION_BUTTONS;
        }
        if ("collapse bars".equalsIgnoreCase(requested)) {
            return SECTION_COLLAPSE;
        }
        for (int i = 0; i < sections.length; i++) {
            if (sections[i].label().equalsIgnoreCase(requested)) {
                return i;
            }
        }
        try {
            int index = Integer.parseInt(requested);
            if (index >= 0 && index < sections.length) {
                return index;
            }
        } catch (NumberFormatException ignored) {
        }
        return SECTION_BUTTONS;
    }

    private int initialTheme() {
        String requested = System.getProperty("libfdx.test.uiTheme", "");
        if (requested != null && requested.length() > 0) {
            for (int i = 0; i < THEME_NAMES.length; i++) {
                if (THEME_NAMES[i].equalsIgnoreCase(requested)) {
                    return i;
                }
            }
            try {
                int index = Integer.parseInt(requested);
                if (index >= 0 && index < THEME_NAMES.length) {
                    return index;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    public void selectTheme(int index) {
        int selected = Math.max(0, Math.min(THEME_NAMES.length - 1, index));
        activeTheme.set(selected);
        if (root != null) {
            root.theme(theme(selected));
        }
    }

    private UiTheme[] createThemes() {
        UiTheme[] themes = new UiTheme[THEME_NAMES.length];
        for (int i = 0; i < themes.length; i++) {
            themes[i] = UiKitShowcaseThemes.build(i, panelPatchRegion);
        }
        return themes;
    }

    public UiTheme theme(int index) {
        int selected = Math.max(0, Math.min(THEME_NAMES.length - 1, index));
        if (demoThemes != null && selected < demoThemes.length && demoThemes[selected] != null) {
            return demoThemes[selected];
        }
        return UiKitShowcaseThemes.build(selected, panelPatchRegion);
    }

    public void enableGlobalScaleForValidation() {
        if (scaleGlobally == null || root == null) {
            return;
        }
        if (!scaleGlobally.get()) {
            scaleGlobally.set(true);
        }
        if (uiScale != null) {
            root.uiScale(uiScale.get());
        }
        scaleGloballyActive = true;
    }

    public void resetGlobalScaleForValidation() {
        if (scaleGlobally == null || root == null) {
            return;
        }
        if (scaleGlobally.get()) {
            scaleGlobally.set(false);
        }
        root.uiScale(1.0f);
        scaleGloballyActive = false;
    }

    private void incrementClick() {
        clickCount.set(clickCount.get() + 1);
    }

    public UiContent activeSectionContent() {
        int index = activeSection.get();
        return sections[index >= 0 && index < sections.length ? index : SECTION_THEMES].content();
    }

    public int clickCount() {
        return clickCount.get();
    }

    public boolean checked() {
        return checked.get();
    }

    public boolean visualDebug() {
        return visualDebug.get();
    }

    public boolean scaleGlobally() {
        return scaleGlobally.get();
    }

    public float uiScale() {
        return uiScale.get();
    }

    public float volume() {
        return volume.get();
    }

    public UiIntState activeSection() {
        return activeSection;
    }

    public int activeTheme() {
        return activeTheme.get();
    }

    public String name() {
        return name.get();
    }

    public UiScrollState navScroll() {
        return navScroll;
    }

    public float initialUiScale() {
        return initialUiScale;
    }

    public UiKitButtonsSection buttons() {
        return buttons;
    }

    public UiKitListsSection lists() {
        return lists;
    }

    public UiKitTabsSection tabs() {
        return tabs;
    }

    public UiKitTextFieldsSection textFields() {
        return textFields;
    }

    public UiKitWindowsSection windows() {
        return windows;
    }

    public UiKitScrollViewSection scrollView() {
        return scrollView;
    }

    public UiKitOverlaysSection overlays() {
        return overlays;
    }

    public UiKitTogglesSection toggles() {
        return toggles;
    }

    public String themeName(int index) {
        return THEME_NAMES[index];
    }

    private record Section(String label, UiContent content, UiScrollState scroll) {
        Section(String label, UiContent content) {
            this(label, content, new UiScrollState());
        }

    }

}
