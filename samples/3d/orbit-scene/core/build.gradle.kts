plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

base {
    archivesName.set("sample_orbitscene_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:g3d"))
}
