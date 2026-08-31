import dev.architectury.plugin.ArchitectPluginExtension
import org.gradle.api.tasks.Sync

plugins {
    java
}

apply(from = rootProject.file("gradle/archive-conventions.gradle.kts"))

val minecraftVersion = stonecutter.current.version
val generatedStonecutterJava = layout.buildDirectory.dir("generated/stonecutter/main/java")
val consolidatedJava = layout.buildDirectory.dir("generated/consolidated/main/java")

apply(plugin = "dev.architectury.loom")
apply(plugin = "architectury-plugin")

fun Project.versionProperty(base: String): String =
    rootProject.property("${base}_${minecraftVersion.replace(".", "_")}") as String

group = rootProject.property("maven_group") as String
version = rootProject.property("mod_version") as String

extensions.configure<BasePluginExtension>("base") {
    archivesName.set("Etherology - Common - $minecraftVersion")
}

extensions.configure<ArchitectPluginExtension>("architectury") {
    minecraft = minecraftVersion
    common(listOf("fabric", "forge"))
}

repositories {
    maven("https://maven.fabricmc.net/") {
        name = "Fabric"
        content {
            includeGroupByRegex("net\\.fabricmc(\\..*)?")
        }
    }
    maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") {
        name = "GeckoLib"
        content {
            includeGroup("software.bernie.geckolib")
            includeGroup("com.eliotlash.mclib")
        }
    }
    mavenCentral()
}

val prepareConsolidatedJava = tasks.register<Sync>("prepareConsolidatedJava") {
    dependsOn("stonecutterGenerate")
    from(generatedStonecutterJava)
    into(consolidatedJava)
}

sourceSets {
    main {
        java.setSrcDirs(listOf(consolidatedJava))
        resources.setSrcDirs(listOf(rootProject.file("common/src/main/resources")))
    }
    test {
        java.setSrcDirs(listOf(rootProject.file("common/src/test/java")))
        resources.setSrcDirs(listOf(rootProject.file("common/src/test/resources")))
    }
}

tasks.named("compileJava") {
    dependsOn(prepareConsolidatedJava)
}
tasks.matching { it.name == "sourcesJar" }.configureEach {
    dependsOn(prepareConsolidatedJava)
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
        "dev.architectury:architectury:${versionProperty("architectury_api_version")}",
    )
    "modImplementation"(
        "software.bernie.geckolib:geckolib-fabric-$minecraftVersion:${
            versionProperty("geckolib_version")
        }",
    )

    "testImplementation"("org.junit.jupiter:junit-jupiter:5.13.4")
    "testImplementation"("org.ow2.asm:asm:9.9")
    "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:1.13.4")
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
