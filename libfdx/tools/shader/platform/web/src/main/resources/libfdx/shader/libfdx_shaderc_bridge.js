(function(global) {
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

    function install(module) {
        var compile = module.cwrap("fdx_shaderc_compile_wgsl_handle", "number",
                ["number", "number", "number", "number", "number", "number", "number"]);
        var status = module.cwrap("fdx_shaderc_result_status", "number", ["number"]);
        var kind = module.cwrap("fdx_shaderc_result_output_kind", "number", ["number"]);
        var output = module.cwrap("fdx_shaderc_result_output", "number", ["number"]);
        var size = module.cwrap("fdx_shaderc_result_output_size", "number", ["number"]);
        var diagnostics = module.cwrap("fdx_shaderc_result_diagnostics", "number", ["number"]);
        var freeResult = module.cwrap("fdx_shaderc_result_free", null, ["number"]);

        global.LibFdxShaderc = {
            compileBase64: function(source, target, stage, entryPoint, glslProfile, glslEsProfile) {
                var sourceSize = module.lengthBytesUTF8(source);
                var sourcePtr = module._malloc(sourceSize + 1);
                var entryPtr = module._malloc(module.lengthBytesUTF8(entryPoint) + 1);
                var glslPtr = module._malloc(module.lengthBytesUTF8(glslProfile) + 1);
                var glslEsPtr = module._malloc(module.lengthBytesUTF8(glslEsProfile) + 1);
                var handle = 0;
                try {
                    module.stringToUTF8(source, sourcePtr, sourceSize + 1);
                    module.stringToUTF8(entryPoint, entryPtr, module.lengthBytesUTF8(entryPoint) + 1);
                    module.stringToUTF8(glslProfile, glslPtr, module.lengthBytesUTF8(glslProfile) + 1);
                    module.stringToUTF8(glslEsProfile, glslEsPtr, module.lengthBytesUTF8(glslEsProfile) + 1);
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
            }
        };
        return global.LibFdxShaderc;
    }

    global.installLibFdxShaderc = install;
})(typeof globalThis !== "undefined" ? globalThis : self);
