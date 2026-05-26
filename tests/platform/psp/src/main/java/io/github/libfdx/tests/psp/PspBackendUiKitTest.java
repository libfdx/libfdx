package io.github.libfdx.tests.psp;

import io.github.libfdx.Fdx;
import io.github.libfdx.application.Application;
import io.github.libfdx.application.ApplicationAdapter;
import io.github.libfdx.display.Display;
import io.github.libfdx.graphics.GraphicsContext;
import io.github.libfdx.input.DefaultInput;
import io.github.libfdx.input.Input;
import io.github.libfdx.input.Key;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiFont;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiTextAlign;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiTheme;
import io.github.libfdx.ui.UiToolkit;

final class PspBackendUiKitTest extends ApplicationAdapter {
    private static final String PSP_BITMAP_FONT_ASSET = "font/bitmap/psp_test_bitmap.fnt";

    private final long exitAfterFrames;
    private final boolean scriptedSmokeInput;
    private Application application;
    private Display display;
    private GraphicsContext graphics;
    private DefaultInput input;
    private UiRoot root;
    private UiIntState clickCount;
    private UiBooleanState ready;
    private UiFloatState volume;
    private UiIntState tab;
    private UiState<String> playerName;
    private long renderedFrames;

    PspBackendUiKitTest(long exitAfterFrames, boolean scriptedSmokeInput) {
        this.exitAfterFrames = exitAfterFrames;
        this.scriptedSmokeInput = scriptedSmokeInput;
    }

    @Override
    public void create(Fdx fdx) {
        application = fdx.app();
        display = fdx.displays().main();
        graphics = fdx.graphics().main();
        Input fdxInput = fdx.input();
        input = fdxInput instanceof DefaultInput ? (DefaultInput) fdxInput : null;
        clickCount = Ui.state(0);
        ready = Ui.state(false);
        volume = Ui.state(0.62f);
        tab = Ui.state(0);
        playerName = Ui.state("PLAYER");
        root = new UiToolkit(fdx.files())
                .theme(pspTheme())
                .root(display, graphics)
                .input(fdxInput)
                .autoUiScale(false)
                .uiScale(1.0f);
        root.setContent(this::buildUi);
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        graphics.clear(0.055f, 0.070f, 0.090f, 1.0f);
        root.update(application.deltaTime());
        if (scriptedSmokeInput) {
            driveScriptedSmokeInput();
        }
        root.render();
        renderedFrames++;
        if (exitAfterFrames > 0L && renderedFrames >= exitAfterFrames) {
            application.requestExit();
        }
    }

    private void buildUi(UiScope ui) {
        ui.panel(Ui.modifier().width(460.0f).height(252.0f).margin(10.0f).padding(10.0f).gap(6.0f), panel -> {
            panel.text("PSP UI KIT");
            panel.row(Ui.modifier().fillWidth().height(38.0f).gap(8.0f), row -> {
                row.button("PRESS", Ui.modifier().width(112.0f).height(32.0f).validationId("pressButton"),
                        () -> clickCount.set(clickCount.get() + 1));
                row.checkbox("READY", Ui.modifier()
                        .width(132.0f)
                        .height(32.0f)
                        .style("text")
                        .validationId("readyCheckbox"), ready);
                row.text("CLICKS " + clickCount.get(), Ui.modifier().width(112.0f).height(32.0f));
            });
            panel.row(Ui.modifier().fillWidth().height(38.0f).gap(8.0f), row -> {
                row.text("VOLUME", Ui.modifier().width(78.0f).height(32.0f));
                row.slider(Ui.modifier().width(210.0f).height(32.0f).validationId("volumeSlider"),
                        volume, 0.0f, 1.0f);
                row.progressBar(Ui.modifier().width(130.0f).height(28.0f), progress());
            });
            panel.row(Ui.modifier().fillWidth().height(38.0f).gap(8.0f), row -> {
                row.text("PLAYER", Ui.modifier().width(78.0f).height(32.0f));
                row.textField(Ui.modifier().width(210.0f).height(32.0f).validationId("playerName"),
                        playerName);
                row.text(tabName(), Ui.modifier().width(90.0f).height(32.0f));
            });
            panel.tabs(Ui.modifier().fillWidth().height(42.0f).validationId("mainTabs"), tab,
                    "MENU", "GAME", "HUD");
            panel.text("STATUS " + (ready.get() ? "READY" : "WAIT") + " / " + playerName.get());
        });
    }

    private void driveScriptedSmokeInput() {
        if (input == null) {
            return;
        }
        if (renderedFrames == 5L) {
            tap(Key.DOWN);
        } else if (renderedFrames == 7L) {
            tap(Key.ENTER);
        } else if (renderedFrames == 10L) {
            tap(Key.DOWN);
        } else if (renderedFrames == 12L) {
            tap(Key.ENTER);
        } else if (renderedFrames == 15L) {
            tap(Key.DOWN);
        } else if (renderedFrames == 17L) {
            tap(Key.DOWN);
        } else if (renderedFrames == 19L) {
            tap(Key.RIGHT);
        } else if (renderedFrames == 22L) {
            tap(Key.DOWN);
        } else if (renderedFrames == 24L) {
            input.dispatchTextInput(" OK");
        }
    }

    private void tap(Key key) {
        input.dispatchKeyDown(key);
        input.dispatchKeyUp(key);
    }

    private String tabName() {
        int index = tab.get();
        if (index == 1) {
            return "GAME";
        }
        if (index == 2) {
            return "HUD";
        }
        return "MENU";
    }

    private float progress() {
        float base = 0.18f + clickCount.get() * 0.18f;
        if (ready.get()) {
            base += 0.25f;
        }
        return base > 1.0f ? 1.0f : base;
    }

    private static UiTheme pspTheme() {
        UiTextStyle text = UiTextStyle.text()
                .font(UiFont.bitmapFile(PSP_BITMAP_FONT_ASSET, 16.0f))
                .size(16.0f)
                .lineHeight(20.0f)
                .color(UiColor.rgba8888(0xf2f4f8ff));
        UiTextStyle buttonText = text.align(UiTextAlign.CENTER);
        UiTheme theme = Ui.darkTheme();
        theme = styleText(theme, "text", text);
        theme = styleText(theme, "button", buttonText);
        theme = styleText(theme, "panel", text);
        theme = styleText(theme, "text-field", text);
        theme = styleText(theme, "text-area", text);
        theme = styleText(theme, "tabs", buttonText);
        return theme;
    }

    private static UiTheme styleText(UiTheme theme, String styleName, UiTextStyle textStyle) {
        UiStyle style = theme.style(styleName);
        if (style == null) {
            style = UiStyle.style();
        }
        return theme.style(styleName, style.text(textStyle));
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.dispose();
            root = null;
        }
    }
}
