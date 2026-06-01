plugins {
    kotlin("jvm") version "1.9.24"
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.aspa.plugin"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://oss.sonatype.org/content/repositories/snapshots")
}

dependencies {
    // Spigot API (Provided by Server)
    compileOnly("org.spigotmc:spigot-api:1.20.4-R0.1-SNAPSHOT")

    // Embedded HTTP Server
    implementation("io.javalin:javalin:6.1.3")
    implementation("org.slf4j:slf4j-simple:2.0.12")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Database Drivers & Connection Pools
    implementation("org.xerial:sqlite-jdbc:3.45.1.0")
    implementation("com.mysql:mysql-connector-j:8.3.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // GeoIP Database Reader
    implementation("com.maxmind.geoip2:geoip2:4.2.0")


    implementation(kotlin("stdlib"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "21"
}

tasks {
    // 1. Task to run 'npm install' in the React 'web/' directory
    val npmInstall by registering(Exec::class) {
        workingDir = file("web")
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            commandLine("cmd", "/c", "npm", "install", "--legacy-peer-deps")
        } else {
            commandLine("npm", "install", "--legacy-peer-deps")
        }
    }

    // 2. Task to run 'npm run build' in the React 'web/' directory
    val npmBuild by registering(Exec::class) {
        dependsOn(npmInstall)
        workingDir = file("web")
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            commandLine("cmd", "/c", "npm", "run", "build")
        } else {
            commandLine("npm", "run", "build")
        }
    }

    // 3. Task to copy React production assets into src/main/resources/web/
    val copyWebAssets by registering(Copy::class) {
        dependsOn(npmBuild)
        from(file("web/dist"))
        into(file("src/main/resources/web"))
    }

    // Ensure resources are processed after front-end is compiled
    processResources {
        dependsOn(copyWebAssets)
    }

    shadowJar {
        archiveClassifier.set("")
        dependsOn(processResources)
        // Relocate libraries to avoid collisions with other plugins
        // relocate("io.javalin", "com.aspa.plugin.libs.javalin")
        // relocate("com.fasterxml.jackson", "com.aspa.plugin.libs.jackson")
        // relocate("com.zaxxer.hikari", "com.aspa.plugin.libs.hikari")
        // relocate("com.mongodb", "com.aspa.plugin.libs.mongodb")
        // relocate("com.maxmind", "com.aspa.plugin.libs.maxmind")
    }

    build {
        dependsOn(shadowJar)
    }
}
