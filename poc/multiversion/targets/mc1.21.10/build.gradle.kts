import net.fabricmc.loom.task.prod.ServerProductionRunTask
import net.fabricmc.loom.task.RemapJarTask

plugins { id("net.fabricmc.fabric-loom-remap"); `maven-publish` }
tasks.register<ServerProductionRunTask>("phase2Server")
tasks.register<RemapJarTask>("remapServerFixtureJar")
apply(from = rootProject.file("gradle/target.gradle"))
