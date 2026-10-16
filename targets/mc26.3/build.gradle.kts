import net.fabricmc.loom.task.prod.ServerProductionRunTask

plugins {
    id("net.fabricmc.fabric-loom")
    `maven-publish`
}
extra["targetMinecraft"] = "26.3"
extra["targetJava"] = 25
extra["targetApi"] = "0.163.0+26.3"
tasks.register<ServerProductionRunTask>("phase2Server")
apply(from = rootProject.file("gradle/target.gradle"))
