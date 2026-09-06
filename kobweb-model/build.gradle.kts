import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.model"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    mavenCentral()
}

val jvmTarget = JvmTarget.JVM_21

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = jvmTarget.target
    targetCompatibility = jvmTarget.target
}
kotlin.compilerOptions.jvmTarget = jvmTarget
