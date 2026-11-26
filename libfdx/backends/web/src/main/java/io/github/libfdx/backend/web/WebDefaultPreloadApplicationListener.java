package io.github.libfdx.backend.web;

import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawable;
import io.github.libfdx.ui.UiFloatState;
import io.github.libfdx.ui.UiStyle;
import io.github.libfdx.ui.UiSize;
import io.github.libfdx.ui.UiTheme;
import java.nio.ByteBuffer;
import org.teavm.jso.JSBody;
import org.teavm.jso.typedarrays.Int8Array;

/**
 * Renders the default libFDX web preloading screen with UI kit.
 *
 * @author xpenatan
 */
public final class WebDefaultPreloadApplicationListener implements WebPreloadApplicationListener {
    private static final String LOGO_ASSET_PATH = WebAssets.DEFAULT_PRELOAD_LOGO_PATH;
    private static final UiColor BACKGROUND = UiColor.rgba8888(0x10141bff);
    private static final UiColor LOGO_MARK = UiColor.rgba8888(0x0265feff);
    private static final UiColor PROGRESS_TRACK = UiColor.rgba8888(0x263142ff);
    private static final float PAGE_PADDING = 24.0f;
    private static final float LOGO_PROGRESS_GAP = 0.0f;
    private static final float LOGO_WIDTH = 360.0f;
    private static final float PROGRESS_WIDTH = LOGO_WIDTH;
    private static final float PROGRESS_TRACK_HEIGHT = 10.0f;
    private static final float LOGO_ASPECT = 512.0f / 341.0f;
    private static final float PROGRESS_RESPONSE_PER_SECOND = 10.0f;
    private static final float PROGRESS_SNAP_EPSILON = 0.001f;

    private Texture logoTexture;
    private TextureRegion logoRegion;
    private int logoLoadCheckedFiles = -1;
    private boolean logoLoadCheckedComplete;
    private final UiFloatState displayedProgress = Ui.state(0.0f);

    /**
     * Runs the create step.
     *
     * @param context the preload context
     */
    @Override
    public void create(WebPreloadContext context) {
        displayedProgress.set(0.0f);
        context.ui().theme(theme());
        context.ui().setContent(ui -> ui.column(Ui.modifier().fill().padding(PAGE_PADDING).gap(LOGO_PROGRESS_GAP), page -> {
            page.spacer(Ui.modifier().weight(1.0f));
            UiSize size = logoSize(context.ui().uiX(context.display().width()) - 2 * PAGE_PADDING,
                    context.ui().uiY(context.display().height()) - 2 * PAGE_PADDING - PROGRESS_TRACK_HEIGHT);
            var logo = Ui.modifier().size(size.width(), size.height()).align(UiAlign.CENTER);
            if (logoRegion == null) page.spacer(logo);
            else page.image(logoRegion, logo.semanticLabel("libFDX"));
            page.progressBar(Ui.modifier().fillWidth().maxWidth(PROGRESS_WIDTH).height(PROGRESS_TRACK_HEIGHT)
                    .align(UiAlign.CENTER).semanticLabel("Loading progress"), displayedProgress, 0, 1);
            page.spacer(Ui.modifier().weight(1.0f));
        }));
    }

    /**
     * Renders one preloading frame.
     *
     * @param context the preload context
     */
    @Override
    public void render(WebPreloadContext context) {
        updateProgress(context);
        loadLogoIfReady(context);
        context.graphics().clear(BACKGROUND.red(), BACKGROUND.green(), BACKGROUND.blue(), BACKGROUND.alpha());
        context.ui().update(context.deltaTime());
        context.ui().render();
    }

    /**
     * Releases resources held by this listener.
     *
     * @param context the preload context
     */
    @Override
    public void dispose(WebPreloadContext context) {
        if (logoTexture != null) {
            logoTexture.dispose();
            logoTexture = null;
            logoRegion = null;
        }
    }

    @Override
    public void resize(WebPreloadContext context, int width, int height) {
        context.ui().requestCompose();
    }

    private void loadLogoIfReady(WebPreloadContext context) {
        if (logoRegion != null) {
            return;
        }
        int loadedFiles = context.progress().loadedFiles();
        boolean complete = context.progress().isComplete();
        if (loadedFiles == logoLoadCheckedFiles && complete == logoLoadCheckedComplete) {
            return;
        }
        logoLoadCheckedFiles = loadedFiles;
        logoLoadCheckedComplete = complete;
        ImageData image = preloadedImage(LOGO_ASSET_PATH);
        if (image == null) {
            return;
        }
        logoTexture = context.graphics().device().createTexture(TextureDescriptor
                .rgba8(LOGO_ASSET_PATH, image.width(), image.height()));
        context.graphics().device().writeTexture(logoTexture, image.rgba());
        logoRegion = new TextureRegion(logoTexture);
        context.ui().requestCompose();
    }

    private static UiTheme theme() {
        return Ui.darkTheme().colors(BACKGROUND, UiColor.WHITE).progressBar(UiStyle.style()
                .background(UiDrawable.color(PROGRESS_TRACK)).foreground(UiDrawable.color(LOGO_MARK)));
    }

    private UiSize logoSize(float availableWidth, float availableHeight) {
        float aspect = logoRegion != null && logoRegion.height() > 0
                ? logoRegion.width() / (float) logoRegion.height()
                : LOGO_ASPECT;
        float width = Math.max(0.0f, Math.min(LOGO_WIDTH, availableWidth));
        float height = width / aspect;
        if (height > availableHeight) {
            height = Math.max(0.0f, availableHeight);
            width = height * aspect;
        }
        return new UiSize(width, height);
    }

    private void updateProgress(WebPreloadContext context) {
        displayedProgress.set(advanceDisplayedProgress(displayedProgress.get(), context.progress().progress(),
                context.deltaTime(), context.progress().isFailed()));
    }

    static float advanceDisplayedProgress(float displayed, float target, float delta, boolean failed) {
        target = clamp01(target);
        if (failed || target <= displayed) {
            return target;
        }
        float response = Math.min(1.0f, Math.max(0.0f, delta) * PROGRESS_RESPONSE_PER_SECOND);
        float next = displayed + (target - displayed) * response;
        return target - next <= PROGRESS_SNAP_EPSILON ? target : next;
    }

    private static float clamp01(float value) {
        if (value < 0.0f) {
            return 0.0f;
        }
        if (value > 1.0f) {
            return 1.0f;
        }
        return value;
    }

    private static ImageData preloadedImage(String path) {
        int width = browserImageWidth(path);
        int height = browserImageHeight(path);
        Int8Array rgbaArray = browserImageRgba(path);
        if (width <= 0 || height <= 0 || rgbaArray == null) {
            return null;
        }
        byte[] bytes = rgbaArray.copyToJavaArray();
        ByteBuffer rgba = ByteBuffer.allocateDirect(bytes.length);
        rgba.put(bytes);
        rgba.flip();
        return new ImageData(width, height, rgba);
    }

    @JSBody(params = { "path" }, script =
            "var image = libfdxPreloadedImage(path);\n" +
            "return image ? image.width : 0;\n" +
            "function libfdxPreloadedImage(value) {\n" +
            "  var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "  var images = root.libfdxImageData || {};\n" +
            "  value = libfdxNormalizePath(value);\n" +
            "  return images[value] || images['assets/' + value] || null;\n" +
            "}\n" +
            "function libfdxNormalizePath(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "}")
    private static native int browserImageWidth(String path);

    @JSBody(params = { "path" }, script =
            "var image = libfdxPreloadedImage(path);\n" +
            "return image ? image.height : 0;\n" +
            "function libfdxPreloadedImage(value) {\n" +
            "  var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "  var images = root.libfdxImageData || {};\n" +
            "  value = libfdxNormalizePath(value);\n" +
            "  return images[value] || images['assets/' + value] || null;\n" +
            "}\n" +
            "function libfdxNormalizePath(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "}")
    private static native int browserImageHeight(String path);

    @JSBody(params = { "path" }, script =
            "var image = libfdxPreloadedImage(path);\n" +
            "return image ? image.rgba : null;\n" +
            "function libfdxPreloadedImage(value) {\n" +
            "  var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "  var images = root.libfdxImageData || {};\n" +
            "  value = libfdxNormalizePath(value);\n" +
            "  return images[value] || images['assets/' + value] || null;\n" +
            "}\n" +
            "function libfdxNormalizePath(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "}")
    private static native Int8Array browserImageRgba(String path);
}
