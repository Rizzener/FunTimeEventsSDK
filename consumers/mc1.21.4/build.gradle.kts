import net.fabricmc.loom.task.prod.ClientProductionRunTask
import net.fabricmc.loom.task.RemapJarTask

plugins { id("net.fabricmc.fabric-loom-remap") version "1.17.21" }
tasks.register<ClientProductionRunTask>("prodClient")
tasks.register<RemapJarTask>("remapPeerJar")
extra["targetMinecraft"] = "1.21.4"
extra["targetJava"] = 21
extra["targetApi"] = "0.113.0+1.21.4"
apply(from = file("../common/consumer.gradle"))
