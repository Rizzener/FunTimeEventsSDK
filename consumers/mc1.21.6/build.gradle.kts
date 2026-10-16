import net.fabricmc.loom.task.prod.ClientProductionRunTask
import net.fabricmc.loom.task.RemapJarTask

plugins { id("net.fabricmc.fabric-loom-remap") version "1.17.21" }
tasks.register<ClientProductionRunTask>("prodClient")
tasks.register<RemapJarTask>("remapPeerJar")
extra["targetMinecraft"] = "1.21.6"
apply(from = file("../common/consumer.gradle"))
