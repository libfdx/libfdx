plugins {
    `java-library`
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
    withSourcesJar()
    withJavadocJar()
}

base {
    archivesName.set("backend_desktop_cpp")
}

val nativeClasses = sourceSets.create("native")

configurations[nativeClasses.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    api(project(":libfdx:backends:cpp_shared"))
    api(project(":libfdx:framework:application"))
    api(project(":libfdx:framework:audio"))
    api(project(":libfdx:framework:assets:manager"))
    api(project(":libfdx:extensions:graphics:gl:core"))
    api(project(":libfdx:extensions:graphics:vulkan:core"))
    api(project(":libfdx:framework:fdx:core"))
    runtimeOnly(project(":libfdx:framework:fdx:platform:desktop"))
    runtimeOnly("org.lwjgl:lwjgl-openal:${libs.versions.lwjgl.get()}:natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-openal:${libs.versions.lwjgl.get()}:natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-openal:${libs.versions.lwjgl.get()}:natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-openal:${libs.versions.lwjgl.get()}:natives-macos-arm64")
    implementation(project(":libfdx:framework:assets:loaders"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    add(nativeClasses.implementationConfigurationName,
        files(tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory }))
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    from(nativeClasses.output.classesDirs) {
        into("libfdx-native-classes")
    }
    from(project(":libfdx:backends:c_shared").file("src/main/resources/libfdx-native/desktop/desktop_c")) {
        include("libfdx_native_image.h", "libfdx_native_image.cpp")
        into("libfdx-native/desktop/desktop_cpp")
    }
    from(project(":libfdx:backends:desktop_c").file("src/main/resources/libfdx-native/desktop/desktop_c")) {
        include("libfdx_desktop_shaderc.h", "libfdx_desktop_shaderc.cpp")
        into("libfdx-native/desktop/desktop_cpp")
    }
    from(rootProject.file("libfdx/framework/fdx/platform/shared/src/main/cpp/runtime_fdx")) {
        include("libfdx_freetype.h", "libfdx_freetype.cpp")
        into("libfdx-native/desktop/desktop_cpp")
    }
}

tasks.named<Jar>("sourcesJar") {
    from("src/native/java") {
        into("native")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "backend_desktop_cpp"
            from(components["java"])
        }
    }
}
