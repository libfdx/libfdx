import org.gradle.api.GradleException
import org.gradle.api.tasks.JavaExec

plugins {
    id("maven-publish")
    id("java-library")
}

java {
    sourceCompatibility = JavaVersion.toVersion(25)
    targetCompatibility = JavaVersion.toVersion(25)
}

val moduleName = "collections"

base {
    archivesName.set(moduleName)
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

val collectionBenchmarkNames = listOf(
    "Array",
    "IntArray",
    "LongArray",
    "FloatArray",
    "ObjectMap",
    "OrderedMap",
    "OrderedIntNodeMap",
    "OrderedIntSparseNodeMap",
    "IntMap",
    "LongMap",
    "FloatMap",
    "ObjectSet",
    "IntSet",
    "ObjectQueue",
    "ObjectLinkedList",
)
val collectionBenchmarkReportDirectory = layout.buildDirectory.dir("reports/jmh")
val collectionBenchmarkRuntimeClasspath = sourceSets["test"].runtimeClasspath
val collectionBenchmarkRunner = "io.github.libfdx.collections.CollectionsBenchmarkRunner"

fun JavaExec.configureCollectionBenchmark(
    collections: List<String>,
    reportStem: String,
) {
    group = "verification"
    dependsOn(project.tasks.named("testClasses"))
    classpath = collectionBenchmarkRuntimeClasspath
    mainClass.set(collectionBenchmarkRunner)
    setArgs(
        listOf(
            collectionBenchmarkReportDirectory.get().asFile.absolutePath,
            reportStem,
        ) + collections,
    )
}

tasks.register<JavaExec>("benchmarkCollections") {
    description = "Runs all collection JMH benchmarks and writes JSON and Markdown reports."
    configureCollectionBenchmark(collectionBenchmarkNames, "collections")
}

collectionBenchmarkNames.forEach { collectionName ->
    val reportStem = collectionName
        .replace(Regex("(?<!^)(?=[A-Z])"), "-")
        .lowercase()
    tasks.register<JavaExec>("benchmark$collectionName") {
        description = if (collectionName == "OrderedIntNodeMap") {
            "Compares Array, IntMap, OrderedMap, OrderedIntNodeMap, and OrderedIntSparseNodeMap and writes JSON and Markdown reports."
        } else {
            "Benchmarks $collectionName and writes $reportStem JSON and Markdown reports."
        }
        configureCollectionBenchmark(listOf(collectionName), reportStem)
    }
}

tasks.register<JavaExec>("benchmarkSelectedCollections") {
    group = "verification"
    description = "Benchmarks -Pcollections=Array,ObjectMap and writes selected reports."
    dependsOn(tasks.named("testClasses"))
    classpath = collectionBenchmarkRuntimeClasspath
    mainClass.set(collectionBenchmarkRunner)
    doFirst {
        val selection = providers.gradleProperty("collections").orNull
            ?: throw GradleException(
                "Missing -Pcollections. Example: -Pcollections=Array,ObjectMap",
            )
        setArgs(
            listOf(
                collectionBenchmarkReportDirectory.get().asFile.absolutePath,
                "selected",
                selection,
            ),
        )
    }
}

tasks.register<JavaExec>("benchmarkCollectionsQuick") {
    group = "verification"
    description = "Runs one short JMH iteration for collection benchmark validation."
    dependsOn(tasks.named("testClasses"))
    classpath = collectionBenchmarkRuntimeClasspath
    mainClass.set("io.github.libfdx.collections.CollectionsBenchmark")
    args(
        "io.github.libfdx.collections.CollectionsBenchmark",
        "-wi", "1",
        "-i", "1",
        "-f", "1",
        "-w", "100ms",
        "-r", "100ms",
        "-v", "SILENT",
    )
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = moduleName
            from(components["java"])
        }
    }
}
