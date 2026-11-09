package io.github.libfdx.tests.utils.shadergrapheditorvisualtest;

import io.github.libfdx.tests.utils.preview.DesktopPreviewEntry;
import io.github.libfdx.core.Disposable;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.ui.DefaultShaderGraphEditorCompiler;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorDocument;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorSession;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorView;
import io.github.libfdx.tools.uipreview.UiPreviewContext;
import io.github.libfdx.ui.UiContent;
import io.github.libfdx.ui.UiScope;

/** Supplies a sample document to the real shader graph editor composition. */
public final class ShaderGraphEditorPreview extends DesktopPreviewEntry {
    @Override
    public UiContent create(UiPreviewContext context) {
        return context.own(new Content());
    }

    private static final class Content implements UiContent, Disposable {
        private final ShaderGraphEditorView editor;
        private boolean disposed;

        Content() {
            ShaderGraphBuilder graph = new ShaderGraphBuilder("composition_preview", ShaderGraphKind.FUNCTION);
            graph.output("value", graph.constant("sample", ShaderGraphLiteral.f32(0.35f)));
            ShaderGraphEditorDocument document = ShaderGraphEditorDocument.of(graph.build());
            editor = new ShaderGraphEditorView(new ShaderGraphEditorSession(document), new DefaultShaderGraphEditorCompiler());
        }

        public void build(UiScope ui) {
            editor.build(ui);
        }

        public void dispose() {
            editor.dispose();
            disposed = true;
        }

        public boolean isDisposed() {
            return disposed;
        }

    }

}
