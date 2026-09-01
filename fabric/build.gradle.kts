import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import dev.architectury.plugin.ArchitectPluginExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.api.fabricapi.FabricApiExtension
import net.fabricmc.loom.task.RemapJarTask
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.tasks.Jar
import groovy.json.JsonSlurper
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipFile

plugins {
    java
}

apply(from = rootProject.file("gradle/archive-conventions.gradle.kts"))

val minecraftVersion = stonecutter.current.version
val generatedStonecutterJava = layout.buildDirectory.dir("generated/stonecutter/main/java")
val consolidatedJava = layout.buildDirectory.dir("generated/consolidated/main/java")
val datagenOutputPath = providers.gradleProperty("etherologyDatagenOutput")
    .getOrElse("build/datagen/$minecraftVersion")
val datagenOutputDirectory = rootProject.file(datagenOutputPath)
val commonProjectPath = requireNotNull(stonecutter.node.sibling("common")).hierarchy.toString()
val commonProject = project(commonProjectPath)
evaluationDependsOn(commonProjectPath)

apply(plugin = "dev.architectury.loom")
apply(plugin = "architectury-plugin")
apply(plugin = "com.gradleup.shadow")

fun Project.versionProperty(base: String): String =
    rootProject.property("${base}_${minecraftVersion.replace(".", "_")}") as String

group = rootProject.property("maven_group") as String
version = rootProject.property("mod_version") as String

extensions.configure<BasePluginExtension>("base") {
    archivesName.set("Etherology - Fabric - $minecraftVersion")
}

extensions.configure<ArchitectPluginExtension>("architectury") {
    minecraft = minecraftVersion
    platformSetupLoomIde()
    fabric()
}

repositories {
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
        content {
            includeGroupByRegex("net\\.fabricmc(\\..*)?")
        }
    }
    maven("https://maven.architectury.dev/") {
        name = "Architectury"
        content {
            includeGroupByRegex("dev\\.architectury(\\..*)?")
        }
    }
    maven("https://maven.wispforest.io/") {
        name = "WispForest"
        content {
            includeGroup("io.wispforest")
        }
    }
    maven("https://maven.ladysnake.org/releases") {
        name = "Ladysnake"
        content {
            includeGroup("dev.onyxstudios.cardinal-components-api")
        }
    }
    maven("https://repo.sleeping.town/") {
        name = "SleepingTown"
        content {
            includeGroup("dev.emi")
        }
    }
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") {
        name = "GeckoLib"
        content {
            includeGroup("software.bernie.geckolib")
            includeGroup("com.eliotlash.mclib")
        }
    }
    maven("https://jitpack.io") {
        name = "JitPack"
        content {
            includeGroup("com.github.Chocohead")
            includeGroup("com.github.CrimsonDawn45")
        }
    }
    maven("https://maven.terraformersmc.com/releases/") {
        name = "TerraformersMC"
        content {
            includeGroup("com.terraformersmc")
        }
    }
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://maven.bawnorton.com/releases/") {
        name = "Bawnorton"
        content {
            includeGroup("com.github.bawnorton.mixinsquared")
        }
    }
    maven("https://maven.shedaniel.me/") {
        name = "Shedaniel"
        content {
            includeGroupByRegex("me\\.shedaniel(\\..*)?")
        }
    }
    mavenCentral()
}

val prepareConsolidatedJava = tasks.register<Sync>("prepareConsolidatedJava") {
    dependsOn("stonecutterGenerate")
    from(generatedStonecutterJava)
    into(consolidatedJava)
}

// Keep these canonical roots as production inputs until the port partitions them between common
// and Fabric platform source roots.
sourceSets {
    main {
        java.setSrcDirs(
            listOf(
                consolidatedJava,
                rootProject.file("src/main/java"),
            ),
        )
        resources.setSrcDirs(
            listOf(
                rootProject.file("src/main/resources"),
                rootProject.file("src/main/generated"),
                rootProject.file("fabric/src/main/resources"),
            ),
        )
    }
}

extensions.configure<LoomGradleExtensionAPI>("loom") {
    accessWidenerPath.set(rootProject.file("src/main/resources/etherology.accesswidener"))
    splitEnvironmentSourceSets()
    mods {
        create("etherology") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets["client"])
        }
    }
}

extensions.configure<FabricApiExtension>("fabricApi") {
    configureDataGeneration {
        addToResources.set(false)
        client.set(true)
        modId.set("etherology")
        outputDirectory.set(datagenOutputDirectory)
        strictValidation.set(true)
    }
}

val canonicalClientResourceRoot = rootProject.file("src/client/resources")
val canonicalClientResourcePaths = fileTree(canonicalClientResourceRoot)
    .files
    .filter { it.isFile }
    .map { it.relativeTo(canonicalClientResourceRoot).invariantSeparatorsPath }

tasks.register<Sync>("promoteDatagen") {
    group = "fabric"
    description = "Regenerates and promotes version-correct data while preserving hand-authored resources."
    dependsOn("runDatagen")
    from(datagenOutputDirectory) {
        exclude(".cache/**")
        canonicalClientResourcePaths.forEach { exclude(it) }
    }
    into(rootProject.file("src/main/generated"))
}

sourceSets.named("client") {
    java.setSrcDirs(
        listOf(
            rootProject.file("src/client/java"),
            rootProject.file("fabric/src/client/java"),
        ),
    )
    resources.setSrcDirs(
        listOf(
            rootProject.file("src/client/resources"),
            rootProject.file("fabric/src/client/resources"),
        ),
    )
}

tasks.named("compileJava") {
    dependsOn(prepareConsolidatedJava)
}
tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(prepareConsolidatedJava)
}

configurations {
    create("common")
    create("shadowBundle")
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
    findByName("clientCompileOnly")?.extendsFrom(compileOnly.get())
    findByName("clientAnnotationProcessor")?.extendsFrom(annotationProcessor.get())
    findByName("clientCompileClasspath")?.extendsFrom(configurations["common"])
    findByName("clientRuntimeClasspath")?.extendsFrom(configurations["common"])
    findByName("developmentFabric")?.extendsFrom(configurations["common"])
}

dependencies {
    "minecraft"("net.minecraft:minecraft:${versionProperty("minecraft_version")}")
    "mappings"(
        "net.fabricmc:yarn:${versionProperty("yarn_mappings")}:v2",
    )
    "modImplementation"(
        "net.fabricmc:fabric-loader:${versionProperty("fabric_loader_version")}",
    )
    "modImplementation"(
        "net.fabricmc.fabric-api:fabric-api:${versionProperty("fabric_api_version")}",
    )
    "modImplementation"(
        "dev.architectury:architectury-fabric:${versionProperty("architectury_api_version")}",
    )

    val lombok = "org.projectlombok:lombok:${versionProperty("lombok_version")}"
    "compileOnly"(lombok)
    "annotationProcessor"(lombok)

    val owoLibVersion = versionProperty("owo_lib_version")
    "modImplementation"("io.wispforest:owo-lib:$owoLibVersion")
    "include"("io.wispforest:owo-sentinel:$owoLibVersion")

    val cardinalComponentsVersion = versionProperty("cardinal_components_version")
    "modImplementation"(
        "dev.onyxstudios.cardinal-components-api:cardinal-components-base:$cardinalComponentsVersion",
    )
    "modImplementation"(
        "dev.onyxstudios.cardinal-components-api:cardinal-components-entity:$cardinalComponentsVersion",
    )
    "modImplementation"(
        "dev.onyxstudios.cardinal-components-api:cardinal-components-chunk:$cardinalComponentsVersion",
    )

    "modImplementation"("dev.emi:trinkets:${versionProperty("trinkets_version")}")
    "modImplementation"(
        "software.bernie.geckolib:geckolib-fabric-$minecraftVersion:${versionProperty("geckolib_version")}",
    )
    "modImplementation"(
        "com.github.CrimsonDawn45:Fabric-Shield-Lib:v${versionProperty("fabric_shield_lib_version")}",
    )
    "modLocalRuntime"(
        "maven.modrinth:midnightlib:${versionProperty("midnightlib_version")}",
    )
    "modImplementation"(
        "com.terraformersmc:biolith-fabric:${versionProperty("biolith_version")}",
    )

    val mixinSquared =
        "com.github.bawnorton.mixinsquared:mixinsquared-fabric:${versionProperty("mixin_squared_version")}"
    "modImplementation"(mixinSquared)
    "annotationProcessor"(mixinSquared)
    "include"(mixinSquared)

    val mixinExtras =
        "io.github.llamalad7:mixinextras-common:${versionProperty("mixin_extras_version")}"
    "compileOnly"(mixinExtras)
    "annotationProcessor"(mixinExtras)
    val fabricAsm = "com.github.Chocohead:Fabric-ASM:${versionProperty("fabric_asm_version")}"
    "modImplementation"(fabricAsm) {
        exclude(group = "net.fabricmc.fabric-api")
    }
    "include"(fabricAsm)

    "modCompileOnly"(
        "me.shedaniel.cloth:cloth-config-fabric:${versionProperty("cloth_config_version")}",
    )
    "modCompileOnly"(
        "me.shedaniel:RoughlyEnoughItems-api-fabric:${versionProperty("rei_version")}",
    )
    "modCompileOnly"(
        "me.shedaniel:RoughlyEnoughItems-default-plugin-fabric:${versionProperty("rei_version")}",
    )
    "modCompileOnly"(
        "dev.emi:emi-fabric:${versionProperty("emi_version")}:api",
    )
    "modLocalRuntime"(
        "com.terraformersmc:modmenu:${versionProperty("mod_menu_version")}",
    ) {
        exclude(group = "net.fabricmc.fabric-api")
    }
    "modLocalRuntime"(
        "me.shedaniel.cloth:cloth-config-fabric:${versionProperty("cloth_config_version")}",
    )
    "modLocalRuntime"(
        "me.shedaniel:RoughlyEnoughItems-fabric:${versionProperty("rei_version")}",
    )

    "common"(project.files(commonProject.tasks.named("jar")))
    "shadowBundle"(project.files(commonProject.tasks.named("transformProductionFabric")))

    "testImplementation"("org.junit.jupiter:junit-jupiter:5.13.4")
    "testImplementation"("org.ow2.asm:asm:9.9")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.13.4")
}

val expandedFabricMetadata = mapOf(
    "version" to project.version.toString(),
    "minecraft_version" to versionProperty("minecraft_version"),
    "java_version" to versionProperty("java_version"),
    "fabric_loader_version" to versionProperty("fabric_loader_version"),
    "fabric_api_version" to versionProperty("fabric_api_version"),
    "architectury_api_version" to versionProperty("architectury_api_version"),
    "owo_lib_version" to versionProperty("owo_lib_version"),
    "cardinal_components_version" to versionProperty("cardinal_components_version"),
    "trinkets_version" to versionProperty("trinkets_version"),
    "geckolib_version" to versionProperty("geckolib_version"),
    "fabric_shield_lib_version" to versionProperty("fabric_shield_lib_version"),
    "biolith_version" to versionProperty("biolith_version"),
    "fabric_asm_metadata_version" to versionProperty("fabric_asm_version").removePrefix("v"),
    "cloth_config_version" to versionProperty("cloth_config_version"),
    "mod_menu_version" to versionProperty("mod_menu_version"),
    "rei_version" to versionProperty("rei_version"),
    "emi_version" to versionProperty("emi_version"),
)

tasks.named<ProcessResources>("processResources") {
    inputs.properties(expandedFabricMetadata)
    filesMatching("fabric.mod.json") {
        expand(expandedFabricMetadata)
    }
}

val javaVersion = versionProperty("java_version").toInt()
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(javaVersion)
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(project.configurations["shadowBundle"])
    from(sourceSets["client"].output)
    archiveClassifier.set("dev-shadow")
}

tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    dependsOn(tasks.named("shadowJar"))
    val shadowJar = tasks.named<ShadowJar>("shadowJar")
    mustRunAfter(shadowJar)
    inputFile.set(shadowJar.get().archiveFile)
}

if (minecraftVersion == "1.20.1") {
    val fabricMetalBlockEvidenceArchive = rootProject.file(
        "docs/evidence/fabric-1.20.1/metal-block-registry-v23",
    )
    val fabricMetalBlockEvidenceVerifier =
        rootProject.file("scripts/e2e/fabric_metal_block_evidence.py")
    val fabricMetalBlockEvidenceTest =
        rootProject.file("scripts/e2e/test_fabric_metal_block_evidence.py")
    val fabricClientRunner = rootProject.file("scripts/e2e/client.py")
    val fabricEvidenceLibrary = rootProject.file("scripts/e2e/evidence.py")
    val fabricEvidenceTestLibrary = rootProject.file("scripts/e2e/test_evidence.py")
    val fabricActiveProfile = rootProject.file("scripts/e2e/fabric-1.20.1-profile.json")
    val fabricProfileSnapshotV23 =
        rootProject.file("scripts/e2e/fabric-1.20.1-profile-v23.json")
    val fabricForestLanternEvidenceArchive = rootProject.file(
        "docs/evidence/fabric-1.20.1/forest-lantern-v24",
    )
    val fabricForestLanternEvidenceVerifier =
        rootProject.file("scripts/e2e/fabric_forest_lantern_evidence.py")
    val fabricForestLanternEvidenceTest =
        rootProject.file("scripts/e2e/test_fabric_forest_lantern_evidence.py")
    val fabricProfileSnapshotV24 =
        rootProject.file("scripts/e2e/fabric-1.20.1-profile-v24.json")
    val fabricAttrahiteEvidenceArchive = rootProject.file(
        "docs/evidence/fabric-1.20.1/attrahite-block-registry-v26",
    )
    val fabricAttrahiteEvidenceVerifier =
        rootProject.file("scripts/e2e/fabric_attrahite_evidence_v26.py")
    val fabricAttrahiteEvidenceTest =
        rootProject.file("scripts/e2e/test_fabric_attrahite_evidence_v26.py")
    val fabricProfileSnapshotV25 =
        rootProject.file("scripts/e2e/fabric-1.20.1-profile-v25.json")
    val fabricProfileSnapshotV26 =
        rootProject.file("scripts/e2e/fabric-1.20.1-profile-v26.json")
    val fabricAttrahiteHarnessSize = 292255L
    val fabricAttrahiteHarnessSha256 =
        "6ba6379b35e00d7a1b27f0c328fa652bcda9af4cc79a763cca609a17e604df4b"

    val fabricMetalBlockRegistryEvidenceSafetyTest =
        tasks.register<Exec>("fabricMetalBlockRegistryEvidenceSafetyTest") {
            group = "verification"
            description =
                "Runs the Fabric metal-block-registry v23 verifier safety tests."
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_fabric_metal_block_evidence.py",
            )
            inputs.files(
                fabricMetalBlockEvidenceVerifier,
                fabricMetalBlockEvidenceTest,
                fabricClientRunner,
                fabricEvidenceLibrary,
                fabricEvidenceTestLibrary,
                fabricActiveProfile,
                fabricProfileSnapshotV23,
            )
            inputs.dir(fabricMetalBlockEvidenceArchive)
                .withPropertyName("fabricMetalBlockEvidenceArchiveSafetyFixture")
                .optional()
        }

    tasks.register<Exec>("validateFabricMetalBlockRegistryEvidenceArchiveIntegrity") {
        group = "verification"
        description =
            "Validates the immutable Fabric metal-block-registry v23 archive."
        dependsOn(fabricMetalBlockRegistryEvidenceSafetyTest)
        workingDir(rootProject.projectDir)
        commandLine(
            "python3",
            "-B",
            fabricMetalBlockEvidenceVerifier.absolutePath,
            "--archive",
            fabricMetalBlockEvidenceArchive.absolutePath,
        )
        inputs.files(
            fabricMetalBlockEvidenceVerifier,
            fabricClientRunner,
            fabricEvidenceLibrary,
        )
        inputs.dir(fabricMetalBlockEvidenceArchive)
            .withPropertyName("fabricMetalBlockEvidenceArchive")
            .optional()
    }

    val fabricForestLanternEvidenceSafetyTest =
        tasks.register<Exec>("fabricForestLanternEvidenceSafetyTest") {
            group = "verification"
            description =
                "Runs the Fabric Forest Lantern v24 verifier safety tests."
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_fabric_forest_lantern_evidence.py",
                "scripts/e2e/test_client.py",
            )
            inputs.files(
                fabricForestLanternEvidenceVerifier,
                fabricForestLanternEvidenceTest,
                fabricClientRunner,
                rootProject.file("scripts/e2e/test_client.py"),
                rootProject.file("scripts/e2e/fabric-1.20.1-profile-v20.json"),
                rootProject.file("scripts/e2e/fabric-1.20.1-profile-v21.json"),
                rootProject.file("scripts/e2e/fabric-1.20.1-profile-v22.json"),
                fabricProfileSnapshotV23,
                fabricEvidenceLibrary,
                fabricEvidenceTestLibrary,
                fabricActiveProfile,
                fabricProfileSnapshotV24,
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
                rootProject.file("src/main/resources/fabric.mod.json"),
                rootProject.file("fabric/build.gradle.kts"),
                rootProject.file("docs/testing/E2E-CONTRACT.md"),
            )
        }

    val validateFabricForestLanternEvidenceArchiveIntegrity =
        tasks.register<Exec>("validateFabricForestLanternEvidenceArchiveIntegrity") {
            group = "verification"
            description =
                "Validates the immutable Fabric Forest Lantern v24 archive."
            dependsOn(fabricForestLanternEvidenceSafetyTest)
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                fabricForestLanternEvidenceVerifier.absolutePath,
                "--archive",
                fabricForestLanternEvidenceArchive.absolutePath,
            )
            inputs.files(
                fabricForestLanternEvidenceVerifier,
                fabricClientRunner,
                fabricEvidenceLibrary,
            )
            inputs.dir(fabricForestLanternEvidenceArchive)
                .withPropertyName("fabricForestLanternEvidenceArchive")
                .optional()
        }

    val fabricAttrahiteEvidenceSafetyTest =
        tasks.register<Exec>("fabricAttrahiteEvidenceSafetyTest") {
            group = "verification"
            description =
                "Runs the Fabric Attrahite block-registry v26 verifier safety tests."
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                "-m",
                "unittest",
                "scripts/e2e/test_fabric_attrahite_evidence_v26.py",
                "scripts/e2e/test_client.py",
            )
            inputs.files(
                fabricAttrahiteEvidenceVerifier,
                fabricAttrahiteEvidenceTest,
                fabricClientRunner,
                rootProject.file("scripts/e2e/test_client.py"),
                rootProject.file("scripts/e2e/fabric-1.20.1-profile-v20.json"),
                rootProject.file("scripts/e2e/fabric-1.20.1-profile-v21.json"),
                rootProject.file("scripts/e2e/fabric-1.20.1-profile-v22.json"),
                fabricProfileSnapshotV23,
                fabricProfileSnapshotV24,
                fabricProfileSnapshotV25,
                fabricProfileSnapshotV26,
                rootProject.file("scripts/e2e/fabric_attrahite_evidence_v25.py"),
                fabricEvidenceLibrary,
                fabricEvidenceTestLibrary,
                fabricActiveProfile,
                rootProject.file("release/release-matrix.json"),
                rootProject.file("gradle.properties"),
                rootProject.file("src/main/resources/fabric.mod.json"),
                rootProject.file("fabric/build.gradle.kts"),
                rootProject.file("docs/testing/E2E-CONTRACT.md"),
            )
            inputs.files(
                rootProject.fileTree("src/main/generated/assets/etherology") {
                    include("blockstates/attrahite*.json")
                    include("models/block/attrahite*.json")
                    include("models/item/attrahite*.json")
                },
                rootProject.fileTree("src/client/resources/assets/etherology") {
                    include("textures/block/attrahite*.png")
                },
            )
        }

    val validateFabricAttrahiteEvidenceArchiveIntegrity =
        tasks.register<Exec>("validateFabricAttrahiteEvidenceArchiveIntegrity") {
            group = "verification"
            description =
                "Validates the immutable Fabric Attrahite block-registry v26 archive."
            dependsOn(fabricAttrahiteEvidenceSafetyTest)
            workingDir(rootProject.projectDir)
            commandLine(
                "python3",
                "-B",
                fabricAttrahiteEvidenceVerifier.absolutePath,
                "--archive",
                fabricAttrahiteEvidenceArchive.absolutePath,
            )
            inputs.files(
                fabricAttrahiteEvidenceVerifier,
                fabricClientRunner,
                fabricEvidenceLibrary,
            )
            inputs.dir(fabricAttrahiteEvidenceArchive)
                .withPropertyName("fabricAttrahiteEvidenceArchive")
                .optional()
        }

    val e2eHarness = sourceSets.create("e2eHarness") {
        java.setSrcDirs(
            listOf(rootProject.file("e2e-harness/fabric/1.20.1/src/main/java")),
        )
        resources.setSrcDirs(
            listOf(rootProject.file("e2e-harness/fabric/1.20.1/src/main/resources")),
        )
        compileClasspath += configurations["clientCompileClasspath"]
        runtimeClasspath += output + compileClasspath
    }

    val e2eHarnessTest = sourceSets.create("e2eHarnessTest") {
        java.setSrcDirs(
            listOf(rootProject.file("e2e-harness/fabric/1.20.1/src/test/java")),
        )
        resources.setSrcDirs(emptyList<String>())
        compileClasspath += e2eHarness.output + e2eHarness.compileClasspath
        runtimeClasspath += output + e2eHarness.output + e2eHarness.runtimeClasspath
    }
    configurations[e2eHarnessTest.implementationConfigurationName]
        .extendsFrom(configurations["testImplementation"])
    configurations[e2eHarnessTest.runtimeOnlyConfigurationName]
        .extendsFrom(configurations["testRuntimeOnly"])

    val e2eHarnessTestTask = tasks.register<Test>("e2eHarnessTest") {
        group = "verification"
        description = "Runs focused unit tests for the Fabric 1.20.1 E2E harness."
        dependsOn(e2eHarness.classesTaskName)
        testClassesDirs = e2eHarnessTest.output.classesDirs
        classpath = e2eHarnessTest.runtimeClasspath
        useJUnitPlatform()
    }

    val expandedE2eHarnessMetadata = mapOf(
        "version" to project.version.toString(),
        "minecraft_version" to versionProperty("minecraft_version"),
        "java_version" to versionProperty("java_version"),
        "fabric_loader_version" to versionProperty("fabric_loader_version"),
        "fabric_api_version" to versionProperty("fabric_api_version"),
    )

    tasks.named<ProcessResources>(e2eHarness.processResourcesTaskName) {
        inputs.properties(expandedE2eHarnessMetadata)
        filesMatching("fabric.mod.json") {
            expand(expandedE2eHarnessMetadata)
        }
    }

    val e2eHarnessJar = tasks.register<Jar>("e2eHarnessJar") {
        group = "e2e"
        description = "Packages the named Fabric 1.20.1 client E2E harness classes."
        dependsOn(e2eHarness.classesTaskName)
        from(e2eHarness.output)
        archiveBaseName.set("Etherology-E2E-Harness-Fabric-$minecraftVersion")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("dev")
        destinationDirectory.set(layout.buildDirectory.dir("e2e-harness/devlibs"))
    }

    val remapE2eHarnessJar = tasks.register<RemapJarTask>("remapE2eHarnessJar") {
        group = "e2e"
        description = "Remaps the separate Fabric 1.20.1 client E2E harness for a packaged run."
        dependsOn(e2eHarnessJar)
        inputFile.set(e2eHarnessJar.flatMap { it.archiveFile })
        classpath.from(e2eHarness.compileClasspath)
        addNestedDependencies.set(false)
        useMixinAP.set(false)
        archiveBaseName.set("Etherology-E2E-Harness-Fabric-$minecraftVersion")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        destinationDirectory.set(layout.buildDirectory.dir("e2e-harness/libs"))
    }

    fun validateE2eHarnessJar(harnessFile: File) {
        ZipFile(harnessFile).use { harnessZip ->
            val harnessEntries = harnessZip.entries().asSequence().map { it.name }.toSet()
            val harnessClassEntries = harnessEntries.filter { it.endsWith(".class") }
            check(harnessEntries.any {
                it == "dev/theplumteam/etherology/e2e/fabric/PhaseZeroHarness.class"
            }) {
                "E2E harness JAR has no client entrypoint class"
            }
            check(
                "dev/theplumteam/etherology/e2e/fabric/mixin/GameRendererMixin.class" in
                    harnessEntries,
            ) {
                "E2E harness JAR has no completed-render callback mixin"
            }
            check(harnessClassEntries.all {
                it.startsWith("dev/theplumteam/etherology/e2e/fabric/")
            }) {
                "E2E harness JAR contains classes outside its isolated package"
            }
            check(harnessEntries.none { it.endsWith(".jar") }) {
                "E2E harness JAR contains nested dependencies"
            }
            harnessClassEntries.forEach { classEntryName ->
                val classEntry = requireNotNull(harnessZip.getEntry(classEntryName))
                val classConstants = harnessZip.getInputStream(classEntry).use { input ->
                    String(input.readAllBytes(), StandardCharsets.ISO_8859_1)
                }
                check(!classConstants.contains("ru/feytox/etherology/")) {
                    "E2E harness class $classEntryName links to production Etherology code"
                }
            }

            val metadataEntry = requireNotNull(harnessZip.getEntry("fabric.mod.json")) {
                "E2E harness JAR has no fabric.mod.json"
            }
            val metadataText = harnessZip.getInputStream(metadataEntry)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            val metadata = JsonSlurper().parseText(metadataText) as Map<*, *>
            check(metadata["id"] == "etherology_e2e_harness") {
                "E2E harness metadata has the wrong mod id"
            }
            check(metadata["environment"] == "client") {
                "E2E harness is not client-only"
            }
            val mixinDeclarations = metadata["mixins"] as? List<*>
                ?: error("E2E harness metadata has no mixin declarations")
            check(
                mixinDeclarations.singleOrNull() == mapOf(
                    "config" to "etherology-e2e-harness.mixins.json",
                    "environment" to "client",
                ),
            ) {
                "E2E harness metadata does not declare its exact client mixin config"
            }
            val dependencies = metadata["depends"] as? Map<*, *>
                ?: error("E2E harness metadata has no dependency table")
            check(dependencies["etherology"] == "=${project.version}") {
                "E2E harness does not require the exact production mod version"
            }

            val mixinConfigEntry = requireNotNull(
                harnessZip.getEntry("etherology-e2e-harness.mixins.json"),
            ) {
                "E2E harness JAR has no completed-render mixin config"
            }
            val mixinConfigText = harnessZip.getInputStream(mixinConfigEntry)
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
            val mixinConfig = JsonSlurper().parseText(mixinConfigText) as Map<*, *>
            check(mixinConfig["required"] == true) {
                "E2E completed-render mixin config is not required"
            }
            check(
                mixinConfig["package"] ==
                    "dev.theplumteam.etherology.e2e.fabric.mixin",
            ) {
                "E2E completed-render mixin config has the wrong package"
            }
            check(mixinConfig["client"] == listOf("GameRendererMixin")) {
                "E2E completed-render mixin config has the wrong client mixin inventory"
            }
        }
    }

    val verifyE2eHarnessArtifact = tasks.register("verifyE2eHarnessArtifact") {
        group = "verification"
        description = "Validates the remapped Fabric 1.20.1 client E2E harness artifact."
        dependsOn(remapE2eHarnessJar)
        inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })

        doLast {
            validateE2eHarnessJar(remapE2eHarnessJar.get().archiveFile.get().asFile)
        }
    }

    val verifyAttrahiteE2eHarnessArtifact =
        tasks.register("verifyAttrahiteE2eHarnessArtifact") {
            group = "verification"
            description =
                "Binds the Fabric Attrahite v26 run to its exact packaged harness bytes."
            dependsOn(verifyE2eHarnessArtifact)
            inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })

            doLast {
                val harnessFile = remapE2eHarnessJar.get().archiveFile.get().asFile
                val harnessDigest = MessageDigest.getInstance("SHA-256")
                    .digest(harnessFile.readBytes())
                    .joinToString("") { byte ->
                        "%02x".format(byte.toInt() and 0xff)
                    }
                check(harnessFile.length() == fabricAttrahiteHarnessSize) {
                    "Fabric Attrahite harness size changed: ${harnessFile.length()}"
                }
                check(harnessDigest == fabricAttrahiteHarnessSha256) {
                    "Fabric Attrahite harness SHA-256 changed: $harnessDigest"
                }
            }
        }
    fabricAttrahiteEvidenceSafetyTest.configure {
        dependsOn(verifyAttrahiteE2eHarnessArtifact)
    }

    val productionJar = tasks.named<RemapJarTask>("remapJar")
    val verifyE2eHarnessIsolation = tasks.register("verifyE2eHarnessIsolation") {
        group = "verification"
        description = "Proves the packaged client harness is separate from the production mod."
        dependsOn(productionJar, verifyE2eHarnessArtifact)
        inputs.file(productionJar.flatMap { it.archiveFile })
        inputs.file(remapE2eHarnessJar.flatMap { it.archiveFile })

        doLast {
            val productionFile = productionJar.get().archiveFile.get().asFile
            val harnessFile = remapE2eHarnessJar.get().archiveFile.get().asFile
            check(productionFile != harnessFile) {
                "The production and E2E harness tasks resolved to the same artifact"
            }

            ZipFile(productionFile).use { productionZip ->
                val productionEntries = productionZip.entries().asSequence().map { it.name }.toSet()
                check(productionEntries.none { it.startsWith("dev/theplumteam/etherology/e2e/") }) {
                    "Production JAR contains E2E harness classes"
                }
                check(productionEntries.none {
                    it.contains("Etherology-E2E-Harness", ignoreCase = true)
                        || it.contains("etherology_e2e_harness", ignoreCase = true)
                }) {
                    "Production JAR contains an E2E harness artifact"
                }

                val metadataEntry = requireNotNull(productionZip.getEntry("fabric.mod.json")) {
                    "Production JAR has no root fabric.mod.json"
                }
                val metadataText = productionZip.getInputStream(metadataEntry)
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                val metadata = JsonSlurper().parseText(metadataText) as Map<*, *>
                check(metadata["id"] == "etherology") {
                    "Production JAR root metadata is not Etherology"
                }
                check(!metadataText.contains("etherology_e2e_harness")) {
                    "Production JAR metadata references the E2E harness"
                }
            }
        }
    }

    tasks.register("buildE2eHarness") {
        group = "e2e"
        description = "Builds and validates the separate Fabric 1.20.1 packaged E2E harness."
        dependsOn(e2eHarnessTestTask, verifyE2eHarnessArtifact)
    }

    tasks.register("validateFabricForestLanternV24Milestone") {
        group = "verification"
        description =
            "Validates the packaged harness and frozen Fabric Forest Lantern v24 evidence."
        dependsOn(
            e2eHarnessTestTask,
            verifyE2eHarnessArtifact,
            validateFabricForestLanternEvidenceArchiveIntegrity,
        )
    }

    tasks.register("validateFabricAttrahiteV26Milestone") {
        group = "verification"
        description =
            "Validates the pinned harness and frozen Fabric Attrahite v26 evidence."
        dependsOn(
            e2eHarnessTestTask,
            verifyAttrahiteE2eHarnessArtifact,
            validateFabricAttrahiteEvidenceArchiveIntegrity,
        )
    }
}
