package com.varabyte.kobweb.intellij.schema.conf

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.varabyte.kobweb.intellij.util.kobweb.conf.isKobwebConf
import com.varabyte.kobweb.intellij.util.ux.UxGlobals

/** The same URL that SchemaStore's catalog lists, so IntelliJ treats both as one schema. */
private const val KOBWEB_CONF_SCHEMA_URL =
    "https://raw.githubusercontent.com/varabyte/kobweb/main/schemas/kobweb-conf.schema.json"

/**
 * Applies Kobweb's schema to `.kobweb/conf.yaml` files.
 *
 * SchemaStore's catalog already lists this schema, but IntelliJ only applies it to a `.kobweb` folder at the root of
 * the project, which misses the common case of a site living in a subfolder (e.g. `site/.kobweb/conf.yaml`). See
 * https://youtrack.jetbrains.com/issue/IJPL-259915
 *
 * This only takes effect from IntelliJ 2026.3 on, as earlier versions ignore providers without a schema file. We
 * decided supporting them wasn't worth the complexity of a version check or a bundled copy of the schema: they didn't
 * apply the schema before this class either, and users can still select it manually from the JSON schema widget in
 * the status bar for now.
 */
class KobwebConfSchemaProviderFactory : JsonSchemaProviderFactory, DumbAware {
    override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(KobwebConfSchemaProvider)

    private object KobwebConfSchemaProvider : JsonSchemaFileProvider {
        override fun isAvailable(file: VirtualFile) = file.isKobwebConf()
        override fun getName() = UxGlobals.textBundle.message("schema.conf.name")
        // We only support our schema as a remote source. If this value were non-null, then `getRemoteSource` would be
        // ignored.
        override fun getSchemaFile(): VirtualFile? = null
        override fun getSchemaType() = SchemaType.remoteSchema
        override fun getRemoteSource() = KOBWEB_CONF_SCHEMA_URL
    }
}
