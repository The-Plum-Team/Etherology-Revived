pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.architectury.dev/") {
            name = "Architectury"
        }
        maven("https://libraries.minecraft.net/") {
            name = "Mojang"
        }
        maven("https://maven.minecraftforge.net/") {
            name = "Forge"
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "etherology-original-fabric-1.21.1-harness"
