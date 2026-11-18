val release = libs.versions.libfdxRelease.get()
val ref = providers.environmentVariable("LIBFDX_REF").orNull?.trim()?.removePrefix("refs/tags/")
val releaseGenerator = providers.gradleProperty("libfdx.projectGenerator.release").orNull.toBoolean()
    || ref == release || ref == "v$release"

extra["generatorLibfdxVersion"] = if (releaseGenerator) release else libs.versions.libfdxSnapshot.get()
extra["generatorChannel"] = if (releaseGenerator) "release" else "snapshot"
