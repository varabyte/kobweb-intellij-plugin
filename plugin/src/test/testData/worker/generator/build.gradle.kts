import com.varabyte.kobweb.gradle.core.extensions.KobwebBlock
import com.varabyte.kobweb.gradle.worker.extensions.worker
import com.varabyte.kobweb.gradle.worker.util.configAsKobwebWorker
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kobweb.worker) apply false
}

val updateGoldenFiles = tasks.register("updateGoldenFiles") {
    group = "kobweb"
    description = "Runs Kobweb's worker processor on every test case, saving its output into each case's `expected` folder."
}

val kobwebWorkerLib = libs.kobweb.worker

subprojects {
    apply(plugin = "org.jetbrains.kotlin.multiplatform")
    apply(plugin = "com.varabyte.kobweb.worker")

    // Keep build output out of the test data folders
    layout.buildDirectory = rootProject.layout.buildDirectory.dir(name)

    val caseProperties = Properties().apply {
        projectDir.resolve("case.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
    }
    caseProperties.getProperty("fqcn")?.let { fqcn ->
        configure<KobwebBlock> { worker.fqcn.set(fqcn) }
    }

    configure<KotlinMultiplatformExtension> {
        // Use a fixed name, since the default one is random, which would cause noisy diffs whenever golden files are regenerated
        configAsKobwebWorker(name)
        sourceSets.named("jsMain") {
            kotlin.setSrcDirs(listOf("src"))
            dependencies { implementation(kobwebWorkerLib) }
        }
    }

    val syncExpected = tasks.register<Sync>("syncExpected") {
        dependsOn("kspKotlinJs")
        from(layout.buildDirectory.dir("generated/ksp/js/jsMain/kotlin")) {
            // The processor also generates the worker's entry point, which isn't relevant to the IDE plugin
            exclude("main.kt")
        }
        into(projectDir.resolve("expected"))
    }
    updateGoldenFiles.configure { dependsOn(syncExpected) }
}
