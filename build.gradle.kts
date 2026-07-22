plugins {
    `java-library`
    id("com.gradleup.shadow") version "9.4.1"
}

group = "me.rique"
version = "1.0.400"
description = "SMPCore - Core plugin for Paper 26.2"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.citizensnpcs.co/repo")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.61-beta")
    compileOnly("net.citizensnpcs:citizensapi:2.0.43-SNAPSHOT") {
        isTransitive = false
    }
    compileOnly("net.citizensnpcs:citizens-main:2.0.43-SNAPSHOT") {
        isTransitive = false
    }
    implementation("org.xerial:sqlite-jdbc:3.53.2.0")
    implementation("com.zaxxer:HikariCP:7.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    testImplementation("io.papermc.paper:paper-api:26.2.build.61-beta")
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.13.4")
}

tasks {
    val resourcePackFileName = "SMPCore-resource-pack.zip"
    val bedrockResourcePackFileName = "SMPCore-bedrock-resource-pack.mcpack"
    val resourcePackOutputDirectory = layout.buildDirectory.dir("resourcepack")
    val resourcePackArchive = resourcePackOutputDirectory.map { it.file(resourcePackFileName) }
    val projectVersionParts = project.version.toString().split('.').map { it.toInt() }
    val buildResourcePack by registering(Zip::class) {
        group = "build"
        description = "Builds the SMPCore resource pack zip."
        archiveFileName.set(resourcePackFileName)
        destinationDirectory.set(resourcePackOutputDirectory)
        from("src/main/resourcepack")
    }
    val publishResourcePack by registering(Exec::class) {
        group = "publishing"
        description = "Validates and atomically publishes the built SMPCore resource pack."
        dependsOn(buildResourcePack)
        inputs.file(resourcePackArchive)
        val publishRequested = providers.environmentVariable("PUBLISH_RESOURCE_PACK").orNull == "1"
            || providers.gradleProperty("publishResourcePack").orNull == "1"
        environment("PUBLISH_RESOURCE_PACK", if (publishRequested) "1" else "0")
        commandLine(
            "powershell.exe",
            "-NoLogo",
            "-NoProfile",
            "-ExecutionPolicy", "Bypass",
            "-File", layout.projectDirectory.file("scripts/publish-resource-pack.ps1").asFile.absolutePath,
            "-PackPath", resourcePackArchive.get().asFile.absolutePath
        )
    }
    val buildBedrockResourcePack by registering(Zip::class) {
        group = "build"
        description = "Builds the Geyser-compatible Bedrock resource pack."
        inputs.property("projectVersion", project.version.toString())
        archiveFileName.set(bedrockResourcePackFileName)
        destinationDirectory.set(resourcePackOutputDirectory)
        from("src/main/bedrock-resourcepack") {
            filesMatching("manifest.json") {
                filter { line ->
                    line.replace(
                        "[1, 0, 0]",
                        "[${projectVersionParts[0]}, ${projectVersionParts[1]}, ${projectVersionParts[2]}]"
                    )
                }
            }
        }
        from("src/main/resourcepack/assets/smpcore/textures/item") {
            include("backpack.png", "expanded_backpack.png", "team_leader_crown.png", "first_dragon_sigil.png")
            into("textures/items")
        }
        from("src/main/resourcepack/assets/smpcore/textures/entity/equipment/humanoid") {
            include("team_leader_crown.png")
            rename("team_leader_crown.png", "team_leader_crown_1.png")
            into("textures/models/armor")
        }
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
        dependsOn(buildBedrockResourcePack)
    }
    jar {
        archiveClassifier = "unshaded"
    }
    compileJava {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
    }
    test {
        useJUnitPlatform()
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
