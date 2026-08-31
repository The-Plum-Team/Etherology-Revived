import dev.kikugie.stonecutter.controller.flag.StonecutterFlag
import org.gradle.api.tasks.Exec

plugins {
    id("dev.kikugie.stonecutter")
    id("dev.architectury.loom") version "1.17.480" apply false
    id("architectury-plugin") version "3.5.167" apply false
    id("com.gradleup.shadow") version "8.3.11" apply false
}

stonecutter active null

stonecutter {
    flags {
        set(StonecutterFlag.GENERATE_MANIFEST, false)
    }
}

val matrixState = gradle.extensions.extraProperties
val releaseMatrixFile = matrixState["etherologyReleaseMatrixFile"] as java.io.File
val supportCatalogFile = rootProject.file("release/support-catalog.json")
val supportCatalogValidator = rootProject.file("scripts/release/validate_support_catalog.py")
val gradlePropertiesFile = rootProject.file("gradle.properties")
val releaseMatrix = matrixState["etherologyReleaseMatrix"] as Map<*, *>
@Suppress("UNCHECKED_CAST")
val releaseArtifacts = matrixState["etherologyReleaseArtifacts"] as List<Map<*, *>>
val releaseLaneCount = (releaseMatrix["lane_count"] as? Number)?.toInt()
    ?: error("Missing lane_count in $releaseMatrixFile")
val unitTestVersion = releaseMatrix["unit_test_version"]?.toString()
    ?: error("Missing unit_test_version in $releaseMatrixFile")
check(releaseArtifacts.size == releaseLaneCount) {
    "Release artifact inventory has ${releaseArtifacts.size} rows; expected lane_count=$releaseLaneCount"
}

val releaseArtifactTasks = releaseArtifacts.map { artifact ->
    artifact["gradle_task"]?.toString() ?: error("Release artifact is missing gradle_task")
}

val validateReleaseLaneInventory = tasks.register("validateReleaseLaneInventory") {
    group = "verification"
    description = "Checks that every release-matrix lane resolves to a real Gradle task."
    inputs.file(releaseMatrixFile)
    doLast {
        releaseArtifactTasks.forEach { taskPath ->
            tasks.getByPath(taskPath)
        }
    }
}

tasks.register<Exec>("validateSupportCatalog") {
    group = "verification"
    description = "Checks the version roadmap and current release-matrix membership."
    inputs.files(
        supportCatalogFile,
        releaseMatrixFile,
        gradlePropertiesFile,
        supportCatalogValidator,
    )
    environment("PYTHONDONTWRITEBYTECODE", "1")
    commandLine(
        "python3",
        supportCatalogValidator.absolutePath,
        "--catalog",
        supportCatalogFile.absolutePath,
        "--release-matrix",
        releaseMatrixFile.absolutePath,
        "--gradle-properties",
        gradlePropertiesFile.absolutePath,
    )
}

tasks.register("testStableLane") {
    group = "verification"
    description = "Runs loader-independent tests on common $unitTestVersion."
    dependsOn(":common:$unitTestVersion:test")
}

tasks.register("check") {
    group = "verification"
    description = "Runs the matrix inventory and loader-independent verification suite."
    dependsOn(validateReleaseLaneInventory, "testStableLane")
}

tasks.register("buildAllLanes") {
    group = "build"
    description = "Builds all $releaseLaneCount production artifacts from the release matrix."
    dependsOn(validateReleaseLaneInventory, "testStableLane", releaseArtifactTasks)
}
