import org.gradle.jvm.tasks.Jar

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
    archivesName.set("vulkan_desktop_cpp")
}

dependencies {
    api(project(":libfdx:extensions:graphics:vulkan:core"))
    runtimeOnly(project(":libfdx:backends:cpp_shared"))
}

val desktopVulkanSources = project(":libfdx:extensions:graphics:vulkan:platform:desktop_c")
    .layout.projectDirectory.dir("src/main/resources/libfdx-native/desktop/desktop_vulkan")

tasks.processResources {
    from(desktopVulkanSources) {
        into("libfdx-native/desktop/vulkan_cpp")
    }
}

tasks.named<Jar>("sourcesJar") {
    from(desktopVulkanSources) {
        into("libfdx-native/desktop/vulkan_cpp")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "vulkan_desktop_cpp"
            from(components["java"])
        }
    }
}
