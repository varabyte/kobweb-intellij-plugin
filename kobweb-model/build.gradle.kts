import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij.base)
}

group = "com.varabyte.kobweb.intellij.model"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Dependencies will ultimately be provided by the IDE.
    compileOnly(gradleApi())
    // The exact version is not critical as long as the API remains backwards compatible with the target IDE.
    compileOnly("com.jetbrains.intellij.gradle:gradle-tooling-extension:242.21829.142")
}

// These model classes will run inside the Gradle JVM, not the IntelliJ JVM.
val jvmTarget = JvmTarget.JVM_1_8

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = jvmTarget.target
    targetCompatibility = jvmTarget.target
}
kotlin.compilerOptions.jvmTarget = jvmTarget
