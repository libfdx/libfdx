package io.github.libfdx.backend.web;

import io.github.libfdx.assets.loaders.ImageData;
import io.github.libfdx.graphics.Texture;
import io.github.libfdx.graphics.TextureDescriptor;
import io.github.libfdx.graphics.g2d.TextureRegion;
import io.github.libfdx.ui.Ui;
import io.github.libfdx.ui.UiAlign;
import io.github.libfdx.ui.UiColor;
import io.github.libfdx.ui.UiDrawContext;
import io.github.libfdx.ui.UiRect;
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
    private static final UiColor PROGRESS_EDGE = UiColor.rgba8888(0x53627799);
    private static final UiColor PROGRESS_SHADOW = UiColor.rgba8888(0x00000066);
    private static final UiColor PROGRESS_HIGHLIGHT = UiColor.rgba8888(0x77aaffff);
    private static final UiColor PROGRESS_CAP = UiColor.rgba8888(0x4f92ffff);
    private static final float PAGE_PADDING = 24.0f;
    private static final float LOGO_PROGRESS_GAP = 0.0f;
    private static final float LOGO_WIDTH = 360.0f;
    private static final float PROGRESS_WIDTH = LOGO_WIDTH;
    private static final float PROGRESS_TRACK_HEIGHT = 10.0f;
    private static final float PROGRESS_HEIGHT = PROGRESS_TRACK_HEIGHT + 2.0f;
    private static final float PROGRESS_OFFSET_Y = 0.0f;
    private static final float COMPLETE_FILL_SECONDS = 2.0f;
    private static final float LOGO_ASPECT = 512.0f / 341.0f;

    private Texture logoTexture;
    private TextureRegion logoRegion;
    private int logoLoadCheckedFiles = -1;
    private boolean logoLoadCheckedComplete;
    private float displayedProgress;

    /**
     * Runs the create step.
     *
     * @param context the preload context
     */
    @Override
    public void create(WebPreloadContext context) {
        displayedProgress = 0.0f;
        context.ui().theme(theme());
        context.ui().setContent(ui -> ui.column(Ui.modifier().fill().padding(PAGE_PADDING).gap(LOGO_PROGRESS_GAP), page -> {
            page.spacer(Ui.modifier().weight(1.0f));
            page.custom("web-preload-logo",
                    Ui.modifier().fillWidth().maxWidth(LOGO_WIDTH).align(UiAlign.CENTER), logo -> {
                        logo.measure(constraints -> logoSize(constraints.maxWidth(), constraints.maxHeight()));
                        logo.draw(this::drawLogo);
                    });
            page.custom("web-preload-progress",
                    Ui.modifier().fillWidth().maxWidth(PROGRESS_WIDTH).height(PROGRESS_HEIGHT)
                            .align(UiAlign.CENTER).offset(0.0f, PROGRESS_OFFSET_Y),
                    progress -> {
                        progress.measure(constraints -> progressSize(constraints.maxWidth(), constraints.maxHeight()));
                        progress.draw(this::drawProgress);
                    });
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
    }

    private static UiTheme theme() {
        return Ui.darkTheme().colors(BACKGROUND, UiColor.WHITE);
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

    private static UiSize progressSize(float availableWidth, float availableHeight) {
        return new UiSize(Math.max(0.0f, Math.min(PROGRESS_WIDTH, availableWidth)),
                Math.max(0.0f, Math.min(PROGRESS_HEIGHT, availableHeight)));
    }

    private void updateProgress(WebPreloadContext context) {
        float target = context.progress().progress();
        float delta = Math.max(0.0f, context.deltaTime());
        if (context.progress().isComplete()) {
            displayedProgress = Math.min(1.0f, displayedProgress + delta / COMPLETE_FILL_SECONDS);
        } else if (target <= displayedProgress) {
            displayedProgress = target;
        } else {
            displayedProgress += (target - displayedProgress) * Math.min(1.0f, delta * 10.0f);
        }
    }

    private void drawLogo(UiDrawContext draw, UiRect bounds) {
        if (logoRegion == null || logoRegion.width() <= 0 || logoRegion.height() <= 0) {
            return;
        }
        float aspect = logoRegion.width() / (float) logoRegion.height();
        float width = Math.min(bounds.width(), bounds.height() * aspect);
        float height = width / aspect;
        if (height > bounds.height()) {
            height = bounds.height();
            width = height * aspect;
        }
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        float x = bounds.x() + (bounds.width() - width) * 0.5f;
        float y = bounds.y() + (bounds.height() - height) * 0.5f;
        draw.image(logoRegion, x, y, width, height, UiColor.WHITE);
    }

    private void drawProgress(UiDrawContext draw, UiRect bounds) {
        float width = Math.min(PROGRESS_WIDTH, bounds.width());
        float height = Math.min(PROGRESS_TRACK_HEIGHT, bounds.height());
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        float x = bounds.x() + (bounds.width() - width) * 0.5f;
        float y = bounds.y() + (bounds.height() - height) * 0.5f;
        draw.rect(x, y, width, height, PROGRESS_TRACK);
        draw.rect(x, y - 1.0f, width, 1.0f, PROGRESS_EDGE);
        draw.rect(x, y + height, width, 1.0f, PROGRESS_SHADOW);

        float fill = width * clamp01(displayedProgress);
        if (fill <= 0.0f) {
            return;
        }
        draw.rect(x, y, fill, height, LOGO_MARK);
        draw.rect(x, y, fill, 1.0f, PROGRESS_HIGHLIGHT);
        float capWidth = Math.min(8.0f, fill);
        draw.rect(x + fill - capWidth, y, capWidth, height, PROGRESS_CAP);
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
