plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.4.1"
}

group = "me.rique"
version = "1.0.0"
description = "SMPCore - Core plugin for Paper 26.1.2"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.68-stable")
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
        exclude("META-INF/maven/**")
        exclude("META-INF/native-image/**")
        exclude("META-INF/versions/**")
        // mergeServiceFiles is critical: SQLite JDBC registers itself via JDBC 4.0 SPI
        // (META-INF/services/java.sql.Driver). Without this, DriverManager can't find
        // the driver after relocation and HikariCP throws ClassNotFoundException.
        mergeServiceFiles()
        // Do NOT call minimize() - it strips classes that are loaded reflectively at
        // runtime by HikariCP and SQLite JDBC (e.g. connection factory, pool internals).
    }
    val linuxX64Jar by registering(Zip::class) {
        group = "build"
        description = "Builds a compact Linux x86_64 deployment jar for the hosted server."
        dependsOn(shadowJar)
        archiveFileName.set("${project.name}-${project.version}-linux-x64.jar")
        destinationDirectory.set(layout.buildDirectory.dir("libs"))
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        from(provider { zipTree(shadowJar.get().archiveFile.get().asFile) }) {
            exclude("me/rique/smpcore/libs/sqlite/native/FreeBSD/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux-Android/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux-Musl/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Mac/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Windows/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/aarch64/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/arm/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/armv6/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/armv7/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/ppc64/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/riscv64/**")
            exclude("me/rique/smpcore/libs/sqlite/native/Linux/x86/**")
        }
    }
    build {
        dependsOn(shadowJar)
        dependsOn(linuxX64Jar)
        dependsOn(buildResourcePack)
    }
    jar {
        archiveClassifier = "unshaded"
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
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

