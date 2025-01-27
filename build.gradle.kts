plugins {
    kotlin("jvm") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.5"
}

group = "xyz.fortern"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.3-R0.1-SNAPSHOT")
    // Adventure API
    compileOnly("net.kyori:adventure-api:4.18.0")
    // Kotlin Stdlib https://mvnrepository.com/artifact/org.jetbrains.kotlin/kotlin-stdlib
    implementation(kotlin("stdlib"))
}

kotlin {
    jvmToolchain(21)
}

tasks.build {
    dependsOn("shadowJar")
}
