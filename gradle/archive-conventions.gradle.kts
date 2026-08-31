import org.gradle.jvm.tasks.Jar

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest.attributes.keys
        .filter { it.startsWith("Stonecutter-") }
        .forEach { manifest.attributes.remove(it) }
    doFirst {
        manifest.attributes.keys
            .filter { it.startsWith("Stonecutter-") }
            .forEach { manifest.attributes.remove(it) }
    }
}
