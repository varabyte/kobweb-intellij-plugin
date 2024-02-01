import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "com.varabyte.kobweb.intellij.model"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
}

dependencies {
    // Dependencies will ultimately be provided by the IDE.
    compileOnly(gradleApi())
    // The version doesn't matter too much here; just using something recent at the time of writing this build script.
    // As long as the project compiles and the API is backwards compatible with latest IJ APIs, we should be good.
    compileOnly("com.jetbrains.intellij.gradle:gradle-tooling-extension:233.13135.103")
}

tasks {
    // These model classes will be run using the Gradle JVM, not the IntelliJ JVM. Let's set them to
    // an old version to reduce the chance of JDK incompatibilities.
    val oldJdkVersion = JavaVersion.VERSION_1_8.toString()

    withType<JavaCompile> {
        sourceCompatibility = oldJdkVersion
        targetCompatibility = oldJdkVersion
    }
    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = oldJdkVersion
    }
}
