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

/**
 * The choices a user makes for a new Kobweb app module.
 *
 * @property title The site's title, shown in the browser's tab.
 */
data class KobwebAppOptions(
    val group: String,
    val title: String,
    val useSilk: Boolean,
    val useServer: Boolean,
    val useMarkdown: Boolean,
    val generateReadme: Boolean,
    val addSampleCode: Boolean,
)

/** The choices a user makes for a new Kobweb library module. */
data class KobwebLibraryOptions(
    val group: String,
    val useSilk: Boolean,
    val useServer: Boolean,
    val useMarkdown: Boolean,
    val generateReadme: Boolean,
    val addSampleCode: Boolean,
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
     * Writes an app module into [moduleDir], a path relative to [rootDir] (e.g. `"site"` or `"apps/site"`).
     *
     * Must be called inside a write command.
     *
     * @return the new module's build script.
     */
    fun writeApp(moduleDir: String, options: KobwebAppOptions): VirtualFile {
        val packagePath = options.group.replace('.', '/')
        val properties = mapOf(
            "MODULE_NAME" to moduleDir.substringAfterLast('/'),
            "GROUP" to options.group,
            "PACKAGE" to options.group,
            "PACKAGE_PATH" to packagePath,
            // Names the site's JS and server files, which conf.yaml points at. Like `kobweb create`, this uses the last
            // part of the group.
            "APP_NAME" to options.group.substringAfterLast('.'),
            "TITLE" to options.title,
            "USE_SILK" to options.useSilk,
            "USE_SERVER" to options.useServer,
            "USE_MARKDOWN" to options.useMarkdown,
        ) + addCatalogEntries(APP_TEMPLATE_LIBRARIES, APP_TEMPLATE_PLUGINS)

        val moduleRoot = VfsUtil.createDirectoryIfMissing(rootDir, moduleDir)
        val kotlinDir = "src/jsMain/kotlin/$packagePath"
        val buildFile = moduleRoot.writeTemplate("build.gradle.kts", "Kobweb Application build.gradle.kts", properties)
        moduleRoot.writeTemplate(".kobweb/conf.yaml", "Kobweb Application conf.yaml", properties)
        moduleRoot.writeTemplate(".gitignore", "Kobweb Application gitignore", properties)
        moduleRoot.writeTemplate("$kotlinDir/AppEntry.kt", "Kobweb Application AppEntry.kt", properties)
        if (options.generateReadme) {
            moduleRoot.writeTemplate("README.md", "Kobweb Application README.md", properties)
        }
        if (options.addSampleCode) {
            moduleRoot.writeTemplate(
                "$kotlinDir/components/layouts/PageLayout.kt",
                "Kobweb PageLayout.kt",
                properties,
            )
            moduleRoot.writeTemplate("$kotlinDir/pages/Index.kt", "Kobweb Application Index.kt", properties)
            if (options.useMarkdown) {
                moduleRoot.writeTemplate(
                    "src/jsMain/resources/markdown/About.md",
                    "Kobweb Application About.md",
                    properties,
                )
            }
            moduleRoot.copyResource("src/jsMain/resources/public/favicon.ico", FAVICON_RESOURCE)
        }
        // Created even though they're empty, so users can see where each kind of code goes
        VfsUtil.createDirectoryIfMissing(moduleRoot, "$kotlinDir/components")
        VfsUtil.createDirectoryIfMissing(moduleRoot, "$kotlinDir/pages")
        VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jsMain/resources/public")
        if (options.useMarkdown) {
            VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jsMain/resources/markdown")
        }
        if (options.useServer) {
            VfsUtil.createDirectoryIfMissing(moduleRoot, "src/jvmMain/kotlin/$packagePath/api")
        }

        includeInGradleSettings(moduleDir)
        return buildFile
    }

    /**
     * Writes a library module into [moduleDir], a path relative to [rootDir] (e.g. `"mylib"` or `"libs/mylib"`).
     *
     * Must be called inside a write command.
     *
     * @return the new module's build script.
     */
    fun writeLibrary(moduleDir: String, options: KobwebLibraryOptions): VirtualFile {
        val packagePath = options.group.replace('.', '/')
        val properties = mapOf(
            "MODULE_NAME" to moduleDir.substringAfterLast('/'),
            "GROUP" to options.group,
            "PACKAGE" to options.group,
            "PACKAGE_PATH" to packagePath,
            "USE_SILK" to options.useSilk,
            "USE_SERVER" to options.useServer,
            "USE_MARKDOWN" to options.useMarkdown,
        ) + addCatalogEntries(LIBRARY_TEMPLATE_LIBRARIES, LIBRARY_TEMPLATE_PLUGINS)

        val moduleRoot = VfsUtil.createDirectoryIfMissing(rootDir, moduleDir)
        val buildFile = moduleRoot.writeTemplate("build.gradle.kts", "Kobweb Library build.gradle.kts", properties)
        if (options.generateReadme) {
            moduleRoot.writeTemplate("README.md", "Kobweb Library README.md", properties)
        }
        if (options.addSampleCode) {
            moduleRoot.writeTemplate(
                "src/jsMain/kotlin/$packagePath/components/layouts/PageLayout.kt",
                "Kobweb PageLayout.kt",
                properties,
            )
            moduleRoot.writeTemplate(
                "src/jsMain/kotlin/$packagePath/pages/Example.kt",
                "Kobweb Library Example.kt",
                properties,
            )
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

        includeInGradleSettings(moduleDir)
        return buildFile
    }

    /**
     * Adds the given entries of the template catalog to the build's catalog, if it doesn't have them yet.
     *
     * @param libraries Template property names mapped to library aliases in the template catalog.
     * @param plugins Template property names mapped to plugin aliases in the template catalog.
     * @return the template property names, mapped to the entries' accessors in the build's catalog (e.g.
     *   `libs.kobweb.core`).
     */
    private fun addCatalogEntries(libraries: Map<String, String>, plugins: Map<String, String>): Map<String, String> {
        val templateCatalog = loadTemplateCatalog()
        val userCatalog = findOrCreateUserCatalog()
        userCatalog.copyEntriesIfAbsent(
            from = templateCatalog,
            libraryAliases = libraries.values.toList(),
            pluginAliases = plugins.values.toList(),
        )
        userCatalog.applyChanges()

        return buildMap {
            for ((name, alias) in libraries) {
                val id = templateCatalog.libraries.getValue(alias).id
                put(name, userCatalog.libraryAccessor(userCatalog.findLibraryAlias(id)!!))
            }
            for ((name, alias) in plugins) {
                val id = templateCatalog.plugins.getValue(alias).id
                put(name, userCatalog.pluginAccessor(userCatalog.findPluginAlias(id)!!))
            }
        }
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

    private fun includeInGradleSettings(moduleDir: String) {
        val gradlePath = ":" + moduleDir.replace('/', ':')
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

    /** Copies the plugin resource at [resourcePath] to [relativePath] inside this directory. */
    private fun VirtualFile.copyResource(relativePath: String, resourcePath: String) {
        val bytes = checkNotNull(KobwebModuleWriter::class.java.getResource(resourcePath)).readBytes()
        val dir = VfsUtil.createDirectoryIfMissing(this, relativePath.substringBeforeLast('/'))
        val name = relativePath.substringAfterLast('/')
        val file = dir.findChild(name) ?: dir.createChildData(this@KobwebModuleWriter, name)
        file.setBinaryContent(bytes)
    }

    private companion object {
        const val CATALOG_NAME = "libs"
        const val CATALOG_DIR = "gradle"
        const val CATALOG_FILE_NAME = "libs.versions.toml"
        const val TEMPLATE_CATALOG_RESOURCE = "/kobweb/templates/libs.versions.toml"
        const val FAVICON_RESOURCE = "/kobweb/templates/app/favicon.ico"
        val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")

        /** Template property name to its alias in the template catalog. */
        val APP_TEMPLATE_LIBRARIES = mapOf(
            "COMPOSE_RUNTIME" to "compose-runtime",
            "COMPOSE_HTML_CORE" to "compose-html-core",
            "KOBWEB_CORE" to "kobweb-core",
            "KOBWEB_SILK" to "kobweb-silk",
            "SILK_ICONS_FA" to "silk-icons-fa",
            "KOBWEBX_MARKDOWN" to "kobwebx-markdown",
            "KOBWEB_API" to "kobweb-api",
        )

        /** Template property name to its alias in the template catalog. */
        val APP_TEMPLATE_PLUGINS = mapOf(
            "KOTLIN_MULTIPLATFORM_PLUGIN" to "kotlin-multiplatform",
            "COMPOSE_COMPILER_PLUGIN" to "compose-compiler",
            "KOBWEB_APPLICATION_PLUGIN" to "kobweb-application",
            "KOBWEBX_MARKDOWN_PLUGIN" to "kobwebx-markdown",
        )

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
