plugins { `java-library` }
dependencies {
    compileOnly("com.google.code.gson:gson:2.11.0")
}
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    withSourcesJar()
}
tasks.withType<JavaCompile>().configureEach { options.release.set(21) }
val verification = sourceSets.create("verification") {
    resources.srcDir(rootProject.file("fixtures/data"))
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
dependencies {
    add(verification.implementationConfigurationName, "com.google.code.gson:gson:2.11.0")
}
val verifyCore = tasks.register<JavaExec>("verifyCore") {
    dependsOn(tasks.named(verification.classesTaskName))
    classpath = verification.runtimeClasspath
    mainClass.set("net.funtimeevents.poc.core.PocCoreCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(verifyCore) }
val verifyBanSlice = tasks.register<JavaExec>("verifyBanSlice") {
    dependsOn(tasks.named(verification.classesTaskName))
    classpath = verification.runtimeClasspath
    mainClass.set("net.funtimeevents.poc.core.BanSliceCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(verifyBanSlice) }

val verifyDisconnectStress = tasks.register<JavaExec>("verifyDisconnectStress") {
    dependsOn(tasks.named(verification.classesTaskName))
    classpath = verification.runtimeClasspath
    mainClass.set("net.funtimeevents.poc.core.DisconnectStressCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(verifyDisconnectStress) }
val verifyScheduler = tasks.register<JavaExec>("verifyScheduler") {
    dependsOn(tasks.named(verification.classesTaskName))
    classpath = verification.runtimeClasspath
    mainClass.set("net.funtimeevents.poc.core.SchedulerCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
}
tasks.check { dependsOn(verifyScheduler) }
val verifyDisconnectStress25 = tasks.register<JavaExec>("verifyDisconnectStress25") {
    dependsOn(tasks.named(verification.classesTaskName))
    classpath = verification.runtimeClasspath
    mainClass.set("net.funtimeevents.poc.core.DisconnectStressCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) })
}
tasks.check { dependsOn(verifyDisconnectStress25) }
