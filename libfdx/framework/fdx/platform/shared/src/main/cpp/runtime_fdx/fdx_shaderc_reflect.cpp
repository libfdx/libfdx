#include "fdx_shaderc.h"

#include <cstdint>
#include <fstream>
#include <iostream>
#include <limits>
#include <string>
#include <vector>

namespace {

int Run(const char* input_path, const char* output_path) {
    std::ifstream input(input_path, std::ios::binary | std::ios::ate);
    if (!input) {
        std::cerr << "fdx_shaderc_reflect: cannot open input WGSL: " << input_path << '\n';
        return 2;
    }
    std::streamsize input_size = input.tellg();
    if (input_size <= 0 || input_size > std::numeric_limits<int32_t>::max()) {
        std::cerr << "fdx_shaderc_reflect: input WGSL is empty or exceeds the native size limit\n";
        return 2;
    }
    input.seekg(0, std::ios::beg);
    std::vector<char> source(static_cast<size_t>(input_size));
    if (!input.read(source.data(), input_size)) {
        std::cerr << "fdx_shaderc_reflect: failed to read input WGSL\n";
        return 2;
    }

    void* result = fdx_shaderc_compile_wgsl_handle(
        source.data(), static_cast<int32_t>(source.size()), FDX_SHADERC_TARGET_WGPU_WGSL,
        FDX_SHADERC_STAGE_MODULE, "", "330", "300");
    if (result == nullptr) {
        std::cerr << "fdx_shaderc_reflect: native compiler allocation failed\n";
        return 3;
    }

    int exit_code = 0;
    if (fdx_shaderc_result_status(result) != 0) {
        std::cerr << "fdx_shaderc_reflect: " << fdx_shaderc_result_diagnostics(result) << '\n';
        exit_code = 3;
    } else {
        const uint8_t* reflection = fdx_shaderc_result_reflection(result);
        int32_t reflection_size = fdx_shaderc_result_reflection_size(result);
        if (reflection == nullptr || reflection_size < 8) {
            std::cerr << "fdx_shaderc_reflect: compiler returned no FDXI reflection\n";
            exit_code = 3;
        } else {
            std::ofstream output(output_path, std::ios::binary | std::ios::trunc);
            if (!output ||
                !output.write(reinterpret_cast<const char*>(reflection), reflection_size)) {
                std::cerr << "fdx_shaderc_reflect: failed to write output FDXI: " << output_path
                          << '\n';
                exit_code = 4;
            }
        }
    }
    fdx_shaderc_result_free(result);
    return exit_code;
}

}  // namespace

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "Usage: fdx_shaderc_reflect <input.wgsl> <output.fdxi>\n";
        return 1;
    }
    return Run(argv[1], argv[2]);
}
