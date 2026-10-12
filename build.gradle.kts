plugins {
    kotlin("jvm") version "1.9.24"
    id("io.izzel.taboolib") version "2.0.38"
}

group = "com.worldscript"
version = "1.0.0"
val embeddedTabooLibVersion = "6.3.0-75b18a2"
val modernBukkitTestRuntime by configurations.creating

repositories {
    mavenLocal()
    mavenCentral()
    gradlePluginPortal()
    maven("https://repo.tabooproject.org/repository/releases/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
}

dependencies {
    // Compile against the oldest supported API; newer APIs belong in adapters.
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    taboo("org.jetbrains.kotlin:kotlin-stdlib:1.9.24")
    taboo("io.izzel.taboolib:common-env:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:common-platform-api:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:common-reflex:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:common-util:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:basic-configuration:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:bukkit-nms:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:bukkit-nms-stable:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:minecraft-chat:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:minecraft-i18n:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:platform-bukkit-impl:$embeddedTabooLibVersion")
    taboo("io.izzel.taboolib:minecraft-kether:$embeddedTabooLibVersion")
    taboo("org.bstats:bstats-bukkit:3.2.1")
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    testImplementation("org.mockito:mockito-inline:4.11.0")
    modernBukkitTestRuntime("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
}

taboolib {
    relocate("org.bstats", "com.worldscript.libs.bstats")
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
        taboolib = embeddedTabooLibVersion
    }
}

kotlin {
    jvmToolchain(8)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
    }
}

tasks.test {
    useJUnitPlatform()
    exclude("**/ModernCompatibilityTest*")
}

tasks.register<JavaExec>("runModernCompatibilityTests") {
    group = "verification"
    description = "Checks the Bukkit adapters against the actual Paper 1.21.8 API."
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    mainClass.set("com.worldscript.ModernCompatibilityTest")
    classpath = files(
        providers.provider { sourceSets.test.get().output.files },
        providers.provider { sourceSets.main.get().output.files },
        modernBukkitTestRuntime,
        configurations.testRuntimeClasspath.get().filter { !it.name.startsWith("spigot-api-") },
    )
    dependsOn(tasks.testClasses)
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
    dependsOn("runModernCompatibilityTests")
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
