plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.3"
}

group = "me.rique"
version = "1.0.0"
description = "SMPCore - Core plugin for Paper 1.21.11"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("com.zaxxer:HikariCP:5.1.0")
}

tasks {
    val buildResourcePack by registering(Zip::class) {
        group = "build"
        description = "Builds the SMPCore resource pack zip."
        archiveFileName.set("SMPCore-resource-pack.zip")
        destinationDirectory.set(layout.buildDirectory.dir("resourcepack"))
        from("src/main/resourcepack")
    }
    shadowJar {
        archiveClassifier = ""
        relocate("org.xerial", "me.rique.smpcore.libs.xerial")
        relocate("org.sqlite", "me.rique.smpcore.libs.sqlite")
        relocate("com.zaxxer.hikari", "me.rique.smpcore.libs.hikari")
        // mergeServiceFiles is critical: SQLite JDBC registers itself via JDBC 4.0 SPI
        // (META-INF/services/java.sql.Driver). Without this, DriverManager can't find
        // the driver after relocation and HikariCP throws ClassNotFoundException.
        mergeServiceFiles()
        // Do NOT call minimize() - it strips classes that are loaded reflectively at
        // runtime by HikariCP and SQLite JDBC (e.g. connection factory, pool internals).
    }
    build {
        dependsOn(shadowJar)
        dependsOn(buildResourcePack)
    }
    jar {
        archiveClassifier = "unshaded"
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
    }
    processResources {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("paper-plugin.yml") {
            expand(props)
        }
    }
}

