package io.github.libfdx.backend.web;

import io.github.libfdx.core.FdxException;
import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSUndefined;
import org.teavm.platform.metadata.ResourceArray;

/**
 * Coordinates web startup asset and native-runtime preloading.
 *
 * @author xpenatan
 */
public final class WebAssetPreloader {
    private static boolean installed;

    private WebAssetPreloader() {
    }

    /**
     * Runs the install and preload step.
     */
    public static void installAndPreload() {
        installAndBeginPreload().await();
        if (isFailed()) {
            throw new FdxException("Web preload failed: " + errorMessage());
        }
    }

    /**
     * Runs the install and begin preload step.
     *
     * @return the preload promise
     */
    public static JSPromise<JSUndefined> installAndBeginPreload() {
        install();
        return beginPreload();
    }

    /**
     * Runs the install step.
     */
    public static void install() {
        if (installed) {
            return;
        }
        ResourceArray<WebGeneratedAsset> assets = WebGeneratedAssets.assets();
        beginInstall();
        if (assets != null) {
            for (int index = 0; index < assets.size(); index++) {
                WebGeneratedAsset asset = assets.get(index);
                addAsset(asset.getPath(), asset.getSize());
            }
        }
        addAsset(WebAssets.DEFAULT_PRELOAD_LOGO_PATH, WebAssets.DEFAULT_PRELOAD_LOGO_SIZE);
        finishInstall();
        installed = true;
    }

    /**
     * Returns whether startup preloading completed.
     *
     * @return true if complete
     */
    public static boolean isComplete() {
        return preloadComplete();
    }

    /**
     * Returns whether startup preloading failed.
     *
     * @return true if failed
     */
    public static boolean isFailed() {
        return preloadFailed();
    }

    /**
     * Returns the preload error message.
     *
     * @return the error message
     */
    public static String errorMessage() {
        return preloadErrorMessage();
    }

    /**
     * Returns the loaded file count.
     *
     * @return the loaded file count
     */
    public static int loadedFiles() {
        return preloadLoadedFiles();
    }

    /**
     * Returns the total file count.
     *
     * @return the total file count
     */
    public static int totalFiles() {
        return preloadTotalFiles();
    }

    /**
     * Returns the loaded byte count.
     *
     * @return the loaded byte count
     */
    public static double loadedBytes() {
        return preloadLoadedBytes();
    }

    /**
     * Returns the total byte count.
     *
     * @return the total byte count
     */
    public static double totalBytes() {
        return preloadTotalBytes();
    }

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "root.libfdxAssetPaths = [];\n" +
            "root.libfdxAssetManifest = Object.create(null);\n" +
            "root.libfdxAssets = root.libfdxAssets || Object.create(null);\n" +
            "root.libfdxImageData = root.libfdxImageData || Object.create(null);\n" +
            "root.libfdxPreloadPromise = null;\n" +
            "root.libfdxPreloadState = {\n" +
            "  active: false,\n" +
            "  complete: false,\n" +
            "  failed: false,\n" +
            "  errorMessage: '',\n" +
            "  loadedFiles: 0,\n" +
            "  totalFiles: 0,\n" +
            "  loadedBytes: 0,\n" +
            "  totalBytes: 0\n" +
            "};\n" +
            "var runtime = root.libfdxRuntimePreload || {};\n" +
            "[runtime.script, runtime.wasm].forEach(function(entry) {\n" +
            "  if (!entry || !(entry.size > 0)) return;\n" +
            "  root.libfdxPreloadState.totalFiles += 1;\n" +
            "  root.libfdxPreloadState.totalBytes += entry.size;\n" +
            "});\n" +
            "function normalize(path) {\n" +
            "  path = (path || '').replace(/\\\\/g, '/');\n" +
            "  while (path.indexOf('./') === 0) path = path.substring(2);\n" +
            "  while (path.indexOf('/') === 0) path = path.substring(1);\n" +
            "  if (path.indexOf('assets/') === 0) path = path.substring(7);\n" +
            "  return path;\n" +
            "}\n" +
            "function isImageAsset(path) {\n" +
            "  path = normalize(path).toLowerCase();\n" +
            "  return path.endsWith('.png') || path.endsWith('.jpg') || path.endsWith('.jpeg');\n" +
            "}\n" +
            "function decodeImageAsset(path, buffer) {\n" +
            "  return loadImage(buffer).then(function(image) {\n" +
            "    var canvas = document.createElement('canvas');\n" +
            "    canvas.width = image.width || image.naturalWidth;\n" +
            "    canvas.height = image.height || image.naturalHeight;\n" +
            "    var context = canvas.getContext('2d');\n" +
            "    context.drawImage(image, 0, 0);\n" +
            "    if (typeof image.close === 'function') image.close();\n" +
            "    var rgba = context.getImageData(0, 0, canvas.width, canvas.height).data;\n" +
            "    var copy = new Uint8Array(rgba.length);\n" +
            "    copy.set(rgba);\n" +
            "    root.libfdxImageData[normalize(path)] = {\n" +
            "      width: canvas.width,\n" +
            "      height: canvas.height,\n" +
            "      rgba: copy\n" +
            "    };\n" +
            "    root.libfdxImageData['assets/' + normalize(path)] = root.libfdxImageData[normalize(path)];\n" +
            "  });\n" +
            "}\n" +
            "function markLoaded(path, buffer) {\n" +
            "  var state = root.libfdxPreloadState;\n" +
            "  var normalized = normalize(path);\n" +
            "  var entry = root.libfdxAssetManifest && root.libfdxAssetManifest[normalized];\n" +
            "  var byteLength = entry && typeof entry.size === 'number' ? entry.size : ((buffer && (buffer.byteLength || buffer.length)) || 0);\n" +
            "  state.loadedFiles = Math.min(state.totalFiles, state.loadedFiles + 1);\n" +
            "  state.loadedBytes += Math.max(0, byteLength);\n" +
            "  if (state.totalBytes > 0) {\n" +
            "    state.loadedBytes = Math.min(state.totalBytes, state.loadedBytes);\n" +
            "  }\n" +
            "}\n" +
            "function loadImage(buffer) {\n" +
            "  var blob = new Blob([buffer]);\n" +
            "  if (root.createImageBitmap) {\n" +
            "    return root.createImageBitmap(blob);\n" +
            "  }\n" +
            "  return new Promise(function(resolve, reject) {\n" +
            "    var image = new Image();\n" +
            "    var url = URL.createObjectURL(blob);\n" +
            "    image.onload = function() {\n" +
            "      URL.revokeObjectURL(url);\n" +
            "      resolve(image);\n" +
            "    };\n" +
            "    image.onerror = function(error) {\n" +
            "      URL.revokeObjectURL(url);\n" +
            "      reject(error);\n" +
            "    };\n" +
            "    image.src = url;\n" +
            "  });\n" +
            "}\n" +
            "root.__libfdxAssetNormalize = normalize;\n" +
            "root.libfdxPreloadAssets = function(assetPaths) {\n" +
            "  assetPaths = assetPaths || root.libfdxAssetPaths || [];\n" +
            "  var state = root.libfdxPreloadState;\n" +
            "  state.active = true;\n" +
            "  state.complete = false;\n" +
            "  state.failed = false;\n" +
            "  state.errorMessage = '';\n" +
            "  state.loadedFiles = 0;\n" +
            "  state.loadedBytes = 0;\n" +
            "  if (!assetPaths.length) {\n" +
            "    return Promise.resolve();\n" +
            "  }\n" +
            "  var assets = root.libfdxAssets;\n" +
            "  return Promise.all(assetPaths.map(function(path) {\n" +
            "    var normalized = normalize(path);\n" +
            "    var existing = assets[normalized] || assets['assets/' + normalized];\n" +
            "    if (existing) {\n" +
            "      markLoaded(normalized, existing);\n" +
            "      return Promise.resolve();\n" +
            "    }\n" +
            "    return fetch('assets/' + normalized).then(function(response) {\n" +
            "      if (!response.ok) throw new Error('Could not preload asset ' + normalized + ': ' + response.status);\n" +
            "      return response.arrayBuffer();\n" +
            "    }).then(function(buffer) {\n" +
            "      assets[normalized] = buffer;\n" +
            "      assets['assets/' + normalized] = buffer;\n" +
            "      if (isImageAsset(normalized)) {\n" +
            "        return decodeImageAsset(normalized, buffer).then(function() {\n" +
            "          markLoaded(normalized, buffer);\n" +
            "        });\n" +
            "      }\n" +
            "      markLoaded(normalized, buffer);\n" +
            "    });\n" +
            "  }));\n" +
            "};")
    private static native void beginInstall();

    @JSBody(params = { "path", "size" }, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var normalize = root.__libfdxAssetNormalize || function(value) {\n" +
            "  value = (value || '').replace(/\\\\/g, '/');\n" +
            "  while (value.indexOf('./') === 0) value = value.substring(2);\n" +
            "  while (value.indexOf('/') === 0) value = value.substring(1);\n" +
            "  if (value.indexOf('assets/') === 0) value = value.substring(7);\n" +
            "  return value;\n" +
            "};\n" +
            "var normalized = normalize(path);\n" +
            "root.libfdxAssetManifest = root.libfdxAssetManifest || Object.create(null);\n" +
            "if (root.libfdxAssetManifest[normalized] || root.libfdxAssetManifest['assets/' + normalized]) return;\n" +
            "root.libfdxAssetPaths = root.libfdxAssetPaths || [];\n" +
            "root.libfdxAssetPaths.push(normalized);\n" +
            "var entry = { size: size };\n" +
            "root.libfdxAssetManifest[normalized] = entry;\n" +
            "root.libfdxAssetManifest['assets/' + normalized] = entry;\n" +
            "root.libfdxPreloadState = root.libfdxPreloadState || { active: false, complete: false, failed: false, errorMessage: '', loadedFiles: 0, totalFiles: 0, loadedBytes: 0, totalBytes: 0 };\n" +
            "root.libfdxPreloadState.totalFiles += 1;\n" +
            "root.libfdxPreloadState.totalBytes += Math.max(0, size || 0);")
    private static native void addAsset(String path, double size);

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "delete root.__libfdxAssetNormalize;")
    private static native void finishInstall();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState || (root.libfdxPreloadState = { active: false, complete: false, failed: false, errorMessage: '', loadedFiles: 0, totalFiles: 0, loadedBytes: 0, totalBytes: 0 });\n" +
            "if (root.libfdxPreloadPromise && !state.failed) {\n" +
            "  return root.libfdxPreloadPromise.then(function() { return undefined; });\n" +
            "}\n" +
            "var preloadAssets = root.libfdxPreloadAssets || function() { return Promise.resolve(); };\n" +
            "var preloadRuntime = root.libfdxPreloadRuntimeCore || function() { return Promise.resolve(); };\n" +
            "root.libfdxPreloadPromise = Promise.all([\n" +
            "  preloadAssets(root.libfdxAssetPaths || []),\n" +
            "  preloadRuntime()\n" +
            "]).then(function() {\n" +
            "  state.loadedFiles = state.totalFiles;\n" +
            "  state.loadedBytes = state.totalBytes;\n" +
            "  state.complete = true;\n" +
            "}).catch(function(error) {\n" +
            "  state.failed = true;\n" +
            "  state.complete = true;\n" +
            "  state.errorMessage = error && error.message ? error.message : String(error || 'Unknown preload error');\n" +
            "}).then(function() { return undefined; });\n" +
            "return root.libfdxPreloadPromise;")
    private static native JSPromise<JSUndefined> beginPreload();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return !!state && state.complete === true;")
    private static native boolean preloadComplete();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return !!state && state.failed === true;")
    private static native boolean preloadFailed();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return state && state.errorMessage ? state.errorMessage : '';")
    private static native String preloadErrorMessage();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return state && typeof state.loadedFiles === 'number' ? state.loadedFiles : 0;")
    private static native int preloadLoadedFiles();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return state && typeof state.totalFiles === 'number' ? state.totalFiles : 0;")
    private static native int preloadTotalFiles();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return state && typeof state.loadedBytes === 'number' ? state.loadedBytes : 0;")
    private static native double preloadLoadedBytes();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var state = root.libfdxPreloadState;\n" +
            "return state && typeof state.totalBytes === 'number' ? state.totalBytes : 0;")
    private static native double preloadTotalBytes();
}
