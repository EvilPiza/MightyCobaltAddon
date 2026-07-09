import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  kotlin("jvm")
  id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
  `maven-publish`
  java
}

val baseGroup: String by project
val lwjglVersion: String by project
val addonVersion: String by project
val addonName: String by project

base {
  archivesName = addonName
  version = addonVersion
  group = baseGroup
}

repositories {
  mavenCentral()
  maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
  maven("https://jitpack.io/")
}

dependencies {
  minecraft("com.mojang:minecraft:${property("minecraft_version")}")
  implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

  implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")
  implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

  implementation("com.github.CobaltScripts:Cobalt:master-SNAPSHOT") {
    exclude(group = "com.jagrosh", module = "DiscordIPC")
  }
  implementation("io.github.CDAGaming:DiscordIPC:0.10.2")

  implementation("org.reflections:reflections:0.10.2")
  implementation("org.lwjgl:lwjgl-nanovg:${lwjglVersion}")

  listOf("windows", "linux", "macos", "macos-arm64").forEach {
    implementation("org.lwjgl:lwjgl-nanovg:${lwjglVersion}:natives-$it")
  }

  runtimeOnly("me.djtheredstoner:DevAuth-fabric:1.2.1")
  runtimeOnly("org.apache.httpcomponents:httpclient:4.5.14")
}

tasks {
  processResources {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
      expand(getProperties())
      expand(mutableMapOf("version" to project.version))
    }
  }

  publishing {
    publications {
      create<MavenPublication>("mavenJava") {
        artifact(jar)
        artifact(kotlinSourcesJar)
      }
    }
  }

  compileKotlin {
    compilerOptions {
      jvmTarget = JvmTarget.JVM_25
    }
  }
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
}
