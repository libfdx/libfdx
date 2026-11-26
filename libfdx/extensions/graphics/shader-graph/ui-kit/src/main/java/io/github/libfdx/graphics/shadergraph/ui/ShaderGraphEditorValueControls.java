package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphParameterKind;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import java.util.Objects;

/** Selected value node controls. Drafts are committed together on Apply or Enter. */
final class ShaderGraphEditorValueControls {
    private final ShaderGraphEditorValueFields fields = new ShaderGraphEditorValueFields();
    private final UiState<String> name = Ui.state("");
    private ShaderGraphEditorNodeValue inspected;

    void build(UiScope ui, ShaderGraphEditorSession session, ShaderGraphEditorNodeValue value, Runnable compile) {
        if (!Objects.equals(value, inspected)) {
            if (value != null && (inspected == null || !inspected.node().id().equals(value.node().id()))) {
                name.set(value.node().id().value());
            }
            inspected = value;
            if (value != null) fields.reset(value.literal());
        }
        if (value == null) return;
        var parameter = value.parameter();
        String id = parameter == null ? value.node().id().value() : parameter.id().value();
        ui.selectableText(value.source() + "  " + id, UiModifier.none().style(ShaderGraphEditorTheme.HEADING_STYLE));
        var literal = value.literal();
        boolean supplied = value.externallySupplied();
        if (supplied) ui.selectableText(parameter.kind() == ShaderGraphParameterKind.STAGE_INPUT
                ? "Supplied by the shader stage." : "Supplied by the calling graph.");
        if (literal == null || ShaderGraphEditorNodeValue.editableComponents(literal.type()) == 0) return;

        Runnable apply = () -> apply(session, value, false, compile);
        String label = supplied ? "Constant override" : parameter == null ? "Value" : "Default";
        fields.build(ui, literal, label, apply, null);
        ui.button(supplied ? "Replace with constant" : parameter == null ? "Apply constant" : "Apply default", apply);
        if (parameter == null && literal.type().valueType().scalarType() != ShaderScalarType.BOOL && session.activeGraph().kind() == ShaderGraphKind.SURFACE) {
            ui.row(UiModifier.none().fillWidth().gap(4), row -> {
                row.selectableText("Parameter name");
                row.textField(UiModifier.none().weight(1).semanticLabel("Material parameter name"), name);
            });
            ui.button("Expose as material parameter", () -> apply(session, value, true, compile));
        }
        fields.showError(ui);
    }

    private void apply(ShaderGraphEditorSession session, ShaderGraphEditorNodeValue value, boolean expose, Runnable compile) {
        if (fields.submit(value.literal(), literal -> {
            if (expose) {
                session.editActiveGraph("Expose material parameter",
                        graph -> ShaderGraphSemanticEdits.exposeConstant(graph, value.node(), name.get(), literal));
            } else {
                value.apply(session, literal);
            }
        })) compile.run();
    }

}
