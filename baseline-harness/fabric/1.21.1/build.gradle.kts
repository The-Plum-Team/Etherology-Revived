import groovy.json.JsonSlurper
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
    id("dev.architectury.loom") version "1.17.480"
}

group = "dev.theplumteam.etherology.baseline"
version = providers.gradleProperty("harness_version").get()

base {
    archivesName.set("Etherology-Original-E2E-Harness-Fabric-1.21.1")
}

repositories {
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
    }
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.ow2.asm:asm:9.9")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

val expandedMetadata = mapOf(
    "version" to project.version.toString(),
    "minecraft_version" to property("minecraft_version").toString(),
    "java_version" to property("java_version").toString(),
    "fabric_loader_version" to property("fabric_loader_version").toString(),
    "fabric_api_version" to property("fabric_api_version").toString(),
    "etherology_version" to property("etherology_version").toString(),
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(expandedMetadata)
    filesMatching("fabric.mod.json") {
        expand(expandedMetadata)
    }
}

val javaVersion = property("java_version").toString().toInt()
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val namedHarnessJar = tasks.named<Jar>("jar") {
    archiveClassifier.set("dev")
}

val remappedHarnessJar = tasks.register<RemapJarTask>("remapHarnessJar") {
    group = "verification"
    description = "Remaps the separately packaged original Fabric 1.21.1 harness."
    dependsOn(namedHarnessJar)
    inputFile.set(namedHarnessJar.flatMap { it.archiveFile })
    classpath.from(sourceSets.main.get().compileClasspath)
    addNestedDependencies.set(false)
    useMixinAP.set(false)
    archiveBaseName.set("Etherology-Original-E2E-Harness-Fabric-1.21.1")
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
}

val validateHarnessArtifact = tasks.register("validateHarnessArtifact") {
    group = "verification"
    description = "Validates the isolated original Fabric 1.21.1 harness JAR."
    dependsOn(remappedHarnessJar)
    inputs.file(remappedHarnessJar.flatMap { it.archiveFile })

    doLast {
        val harnessFile = remappedHarnessJar.get().archiveFile.get().asFile
        ZipFile(harnessFile).use { harnessZip ->
            val entries = harnessZip.entries().asSequence().map { it.name }.toSet()
            val classEntries = entries.filter { it.endsWith(".class") }
            check(
                "dev/theplumteam/etherology/baseline/fabric/OriginalPhaseZeroHarness.class" in
                    entries,
            ) {
                "Harness JAR has no client entrypoint class"
            }
            check(
                "dev/theplumteam/etherology/baseline/fabric/" +
                    "AttrahiteBlockRegistryScenario.class" in entries,
            ) {
                "Harness JAR has no Attrahite block-registry scenario"
            }
            check(
                "dev/theplumteam/etherology/baseline/fabric/mixin/GameRendererMixin.class" in
                    entries,
            ) {
                "Harness JAR has no completed-render mixin"
            }
            check(
                "dev/theplumteam/etherology/baseline/fabric/mixin/PlayerEntityJumpInvoker.class" in
                    entries,
            ) {
                "Harness JAR has no vanilla jump invoker"
            }
            check(classEntries.all {
                it.startsWith("dev/theplumteam/etherology/baseline/fabric/")
            }) {
                "Harness JAR contains classes outside its isolated package"
            }
            check(entries.none { it.endsWith(".jar") }) {
                "Harness JAR contains nested dependencies"
            }
            classEntries.forEach { classEntryName ->
                val classEntry = requireNotNull(harnessZip.getEntry(classEntryName))
                val classBytes = harnessZip.getInputStream(classEntry).use { input ->
                    input.readAllBytes()
                }
                check(classBytes.size >= 8) {
                    "Harness class $classEntryName has a truncated classfile header"
                }
                val classMajorVersion = ((classBytes[6].toInt() and 0xff) shl 8) or
                    (classBytes[7].toInt() and 0xff)
                check(classMajorVersion == javaVersion + 44) {
                    "Harness class $classEntryName is not exact Java $javaVersion bytecode"
                }
                val constants = String(classBytes, StandardCharsets.ISO_8859_1)
                check(!constants.contains("ru/feytox/etherology/")) {
                    "Harness class $classEntryName links to Etherology implementation code"
                }
            }

            val metadataEntry = requireNotNull(harnessZip.getEntry("fabric.mod.json")) {
                "Harness JAR has no fabric.mod.json"
            }
            val metadataText = harnessZip.getInputStream(metadataEntry)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            val metadata = JsonSlurper().parseText(metadataText) as Map<*, *>
            check(metadata["id"] == "etherology_original_baseline_harness") {
                "Harness metadata has the wrong mod id"
            }
            check(metadata["version"] == project.version.toString()) {
                "Harness metadata has the wrong version"
            }
            check(metadata["environment"] == "client") {
                "Harness is not client-only"
            }
            val entrypoints = metadata["entrypoints"] as? Map<*, *>
                ?: error("Harness metadata has no entrypoints")
            check(
                entrypoints == mapOf(
                    "client" to listOf(
                        "dev.theplumteam.etherology.baseline.fabric.OriginalPhaseZeroHarness",
                    ),
                ),
            ) {
                "Harness metadata has the wrong client entrypoint"
            }
            check(
                metadata["mixins"] == listOf(
                    mapOf(
                        "config" to "etherology-original-baseline-harness.mixins.json",
                        "environment" to "client",
                    ),
                ),
            ) {
                "Harness metadata does not declare its exact client-only mixin config"
            }
            val dependencies = metadata["depends"] as? Map<*, *>
                ?: error("Harness metadata has no dependency table")
            check(dependencies["etherology"] == "=${project.property("etherology_version")}") {
                "Harness does not require the exact published Etherology version"
            }
            check(dependencies["minecraft"] == "=${project.property("minecraft_version")}") {
                "Harness does not require the exact Minecraft version"
            }
            check(dependencies["fabricloader"] == "=${project.property("fabric_loader_version")}") {
                "Harness does not require the exact Fabric Loader version"
            }
            check(dependencies["fabric-api"] == "=${project.property("fabric_api_version")}") {
                "Harness does not require the exact Fabric API version"
            }
            check(dependencies["java"] == ">=${project.property("java_version")}") {
                "Harness does not declare its exact Java compatibility floor"
            }
            check(
                dependencies.keys == setOf(
                    "fabricloader",
                    "fabric-api",
                    "minecraft",
                    "java",
                    "etherology",
                ),
            ) {
                "Harness dependency inventory is not exact"
            }

            val mixinEntry = requireNotNull(
                harnessZip.getEntry("etherology-original-baseline-harness.mixins.json"),
            ) {
                "Harness JAR has no mixin config"
            }
            val mixinText = harnessZip.getInputStream(mixinEntry)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            val mixin = JsonSlurper().parseText(mixinText) as Map<*, *>
            check(
                mixin == mapOf(
                    "required" to true,
                    "package" to
                        "dev.theplumteam.etherology.baseline.fabric.mixin",
                    "compatibilityLevel" to "JAVA_21",
                    "client" to listOf(
                        "GameRendererMixin",
                        "PlayerEntityJumpInvoker",
                    ),
                    "injectors" to mapOf("defaultRequire" to 1),
                ),
            ) {
                "Harness mixin config does not match the exact client-only contract"
            }
        }
    }
}

tasks.register("buildHarness") {
    group = "verification"
    description = "Builds, tests, remaps, and validates the original 1.21.1 harness."
    dependsOn(tasks.test, validateHarnessArtifact)
}
