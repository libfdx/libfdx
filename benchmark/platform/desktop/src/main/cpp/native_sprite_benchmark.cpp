#include <jni.h>
#include <GL/glew.h>
#include <GLFW/glfw3.h>

#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_PNG
#include <stb_image.h>

#include "sprite_vertices.hpp"

#include <algorithm>
#include <array>
#include <chrono>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <memory>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace {
constexpr int spriteCount = 8191;
constexpr int floatsPerSprite = 20;
constexpr int vertexBytes = spriteCount * floatsPerSprite * sizeof(float);
constexpr int indexCount = spriteCount * 6;
constexpr std::uint64_t positionSeed = 0x51f15e2d;
constexpr std::int64_t nanosPerSecond = 1000000000;
constexpr const char* texturePath = "benchmark/assets/fdx.png";

std::int64_t now() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
            std::chrono::steady_clock::now().time_since_epoch()).count();
}

struct Options {
    double seconds = 20;
    double warmup = 5;
    bool profile = false;
    bool cpuDiagnostic = false;
    std::string result;
    std::string capture;
};

double readSeconds(const std::string& value) {
    std::size_t end;
    const double seconds = std::stod(value, &end);
    if (end != value.size() || !std::isfinite(seconds) || seconds < 0 || seconds > 3600) {
        throw std::invalid_argument("Benchmark duration must be between 0 and 3600 seconds");
    }
    return seconds;
}

Options parse(JNIEnv* env, jobjectArray args) {
    Options options;
    const jsize length = env->GetArrayLength(args);
    for (jsize i = 0; i < length; ++i) {
        auto argument = static_cast<jstring>(env->GetObjectArrayElement(args, i));
        if (!argument) throw std::invalid_argument("Missing benchmark argument");
        const char* text = env->GetStringUTFChars(argument, nullptr);
        if (!text) throw std::runtime_error("Could not read benchmark argument");
        const std::string value(text);
        env->ReleaseStringUTFChars(argument, text);
        env->DeleteLocalRef(argument);
        const auto separator = value.find('=');
        if (separator == std::string::npos) throw std::invalid_argument("Expected --name=value: " + value);
        const auto name = value.substr(0, separator);
        const auto setting = value.substr(separator + 1);
        if (name == "--seconds") options.seconds = readSeconds(setting);
        else if (name == "--warmupSeconds") options.warmup = readSeconds(setting);
        else if (name == "--result") options.result = setting;
        else if (name == "--capture") options.capture = setting;
        else if (name == "--cpuDiagnostic" && setting == "full") options.cpuDiagnostic = true;
        else if (name == "--frameProfile" && (setting == "true" || setting == "false")) options.profile = setting == "true";
        else if (name != "--sprites" || setting != "8191") throw std::invalid_argument("Unsupported option: " + value);
    }
    if (options.capture.empty() && options.seconds <= options.warmup) {
        throw std::invalid_argument("Benchmark duration must exceed warm-up");
    }
    if (options.cpuDiagnostic && (options.profile || !options.capture.empty())) {
        throw std::invalid_argument("cpuDiagnostic requires a run without frame profiling or capture");
    }
    return options;
}

class Window {
public:
    GLFWwindow* handle = nullptr;

    Window() {
        if (!glfwInit()) throw std::runtime_error("GLFW initialization failed");
    }

    ~Window() {
        if (handle) glfwDestroyWindow(handle);
        glfwTerminate();
    }

    void open() {
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);
        handle = glfwCreateWindow(640, 480, "libFDX benchmark - C++ through JNI", nullptr, nullptr);
        if (!handle) throw std::runtime_error("Could not create the OpenGL window");
        glfwMakeContextCurrent(handle);
        glfwSwapInterval(0);
        if (glewInit() != GLEW_OK) throw std::runtime_error("GLEW initialization failed");
        glGetError(); // GLEW can leave GL_INVALID_ENUM on a core context.
    }
};

GLuint compile(GLenum stage, const char* source) {
    const GLuint shader = glCreateShader(stage);
    glShaderSource(shader, 1, &source, nullptr);
    glCompileShader(shader);
    GLint ok;
    glGetShaderiv(shader, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024]{};
        glGetShaderInfoLog(shader, sizeof(log), nullptr, log);
        glDeleteShader(shader);
        throw std::runtime_error(log);
    }
    return shader;
}

struct Sprite {
    using Vertices = std::array<float, floatsPerSprite>;
    std::unique_ptr<Vertices> vertices;
    const GLuint* texture;
    float x, y, rotation;
    float scaleX = 1, scaleY = 1;
    bool dirty = true;

    Sprite(const GLuint* texture, float x, float y, float rotation, float white)
        : vertices(std::make_unique<Vertices>(Vertices{0, 0, white, 0, 1, 0, 0, white, 0, 0,
                0, 0, white, 1, 0, 0, 0, white, 1, 1})), texture(texture), x(x), y(y), rotation(rotation) {}

    void rotate(float degrees) {
        if (degrees == 0) return;
        rotation += degrees;
        dirty = true;
    }

    void setScale(float scale) {
        scaleX = scaleY = scale;
        dirty = true;
    }

    const float* getVertices(const float* sine) {
        if (dirty) {
            dirty = false;
            sprite_vertices::writePositions(vertices->data(), x + 16.f, y + 16.f, rotation,
                    -16.f * scaleX, -16.f * scaleY, 16.f * scaleX, 16.f * scaleY, sine);
        }
        return vertices->data();
    }
};

float nextFloat(std::uint64_t& state) {
    // java.util.Random uses a 48-bit LCG and 24 bits for nextFloat().
    state = (state * 0x5deece66dULL + 11) & ((1ULL << 48) - 1);
    return static_cast<float>(state >> 24) / (1 << 24);
}

enum Phase { Fill, Staging, Upload, Submit, PhaseCount };
using Phases = std::array<std::int64_t, PhaseCount>;

class SpriteScene {
    GLuint program = 0, texture = 0, vao = 0, vertexBuffer = 0, indexBuffer = 0, uniformBuffer = 0;
    GLint sampler = -1;
    std::array<float, 16> projection{};
    std::array<float, sprite_vertices::sineCount> sine{};
    // Match Java's CpuSprite[] of objects, each owning a separate float array.
    std::vector<std::unique_ptr<Sprite>> sprites;
    std::vector<float> vertices = std::vector<float>(spriteCount * floatsPerSprite);
    std::vector<float> staging = std::vector<float>(spriteCount * floatsPerSprite);
    float scale = 1;
    float scaleSpeed = -1;
    const GLuint* lastTexture = nullptr;
    bool drawing = false;
    int cursor = 0, renderCalls = 0, uploadedBytes = 0;

public:
    ~SpriteScene() {
        glDeleteBuffers(1, &uniformBuffer);
        glDeleteBuffers(1, &indexBuffer);
        glDeleteBuffers(1, &vertexBuffer);
        glDeleteVertexArrays(1, &vao);
        glDeleteTextures(1, &texture);
        glDeleteProgram(program);
    }

    void create(int width, int height) {
        // GLSL equivalent of CpuSpriteBatch's WGSL, with the GL clip coordinates already normalized.
        const char* vertex = R"(#version 330 core
layout(location=0) in vec2 position;
layout(location=1) in vec4 color;
layout(location=2) in vec2 uv;
layout(std140) uniform Projection { mat4 matrix; };
out vec4 spriteColor;
out vec2 spriteUv;
void main() {
    gl_Position = matrix * vec4(position, 0.0, 1.0);
    spriteColor = vec4(color.rgb, color.a * (255.0 / 254.0));
    spriteUv = uv;
}
)";
        const char* fragment = R"(#version 330 core
uniform sampler2D spriteTexture;
in vec4 spriteColor;
in vec2 spriteUv;
layout(location=0) out vec4 color;
void main() { color = spriteColor * texture(spriteTexture, spriteUv); }
)";
        program = glCreateProgram();
        for (const auto& [stage, source] : {std::pair<GLenum, const char*>{GL_VERTEX_SHADER, vertex},
                {GL_FRAGMENT_SHADER, fragment}}) {
            const GLuint shader = compile(stage, source);
            glAttachShader(program, shader);
            glDeleteShader(shader);
        }
        glLinkProgram(program);
        GLint linked;
        glGetProgramiv(program, GL_LINK_STATUS, &linked);
        if (!linked) {
            char log[1024]{};
            glGetProgramInfoLog(program, sizeof(log), nullptr, log);
            throw std::runtime_error(log);
        }
        glUniformBlockBinding(program, glGetUniformBlockIndex(program, "Projection"), 0);
        sampler = glGetUniformLocation(program, "spriteTexture");
        projection[0] = 2.f / width;
        projection[5] = 2.f / height;
        projection[10] = projection[12] = projection[13] = -1;
        projection[15] = 1;

        int imageWidth, imageHeight, components;
        std::unique_ptr<stbi_uc, decltype(&stbi_image_free)> pixels(
                stbi_load(texturePath, &imageWidth, &imageHeight, &components, STBI_rgb_alpha), stbi_image_free);
        if (!pixels) throw std::runtime_error("Could not decode benchmark/assets/fdx.png");
        glGenTextures(1, &texture);
        glBindTexture(GL_TEXTURE_2D, texture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, imageWidth, imageHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels.get());

        glGenVertexArrays(1, &vao);
        glBindVertexArray(vao);
        glGenBuffers(1, &vertexBuffer);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferData(GL_ARRAY_BUFFER, vertexBytes, nullptr, GL_DYNAMIC_DRAW);
        std::vector<std::uint16_t> indices(indexCount);
        for (int i = 0; i < spriteCount; ++i) {
            const auto v = static_cast<std::uint16_t>(i * 4);
            auto* index = indices.data() + i * 6;
            index[0] = v; index[1] = v + 1; index[2] = v + 2;
            index[3] = v + 2; index[4] = v + 3; index[5] = v;
        }
        glGenBuffers(1, &indexBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexCount * sizeof(std::uint16_t), indices.data(), GL_STATIC_DRAW);
        glGenBuffers(1, &uniformBuffer);
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
        glBufferData(GL_UNIFORM_BUFFER, sizeof(projection), nullptr, GL_DYNAMIC_DRAW);

        constexpr float fullCircle = 3.14159265358979323846f * 2;
        for (int i = 0; i < sprite_vertices::sineCount; ++i) {
            const float angle = (i + 0.5f) / sprite_vertices::sineCount * fullCircle;
            sine[i] = static_cast<float>(std::sin(static_cast<double>(angle)));
        }
        sine[0] = sine[8192] = 0;
        sine[4096] = 1;
        sine[12288] = -1;

        std::uint64_t random = positionSeed ^ 0x5deece66dULL;
        const std::uint32_t whiteBits = 0xfeffffff;
        float white;
        std::memcpy(&white, &whiteBits, sizeof(white));
        sprites.reserve(spriteCount);
        for (int i = 0; i < spriteCount; ++i) {
            const float x = nextFloat(random) * std::max(0, width - 32);
            const float y = nextFloat(random) * std::max(0, height - 32);
            sprites.push_back(std::make_unique<Sprite>(&texture, x, y, 360.f * i / spriteCount, white));
        }
    }

    Phases draw(int width, int height, float delta, bool profile) {
        scale += scaleSpeed * delta * 0.5f;
        if (scale <= 0.5f) { scale = 0.5f; scaleSpeed = 1; }
        else if (scale >= 1) { scale = 1; scaleSpeed = -1; }
        begin(width, height);
        const auto fillStart = profile ? now() : 0;
        for (auto& sprite : sprites) {
            sprite->rotate(20.f * delta);
            sprite->setScale(scale);
            drawSprite(*sprite);
        }
        const auto fill = profile ? now() - fillStart : 0;
        auto phases = end(width, height, profile);
        phases[Fill] = fill;
        return phases;
    }

    void verifyBatch() const {
        if (renderCalls != 1 || uploadedBytes != vertexBytes) throw std::runtime_error("Unexpected batch workload");
    }

    void diagnose(int width, int height) {
        begin(width, height);
        for (auto& sprite : sprites) drawSprite(*sprite);
        constexpr int passes = 2000;
        const auto work = [&] {
            for (int pass = 0; pass < passes; ++pass) {
                cursor = 0;
                for (auto& sprite : sprites) {
                    sprite->rotate(20.f * (1.f / 60.f));
                    sprite->setScale(0.875f);
                    drawSprite(*sprite);
                }
            }
        };
        work();
        printState(-1);
        for (int sample = 0; sample < 5; ++sample) {
            const auto start = now();
            work();
            const auto elapsed = now() - start;
            std::cout << "CPU_PHASE mode=full sample=" << sample << " sprites=" << spriteCount
                      << " passes=" << passes << " nanos=" << elapsed << '\n';
            printState(sample);
        }
        cursor = 0;
        for (auto& sprite : sprites) drawSprite(*sprite);
        end(width, height, false);
        verifyBatch();
        std::cout << "CPU_PHASE_DONE mode=full draws=" << renderCalls << " bytes=" << uploadedBytes << '\n';
    }

private:
    void begin(int width, int height) {
        if (drawing) throw std::runtime_error("Batch already drawing");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        glClearColor(0.08f, 0.08f, 0.1f, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        drawing = true;
        renderCalls = uploadedBytes = 0;
    }

    void drawSprite(Sprite& sprite) {
        append(sprite.texture, sprite.getVertices(sine.data()), 0, floatsPerSprite);
    }

    void append(const GLuint* spriteTexture, const float* source, int offset, int count) {
        if (!drawing) throw std::runtime_error("Begin the batch before drawing");
        const int capacity = static_cast<int>(vertices.size());
        int remaining = capacity;
        if (spriteTexture != lastTexture) {
            flush(false);
            lastTexture = spriteTexture;
        } else {
            remaining -= cursor;
            if (remaining == 0) {
                flush(false);
                remaining = capacity;
            }
        }
        int copyCount = std::min(remaining, count);
        std::memcpy(vertices.data() + cursor, source + offset, copyCount * sizeof(float));
        cursor += copyCount;
        count -= copyCount;
        while (count > 0) {
            offset += copyCount;
            flush(false);
            copyCount = std::min(capacity, count);
            std::memcpy(vertices.data(), source + offset, copyCount * sizeof(float));
            cursor += copyCount;
            count -= copyCount;
        }
    }

    Phases flush(bool profile) {
        Phases phases{};
        if (cursor == 0) return phases;
        auto mark = profile ? now() : 0;
        auto phase = [&](Phase part) {
            if (profile) {
                const auto end = now();
                phases[part] = end - mark;
                mark = end;
            }
        };
        const int bytes = cursor * sizeof(float);
        std::memcpy(staging.data(), vertices.data(), bytes);
        phase(Staging);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glBufferSubData(GL_ARRAY_BUFFER, 0, bytes, staging.data());
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        phase(Upload);

        glUseProgram(program);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(GL_FALSE);
        glEnable(GL_BLEND);
        glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, *lastTexture);
        glUniform1i(sampler, 0);
        glBindBuffer(GL_ARRAY_BUFFER, vertexBuffer);
        glEnableVertexAttribArray(0);
        glEnableVertexAttribArray(1);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 20, nullptr);
        glVertexAttribPointer(1, 4, GL_UNSIGNED_BYTE, GL_TRUE, 20, reinterpret_cast<void*>(8));
        glVertexAttribPointer(2, 2, GL_FLOAT, GL_FALSE, 20, reinterpret_cast<void*>(12));
        for (GLuint i = 0; i < 3; ++i) glVertexAttribDivisor(i, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indexBuffer);
        glBindBufferBase(GL_UNIFORM_BUFFER, 0, uniformBuffer);
        glBindBuffer(GL_UNIFORM_BUFFER, uniformBuffer);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(projection), projection.data());
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        glDrawElementsBaseVertex(GL_TRIANGLES, cursor / floatsPerSprite * 6, GL_UNSIGNED_SHORT, nullptr, 0);
        phase(Submit);
        ++renderCalls;
        uploadedBytes += bytes;
        cursor = 0;
        return phases;
    }

    Phases end(int width, int height, bool profile) {
        if (!drawing) throw std::runtime_error("Batch is not drawing");
        const auto phases = flush(profile);
        lastTexture = nullptr;
        drawing = false;
        glDisable(GL_SCISSOR_TEST);
        for (GLuint i = 0; i < 3; ++i) glDisableVertexAttribArray(i);
        glUseProgram(0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        glViewport(0, 0, width, height);
        return phases;
    }

    static std::uint64_t hash(std::uint64_t value, const float* data, std::size_t count) {
        for (std::size_t i = 0; i < count; ++i) {
            std::uint32_t bits;
            std::memcpy(&bits, data + i, sizeof(bits));
            value = (value ^ bits) * 0x100000001b3ULL;
        }
        return value;
    }

    void printState(int sample) const {
        if (cursor != static_cast<int>(vertices.size()) || renderCalls != 0) {
            throw std::runtime_error("Unexpected flush or incomplete batch in CPU diagnostic");
        }
        constexpr std::uint64_t seed = 0xcbf29ce484222325ULL;
        std::uint64_t spriteHash = seed;
        for (const auto& sprite : sprites) spriteHash = hash(spriteHash, sprite->vertices->data(), floatsPerSprite);
        const auto batchHash = hash(seed, vertices.data(), vertices.size());
        if (spriteHash != batchHash) throw std::runtime_error("Copied vertices differ from sprite vertices");
        std::cout << "CPU_PHASE_STATE mode=full sample=" << sample
                  << " spriteHash=" << static_cast<std::int64_t>(spriteHash)
                  << " batchHash=" << static_cast<std::int64_t>(batchHash) << '\n';
    }
};

std::ofstream openOutput(const std::string& name) {
    const auto output = std::filesystem::u8path(name);
    if (output.has_parent_path()) std::filesystem::create_directories(output.parent_path());
    std::ofstream file(output, std::ios::binary);
    file.exceptions(std::ios::failbit | std::ios::badbit);
    return file;
}

void captureFrame(const std::string& path, int width, int height) {
    std::vector<char> pixels(width * height * 4);
    glPixelStorei(GL_PACK_ALIGNMENT, 1);
    glReadBuffer(GL_BACK);
    glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels.data());
    auto output = openOutput(path);
    output << "P6\n" << width << ' ' << height << "\n255\n";
    for (std::size_t i = 0; i < pixels.size(); i += 4) output.write(pixels.data() + i, 3);
}

void checkGl() {
    const GLenum error = glGetError();
    if (error != GL_NO_ERROR) throw std::runtime_error("OpenGL error " + std::to_string(error));
}

void run(const Options& options) {
    Window window;
    window.open();
    int width, height;
    glfwGetFramebufferSize(window.handle, &width, &height);
    if (width <= 0 || height <= 0) throw std::runtime_error("Empty sprite framebuffer");
    SpriteScene scene;
    scene.create(width, height);
    checkGl();
    if (!options.capture.empty()) {
        scene.draw(width, height, 1.f / 60.f, false);
        scene.verifyBatch();
        captureFrame(options.capture, width, height);
        checkGl();
        return;
    }
    if (options.cpuDiagnostic) {
        scene.diagnose(width, height);
        checkGl();
        return;
    }

    const auto warmup = static_cast<std::int64_t>(options.warmup * nanosPerSecond);
    const auto limit = static_cast<std::int64_t>(options.seconds * nanosPerSecond);
    const auto first = now();
    std::int64_t previous = 0, previousEnd = 0, intervals = 0, total = 0, cpu = 0;
    Phases previousPhases{}, phaseTotals{};
    while (!glfwWindowShouldClose(window.handle)) {
        glfwPollEvents();
        const auto start = now();
        if (previous && previous - first >= warmup) {
            ++intervals;
            total += start - previous;
            cpu += previousEnd - previous;
            for (int i = 0; i < PhaseCount; ++i) phaseTotals[i] += previousPhases[i];
        }
        const float delta = static_cast<float>(start - (previous ? previous : first)) / nanosPerSecond;
        previousPhases = scene.draw(width, height, delta, options.profile);
        previous = start;
        previousEnd = now();
        glfwSwapBuffers(window.handle);
        if (previousEnd - first >= limit) break;
    }
    checkGl();
    scene.verifyBatch();
    if (intervals == 0 || previousEnd - first < limit) throw std::runtime_error("Benchmark did not finish");
    const double fps = static_cast<double>(intervals) * nanosPerSecond / total;
    std::cout << "C++ sprite benchmark: " << fps << " FPS, " << spriteCount << " sprites, 1 draw\n";
    if (!options.result.empty()) {
        auto result = openOutput(options.result);
        result << std::setprecision(12)
               << "benchmark=sprite_batch_libfdx\nworkload=libfdx-cpu-expanded\nimplementation=cpp-jni\n"
               << "spriteStorage=separate-objects-and-arrays\n"
               << "graphicsProvider=gl\nrenderer=" << glGetString(GL_RENDERER) << '\n'
               << "sprites=" << spriteCount << "\nrenderCalls=1\nvertexBytesPerSprite=80\nuploadedVertexBytes=" << vertexBytes << '\n'
               << "framebufferWidth=" << width << "\nframebufferHeight=" << height << "\nrandomSeed=" << positionSeed << '\n'
               << "texture=" << texturePath << "\ntextureFormat=RGBA8_UNORM\nvisible=true\nvSync=false\nforegroundFps=0\n"
               << "completed=true\nwarmupSeconds=" << options.warmup << "\ntargetSeconds=" << options.seconds << '\n'
               << "measuredIntervals=" << intervals << "\nmeasuredFrameFps=" << fps
               << "\nmeasuredSeconds=" << static_cast<double>(total) / nanosPerSecond
               << "\nframeTimeMeanMillis=" << static_cast<double>(total) / intervals / 1e6
               << "\ncpuRenderMeanMillis=" << static_cast<double>(cpu) / intervals / 1e6 << '\n';
        if (options.profile) {
            result << "profileIntervals=" << intervals << "\nprofileFillTotalNanos=" << phaseTotals[Fill]
                   << "\nprofileStagingTotalNanos=" << phaseTotals[Staging] << "\nprofileUploadTotalNanos=" << phaseTotals[Upload]
                   << "\nprofileSubmitTotalNanos=" << phaseTotals[Submit] << "\nprofileOutsideTotalNanos=" << total - cpu << '\n';
        }
    }
}
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_libfdx_benchmark_desktop_NativeSpriteBenchmarkLauncher_run(JNIEnv* env, jclass, jobjectArray args) {
    try {
        run(parse(env, args));
    } catch (const std::exception& error) {
        if (!env->ExceptionCheck()) {
            const jclass exception = env->FindClass("java/lang/IllegalStateException");
            if (exception) env->ThrowNew(exception, error.what());
        }
    }
}
