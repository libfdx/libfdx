plugins { id("java-library") }

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

dependencies {
    implementation(project(":tests:core"))
    compileOnly(libs.playwright)
}
