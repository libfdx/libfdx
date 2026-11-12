
plugins {
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}


base {
    archivesName.set("sample_multiplayer_2d_webrtc_core")
}

dependencies {
    api(project(":libfdx:framework:application"))
    implementation(project(":libfdx:framework:graphics"))
    implementation(project(":libfdx:framework:g2d"))
    implementation(project(":libfdx:framework:ui-kit"))
    implementation(project(":libfdx:framework:net"))
    implementation(project(":libfdx:extensions:net:webrtc:core"))
    implementation(project(":libfdx:extensions:scenario_validator:core"))
    implementation(project(":libfdx:extensions:scenario_validator:ui-kit"))
}
