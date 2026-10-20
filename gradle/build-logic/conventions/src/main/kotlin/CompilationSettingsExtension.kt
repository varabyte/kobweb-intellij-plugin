import org.gradle.api.provider.Property
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/** Settings for the `kobweb-compilation-settings` convention plugin. */
interface CompilationSettingsExtension {
    /** The JVM version to compile for. Defaults to the one the IDE runs on. */
    val jvmTarget: Property<JvmTarget>
}
