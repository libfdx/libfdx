package io.github.libfdx.gradle

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

/** jNative desktop builds. Backend and provider dependencies remain application-owned. */
open class LibfdxDesktopCppExtension @Inject constructor(
    project: Project,
    objects: ObjectFactory
) {
    /** Java source set supplying the application classes, dependencies, and native resources. */
    val sourceSet: Property<String> = objects.property(String::class.java).convention("main")
    val mainClass: Property<String> = objects.property(String::class.java)
    val targetFileName: Property<String> = objects.property(String::class.java).convention("app")
    val outputDir: DirectoryProperty = objects.directoryProperty()
        .convention(project.layout.buildDirectory.dir("dist/desktop-cpp"))
    /** Defaults to Java stack traces and source locations in Debug, disabled in Release. */
    val debugInformation: Property<Boolean> = objects.property(Boolean::class.java)
    /** jNative source layout; package directories keep native object filenames short. */
    val sourceLayout: Property<String> = objects.property(String::class.java).convention("PACKAGE_DIRECTORIES")
    val cmakeExecutable: Property<String> = objects.property(String::class.java).convention("cmake")
    /** Empty uses the desktop backend's host default, including CMAKE_GENERATOR. */
    val generator: Property<String> = objects.property(String::class.java)
        .convention(project.providers.gradleProperty("libfdx.desktopCPP.generator").orElse(""))
    val cmakeArguments: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val cmakeBuildArguments: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val buildTimeoutMinutes: Property<Int> = objects.property(Int::class.javaObjectType).convention(15)
    val runArgs: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
    val targets: NamedDomainObjectContainer<LibfdxDesktopCppTargetExtension> =
        objects.domainObjectContainer(LibfdxDesktopCppTargetExtension::class.java) { name ->
            objects.newInstance(LibfdxDesktopCppTargetExtension::class.java, name, objects).apply {
                runArgs.convention(this@LibfdxDesktopCppExtension.runArgs)
            }
        }

    fun target(name: String, action: Action<in LibfdxDesktopCppTargetExtension>) {
        targets.create(name, action)
    }
}

open class LibfdxDesktopCppTargetExtension @Inject constructor(
    private val targetName: String,
    objects: ObjectFactory
) : Named {
    val mainClass: Property<String> = objects.property(String::class.java)
    val targetFileName: Property<String> = objects.property(String::class.java).convention(targetName)
    val displayName: Property<String> = objects.property(String::class.java).convention(targetName)
    val runArgs: ListProperty<String> = objects.listProperty(String::class.java)

    override fun getName(): String = targetName
}
