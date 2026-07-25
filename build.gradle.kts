plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.salyvn"
version = "1.0.0"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
    mavenLocal()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")

    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")

    implementation("dev.triumphteam:triumph-gui:3.1.10")
    implementation("com.github.technicallycoded:FoliaLib:0.4.3")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier.set("")
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        filesMatching("paper-plugin.yml") {
            expand("version" to project.version)
        }
    }
}
