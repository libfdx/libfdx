package io.github.libfdx.tests.graphics;

import io.github.libfdx.Fdx;
import io.github.libfdx.graphics.shader.ShaderProfile;
import io.github.libfdx.graphics.shadergraph.model.ShaderExpression;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraph;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphBuilder;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorData;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphEditorNode;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphKind;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphLiteral;
import io.github.libfdx.graphics.shadergraph.model.ShaderGraphType;
import io.github.libfdx.graphics.shadergraph.ui.DefaultShaderGraphEditorCompiler;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorArtifact;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorCompileSettings;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorCompilation;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorDocument;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorLayout;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorPreviewMode;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorPreviewSurface;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorSession;
import io.github.libfdx.graphics.shadergraph.ui.ShaderGraphEditorView;
import io.github.libfdx.graphics.shader.reflection.ShaderScalarType;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiCustomContext;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiDrawFunction;
import io.github.libfdx.ui.UiRect;
import io.github.libfdx.ui.UiRoot;
import io.github.libfdx.ui.UiTextStyle;
import io.github.libfdx.ui.UiToolkit;

/**
 * Renders the complete optional shader graph editor through WGPU.
 */
public final class ShaderGraphEditorVisualTest extends GraphicsParityTest {
    private final PreviewSurface preview = new PreviewSurface();
    private UiRoot root;
    private ShaderGraphEditorView editor;

    public ShaderGraphEditorVisualTest(long exitAfterFrames) {
        super(exitAfterFrames);
    }

    @Override
    public void create(Fdx fdx) {
        initialize(fdx, "ShaderGraphEditorVisualTest");
        ShaderGraph graph = graph();
        ShaderGraphEditorDocument document =
                ShaderGraphEditorDocument.of(graph);
        ShaderGraphEditorLayout layout = ShaderGraphEditorLayout.of(
                new ShaderGraphEditorData[] {
                        ShaderGraphEditorData.of(graph.id().value(),
                                new ShaderGraphEditorNode[] {
                                        node("left", 20, 45, 180, 108),
                                        node("right", 20, 290, 180, 108),
                                        node("add", 235, 75, 180, 118),
                                        node("factor", 235, 335, 180, 108),
                                        node("multiply", 440, 175, 190, 118),
                                        node("alpha", 440, 355, 180, 108),
                                        node("color", 620, 145, 200, 140)
                                }, 15, 12, 0.55f)
                }, graph.id().value());
        ShaderGraphEditorSession session =
                new ShaderGraphEditorSession(document, layout);
        session.selectNode("multiply");
        ShaderGraphEditorCompileSettings settings =
                ShaderGraphEditorCompileSettings.builder()
                        .profile(ShaderProfile.PORTABLE_WEBGPU)
                        .capabilities(graphics.device().capabilities())
                        .previewMode(
                                ShaderGraphEditorPreviewMode.FUNCTION_VALUE)
                        .build();
        DefaultShaderGraphEditorCompiler compiler =
                new DefaultShaderGraphEditorCompiler(
                        (request, wgsl) ->
                                new ShaderGraphEditorArtifact[] {
                                        ShaderGraphEditorArtifact.text(
                                                request.settings().target()
                                                        .value(),
                                                request.settings().format()
                                                        .id(),
                                                request.settings()
                                                        .environment().id(),
                                                "canonical-wgsl",
                                                "module", "", wgsl, true)
                                });
        editor = new ShaderGraphEditorView(session, compiler,
                io.github.libfdx.graphics.shadergraph.ui
                        .ShaderGraphEditorPalette.standard(),
                preview, settings);
        editor.compileNow();

        root = new UiToolkit(fdx.files()).root(display, graphics)
                .input(fdx.input());
        root.setContent(editor);
        markCreated();
    }

    @Override
    public void resize(int width, int height) {
        if (root != null) {
            root.resize(width, height);
        }
    }

    @Override
    public void render() {
        graphics.clear(0.025f, 0.032f, 0.05f, 1.0f);
        root.update(application.deltaTime());
        root.render();
        finishFrame();
    }

    @Override
    public void dispose() {
        if (editor != null) {
            editor.dispose();
        }
        dispose(root);
        editor = null;
        root = null;
        verifyDisposed();
    }

    private static ShaderGraph graph() {
        ShaderGraphType vec4 =
                ShaderGraphType.vector(ShaderScalarType.F32, 4);
        ShaderGraphBuilder builder = new ShaderGraphBuilder(
                "editor_demo", ShaderGraphKind.FUNCTION);
        ShaderExpression left = builder.constant("left",
                ShaderGraphLiteral.f32(0.35f));
        ShaderExpression right = builder.constant("right",
                ShaderGraphLiteral.f32(0.55f));
        ShaderExpression add = builder.add("add", left, right);
        ShaderExpression factor = builder.constant("factor",
                ShaderGraphLiteral.f32(0.7f));
        ShaderExpression multiply = builder.multiply("multiply",
                add, factor);
        ShaderExpression color = builder.construct("color", vec4,
                multiply, add, right,
                builder.constant("alpha",
                        ShaderGraphLiteral.f32(1.0f)));
        builder.output("value", color);
        return builder.build();
    }

    private static ShaderGraphEditorNode node(String id, float x, float y,
            float width, float height) {
        return ShaderGraphEditorNode.of(id, x, y, width, height, false);
    }

    private static final class PreviewSurface
            implements ShaderGraphEditorPreviewSurface, UiDrawFunction {
        private static final UiColor BACKGROUND =
                UiColor.rgba8888(0x101827ff);
        private static final UiColor OUTER =
                UiColor.rgba8888(0x23314bff);
        private static final UiColor MID =
                UiColor.rgba8888(0x3264a8ff);
        private static final UiColor INNER =
                UiColor.rgba8888(0x77bff7ff);
        private static final UiColor HIGHLIGHT =
                UiColor.rgba8888(0xd8f3ffff);
        private static final UiTextStyle LABEL = UiTextStyle.text()
                .size(12.0f).color(UiColor.rgba8888(0x9dd8ffff));
        private boolean ready;

        @Override
        public void present(ShaderGraphEditorCompilation compilation,
                ShaderGraphEditorPreviewMode mode) {
            ready = compilation.success()
                    && mode == ShaderGraphEditorPreviewMode.FUNCTION_VALUE;
        }

        @Override
        public void build(UiCustomContext context) {
            context.draw(this);
        }

        @Override
        public void draw(UiDrawContext draw, UiRect bounds) {
            draw.rect(bounds, BACKGROUND);
            float size = Math.min(bounds.width(), bounds.height()) * 0.7f;
            float x = bounds.x() + (bounds.width() - size) * 0.5f;
            float y = bounds.y() + (bounds.height() - size) * 0.5f;
            draw.rect(x, y, size, size, OUTER);
            draw.rect(x + size * 0.12f, y + size * 0.12f,
                    size * 0.76f, size * 0.76f, MID);
            draw.rect(x + size * 0.25f, y + size * 0.25f,
                    size * 0.5f, size * 0.5f, INNER);
            draw.rect(x + size * 0.35f, y + size * 0.3f,
                    size * 0.18f, size * 0.12f, HIGHLIGHT);
            draw.text(ready ? "LAST-GOOD PREVIEW" : "PREVIEW WAITING",
                    new UiRect(bounds.x() + 12.0f,
                            bounds.bottom() - 28.0f,
                            bounds.width() - 24.0f, 20.0f),
                    LABEL);
        }
    }
}
