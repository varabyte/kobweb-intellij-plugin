package com.varabyte.kobweb.intellij.wizards.module

import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.varabyte.kobweb.intellij.util.gradle.catalog.VersionCatalog
import com.varabyte.kobweb.intellij.util.gradle.catalog.copyEntriesIfAbsent
import com.varabyte.kobweb.intellij.util.gradle.catalog.findLibraryAlias
import com.varabyte.kobweb.intellij.util.gradle.catalog.findPluginAlias
import com.varabyte.kobweb.intellij.util.gradle.catalog.libraryAccessor
import com.varabyte.kobweb.intellij.util.gradle.catalog.pluginAccessor

/** The choices a user makes for a new Kobweb library module. */
data class KobwebLibraryOptions(
    val group: String,
    val useSilk: Boolean,
    val useServer: Boolean,
    val useMarkdown: Boolean,
    val generateReadme: Boolean,
)

/**
 * Writes new Kobweb modules into an existing Gradle build.
 *
 * Besides the module's own files, this adds the version catalog entries the module needs (creating
 * `gradle/libs.versions.toml` if the build doesn't have one) and includes the module in the build's settings script.
 *
 * @param rootDir The build's root directory, which holds its settings script.
 */
class KobwebModuleWriter(private val project: Project, private val rootDir: VirtualFile) {
    /**
     * Writes a library module into [moduleDir], a path relative to [rootDir] (e.g. `"mylib"` or `"libs/mylib"`).
     *
     * Must be called inside a write command.
     *
     * @return the new module's build script.
     */
    fun writeLibrary(moduleDir: String, options: KobwebLibraryOptions): VirtualFile {
        val templateCatalog = loadTemplateCatalog()
        val userCatalog = findOrCreateUserCatalog()
        userCatalog.copyEntriesIfAbsent(
            from = templateCatalog,
            libraryAliases = LIBRARY_TEMPLATE_LIBRARIES.values.toList(),
            pluginAliases = LIBRARY_TEMPLATE_PLUGINS.values.toList(),
        )
        userCatalog.applyChanges()

        val properties = buildMap {
            put("GROUP", options.group)
            put("USE_SILK", options.useSilk)
            put("USE_SERVER", options.useServer)
            put("USE_MARKDOWN", options.useMarkdown)
            for ((name, alias) in LIBRARY_TEMPLATE_LIBRARIES) {
                val id = templateCatalog.libraries.getValue(alias).id
                put(name, userCatalog.libraryAccessor(userCatalog.findLibraryAlias(id)!!))
            }
            for ((name, alias) in LIBRARY_TEMPLATE_PLUGINS) {
                val id = templateCatalog.plugins.getValue(alias).id
                put(name, userCatalog.pluginAccessor(userCatalog.findPluginAlias(id)!!))
            }
        }

        val moduleRoot = VfsUtil.createDirectoryIfMissing(rootDir, moduleDir)
        val packagePath = options.group.replace('.', '/')
        val readmeProperties = properties + mapOf(
            "MODULE_NAME" to moduleDir.substringAfterLast('/'),
            "PACKAGE_PATH" to packagePath,
        )
        val buildFile = moduleRoot.writeTemplate("build.gradle.kts", "Kobweb Library build.gradle.kts", properties)
        if (options.generateReadme) {
            moduleRoot.writeTemplate("README.md", "Kobweb Library README.md", readmeProperties)
        }
        // Created even though they're empty, so users can see where each kind of code goes
        VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jsMain/kotlin/$packagePath/components")
        VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jsMain/kotlin/$packagePath/pages")
        VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jsMain/resources")
        if (options.useMarkdown) {
            VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jsMain/resources/markdown")
        }
        if (options.useServer) {
            VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jvmMain/kotlin/$packagePath/api")
        }

        includeInSettings(":" + moduleDir.replace('/', ':'))
        return buildFile
    }

    // The Gradle DSL needs a file inside a real directory, so this reads the resource in place (e.g. inside the
    // plugin's jar) rather than through an in-memory copy
    private fun loadTemplateCatalog(): VersionCatalog {
        val url = checkNotNull(KobwebModuleWriter::class.java.getResource(TEMPLATE_CATALOG_RESOURCE))
        return VersionCatalog(checkNotNull(VfsUtil.findFileByURL(url)).toCatalogModel())
    }

    /** Finds the build's default catalog, or creates it as a copy of the template catalog. */
    private fun findOrCreateUserCatalog(): VersionCatalog {
        val catalogFile = rootDir.findFileByRelativePath("$CATALOG_DIR/$CATALOG_FILE_NAME") ?: run {
            val text = checkNotNull(KobwebModuleWriter::class.java.getResource(TEMPLATE_CATALOG_RESOURCE)).readText()
            VfsUtil.createDirectoryIfMissing(rootDir, CATALOG_DIR)
                .createChildData(this, CATALOG_FILE_NAME)
                .also { VfsUtil.saveText(it, text) }
        }
        return VersionCatalog(catalogFile.toCatalogModel())
    }

    private fun VirtualFile.toCatalogModel() =
        ProjectBuildModel.get(project).versionCatalogsModel.getVersionCatalogModel(this, CATALOG_NAME)

    private fun includeInSettings(gradlePath: String) {
        val settingsFile = SETTINGS_FILE_NAMES.firstNotNullOfOrNull { rootDir.findChild(it) }
            ?: error("No settings script found in ${rootDir.path}")
        val settings = GradleSettingsModel.get(settingsFile, project)
        if (gradlePath !in settings.modulePaths()) {
            settings.addModulePath(gradlePath)
            settings.applyChanges()
        }
    }

    /** Writes the internal file template [templateName] to [relativePath] inside this directory. */
    private fun VirtualFile.writeTemplate(
        relativePath: String,
        templateName: String,
        properties: Map<String, Any>,
    ): VirtualFile {
        val text = FileTemplateManager.getDefaultInstance().getInternalTemplate(templateName).getText(properties)
        val dir = relativePath.substringBeforeLast('/', missingDelimiterValue = "")
            .let { if (it.isEmpty()) this else VfsUtil.createDirectoryIfMissing(this, it) }
        val name = relativePath.substringAfterLast('/')
        val file = dir.findChild(name) ?: dir.createChildData(this@KobwebModuleWriter, name)
        VfsUtil.saveText(file, text)
        return file
    }

    private companion object {
        const val CATALOG_NAME = "libs"
        const val CATALOG_DIR = "gradle"
        const val CATALOG_FILE_NAME = "libs.versions.toml"
        const val TEMPLATE_CATALOG_RESOURCE = "/kobweb/templates/libs.versions.toml"
        val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")

        /** Template property name to its alias in the template catalog. */
        val LIBRARY_TEMPLATE_LIBRARIES = mapOf(
            "COMPOSE_RUNTIME" to "compose-runtime",
            "COMPOSE_HTML_CORE" to "compose-html-core",
            "KOBWEB_CORE" to "kobweb-core",
            "KOBWEB_SILK" to "kobweb-silk",
            "KOBWEBX_MARKDOWN" to "kobwebx-markdown",
            "KOBWEB_API" to "kobweb-api",
        )

        /** Template property name to its alias in the template catalog. */
        val LIBRARY_TEMPLATE_PLUGINS = mapOf(
            "KOTLIN_MULTIPLATFORM_PLUGIN" to "kotlin-multiplatform",
            "COMPOSE_COMPILER_PLUGIN" to "compose-compiler",
            "KOBWEB_LIBRARY_PLUGIN" to "kobweb-library",
            "KOBWEBX_MARKDOWN_PLUGIN" to "kobwebx-markdown",
        )
    }
}
