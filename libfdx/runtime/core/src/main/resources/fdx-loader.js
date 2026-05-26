(function(root) {
    "use strict";

    var modulePromise = null;
    var scriptUrl = (document.currentScript && document.currentScript.src) || "scripts/fdx-loader.js";

    function moduleBaseUrl(path) {
        return new URL(path, scriptUrl).href;
    }

    function javaArrayLength(source) {
        if (!source) return 0;
        if (typeof source.length === "number") return source.length;
        if (source.data && typeof source.data.length === "number") return source.data.length;
        if (source.$data && typeof source.$data.length === "number") return source.$data.length;
        return 0;
    }

    function copyJavaArray(source, ctor) {
        var length = javaArrayLength(source);
        var target = new ctor(length);
        if (!source || length === 0) return target;
        var data = source.data || source.$data || source;
        if (ArrayBuffer.isView(data)) {
            target.set(new ctor(data.buffer, data.byteOffset, Math.min(length, data.length)));
            return target;
        }
        for (var i = 0; i < length; i++) {
            target[i] = data[i];
        }
        return target;
    }

    function ensureModule() {
        if (root.libfdxCoreModule) {
            return Promise.resolve(root.libfdxCoreModule);
        }
        if (modulePromise) {
            return modulePromise;
        }
        if (typeof root.FdxModule !== "function") {
            return Promise.reject(new Error("libfdx core Emscripten module script was not loaded"));
        }
        modulePromise = root.FdxModule({
            locateFile: function(path) {
                return path === "fdx.wasm" ? moduleBaseUrl(path) : path;
            }
        }).then(function(module) {
            root.libfdxCoreModule = module;
            return module;
        });
        return modulePromise;
    }

    function rasterize(fontBytes, codePoints, pixelSize, padding, atlasWidth) {
        var module = root.libfdxCoreModule;
        if (!module) {
            throw new Error("libfdx FreeType Emscripten module is not ready");
        }

        var font = copyJavaArray(fontBytes, Int8Array);
        var points = copyJavaArray(codePoints, Int32Array);
        var fontPtr = 0;
        var codePointPtr = 0;
        var metricIntsPtr = 0;
        var metricFloatsPtr = 0;
        var rgbaPtr = 0;
        var glyphIntsPtr = 0;
        var glyphFloatsPtr = 0;
        var kerningIntsPtr = 0;

        try {
            fontPtr = module._malloc(Math.max(1, font.byteLength));
            codePointPtr = module._malloc(Math.max(1, points.byteLength));
            metricIntsPtr = module._malloc(16);
            metricFloatsPtr = module._malloc(12);
            module.HEAP8.set(font, fontPtr);
            module.HEAP32.set(points, codePointPtr >> 2);

            var measured = module._fdx_freetype_rasterize(fontPtr, font.byteLength, codePointPtr, points.length,
                    pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, 0, 0, 0, 0, 0, 0, 0, 0);
            if (!measured) {
                throw new Error("FreeType failed to measure native web font");
            }

            var metricInts = new Int32Array(module.HEAP32.buffer, metricIntsPtr, 4);
            var metricFloats = new Float32Array(module.HEAPF32.buffer, metricFloatsPtr, 3);
            var width = metricInts[0];
            var height = metricInts[1];
            var glyphCount = metricInts[2];
            var kerningCount = metricInts[3];
            if (width <= 0 || height <= 0 || glyphCount < 0 || kerningCount < 0) {
                throw new Error("FreeType returned invalid native web font metrics");
            }

            var rgbaSize = width * height * 4;
            rgbaPtr = module._malloc(Math.max(1, rgbaSize));
            glyphIntsPtr = module._malloc(Math.max(1, glyphCount * 5 * 4));
            glyphFloatsPtr = module._malloc(Math.max(1, glyphCount * 3 * 4));
            kerningIntsPtr = module._malloc(Math.max(1, kerningCount * 3 * 4));

            var rasterized = module._fdx_freetype_rasterize(fontPtr, font.byteLength, codePointPtr, points.length,
                    pixelSize, padding, atlasWidth, metricIntsPtr, metricFloatsPtr, rgbaPtr, rgbaSize,
                    glyphIntsPtr, glyphCount * 5, glyphFloatsPtr, glyphCount * 3, kerningIntsPtr, kerningCount * 3);
            if (!rasterized) {
                throw new Error("FreeType failed to rasterize native web font");
            }

            return {
                nativeSize: metricFloats[0],
                lineHeight: metricFloats[1],
                baseLine: metricFloats[2],
                atlasWidth: width,
                atlasHeight: height,
                glyphCount: glyphCount,
                kerningCount: kerningCount,
                rgba: new Int8Array(module.HEAPU8.slice(rgbaPtr, rgbaPtr + rgbaSize).buffer),
                glyphInts: new Int32Array(module.HEAP32.slice(glyphIntsPtr >> 2, (glyphIntsPtr >> 2) + glyphCount * 5).buffer),
                glyphFloats: new Float32Array(module.HEAPF32.slice(glyphFloatsPtr >> 2, (glyphFloatsPtr >> 2) + glyphCount * 3).buffer),
                kerningInts: new Int32Array(module.HEAP32.slice(kerningIntsPtr >> 2, (kerningIntsPtr >> 2) + kerningCount * 3).buffer)
            };
        } finally {
            if (kerningIntsPtr) module._free(kerningIntsPtr);
            if (glyphFloatsPtr) module._free(glyphFloatsPtr);
            if (glyphIntsPtr) module._free(glyphIntsPtr);
            if (rgbaPtr) module._free(rgbaPtr);
            if (metricFloatsPtr) module._free(metricFloatsPtr);
            if (metricIntsPtr) module._free(metricIntsPtr);
            if (codePointPtr) module._free(codePointPtr);
            if (fontPtr) module._free(fontPtr);
        }
    }

    root.libfdxPreloadRuntimeCore = ensureModule;
    root.libfdxFreeTypeRasterize = rasterize;
})(typeof window !== "undefined" ? window : globalThis);
