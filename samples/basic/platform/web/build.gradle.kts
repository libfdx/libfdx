import io.github.libfdx.build.LibExt

plugins {
    id("java")
    id("io.github.libfdx")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

group = "${LibExt.fdxGroup}.samples.basic"


base {
    archivesName.set("sample_basic_web")
}

dependencies {
    implementation(project(":samples:basic:core"))
    if (LibExt.usePublishedLibfdx) {
        implementation("${LibExt.fdxGroup}:backend_web:${LibExt.publishedLibfdxVersion}")
        implementation("${LibExt.fdxGroup}:gl_web:${LibExt.publishedLibfdxVersion}")
    } else {
        implementation(project(":libfdx:backends:web"))
        implementation(project(":libfdx:extensions:graphics:gl:platform:web"))
    }
}

libfdx {
    js {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebJsLauncher")
        htmlTitle.set("libfdx Basic - WebGL JS")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
    wasm {
        mainClass.set("io.github.libfdx.samples.basic.web.BasicWebWasmLauncher")
        htmlTitle.set("libfdx Basic - WebGL Wasm")
        canvasId.set("libfdx-canvas")
        htmlWidth.set(0)
        htmlHeight.set(0)
    }
}
