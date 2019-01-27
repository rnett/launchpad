import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.20"
    `maven-publish`
}

group = "com.rnett.launchpad"
version = "1.0.0"

repositories {
    mavenCentral()
    jcenter()
}

val coroutines_version = "1.1.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

