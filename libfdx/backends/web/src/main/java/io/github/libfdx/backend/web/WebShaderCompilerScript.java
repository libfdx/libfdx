package io.github.libfdx.backend.web;

/** Shared build-time source for the browser and worker native shader bridges. */
final class WebShaderCompilerScript {
    private WebShaderCompilerScript() { }

    static String source() { return SOURCE; }

    static final String SOURCE = """
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

                    function wire(status, kind, output, diagnostics, reflection, targetInterface) {
                        var out = output || new Uint8Array(0);
                        var diag = utf8Bytes(diagnostics || "");
                        var reflected = reflection || new Uint8Array(0);
                        var translated = targetInterface || new Uint8Array(0);
                        var bytes = [70, 68, 88, 82];
                        writeInt(bytes, 2);
                        writeInt(bytes, status);
                        writeInt(bytes, kind);
                        writeInt(bytes, out.length);
                        writeInt(bytes, diag.length);
                        writeInt(bytes, reflected.length);
                        writeInt(bytes, translated.length);
                        for (var i = 0; i < out.length; i++) {
                            bytes.push(out[i]);
                        }
                        for (var j = 0; j < diag.length; j++) {
                            bytes.push(diag[j]);
                        }
                        for (var k = 0; k < reflected.length; k++) {
                            bytes.push(reflected[k]);
                        }
                        for (var m = 0; m < translated.length; m++) {
                            bytes.push(translated[m]);
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
                        var reflection;
                        var reflectionSize;
                        var targetInterface;
                        var targetInterfaceSize;
                        var freeResult;
                        try {
                            compile = module.cwrap("fdx_shaderc_compile_wgsl_handle", "number",
                                    ["number", "number", "number", "number", "number", "number", "number"]);
                            status = module.cwrap("fdx_shaderc_result_status", "number", ["number"]);
                            kind = module.cwrap("fdx_shaderc_result_output_kind", "number", ["number"]);
                            output = module.cwrap("fdx_shaderc_result_output", "number", ["number"]);
                            size = module.cwrap("fdx_shaderc_result_output_size", "number", ["number"]);
                            diagnostics = module.cwrap("fdx_shaderc_result_diagnostics", "number", ["number"]);
                            reflection = module.cwrap("fdx_shaderc_result_reflection", "number", ["number"]);
                            reflectionSize = module.cwrap("fdx_shaderc_result_reflection_size", "number", ["number"]);
                            targetInterface = module.cwrap(
                                    "fdx_shaderc_result_target_interface", "number", ["number"]);
                            targetInterfaceSize = module.cwrap(
                                    "fdx_shaderc_result_target_interface_size", "number", ["number"]);
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
                                if (!handle) {
                                    throw new Error("Native shader compiler returned no result handle");
                                }
                                var resultStatus = status(handle);
                                var resultKind = kind(handle);
                                var resultSize = size(handle);
                                if (resultSize < 0) {
                                    throw new Error("Native shader compiler returned a negative output size");
                                }
                                var resultOutput = new Uint8Array(0);
                                if (resultSize > 0) {
                                    var outputPtr = output(handle);
                                    if (!outputPtr) {
                                        throw new Error("Native shader compiler returned a null output pointer");
                                    }
                                    resultOutput = module.HEAPU8.slice(outputPtr, outputPtr + resultSize);
                                }
                                var diagnosticPtr = diagnostics(handle);
                                var diagnosticText = diagnosticPtr ? module.UTF8ToString(diagnosticPtr) : "";
                                var reflectedSize = reflectionSize(handle);
                                if (reflectedSize < 0) {
                                    throw new Error("Native shader compiler returned a negative reflection size");
                                }
                                var resultReflection = new Uint8Array(0);
                                if (reflectedSize > 0) {
                                    var reflectionPtr = reflection(handle);
                                    if (!reflectionPtr) {
                                        throw new Error("Native shader compiler returned a null reflection pointer");
                                    }
                                    resultReflection = module.HEAPU8.slice(
                                            reflectionPtr, reflectionPtr + reflectedSize);
                                }
                                var translatedSize = targetInterfaceSize(handle);
                                if (translatedSize < 0) {
                                    throw new Error("Native shader compiler returned a negative target-interface size");
                                }
                                var resultTargetInterface = new Uint8Array(0);
                                if (translatedSize > 0) {
                                    var targetInterfacePtr = targetInterface(handle);
                                    if (!targetInterfacePtr) {
                                        throw new Error(
                                                "Native shader compiler returned a null target-interface pointer");
                                    }
                                    resultTargetInterface = module.HEAPU8.slice(
                                            targetInterfacePtr, targetInterfacePtr + translatedSize);
                                }
                                return wire(resultStatus, resultKind, resultOutput, diagnosticText,
                                        resultReflection, resultTargetInterface);
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

                """;

}
