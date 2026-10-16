import groovy.json.JsonSlurper

plugins { base }
val targetConfig = JsonSlurper().parse(file("targets.json")) as Map<*, *>
allprojects {
    group = "net.funtimeevents.poc"
    version = (findProperty("poc_version") as String?) ?: targetConfig["sdk_version"] as String
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }
}
tasks.register("pocBuild") {
    dependsOn(":core:check")
    dependsOn(subprojects.filter { it.name.startsWith("mc") }.map { "${it.path}:build" })
}
tasks.register("pocPublishLocal") {
    dependsOn(subprojects.filter { it.name.startsWith("mc") }.map { "${it.path}:publishToMavenLocal" })
}
