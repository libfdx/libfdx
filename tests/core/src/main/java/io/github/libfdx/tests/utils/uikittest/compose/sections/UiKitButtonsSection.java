package io.github.libfdx.tests.utils.uikittest.compose.sections;

import static io.github.libfdx.tests.utils.uikittest.showcase.UiKitShowcaseThemes.textStyle;

import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiEasing;
import io.github.libfdx.ui.UiIntState;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiStyle;
import java.util.function.BooleanSupplier;

/** Demonstrates button variants, constrained layout, and transitions. */
public final class UiKitButtonsSection implements UiContent {
    private final UiIntState clickCount;
    private final UiBooleanState showDetails;
    private final Runnable incrementClick;
    private final Runnable togglePopup;
    private final UiKitButtonCustomization customization;
    public final UiBooleanState dynamicEnabled = Ui.state(true);
    public final UiIntState transitionPhase = Ui.state(0);
    private final UiStyle inlineButtonStyle = UiStyle.button()
            .background(UiDrawable.color(UiColor.rgba8888(0xb6378cff)))
            .foreground(UiDrawable.color(UiColor.rgba8888(0xffe66dff)))
            .text(textStyle(15.0f, 0xffffffff).wrap(false).ellipsis(true));

    public UiKitButtonsSection(UiIntState clickCount, UiBooleanState showDetails,
            Runnable incrementClick, Runnable togglePopup, TextureRegion texture, TextureRegion logo,
            TextureRegion buttonBackground, BooleanSupplier compactViewport) {
        this.clickCount = clickCount;
        this.showDetails = showDetails;
        this.incrementClick = incrementClick;
        this.togglePopup = togglePopup;
        customization = new UiKitButtonCustomization(texture, logo, buttonBackground, incrementClick, compactViewport);
    }

    @Override
    public void build(UiScope content) {
        content.text("Buttons", Ui.modifier().style("title"));
        buildButtonVariants(content);
        buildAnimatedDetails(content);
        buildStatusTiles(content);
        customization.build(content);
        buildConstrainedButtons(content);
        buildInlineStyleExample(content);
        buildTransitionExample(content);
    }

    private void buildButtonVariants(UiScope content) {
        content.text("Variants", Ui.modifier().style("muted"));
        content.row(Ui.modifier().fillWidth().gap(12.0f), row -> {
            row.button("Press", Ui.modifier().validationId(UiKitValidationIds.BUTTON_PRESS),
                    incrementClick);
            row.button("Details", () -> showDetails.toggle());
            row.button("Disabled", Ui.modifier().enabled(false), incrementClick);
            row.button("Dynamic", Ui.modifier().enabled(dynamicEnabled.get())
                    .semanticLabel("Dynamic button"), incrementClick);
        });
        content.spacer(Ui.modifier().height(8.0f));
        content.row(Ui.modifier().fillWidth().gap(12.0f), row -> {
            row.button("Wide action", Ui.modifier().fillWidth().weight(1.0f), incrementClick);
            row.button("Compact", Ui.modifier().width(104.0f), incrementClick);
            row.button("Popup", togglePopup);
        });
    }

    private void buildAnimatedDetails(UiScope content) {
        content.animateContentSize(Ui.modifier().fillWidth().gap(4.0f), Ui.animation().durationMillis(160),
                animated -> animated.animatedVisibility(showDetails.get(),
                        Ui.animation().durationMillis(160).fade().slideY(-4.0f),
                        details -> details.text("Animated content",
                                Ui.modifier().style("small"))));
    }

    private void buildStatusTiles(UiScope content) {
        content.grid(3, Ui.modifier().fillWidth().gap(8.0f), grid -> {
            tile(grid, "primary", "Press");
            tile(grid, "toggle", showDetails.get() ? "shown" : "hidden");
            tile(grid, "count", String.valueOf(clickCount.get()));
        });
    }

    private void buildConstrainedButtons(UiScope content) {
        content.divider(Ui.modifier().fillWidth());
        content.text("Constrained layout", Ui.modifier().style("muted"));
        content.text("Three 150 px button requests shrink into a 250 px row without wrapping.",
                Ui.modifier().style("small"));
        content.panel(Ui.modifier().fillWidth().padding(10.0f).style("tile"), panel ->
                panel.row(Ui.modifier().fillWidth().height(40.0f).gap(6.0f)
                                .semanticLabel("Constrained row"),
                        row -> {
                            row.button("Alpha", Ui.modifier().width(150.0f)
                                            .validationId(UiKitValidationIds.CONSTRAINED_ALPHA),
                                    incrementClick);
                            row.button("Beta", Ui.modifier().width(150.0f)
                                            .validationId(UiKitValidationIds.CONSTRAINED_BETA),
                                    incrementClick);
                            row.button("Gamma", Ui.modifier().width(150.0f)
                                            .validationId(UiKitValidationIds.CONSTRAINED_GAMMA),
                                    incrementClick);
                        }));
    }

    private void buildInlineStyleExample(UiScope content) {
        content.text("Inline customization", Ui.modifier().style("muted"));
        content.button("One-off styled button",
                Ui.modifier().style(inlineButtonStyle), incrementClick);
        content.text("An inline style can override the active theme for one button.",
                Ui.modifier().style("small"));
    }

    private void buildTransitionExample(UiScope content) {
        content.panel(Ui.modifier().width(480).height(80).padding(8).style("tile")
                        .transition(String.valueOf(transitionPhase.get()),
                                Ui.transition().durationMillis(1000).easing(UiEasing.linear())
                                        .alpha(0.1f, 1).scale(0.5f, 1)),
                panel -> panel.text("Retained alpha and scale transition", Ui.modifier().style("small")));
    }

    private void tile(UiScope grid, String title, String value) {
        grid.panel(Ui.modifier().fillWidth().padding(6.0f).gap(3.0f).style("tile"), tile -> {
            tile.text(title, Ui.modifier().fillWidth().style("section"));
            tile.text(value, Ui.modifier().fillWidth().style("small"));
        });
    }

}
