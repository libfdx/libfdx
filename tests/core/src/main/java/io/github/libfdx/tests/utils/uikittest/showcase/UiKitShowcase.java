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
import io.github.libfdx.input.MouseButton;
import io.github.libfdx.tests.utils.uikittest.compose.UiKitSettingsPanel;
import io.github.libfdx.tests.utils.uikittest.compose.UiKitSectionContent;
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
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitTreeSection;
import io.github.libfdx.tests.utils.uikittest.compose.sections.UiKitWindowsSection;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiListState;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScrollState;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTheme;

/** Owns the showcase state, assets, and root setup. */
public final class UiKitShowcase {
    private static final String LOGO_ASSET = "fdx_logo_dark.png";
    private static final String PATCH_ASSET = "ui_panel_patch.png";
    private static final String BUTTON_BACKGROUND_ASSET = "ui_button_game.png";
    private static final AssetDescriptor<Texture> LOGO_TEXTURE = AssetDescriptor.of(LOGO_ASSET, Texture.class);
    private static final AssetDescriptor<Texture> PANEL_PATCH = AssetDescriptor.of(PATCH_ASSET, Texture.class);
    private static final AssetDescriptor<Texture> BUTTON_BACKGROUND = AssetDescriptor.of(BUTTON_BACKGROUND_ASSET, Texture.class);
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
    public static final int SECTION_TREE = 16;
    private static final int FULL_PREVIEW = -1;
    private static final SectionInfo[] SECTION_INFO = {
            new SectionInfo("buttons", "Buttons"),
            new SectionInfo("checkboxes", "Checkboxes"),
            new SectionInfo("sliders", "Sliders"),
            new SectionInfo("progress-bars", "Progress bars"),
            new SectionInfo("tabs", "Tabs"),
            new SectionInfo("text-fields", "Text fields"),
            new SectionInfo("lists", "Lists"),
            new SectionInfo("drawing", "Drawing"),
            new SectionInfo("windows", "Windows"),
            new SectionInfo("scroll-view", "Scroll view"),
            new SectionInfo("overlays", "Overlays"),
            new SectionInfo("tooltips", "Tooltips"),
            new SectionInfo("toggles", "Toggles & choices"),
            new SectionInfo("loading", "Loading & status"),
            new SectionInfo("disclosure", "Disclosure panels"),
            new SectionInfo("themes", "Themes"),
            new SectionInfo("tree", "Tree")
    };
    private final boolean previewMode;
    private Display display;
    private UiRoot root;
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
    private TextureRegion buttonBackgroundRegion;
    private final UiTheme[] demoThemes = new UiTheme[THEME_NAMES.length];
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
    private UiFloatState sliderNormal;
    private UiFloatState sliderWide;
    private UiFloatState sliderDisabled;
    private UiListState inventoryList;

    public UiKitShowcase(boolean previewMode) {
        this.previewMode = previewMode;
    }

    public static void loadAssets(AssetManager assets) {
        assets.load(LOGO_TEXTURE);
        assets.load(PANEL_PATCH);
        assets.load(BUTTON_BACKGROUND);
    }

    /**
     * Prepares the showcase for a host-owned root and asset manager.
     * The host drives asset loading before composition and disposes the preview binding.
     */
    public static PreviewContent previewContent(UiRoot root, AssetManager assets) {
        return new PreviewContent(root, assets, FULL_PREVIEW);
    }

    public static PreviewContent previewSection(UiRoot root, AssetManager assets, int section) {
        if (section < 0 || section >= sectionCount()) throw new IllegalArgumentException("Unknown section: " + section);
        return new PreviewContent(root, assets, section);
    }

    /** Owns one preview's showcase state. The root and assets are borrowed. */
    public static final class PreviewContent implements Disposable {
        private final UiKitShowcase showcase = new UiKitShowcase(true);
        private final UiContent content = UiKitShowcaseContent.deferred(this::preparedContent);
        private UiContent prepared;
        private boolean disposed;

        private PreviewContent(UiRoot root, AssetManager assets, int section) {
            AssetHandle<Texture> logo = section == FULL_PREVIEW || section == SECTION_DRAWING || section == SECTION_BUTTONS
                    ? assets.load(LOGO_TEXTURE) : null;
            AssetHandle<Texture> patch = assets.load(PANEL_PATCH);
            AssetHandle<Texture> background = section == FULL_PREVIEW || section == SECTION_BUTTONS
                    ? assets.load(BUTTON_BACKGROUND) : null;
            Runnable prepare = () -> {
                if (disposed || prepared != null || !patch.isLoaded() || (logo != null && !logo.isLoaded())
                        || (background != null && !background.isLoaded())) {
                    return;
                }
                showcase.initializeState(root.display(), patch.asset(), background == null ? null : background.asset());
                showcase.configureRoot(root);
                TextureRegion image = logo == null ? null : new TextureRegion(logo.asset());
                if (section == FULL_PREVIEW) {
                    showcase.initializeFullContent(image, false);
                    prepared = showcase.content();
                } else {
                    prepared = showcase.isolatedSection(section, image);
                }
            };
            if (logo != null) logo.future().onSuccess(texture -> prepare.run());
            if (background != null) background.future().onSuccess(texture -> prepare.run());
            patch.future().onSuccess(texture -> prepare.run());
        }

        public UiContent content() {
            return content;
        }

        private UiContent preparedContent() {
            if (prepared == null || disposed) {
                throw new FdxException("UI Kit showcase assets are unavailable");
            }
            return prepared;
        }

        @Override
        public void dispose() {
            if (disposed) return;
            disposed = true;
            showcase.detachRoot();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

    }

    public void initialize(AssetManager loadedAssets, Display display, boolean validationActive) {
        Texture logo = loadedAssets.get(LOGO_ASSET, Texture.class);
        Texture patch = loadedAssets.get(PATCH_ASSET, Texture.class);
        initializeState(display, patch, loadedAssets.get(BUTTON_BACKGROUND_ASSET, Texture.class));
        initializeFullContent(new TextureRegion(logo), validationActive);
    }

    private void initializeFullContent(TextureRegion logo, boolean validationActive) {
        navScroll = new UiScrollState();
        initializeSections(logo, validationActive);
        activeSection = Ui.state(initialSection());
    }

    private void initializeState(Display display, Texture patch, Texture buttonBackground) {
        this.display = display;
        panelPatchRegion = new TextureRegion(patch);
        buttonBackgroundRegion = buttonBackground == null ? null : new TextureRegion(buttonBackground);
        for (int i = 0; i < demoThemes.length; i++) demoThemes[i] = null;

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
        sliderNormal = Ui.state(0.45f);
        sliderWide = Ui.state(25.0f);
        sliderDisabled = Ui.state(0.70f);
        activeTheme = Ui.state(initialTheme());
        name = Ui.state("Player");
        overlays = null;
        inventoryList = null;
    }

    private void initializeSections(TextureRegion logoRegion, boolean validationActive) {
        sections = new Section[sectionCount()];
        for (int index = 0; index < sections.length; index++) {
            sections[index] = new Section(createSection(index, logoRegion));
        }
        UiKitDrawingSection drawing = (UiKitDrawingSection) sections[SECTION_DRAWING].content();
        UiKitTooltipsSection tooltips = (UiKitTooltipsSection) sections[SECTION_TOOLTIPS].content();
        UiKitSettingsPanel settings = new UiKitSettingsPanel(display, visualDebug, scaleGlobally, uiScale);
        content = new UiKitShowcaseContent(this, drawing, settings, tooltips, this::incrementClick, validationActive);
    }

    private UiContent createSection(int index, TextureRegion logoRegion) {
        return switch (index) {
            case SECTION_BUTTONS -> buttons = new UiKitButtonsSection(clickCount, showDetails,
                    this::incrementClick, overlays().showPopup::toggle, panelPatchRegion, logoRegion,
                    buttonBackgroundRegion, this::compactViewport);
            case SECTION_CHECKBOXES -> new UiKitCheckboxesSection(checked, compactChecked, visualDebug);
            case SECTION_SLIDERS -> new UiKitSlidersSection(volume, this::compactViewport,
                    sliderNormal, sliderWide, sliderDisabled);
            case SECTION_PROGRESS_BARS -> new UiKitProgressBarsSection(volume, sliderWide, sliderDisabled);
            case SECTION_TABS -> tabs = new UiKitTabsSection(volume, clickCount, inventory(), uiScale);
            case SECTION_TEXT_FIELDS -> textFields = new UiKitTextFieldsSection(name);
            case SECTION_LISTS -> lists = new UiKitListsSection(inventory());
            case SECTION_DRAWING -> new UiKitDrawingSection(sliderNormal, logoRegion, this::compactViewport);
            case SECTION_WINDOWS -> windows = new UiKitWindowsSection(checked, showDetails, this::incrementClick,
                    () -> UiKitShowcaseContent.statusText(this), compactViewport());
            case SECTION_SCROLLVIEW -> scrollView = new UiKitScrollViewSection(name, this::incrementClick);
            case SECTION_OVERLAYS -> overlays();
            case SECTION_TOOLTIPS -> new UiKitTooltipsSection(this::compactViewport, this::incrementClick, compactChecked, name);
            case SECTION_TOGGLES -> toggles = new UiKitTogglesSection();
            case SECTION_LOADING -> new UiKitLoadingSection(volume);
            case SECTION_COLLAPSE -> new UiKitDisclosureSection();
            case SECTION_THEMES -> new UiKitThemesSection(activeTheme, this::selectTheme);
            case SECTION_TREE -> new UiKitTreeSection();
            default -> throw new IllegalArgumentException("Unknown section: " + index);
        };
    }

    private UiContent isolatedSection(int index, TextureRegion logoRegion) {
        UiContent section = createSection(index, logoRegion);
        return switch (index) {
            case SECTION_BUTTONS, SECTION_OVERLAYS -> new UiKitSectionContent(section, overlays()::buildOverlays);
            case SECTION_WINDOWS -> UiKitSectionContent.windows(windows, volume);
            case SECTION_DRAWING -> UiKitSectionContent.drawing((UiKitDrawingSection) section, volume);
            case SECTION_TOOLTIPS -> new UiKitSectionContent(section, ((UiKitTooltipsSection) section)::buildTooltips);
            default -> new UiKitSectionContent(section);
        };
    }

    private UiListState inventory() {
        if (inventoryList == null) {
            inventoryList = new UiListState();
            inventoryList.scrollToItem(1, 0.0f);
        }
        return inventoryList;
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

    public static int sectionCount() {
        return SECTION_INFO.length;
    }

    public static String sectionId(int index) {
        return SECTION_INFO[index].id();
    }

    public static String sectionLabel(int index) {
        return SECTION_INFO[index].label();
    }

    public String activeSectionName() {
        int index = activeSection != null ? activeSection.get() : SECTION_BUTTONS;
        if (index < 0 || index >= sections.length) {
            return sectionLabel(SECTION_BUTTONS);
        }
        return sectionLabel(index);
    }

    public boolean compactViewport() {
        if (display == null || display.width() <= 0) {
            return false;
        }
        float viewportWidth = display.width();
        float scale = 1.0f;
        if (root != null) {
            UiRect viewport = root.viewport();
            if (viewport != null && display.framebufferWidth() > 0) {
                viewportWidth *= viewport.width() / display.framebufferWidth();
            }
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
        return viewportWidth / scale < 1100.0f;
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
        int index = sectionIndex(requested);
        return index < 0 ? SECTION_BUTTONS : index;
    }

    public static int sectionIndex(String requested) {
        if (requested.isEmpty()) return -1;
        if ("collapse bars".equalsIgnoreCase(requested)) {
            return SECTION_COLLAPSE;
        }
        for (int i = 0; i < sectionCount(); i++) {
            if (sectionLabel(i).equalsIgnoreCase(requested) || sectionId(i).equalsIgnoreCase(requested)) {
                return i;
            }
        }
        try {
            int index = Integer.parseInt(requested);
            if (index >= 0 && index < sectionCount()) {
                return index;
            }
        } catch (NumberFormatException ignored) {
        }
        return -1;
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

    public UiTheme theme(int index) {
        int selected = Math.max(0, Math.min(THEME_NAMES.length - 1, index));
        if (demoThemes[selected] == null) {
            demoThemes[selected] = UiKitShowcaseThemes.build(selected, panelPatchRegion);
        }
        return demoThemes[selected];
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
        if (overlays == null) overlays = new UiKitOverlaysSection(this::incrementClick);
        return overlays;
    }

    public UiKitTogglesSection toggles() {
        return toggles;
    }

    public String themeName(int index) {
        return THEME_NAMES[index];
    }

    private record SectionInfo(String id, String label) { }

    private record Section(UiContent content, UiScrollState scroll) {
        Section(UiContent content) {
            this(content, new UiScrollState());
        }

    }

}
