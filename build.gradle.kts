plugins {
    id("base")
}

allprojects {
    group = "io.github.libfdx"
    version = "1.0-SNAPSHOT"

    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        }
        maven {
            url = uri("http://teavm.org/maven/repository/")
            isAllowInsecureProtocol = true
        }
    }
}
