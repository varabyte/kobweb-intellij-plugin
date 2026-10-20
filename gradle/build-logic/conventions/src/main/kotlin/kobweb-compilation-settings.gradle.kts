import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    mavenCentral()
}

val compilationSettings = extensions.create<CompilationSettingsExtension>("compilationSettings")
// Matches the JVM that IntelliJ runs on, which most of our code ends up running in
compilationSettings.jvmTarget.convention(JvmTarget.JVM_21)

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = compilationSettings.jvmTarget.get().target
    targetCompatibility = compilationSettings.jvmTarget.get().target
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(compilationSettings.jvmTarget)
}

// Set jdk-release for all compilation targets. See also: https://jakewharton.com/kotlins-jdk-release-compatibility-flag/
// (Short version: resolves ambiguity Kotlin extension methods and new methods added into more recent JDKs)
pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    configure<KotlinJvmExtension> {
        compilerOptions.freeCompilerArgs.add(compilationSettings.jvmTarget.map { "-Xjdk-release=${it.target}" })
    }
}
