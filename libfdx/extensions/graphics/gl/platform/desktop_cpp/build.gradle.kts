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
    archivesName.set("gl_desktop_cpp")
}

dependencies {
    api(project(":libfdx:extensions:graphics:gl:core"))
    runtimeOnly(project(":libfdx:backends:cpp_shared"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "gl_desktop_cpp"
            from(components["java"])
        }
    }
}
