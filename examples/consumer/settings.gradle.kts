pluginManagement {
    repositories { maven("https://maven.fabricmc.net/"); gradlePluginPortal(); mavenCentral() }
    plugins {
        id("net.fabricmc.fabric-loom-remap") version "1.17.21"
        id("net.fabricmc.fabric-loom") version "1.17.21"
    }
}
rootProject.name = "fte-example-consumer"
