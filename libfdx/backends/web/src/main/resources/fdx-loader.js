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

    function base64(bytes) {
        var binary = "";
        for (var i = 0; i < bytes.length; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }

    function writeInt(bytes, value) {
        bytes.push(value & 255);
        bytes.push((value >> 8) & 255);
        bytes.push((value >> 16) & 255);
        bytes.push((value >> 24) & 255);
    }

    function utf8Bytes(text) {
        return new TextEncoder().encode(text || "");
    }

    function wire(status, kind, output, diagnostics) {
        var out = output || new Uint8Array(0);
        var diag = utf8Bytes(diagnostics || "");
        var bytes = [];
        writeInt(bytes, status);
        writeInt(bytes, kind);
        writeInt(bytes, out.length);
        writeInt(bytes, diag.length);
        for (var i = 0; i < out.length; i++) {
            bytes.push(out[i]);
        }
        for (var j = 0; j < diag.length; j++) {
            bytes.push(diag[j]);
        }
        return base64(bytes);
    }

    function installShaderCompiler(module) {
        if (root.libfdxShaderCompileBase64) {
            return;
        }
        if (typeof module.cwrap !== "function"
                || typeof module.lengthBytesUTF8 !== "function"
                || typeof module.stringToUTF8 !== "function"
                || typeof module.UTF8ToString !== "function") {
            return;
        }

        var compile;
        var status;
        var kind;
        var output;
        var size;
        var diagnostics;
        var freeResult;
        try {
            compile = module.cwrap("fdx_shaderc_compile_wgsl_handle", "number",
                    ["number", "number", "number", "number", "number", "number", "number"]);
            status = module.cwrap("fdx_shaderc_result_status", "number", ["number"]);
            kind = module.cwrap("fdx_shaderc_result_output_kind", "number", ["number"]);
            output = module.cwrap("fdx_shaderc_result_output", "number", ["number"]);
            size = module.cwrap("fdx_shaderc_result_output_size", "number", ["number"]);
            diagnostics = module.cwrap("fdx_shaderc_result_diagnostics", "number", ["number"]);
            freeResult = module.cwrap("fdx_shaderc_result_free", null, ["number"]);
        } catch (ignored) {
            return;
        }

        root.libfdxShaderCompileBase64 = function(source, target, stage, entryPoint, glslProfile, glslEsProfile) {
            var sourceSize = module.lengthBytesUTF8(source);
            var entrySize = module.lengthBytesUTF8(entryPoint);
            var glslSize = module.lengthBytesUTF8(glslProfile);
            var glslEsSize = module.lengthBytesUTF8(glslEsProfile);
            var sourcePtr = module._malloc(sourceSize + 1);
            var entryPtr = module._malloc(entrySize + 1);
            var glslPtr = module._malloc(glslSize + 1);
            var glslEsPtr = module._malloc(glslEsSize + 1);
            var handle = 0;
            try {
                module.stringToUTF8(source, sourcePtr, sourceSize + 1);
                module.stringToUTF8(entryPoint, entryPtr, entrySize + 1);
                module.stringToUTF8(glslProfile, glslPtr, glslSize + 1);
                module.stringToUTF8(glslEsProfile, glslEsPtr, glslEsSize + 1);
                handle = compile(sourcePtr, sourceSize, target, stage, entryPtr, glslPtr, glslEsPtr);
                var resultStatus = status(handle);
                var resultKind = kind(handle);
                var resultSize = size(handle);
                var resultOutput = new Uint8Array(0);
                if (resultSize > 0) {
                    var outputPtr = output(handle);
                    resultOutput = module.HEAPU8.slice(outputPtr, outputPtr + resultSize);
                }
                var diagnosticPtr = diagnostics(handle);
                var diagnosticText = diagnosticPtr ? module.UTF8ToString(diagnosticPtr) : "";
                return wire(resultStatus, resultKind, resultOutput, diagnosticText);
            } finally {
                if (handle) {
                    freeResult(handle);
                }
                module._free(sourcePtr);
                module._free(entryPtr);
                module._free(glslPtr);
                module._free(glslEsPtr);
            }
        };
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
            installShaderCompiler(module);
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
