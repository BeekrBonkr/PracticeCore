plugins {
    java
}

group = "me.beekrbonkr"
version = "0.7.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.marcely.de/repository/maven-public/")
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.3.6")
    // Soft dependency: rush practice pulls maps and the shop from MBedwars.
    compileOnly("de.marcely.bedwars:API:5.5.5")
    // Soft dependency: the PvP bot's player-model disguise is packet-level.
    compileOnly("com.comphenix.protocol:ProtocolLib:5.3.0")
    // Loaded at runtime by the server via plugin.yml `libraries:` — no shading needed.
    compileOnly("fr.mrmicky:fastboard:2.2.1")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.compileJava {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.processResources {
    // Declared as an input so a version bump alone re-expands plugin.yml —
    // without it Gradle serves the cached copy with the old version baked in.
    inputs.property("version", project.version.toString())
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
