package io.github.libfdx.tests.utils.uikittest.compose.sections;

import io.github.libfdx.tests.utils.uikittest.showcase.UiKitValidationIds;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiScope;

/** Demonstrates independently expandable disclosure panels. */
public final class UiKitDisclosureSection implements UiContent {
    private final UiBooleanState collapseBasics = Ui.state(true);
    private final UiBooleanState collapseAdvanced = Ui.state(false);

    @Override
    public void build(UiScope content) {
        content.text("Disclosure panels", Ui.modifier().style("title"));
        content.text("Click the highlighted header to show or hide its content", Ui.modifier().style("muted"));
        buildBasicDisclosure(content);
        buildAdvancedDisclosure(content);
    }

    private void buildBasicDisclosure(UiScope content) {
        content.collapseBar("Basic disclosure",
                Ui.modifier().fillWidth().validationId(UiKitValidationIds.COLLAPSE_BASIC),
                collapseBasics, body -> {
                    body.text("This content is composed only while the disclosure is expanded.",
                            Ui.modifier().style("small"));
                    body.text("The header clearly communicates its expanded or collapsed state.",
                            Ui.modifier().style("small"));
                });
    }

    private void buildAdvancedDisclosure(UiScope content) {
        content.collapseBar("Advanced customization", Ui.modifier().fillWidth(), collapseAdvanced, body -> {
            body.text("Each disclosure owns independent state and body content.",
                    Ui.modifier().style("small"));
            body.text("Header, indicator, and body spacing can be customized through styles.",
                    Ui.modifier().style("small"));
        });
    }

}
