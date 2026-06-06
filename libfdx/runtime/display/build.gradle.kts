plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("display")
}

dependencies {
    api(project(":libfdx:runtime:fdx:core"))
}
