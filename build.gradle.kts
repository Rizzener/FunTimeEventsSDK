import groovy.json.JsonSlurper

plugins { base }
val targetConfig = JsonSlurper().parse(file("targets.json")) as Map<*, *>
allprojects {
    group = (findProperty("maven_group") as String?) ?: targetConfig["maven_group"] as String
    version = (findProperty("mod_version") as String?) ?: targetConfig["sdk_version"] as String
    repositories {
        maven("https://maven.fabricmc.net/")
        mavenCentral()
    }
}
tasks.register("sdkBuild") {
    dependsOn(":core:check")
    dependsOn(subprojects.filter { it.name.startsWith("mc") }.map { "${it.path}:build" })
}
tasks.register("sdkPublishLocal") {
    dependsOn(subprojects.filter { it.name.startsWith("mc") }.map { "${it.path}:publishToMavenLocal" })
}

tasks.register("sdkStage") {
    dependsOn(subprojects.filter { it.name.startsWith("mc") }.map { "${it.path}:publishMavenJavaPublicationToStagingRepository" })
}

tasks.named("check") { dependsOn(":core:check") }
