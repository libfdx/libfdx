package io.github.libfdx.gradle

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EcsProjectBundleTest {
    @TempDir
    lateinit var temporaryDirectory: Path

    @Test
    fun writesDeterministicBundleWithMetadataAndHashes() {
        val input = fixture()
        val library = temporaryDirectory.resolve("helper.jar")
        writeJar(library, "com/example/helper/Helper.class")
        val first = temporaryDirectory.resolve("first.fdxproject").toFile()
        val second = temporaryDirectory.resolve("second.fdxproject").toFile()

        writeEcsProjectBundle(input.spec(first, setOf(library.toFile())))
        writeEcsProjectBundle(input.spec(second, setOf(library.toFile())))

        assertArrayEquals(Files.readAllBytes(first.toPath()), Files.readAllBytes(second.toPath()))
        ZipFile(first).use { zip ->
            val names = zip.entries().asSequence().map(ZipEntry::getName).toList()
            assertEquals(names.sorted(), names)
            assertTrue(names.contains("classes/com/example/GameProject.class"))
            assertTrue(names.contains("assets/sprites/player.png"))
            assertTrue(names.contains("scenes/main.fdxscene"))
            assertTrue(names.contains("lib/helper.jar"))
            val metadata = zip.text("META-INF/fdx-bundle.json")
            assertTrue(metadata.contains("\"formatVersion\": 2"))
            assertTrue(metadata.contains("\"projectAbi\": 6"))
            assertTrue(metadata.contains("\"libfdxAbi\": \"1.2.3\""))
            val hashes = zip.text("META-INF/fdx-hashes.sha256")
            assertTrue(hashes.contains("  fdx-project.json\n"))
            assertTrue(hashes.contains("  classes/com/example/GameProject.class\n"))
        }
    }

    @Test
    fun rejectsManifestConfigurationMismatch() {
        val input = fixture(projectId = "wrong.id")

        val error = assertThrows(GradleException::class.java) {
            writeEcsProjectBundle(input.spec(temporaryDirectory.resolve("bad.fdxproject").toFile()))
        }

        assertTrue(error.message!!.contains("field 'id'"))
    }

    @Test
    fun rejectsBlankProjectName() {
        val input = fixture(projectName = "   ", directoryName = "blank-name")

        val error = assertThrows(GradleException::class.java) {
            writeEcsProjectBundle(input.spec(temporaryDirectory.resolve("blank-name.fdxproject").toFile()))
        }

        assertTrue(error.message!!.contains("field 'name'"))
        assertTrue(error.message!!.contains("must not be blank"))
    }

    @Test
    fun rejectsProtectedProjectAndDependencyClasses() {
        val projectInput = fixture()
        val protectedProjectClass = projectInput.classes.resolve("io/github/libfdx/engine/Fake.class")
        Files.createDirectories(protectedProjectClass.parent)
        Files.write(protectedProjectClass, byteArrayOf(1))
        assertThrows(GradleException::class.java) {
            writeEcsProjectBundle(projectInput.spec(temporaryDirectory.resolve("project-bad.fdxproject").toFile()))
        }

        val dependencyInput = fixture(directoryName = "dependency")
        val library = temporaryDirectory.resolve("libfdx-copy.jar")
        writeJar(library, "io/github/libfdx/ecs/World.class")
        assertThrows(GradleException::class.java) {
            writeEcsProjectBundle(
                dependencyInput.spec(
                    temporaryDirectory.resolve("dependency-bad.fdxproject").toFile(),
                    setOf(library.toFile())
                )
            )
        }
    }

    private fun fixture(
        projectId: String = "com.example.game",
        projectName: String = "Example Game",
        directoryName: String = "project"
    ): Fixture {
        val root = temporaryDirectory.resolve(directoryName)
        val classes = root.resolve("classes")
        val assets = root.resolve("assets")
        val scenes = root.resolve("scenes")
        Files.createDirectories(classes.resolve("com/example"))
        Files.createDirectories(assets.resolve("sprites"))
        Files.createDirectories(scenes)
        Files.write(classes.resolve("com/example/GameProject.class"), byteArrayOf(1, 2, 3))
        Files.write(assets.resolve("sprites/player.png"), byteArrayOf(4, 5, 6))
        Files.writeString(scenes.resolve("main.fdxscene"), "{}\n", StandardCharsets.UTF_8)
        val manifest = root.resolve("fdx-project.json")
        Files.writeString(
            manifest,
            """
            {
              "format": "libfdx.ecs.project",
              "formatVersion": 2,
              "id": "$projectId",
              "name": "$projectName",
              "entryClass": "com.example.GameProject",
              "defaultScene": "scenes/main.fdxscene",
              "assetsDirectory": "assets",
              "gradleRoot": ".",
              "gradleProject": ":core",
              "desktopBundleTask": "libfdx_ecs_project_bundle"
            }
            """.trimIndent() + "\n",
            StandardCharsets.UTF_8
        )
        return Fixture(root, classes, assets, scenes, manifest)
    }

    private fun writeJar(path: Path, entryName: String) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
    }

    private fun ZipFile.text(path: String): String {
        return getInputStream(getEntry(path)).use { input ->
            String(input.readBytes(), StandardCharsets.UTF_8)
        }
    }

    private data class Fixture(
        val root: Path,
        val classes: Path,
        val assets: Path,
        val scenes: Path,
        val manifest: Path
    ) {
        fun spec(output: File, dependencies: Set<File> = emptySet()): EcsProjectBundleSpec {
            return EcsProjectBundleSpec(
                "com.example.game",
                "com.example.GameProject",
                manifest.toFile(),
                assets.toFile(),
                scenes.toFile(),
                setOf(classes.toFile()),
                dependencies,
                6,
                "1.2.3",
                ".",
                ":core",
                ECS_PROJECT_BUNDLE_TASK,
                output
            )
        }
    }
}
