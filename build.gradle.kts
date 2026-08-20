import java.nio.file.Files

plugins {
    java
    id("fabric-loom") version "1.4.6" // 如果需要我可以把这个版本换成你要求的loom版本
}

val minecraftVersion: String by project
val mappingsChannel: String by project
val mappingsVersion: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project

group = "com.joemichaelqiao"
version = "1.0.0"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net/") }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$mappingsChannel+$mappingsVersion:v2")
    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}
