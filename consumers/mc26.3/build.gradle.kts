import net.fabricmc.loom.task.prod.ClientProductionRunTask

plugins { id("net.fabricmc.fabric-loom") version "1.17.21" }
tasks.register<ClientProductionRunTask>("prodClient")
extra["targetMinecraft"] = "26.3"
extra["targetJava"] = 25
extra["targetApi"] = "0.163.0+26.3"
apply(from = file("../common/consumer.gradle"))
