plugins {
    kotlin("jvm") version "2.0.21"
}

group = "com.worldscript"
version = "0.1.4-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
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

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
