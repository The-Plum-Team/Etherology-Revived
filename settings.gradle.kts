pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "FabricPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("net\\.fabricmc(\\..*)?")
            }
        }
        maven("https://maven.architectury.dev/") {
            name = "ArchitecturyPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("dev\\.architectury(\\..*)?")
                includeGroup("architectury-plugin")
                includeGroup("com.github.architectury")
            }
        }
        maven("https://libraries.minecraft.net/") {
            name = "MojangPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("com\\.mojang(\\..*)?")
            }
        }
        maven("https://maven.minecraftforge.net/") {
            name = "ForgePluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("net\\.minecraftforge(\\..*)?")
                includeGroupByRegex("de\\.oceanlabs\\.mcp(\\..*)?")
                includeGroupByRegex("org\\.spongepowered(\\..*)?")
            }
        }
        maven("https://maven.kikugie.dev/releases") {
            name = "StonecutterPluginRepository"
            mavenContent { releasesOnly() }
            content {
                includeGroupByRegex("dev\\.kikugie(\\..*)?")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

apply(from = file("gradle/release-matrix.settings.gradle.kts"))

val matrixState = gradle.extensions.extraProperties
val releaseMatrixFile = matrixState["etherologyReleaseMatrixFile"] as java.io.File
val releaseMatrix = matrixState["etherologyReleaseMatrix"] as Map<*, *>
@Suppress("UNCHECKED_CAST")
val releaseArtifacts = matrixState["etherologyReleaseArtifacts"] as List<Map<*, *>>
val expectedLaneCount = (releaseMatrix["lane_count"] as? Number)?.toInt()
    ?: error("Missing lane_count in $releaseMatrixFile")
check(releaseArtifacts.size == expectedLaneCount) {
    "Release artifact inventory has ${releaseArtifacts.size} rows; expected lane_count=$expectedLaneCount"
}

val releaseLanes = releaseArtifacts.map { artifact ->
    val loader = artifact["loader"]?.toString() ?: error("Release artifact is missing loader")
    val version = artifact["artifact_version"]?.toString()
        ?: error("Release artifact is missing artifact_version")
    val node = artifact["artifact_node"]?.toString()
        ?: error("Release artifact is missing artifact_node")
    check(loader in setOf("fabric", "forge")) { "Unsupported release loader: $loader" }
    check(node == "$loader-$version") { "Release node $node does not match $loader $version" }
    loader to version
}
check(releaseLanes.distinct().size == releaseLanes.size) {
    "Duplicate release lane in $releaseMatrixFile"
}

val releaseLoaders = releaseLanes.map { it.first }.toSet()
val releaseVersions = releaseLanes.map { it.second }.distinct().toTypedArray()

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        versions(*releaseVersions)

        branch("common") {
            versions(*releaseVersions)
        }
        releaseLoaders.sorted().forEach { loader ->
            branch(loader) {
                versions(
                    *releaseLanes
                        .filter { it.first == loader }
                        .map { it.second }
                        .toTypedArray()
                )
            }
        }
    }
}

rootProject.name = "etherology-revived"
