import net.fabricmc.loom.task.prod.ServerProductionRunTask

plugins { id("net.fabricmc.fabric-loom"); `maven-publish` }
tasks.register<ServerProductionRunTask>("phase2Server")
apply(from = rootProject.file("gradle/target.gradle"))
