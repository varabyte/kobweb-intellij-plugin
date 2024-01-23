plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.intellij)
}

group = "com.varabyte.kobweb.intellij"
version = libs.versions.kobweb.ide.plugin.get()

repositories {
    mavenCentral()
    // For Gradle tooling API
    maven("https://repo.gradle.org/gradle/libs-releases")
}

dependencies {
    // For Gradle Tooling API (used for querying information from gradle projects))
    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.11") // Needed by gradle tooling

    testImplementation(libs.truthish)
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2023.3.2")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf(
        "org.jetbrains.kotlin",
        "org.jetbrains.plugins.gradle",
    ))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("241.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
