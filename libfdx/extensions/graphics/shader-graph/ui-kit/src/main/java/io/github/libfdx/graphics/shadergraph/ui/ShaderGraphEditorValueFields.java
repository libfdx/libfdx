package io.github.libfdx.graphics.shadergraph.ui;

import io.github.libfdx.core.FdxException;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiBooleanState;
import io.github.libfdx.ui.UiCustomContext;
import io.github.libfdx.ui.UiModifier;
import io.github.libfdx.ui.UiNode;
import io.github.libfdx.ui.UiScope;
import io.github.libfdx.ui.UiState;
import io.github.libfdx.ui.UiTextInputFilter;
import java.util.List;
import java.util.function.Consumer;

/** UI Kit fields for one uncommitted shader literal, shared by the inspector and canvas. */
final class ShaderGraphEditorValueFields {
    private final List<UiState<String>> fields = List.of(Ui.state(""), Ui.state(""), Ui.state(""), Ui.state(""));
    private final UiBooleanState checked = Ui.state(false);
    private final UiState<String> error = Ui.state("");

    void build(UiScope ui, ShaderGraphLiteral literal, String label, Runnable submit, UiCustomContext inline) {
        var scalar = literal.type().valueType().scalarType();
        if (scalar == ShaderScalarType.BOOL) {
            var box = ui.checkbox(label, checked);
            if (inline != null) inline.initialFocus(box);
            return;
        }
        int count = ShaderGraphEditorNodeValue.editableComponents(literal.type());
        var filter = scalar == ShaderScalarType.F32 ? UiTextInputFilter.FLOAT : UiTextInputFilter.INTEGER;
        ui.row(UiModifier.none().fillWidth().gap(4), row -> {
            for (int i = 0; i < count; i++) {
                String component = count == 1 ? label : String.valueOf("XYZW".charAt(i));
                if (inline == null) row.selectableText(component);
                else if (count > 1) row.text(component);
                UiNode field = row.textField(UiModifier.none().weight(1).semanticLabel(label + (count == 1 ? "" : " " + component)),
                        fields.get(i), filter, submit);
                if (inline != null && i == 0) inline.initialFocus(field, true);
            }
        });
    }

    void showError(UiScope ui) {
        if (!error.get().isEmpty()) ui.selectableText(error.get());
    }

    boolean submit(ShaderGraphLiteral original, Consumer<ShaderGraphLiteral> edit) {
        try {
            edit.accept(read(original));
        } catch (NumberFormatException invalid) {
            error.set("Enter a valid " + ShaderGraphEditorPortLabels.typeName(original.type()) + " value.");
            return false;
        } catch (FdxException invalid) {
            error.set(invalid.getMessage());
            return false;
        }
        error.set("");
        return true;
    }

    void reset(ShaderGraphLiteral literal) {
        error.set("");
        if (literal == null) return;
        int count = ShaderGraphEditorNodeValue.editableComponents(literal.type());
        for (int i = 0; i < count; i++) {
            var component = count == 1 ? literal : literal.element(i);
            fields.get(i).set(ShaderGraphEditorNodeValue.componentText(component));
        }
        checked.set(literal.bits() != 0);
    }

    private ShaderGraphLiteral read(ShaderGraphLiteral original) {
        int count = ShaderGraphEditorNodeValue.editableComponents(original.type());
        var components = new ShaderGraphLiteral[count];
        for (int i = 0; i < count; i++) {
            String text = fields.get(i).get();
            components[i] = switch (original.type().valueType().scalarType()) {
                case F32 -> ShaderGraphLiteral.f32(Float.parseFloat(text));
                case I32 -> ShaderGraphLiteral.i32(Integer.parseInt(text));
                case U32 -> ShaderGraphLiteral.u32(Long.parseLong(text));
                case BOOL -> ShaderGraphLiteral.bool(checked.get());
                default -> throw new IllegalStateException("Unsupported editor value type");
            };
        }
        return count == 1 ? components[0] : ShaderGraphLiteral.composite(original.type(), components);
    }

}
