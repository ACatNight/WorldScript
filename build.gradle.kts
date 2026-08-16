plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.worldscript"
version = "0.1.14-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

kotlin {
    jvmToolchain(21)
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
