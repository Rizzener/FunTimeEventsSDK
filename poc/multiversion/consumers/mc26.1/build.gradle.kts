import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins { id("net.fabricmc.fabric-loom") version "1.17.21" }
tasks.register<ClientProductionRunTask>("prodClient")
extra["targetMinecraft"] = "26.1"
apply(from = file("../common/consumer.gradle"))
