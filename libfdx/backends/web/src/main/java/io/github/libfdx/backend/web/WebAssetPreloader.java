package io.github.libfdx.backend.web;

import org.teavm.jso.JSBody;
import org.teavm.jso.core.JSPromise;
import org.teavm.jso.core.JSUndefined;
import org.teavm.platform.metadata.ResourceArray;

public final class WebAssetPreloader {
    private static boolean installed;

    private WebAssetPreloader() {
    }

    public static void installAndPreload() {
        install();
        preloadAssets().await();
    }

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
        finishInstall();
        installed = true;
    }

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "root.libfdxAssetPaths = [];\n" +
            "root.libfdxAssetManifest = Object.create(null);\n" +
            "root.libfdxAssets = root.libfdxAssets || Object.create(null);\n" +
            "root.libfdxImageData = root.libfdxImageData || Object.create(null);\n" +
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
            "  if (!assetPaths.length) return Promise.resolve();\n" +
            "  var assets = root.libfdxAssets;\n" +
            "  return Promise.all(assetPaths.map(function(path) {\n" +
            "    var normalized = normalize(path);\n" +
            "    return fetch('assets/' + normalized).then(function(response) {\n" +
            "      if (!response.ok) throw new Error('Could not preload asset ' + normalized + ': ' + response.status);\n" +
            "      return response.arrayBuffer();\n" +
            "    }).then(function(buffer) {\n" +
            "      assets[normalized] = buffer;\n" +
            "      assets['assets/' + normalized] = buffer;\n" +
            "      if (isImageAsset(normalized)) {\n" +
            "        return decodeImageAsset(normalized, buffer);\n" +
            "      }\n" +
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
            "root.libfdxAssetPaths = root.libfdxAssetPaths || [];\n" +
            "root.libfdxAssetPaths.push(normalized);\n" +
            "root.libfdxAssetManifest = root.libfdxAssetManifest || Object.create(null);\n" +
            "var entry = { size: size };\n" +
            "root.libfdxAssetManifest[normalized] = entry;\n" +
            "root.libfdxAssetManifest['assets/' + normalized] = entry;")
    private static native void addAsset(String path, double size);

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "delete root.__libfdxAssetNormalize;")
    private static native void finishInstall();

    @JSBody(params = {}, script =
            "var root = typeof window !== 'undefined' ? window : globalThis;\n" +
            "var preload = root.libfdxPreloadAssets || function() { return Promise.resolve(); };\n" +
            "return preload(root.libfdxAssetPaths || []).then(function() { return undefined; });")
    private static native JSPromise<JSUndefined> preloadAssets();
}
