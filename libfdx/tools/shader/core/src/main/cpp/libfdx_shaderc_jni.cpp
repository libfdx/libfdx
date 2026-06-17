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

std::string EncodeResult(const fdx_shaderc_result& result) {
    const char* diagnostics = result.diagnostics != nullptr ? result.diagnostics : "";
    int32_t diagnostic_size = static_cast<int32_t>(std::char_traits<char>::length(diagnostics));
    std::vector<uint8_t> bytes;
    bytes.reserve(16 + static_cast<size_t>(result.output_size) + static_cast<size_t>(diagnostic_size));
    WriteInt(bytes, result.status);
    WriteInt(bytes, result.output_kind);
    WriteInt(bytes, result.output_size);
    WriteInt(bytes, diagnostic_size);
    if (result.output != nullptr && result.output_size > 0) {
        bytes.insert(bytes.end(), result.output, result.output + result.output_size);
    }
    bytes.insert(bytes.end(), diagnostics, diagnostics + diagnostic_size);
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

    fdx_shaderc_options options = {};
    options.target = target;
    options.stage = stage;
    options.entry_point = entry_chars.c_str();
    options.glsl_profile = glsl_chars.c_str();
    options.glsl_es_profile = glsl_es_chars.c_str();

    fdx_shaderc_result result = {};
    fdx_shaderc_compile_wgsl(source_chars.c_str(),
                             static_cast<int32_t>(std::char_traits<char>::length(source_chars.c_str())),
                             &options,
                             &result);
    std::string encoded = EncodeResult(result);
    fdx_shaderc_free_result(&result);
    return env->NewStringUTF(encoded.c_str());
}
