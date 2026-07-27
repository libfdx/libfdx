#include "fdx_shaderc.h"

#include <jni.h>

#include <cstdint>
#include <string>
#include <vector>

namespace {

constexpr char kBase64[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

void WriteInt(std::vector<uint8_t>& bytes, int32_t value) {
    bytes.push_back(static_cast<uint8_t>(value & 0xff));
    bytes.push_back(static_cast<uint8_t>((value >> 8) & 0xff));
    bytes.push_back(static_cast<uint8_t>((value >> 16) & 0xff));
    bytes.push_back(static_cast<uint8_t>((value >> 24) & 0xff));
}

std::string Base64Encode(const std::vector<uint8_t>& bytes) {
    std::string out;
    out.reserve(((bytes.size() + 2) / 3) * 4);
    for (size_t i = 0; i < bytes.size(); i += 3) {
        uint32_t value = static_cast<uint32_t>(bytes[i]) << 16;
        bool has_second = i + 1 < bytes.size();
        bool has_third = i + 2 < bytes.size();
        if (has_second) {
            value |= static_cast<uint32_t>(bytes[i + 1]) << 8;
        }
        if (has_third) {
            value |= bytes[i + 2];
        }
        out.push_back(kBase64[(value >> 18) & 0x3f]);
        out.push_back(kBase64[(value >> 12) & 0x3f]);
        out.push_back(has_second ? kBase64[(value >> 6) & 0x3f] : '=');
        out.push_back(has_third ? kBase64[value & 0x3f] : '=');
    }
    return out;
}

std::string EncodeResult(int32_t status,
                         int32_t output_kind,
                         const uint8_t* output,
                         int32_t output_size,
                         const char* diagnostic_text,
                         const uint8_t* reflection,
                         int32_t reflection_size,
                         const uint8_t* target_interface,
                         int32_t target_interface_size) {
    const char* diagnostics = diagnostic_text != nullptr ? diagnostic_text : "";
    int32_t diagnostic_size = static_cast<int32_t>(std::char_traits<char>::length(diagnostics));
    std::vector<uint8_t> bytes;
    bytes.reserve(32 + static_cast<size_t>(output_size) +
                  static_cast<size_t>(diagnostic_size) +
                  static_cast<size_t>(reflection_size) +
                  static_cast<size_t>(target_interface_size));
    bytes.push_back('F');
    bytes.push_back('D');
    bytes.push_back('X');
    bytes.push_back('R');
    WriteInt(bytes, 2);
    WriteInt(bytes, status);
    WriteInt(bytes, output_kind);
    WriteInt(bytes, output_size);
    WriteInt(bytes, diagnostic_size);
    WriteInt(bytes, reflection_size);
    WriteInt(bytes, target_interface_size);
    if (output != nullptr && output_size > 0) {
        bytes.insert(bytes.end(), output, output + output_size);
    }
    bytes.insert(bytes.end(), diagnostics, diagnostics + diagnostic_size);
    if (reflection != nullptr && reflection_size > 0) {
        bytes.insert(bytes.end(), reflection, reflection + reflection_size);
    }
    if (target_interface != nullptr && target_interface_size > 0) {
        bytes.insert(bytes.end(), target_interface, target_interface + target_interface_size);
    }
    return Base64Encode(bytes);
}

class JStringChars {
  public:
    JStringChars(JNIEnv* env, jstring value) : env_(env), value_(value) {
        chars_ = value != nullptr ? env->GetStringUTFChars(value, nullptr) : nullptr;
    }

    ~JStringChars() {
        if (chars_ != nullptr) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char* c_str() const {
        return chars_ != nullptr ? chars_ : "";
    }

  private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_;
};

std::string CompileEncoded(const char* source,
                           int32_t source_size,
                           int32_t target,
                           int32_t stage,
                           const char* entry_point,
                           const char* glsl_profile,
                           const char* glsl_es_profile) {
    void* result = fdx_shaderc_compile_wgsl_handle(source, source_size, target, stage, entry_point,
                                                   glsl_profile, glsl_es_profile);
    if (result == nullptr) {
        return EncodeResult(1, FDX_SHADERC_OUTPUT_NONE, nullptr, 0,
                            "Runtime shader compiler allocation failed", nullptr, 0, nullptr, 0);
    }
    std::string encoded =
        EncodeResult(fdx_shaderc_result_status(result), fdx_shaderc_result_output_kind(result),
                     fdx_shaderc_result_output(result), fdx_shaderc_result_output_size(result),
                     fdx_shaderc_result_diagnostics(result),
                     fdx_shaderc_result_reflection(result),
                     fdx_shaderc_result_reflection_size(result),
                     fdx_shaderc_result_target_interface(result),
                     fdx_shaderc_result_target_interface_size(result));
    fdx_shaderc_result_free(result);
    return encoded;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_libfdx_tools_shader_FdxTintAndroidJniCompilerBridge_compileNative(
    JNIEnv* env,
    jclass,
    jstring source,
    jint target,
    jint stage,
    jstring entry_point,
    jstring glsl_profile,
    jstring glsl_es_profile) {
    JStringChars source_chars(env, source);
    JStringChars entry_chars(env, entry_point);
    JStringChars glsl_chars(env, glsl_profile);
    JStringChars glsl_es_chars(env, glsl_es_profile);

    std::string encoded =
        CompileEncoded(source_chars.c_str(),
                       static_cast<int32_t>(std::char_traits<char>::length(source_chars.c_str())),
                       target, stage, entry_chars.c_str(), glsl_chars.c_str(),
                       glsl_es_chars.c_str());
    return env->NewStringUTF(encoded.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_io_github_libfdx_backend_android_AndroidRuntimeShaderCompiler_compileNative(
    JNIEnv* env,
    jclass,
    jbyteArray source,
    jint target,
    jint stage,
    jstring entry_point,
    jstring glsl_profile,
    jstring glsl_es_profile) {
    JStringChars entry_chars(env, entry_point);
    JStringChars glsl_chars(env, glsl_profile);
    JStringChars glsl_es_chars(env, glsl_es_profile);
    jsize source_size = source != nullptr ? env->GetArrayLength(source) : 0;
    std::vector<uint8_t> source_bytes(static_cast<size_t>(source_size));
    if (source_size > 0) {
        env->GetByteArrayRegion(source, 0, source_size,
                                reinterpret_cast<jbyte*>(source_bytes.data()));
        if (env->ExceptionCheck()) {
            return nullptr;
        }
    }
    const char* source_data =
        source_bytes.empty() ? nullptr : reinterpret_cast<const char*>(source_bytes.data());
    std::string encoded =
        CompileEncoded(source_data, source_size, target, stage, entry_chars.c_str(),
                       glsl_chars.c_str(), glsl_es_chars.c_str());
    return env->NewStringUTF(encoded.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_libfdx_backend_android_AndroidRuntimeShaderCompiler_isAvailableNative(JNIEnv*, jclass) {
    return JNI_TRUE;
}
