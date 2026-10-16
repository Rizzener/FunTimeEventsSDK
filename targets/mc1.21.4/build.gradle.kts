import net.fabricmc.loom.task.prod.ServerProductionRunTask
import net.fabricmc.loom.task.RemapJarTask

plugins {
    id("net.fabricmc.fabric-loom-remap")
    `maven-publish`
}
extra["targetMinecraft"] = "1.21.4"
extra["targetJava"] = 21
extra["targetApi"] = "0.113.0+1.21.4"
tasks.register<ServerProductionRunTask>("phase2Server")
tasks.register<RemapJarTask>("remapServerFixtureJar")
apply(from = rootProject.file("gradle/target.gradle"))
