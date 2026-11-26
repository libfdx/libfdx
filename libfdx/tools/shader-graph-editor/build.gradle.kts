plugins {
    application
    id("java-library")
    `maven-publish`
}

application {
    mainClass.set("io.github.libfdx.tools.shadergraph.ShaderGraphDesktopEditor")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

for ((provider, displayName) in mapOf("wgpu" to "WGPU", "d3d12" to "Direct3D 12", "gl" to "OpenGL", "vulkan" to "Vulkan")) {
    tasks.register<JavaExec>("libfdx_desktop_jvm_${provider}_run") {
        group = "application"
        description = "Opens the standalone shader graph editor on $displayName."
        mainClass.set(application.mainClass)
        classpath = sourceSets.main.get().runtimeClasspath
        jvmArgs(application.applicationDefaultJvmArgs)
        systemProperty("libfdx.sample.graphics", provider)
    }
}

tasks.startScripts {
    doLast {
        // Expanding every native and framework jar can exceed cmd.exe's 8191-character limit.
        val classpathLine = Regex("(?m)^set CLASSPATH=[^\\r\\n]*")
        windowsScript.writeText(windowsScript.readText().replace(classpathLine) { "set CLASSPATH=%APP_HOME%\\lib\\*" })
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName.set("tools_shader_graph_editor")
}

dependencies {
    api(project(":libfdx:extensions:graphics:shader-graph:core"))
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:files"))
    implementation(project(":libfdx:framework:graphics"))
    implementation(project(":libfdx:extensions:graphics:shader-graph:ui-kit"))
    implementation(project(":libfdx:framework:ui-kit"))
    implementation(project(":libfdx:backends:desktop"))
    implementation(project(":libfdx:extensions:graphics:d3d12:core"))
    implementation(project(":libfdx:extensions:graphics:wgpu:core"))
    runtimeOnly(project(":libfdx:extensions:graphics:gl:platform:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:vulkan:platform:desktop"))
    runtimeOnly(project(":libfdx:extensions:graphics:wgpu:platform:desktop_ffm"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("verify_node_picker") {
    group = "verification"
    description = "Opens a graph on OpenGL, searches for a node and captures the rendered picker."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.libfdx.tools.shadergraph.NodePickerCapture")
    workingDir = rootProject.file("samples/graphics/shader-graph")
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    args(layout.buildDirectory.file("reports/node-picker/search.ppm").get().asFile.absolutePath)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = base.archivesName.get()
            from(components["java"])
        }
    }
}
