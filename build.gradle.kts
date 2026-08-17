plugins {
    kotlin("jvm") version "1.9.24"
    id("io.izzel.taboolib") version "2.0.38"
}

group = "com.worldscript"
version = "0.1.69"

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    taboo("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    taboo("io.izzel.taboolib:common-env:6.3.0")
    taboo("io.izzel.taboolib:common-platform-api:6.3.0")
    taboo("io.izzel.taboolib:common-reflex:6.3.0")
    taboo("io.izzel.taboolib:common-util:6.3.0")
    taboo("io.izzel.taboolib:basic-configuration:6.3.0")
    taboo("io.izzel.taboolib:bukkit-nms:6.3.0")
    taboo("io.izzel.taboolib:bukkit-nms-stable:6.3.0")
    taboo("io.izzel.taboolib:minecraft-chat:6.3.0")
    taboo("io.izzel.taboolib:minecraft-i18n:6.3.0")
    taboo("io.izzel.taboolib:platform-bukkit-impl:6.3.0")
    taboo("io.izzel.taboolib:minecraft-kether:6.3.0")
    implementation("io.izzel.taboolib:platform-bukkit-impl:6.3.0")
    implementation("io.izzel.taboolib:minecraft-kether:6.3.0")
}

taboolib {
    env {
        install(
            "platform-bukkit",
            "platform-bukkit-impl",
            "minecraft-kether",
            "minecraft-chat",
            "minecraft-i18n",
            "bukkit-nms",
            "bukkit-nms-stable",
            "basic-configuration",
        )
    }
    version {
        taboolib = "6.3.0"
    }
}

kotlin {
    jvmToolchain(8)

    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<JavaExec>("runWorldScriptTests") {
    group = "verification"
    description = "Runs pure WorldScript TestRunner checks."
    dependsOn(tasks.compileKotlin)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.worldscript.peripheral.test.WorldScriptTestRunner")
}

tasks.check {
    dependsOn("runWorldScriptTests")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
