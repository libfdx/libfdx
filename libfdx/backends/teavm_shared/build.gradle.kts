plugins {
    id("java-library")
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("http://teavm.org/maven/repository/")
        isAllowInsecureProtocol = true
    }
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("backend_teavm_shared")
}

dependencies {
    api(libs.teavm.tooling)
    api(libs.teavm.classlib)
}
