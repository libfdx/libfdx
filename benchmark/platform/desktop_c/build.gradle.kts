import io.github.libfdx.build.LibExt

plugins {
    id("java")
}

group = "${LibExt.fdxGroup}.benchmark"

base {
    archivesName.set("benchmark_desktop_c")
}

dependencies {
    implementation(project(":benchmark:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_desktop_c:${LibExt.publishedLibfdxVersion}")
        runtimeOnly("${LibExt.fdxGroup}:gl_desktop_c:${LibExt.publishedLibfdxVersion}")
        runtimeOnly("${LibExt.fdxGroup}:vulkan_desktop_c:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop_c"))
        runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop_c"))
    }
}

fun benchmarkPluginTask(name: String): String {
    return ":benchmark:platform:plugin:$name"
}

fun registerBenchmarkAlias(name: String, descriptionText: String) {
    tasks.register(name) {
        group = "benchmark"
        description = descriptionText
        dependsOn(benchmarkPluginTask(name))
    }
}

tasks.register("benchmark_desktop_c_generate_debug") {
    group = "benchmark"
    description = "Generates the desktop_c benchmark Debug project."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_generate_debug"))
}

tasks.register("benchmark_desktop_c_generate_release") {
    group = "benchmark"
    description = "Generates the desktop_c benchmark Release project."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_generate_release"))
}

tasks.register("benchmark_desktop_c_build_debug") {
    group = "benchmark"
    description = "Builds the desktop_c benchmark Debug executable."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_build_debug"))
}

tasks.register("benchmark_desktop_c_build_release") {
    group = "benchmark"
    description = "Builds the desktop_c benchmark Release executable."
    dependsOn(benchmarkPluginTask("libfdx_desktop_c_build_release"))
}

registerBenchmarkAlias(
    "benchmark_desktop_c_gl_debug",
    "Runs the desktop_c OpenGL benchmark suite in Debug and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_gl_release",
    "Runs the desktop_c OpenGL benchmark suite in Release and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_vulkan_debug",
    "Runs the desktop_c Vulkan benchmark suite in Debug and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_vulkan_release",
    "Runs the desktop_c Vulkan benchmark suite in Release and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_debug",
    "Runs the full desktop_c Debug benchmark suite and generates Markdown reports."
)
registerBenchmarkAlias(
    "benchmark_desktop_c_release",
    "Runs the full desktop_c Release benchmark suite and generates Markdown reports."
)
