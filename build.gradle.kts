import io.github.libfdx.build.LibExt

plugins {
    id("base")
}

LibExt.configure(rootProject.projectDir, gradle.startParameter.projectProperties)

allprojects {
    group = LibExt.fdxGroup
    version = LibExt.fdxVersion

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

extra["libfdxPublishTarget"] = LibfdxPublishTarget.LIBRARIES
apply(plugin = "publish")
