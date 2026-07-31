plugins {
    id("fabric-loom")
    id("maven-publish")
}

val isJitpack = (findProperty("jitpack") as String?) == "true"
group = if (isJitpack) "com.github.Rizzener.FunTimeEventsSDK" else ((findProperty("maven_group") as String?) ?: "com.funtimeevents")
version = (findProperty("mod_version") as String?) ?: "0.1.0"
base { archivesName = (findProperty("archives_name") as String?) ?: "fte-api" }

val minecraftVersion = (findProperty("minecraft_version") as String?) ?: "1.21.4"
val yarnMappings = (findProperty("yarn_mappings") as String?) ?: "1.21.4+build.8"
val loaderVersion = (findProperty("loader_version") as String?) ?: "0.16.10"
val fabricApiVersion = (findProperty("fabric_api_version") as String?) ?: (findProperty("fabric_version") as String?) ?: "0.113.0+1.21.4"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings("net.fabricmc:yarn:${yarnMappings}:v2")
    modImplementation("net.fabricmc:fabric-loader:${loaderVersion}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabricApiVersion}")
}

tasks.processResources {
    inputs.property("version", project.version)
    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version))
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set("FunTimeEvents API")
                description.set("FunTimeEvents SDK — client-side tracking library for Minecraft Fabric")
            }
        }
    }
    repositories {
        mavenLocal()
        val publishUrl = findProperty("maven_publish_url") as String?
            ?: System.getenv("FTE_MAVEN_URL")
        if (publishUrl != null) {
            maven {
                url = uri(publishUrl)
                credentials {
                    username = findProperty("maven_publish_user") as String?
                        ?: System.getenv("FTE_MAVEN_USER")
                        ?: ""
                    password = findProperty("maven_publish_password") as String?
                        ?: System.getenv("FTE_MAVEN_PASSWORD")
                        ?: ""
                }
            }
        }
    }
}
