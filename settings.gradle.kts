rootProject.name = "FunTimeEventsSDK"

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("fabric-loom") version "1.17-SNAPSHOT"
    }
}

include(":fte-api")
