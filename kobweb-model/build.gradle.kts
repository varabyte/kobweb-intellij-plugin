import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.model"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    mavenCentral()
    // JetBrains repository for gradle-tooling-extension
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    // Dependencies will ultimately be provided by the IDE.
    compileOnly(gradleApi())
    // The version doesn't matter too much here; just using something recent at the time of writing this build script.
    // As long as the project compiles and the API is backwards compatible with latest IJ APIs, we should be good.
    compileOnly("com.jetbrains.intellij.gradle:gradle-tooling-extension:233.13135.103") {
        // gradle-api already provided above, no need to pull it in twice
        exclude(group = "org.jetbrains.intellij.deps", module = "gradle-api")
    }
}

// These model classes will be run using the Gradle JVM, not the IntelliJ JVM. Let's set them to
// an old version to reduce the chance of JDK incompatibilities.
val targetJvm = JvmTarget.JVM_1_8
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = targetJvm.target
    targetCompatibility = targetJvm.target
}
tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(targetJvm)
    }
}
