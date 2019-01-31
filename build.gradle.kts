import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {

    val atomicfu_version = "0.12.1"

    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:$atomicfu_version")
    }
}

plugins {
    kotlin("jvm") version "1.3.20"
    `maven-publish`
}

apply {
    plugin("kotlinx-atomicfu")
}

group = "com.rnett.launchpad"
version = "1.0.0"

repositories {
    mavenCentral()
    jcenter()
}

val coroutines_version = "1.1.1"
val atomicfu_version = "0.12.1"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutines_version")
    implementation("org.jetbrains.kotlinx:atomicfu:$atomicfu_version")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val sourcesJar by tasks.creating(Jar::class) {
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

artifacts.add("archives", sourcesJar)

publishing {
    publications {
        create("default", MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar)
        }
        create("mavenJava", MavenPublication::class.java) {
            from(components["java"])
            artifact(sourcesJar)
        }
    }
    repositories {
        maven {
            url = uri("$buildDir/repository")
        }
    }
}