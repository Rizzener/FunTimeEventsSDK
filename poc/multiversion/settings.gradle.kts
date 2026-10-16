import groovy.json.JsonSlurper

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        gradlePluginPortal()
        mavenCentral()
    }
    plugins {
        id("net.fabricmc.fabric-loom-remap") version "1.17.21"
        id("net.fabricmc.fabric-loom") version "1.17.21"
    }
}
rootProject.name = "fte-multiversion-poc"
include(":core")
val targetConfig = JsonSlurper().parse(file("targets.json")) as Map<*, *>
val requested = providers.gradleProperty("target_versions").orNull?.split(",")?.toSet()
val versions = (targetConfig["targets"] as List<*>).map { (it as Map<*, *>)["minecraft"] as String }
if (requested != null) require(versions.containsAll(requested)) { "Unknown target_versions: $requested" }
versions.filter { requested == null || it in requested }.forEach {
    require(file("targets/mc$it/build.gradle.kts").isFile) { "Missing target declaration: mc$it" }
    include(":mc$it")
    project(":mc$it").projectDir = file("targets/mc$it")
}
