package com.varabyte.kobweb.intellij.wizards.module

import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.varabyte.kobweb.intellij.util.gradle.catalog.VersionCatalog
import com.varabyte.kobweb.intellij.util.gradle.catalog.copyEntriesIfAbsent
import com.varabyte.kobweb.intellij.util.gradle.catalog.findLibraryAlias
import com.varabyte.kobweb.intellij.util.gradle.catalog.findPluginAlias
import com.varabyte.kobweb.intellij.util.gradle.catalog.libraryAccessor
import com.varabyte.kobweb.intellij.util.gradle.catalog.pluginAccessor
import com.varabyte.kobweb.intellij.util.kobweb.conf.KOBWEB_CONF_PATH
import com.varabyte.kobweb.intellij.util.kobweb.silk.SilkIconSet
import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase

/**
 * The choices a user makes for a new Kobweb app module.
 *
 * @property title The site's title, shown in the browser's tab.
 * @property iconSet The Silk icon set to depend on, or null for none.
 */
data class KobwebAppOptions(
    val group: String,
    val title: String,
    val useSilk: Boolean,
    val iconSet: SilkIconSet?,
    val useServer: Boolean,
    val useMarkdown: Boolean,
    val generateReadme: Boolean,
    val addSampleCode: Boolean,
)

/**
 * The choices a user makes for a new Kobweb library module.
 *
 * @property iconSet The Silk icon set to depend on, or null for none.
 */
data class KobwebLibraryOptions(
    val group: String,
    val useSilk: Boolean,
    val iconSet: SilkIconSet?,
    val useServer: Boolean,
    val useMarkdown: Boolean,
    val generateReadme: Boolean,
    val addSampleCode: Boolean,
)

/**
 * The choices a user makes for a new Kobweb worker module.
 *
 * @property name The worker's name, e.g. `Echo` for an `EchoWorkerFactory` (from which Kobweb generates `EchoWorker`).
 * @property useCustomTypes Whether the worker sends and receives its own serializable classes rather than strings.
 */
data class KobwebWorkerOptions(
    val group: String,
    val name: String,
    val useCustomTypes: Boolean,
    val generateReadme: Boolean,
)

/**
 * Writes new Kobweb modules into a Gradle build, which is either an existing one or a new one started by
 * [writeProjectRoot].
 *
 * Besides the module's own files, this adds the version catalog entries the module needs (creating
 * `gradle/libs.versions.toml` if the build doesn't have one) and includes the module in the build's settings script.
 *
 * @param rootDir The build's root directory, which holds its settings script.
 * @param versionOverrides Versions to use instead of the bundled template catalog's own, keyed like its `[versions]`
 *   table (e.g. `"kotlin"` to `"2.4.10"`). Keys the template catalog doesn't declare are ignored. Versions the build's
 *   catalog already declares are kept either way.
 */
class KobwebModuleWriter(
    private val project: Project,
    private val rootDir: VirtualFile,
    private val versionOverrides: Map<String, String>,
) {
    /**
     * Writes the root files of a new Kobweb project: its settings script, `gradle.properties` and `.gitignore`. Its
     * modules are then added like those of any other build, e.g. with [writeApp].
     *
     * Must be called inside a write command.
     */
    fun writeProjectRoot(projectName: String) {
        val properties = mapOf("PROJECT_NAME" to projectName)
        rootDir.writeTemplate("settings.gradle.kts", "Kobweb Project settings.gradle.kts", properties)
        rootDir.writeTemplate("gradle.properties", "Kobweb Project gradle.properties", properties)
        rootDir.writeTemplate(".gitignore", "Kobweb Project gitignore", properties)
    }

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
            // Goes inside a double-quoted YAML string, whose escapes match Java's
            "TITLE" to StringUtil.escapeStringCharacters(options.title),
            "USE_SILK" to options.useSilk,
            "USE_SERVER" to options.useServer,
            "USE_MARKDOWN" to options.useMarkdown,
            "ADD_HEADER" to true,
        ) + ColorModeIcons.of(options.iconSet).toTemplateProperties() + addCatalogEntries(
            libraries = APP_TEMPLATE_LIBRARIES.withIconSet(options.iconSet),
            plugins = APP_TEMPLATE_PLUGINS,
        )

        val moduleRoot = VfsUtil.createDirectoryIfMissing(rootDir, moduleDir)
        val kotlinDir = "src/jsMain/kotlin/$packagePath"
        val buildFile = moduleRoot.writeTemplate("build.gradle.kts", "Kobweb Application build.gradle.kts", properties)
        moduleRoot.writeTemplate(KOBWEB_CONF_PATH, "Kobweb Application conf.yaml", properties)
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
            moduleRoot.writeTemplate(
                "$kotlinDir/components/sections/Header.kt",
                "Kobweb Application Header.kt",
                properties,
            )
            if (options.useSilk) {
                moduleRoot.writeTemplate(
                    "$kotlinDir/components/widgets/ColorModeButton.kt",
                    "Kobweb Application ColorModeButton.kt",
                    properties,
                )
            }
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

        updateRootProject(moduleDir)
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
            // A header is part of a site's look, which a library's sample pages shouldn't decide
            "ADD_HEADER" to false,
        ) + addCatalogEntries(
            libraries = LIBRARY_TEMPLATE_LIBRARIES.withIconSet(options.iconSet),
            plugins = LIBRARY_TEMPLATE_PLUGINS,
        )

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

        updateRootProject(moduleDir)
        return buildFile
    }

    /**
     * Writes a worker module into [moduleDir], a path relative to [rootDir] (e.g. `"worker"` or `"workers/echo"`).
     *
     * Must be called inside a write command.
     *
     * @return the new module's build script.
     */
    fun writeWorker(moduleDir: String, options: KobwebWorkerOptions): VirtualFile {
        val packagePath = options.group.replace('.', '/')
        val properties = mapOf(
            "MODULE_NAME" to moduleDir.substringAfterLast('/'),
            "GRADLE_PATH" to moduleDir.toGradlePath(),
            "GROUP" to options.group,
            "PACKAGE" to options.group,
            "PACKAGE_PATH" to packagePath,
            "NAME" to options.name,
            // Names the worker's script, which makes it easier to spot when debugging, e.g. `image-processor-worker.js`
            // for `ImageProcessor`
            "SCRIPT_NAME" to options.name.camelCaseToKebabCase() + "-worker",
            "USE_CUSTOM_TYPES" to options.useCustomTypes,
            "INPUT_EXAMPLE" to if (options.useCustomTypes) "${options.name}Input(\"Hello\")" else "\"Hello\"",
        ) + addCatalogEntries(WORKER_TEMPLATE_LIBRARIES, WORKER_TEMPLATE_PLUGINS)

        val moduleRoot = VfsUtil.createDirectoryIfMissing(rootDir, moduleDir)
        val buildFile = moduleRoot.writeTemplate("build.gradle.kts", "Kobweb Worker build.gradle.kts", properties)
        moduleRoot.writeTemplate(
            "src/jsMain/kotlin/$packagePath/${options.name}WorkerFactory.kt",
            "Kobweb Worker WorkerFactory.kt",
            properties,
        )
        if (options.generateReadme) {
            moduleRoot.writeTemplate("README.md", "Kobweb Worker README.md", properties)
        }

        updateRootProject(moduleDir)
        return buildFile
    }

    /**
     * Adds the module at [moduleDir] to the `jsMain` dependencies of each module in [dependentDirs], unless it's
     * already there. Both are paths relative to the build's root, e.g. `"site"`.
     *
     * Must be called inside a write command.
     *
     * @param includeJvm Whether the module also has a JVM target. If so, it goes into a dependent's `commonMain`
     *   dependencies if it has that block, since both of the dependent's targets can then use it. Otherwise, it's also
     *   added to the dependent's `jvmMain` dependencies, if it has that block: most dependents without one are JS-only.
     * @return the dependents whose build script couldn't be edited, because it has none of those blocks the DSL can
     *   find (e.g. it uses `val jsMain by getting`, or has no such block at all).
     */
    fun addAsDependency(moduleDir: String, dependentDirs: List<String>, includeJvm: Boolean): List<String> {
        val gradlePath = moduleDir.toGradlePath()
        val skippedDirs = mutableListOf<String>()
        for (dependentDir in dependentDirs) {
            val buildModel = findBuildFile(dependentDir)?.let { ProjectBuildModel.get(project).getModuleBuildModel(it) }
            // Only existing blocks are edited. Kotlin creates the source sets of each target itself, so adding a block
            // the DSL doesn't find (e.g. `val jsMain by getting`, or none at all) would declare the source set a
            // second time, e.g. `create("jsMain")`, which fails the build.
            val sourceSets = buildModel?.kotlin()?.sourceSets().orEmpty()
            fun findSourceSet(name: String) = sourceSets.find { it.name() == name }
            val commonMain = findSourceSet("commonMain")
            val jsMain = findSourceSet("jsMain")
            val targetSourceSets = when {
                commonMain != null && includeJvm -> listOf(commonMain)
                jsMain != null -> listOfNotNull(jsMain, if (includeJvm) findSourceSet("jvmMain") else null)
                else -> emptyList()
            }
            if (buildModel == null || targetSourceSets.isEmpty()) {
                skippedDirs.add(dependentDir)
                continue
            }
            for (sourceSet in targetSourceSets) {
                val dependencies = sourceSet.dependencies()
                if (dependencies.modules().none { it.path().forceString() == gradlePath }) {
                    dependencies.addModule("implementation", gradlePath)
                }
            }
            buildModel.applyChanges()
        }
        return skippedDirs
    }

    /** The build script of the module in [moduleDir], a path relative to [rootDir]. */
    fun findBuildFile(moduleDir: String): VirtualFile? =
        BUILD_FILE_NAMES.firstNotNullOfOrNull { rootDir.findFileByRelativePath("$moduleDir/$it") }

    /** The Gradle path of the module in [moduleDir], e.g. `:libs:mylib` for `"libs/mylib"`. */
    fun gradlePathOf(moduleDir: String): String = moduleDir.toGradlePath()

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

    // Copied to a real file, which the Gradle DSL needs, as we've encountered that reading it in place from the
    // plugin's jar can return a previous plugin version's contents (perhaps from the VFS's cache of jar contents).
    //
    // NOTE: Its model is never written back, so applying [versionOverrides] changes it only in memory.
    private fun loadTemplateCatalog(): VersionCatalog {
        val dir = checkNotNull(VfsUtil.createDirectoryIfMissing("${PathManager.getTempPath()}/kobweb/templates"))
        val file = dir.findChild(CATALOG_FILE_NAME) ?: dir.createChildData(this, CATALOG_FILE_NAME)
        VfsUtil.saveText(file, readTemplateCatalogText())
        val catalog = VersionCatalog(file.toCatalogModel())
        catalog.applyVersionOverrides()
        return catalog
    }

    private fun readTemplateCatalogText(): String =
        checkNotNull(KobwebModuleWriter::class.java.getResource(TEMPLATE_CATALOG_RESOURCE)).readText()

    /**
     * Finds the build's default catalog, or creates it as a copy of the template catalog, using [versionOverrides].
     *
     * The version overrides aren't saved to a created catalog until the caller calls [VersionCatalog.applyChanges].
     */
    private fun findOrCreateUserCatalog(): VersionCatalog {
        val existingFile = rootDir.findFileByRelativePath("$CATALOG_DIR/$CATALOG_FILE_NAME")
        if (existingFile != null) return VersionCatalog(existingFile.toCatalogModel())

        val text = readTemplateCatalogText()
        val catalogFile = VfsUtil.createDirectoryIfMissing(rootDir, CATALOG_DIR)
            .createChildData(this, CATALOG_FILE_NAME)
            .also { VfsUtil.saveText(it, text) }
        val catalog = VersionCatalog(catalogFile.toCatalogModel())
        catalog.applyVersionOverrides()
        return catalog
    }

    /** Replaces the versions in this catalog's `[versions]` table with [versionOverrides]. */
    private fun VersionCatalog.applyVersionOverrides() {
        val declarations = model.versionDeclarations().getAll()
        for ((key, version) in versionOverrides) {
            declarations[key]?.completeModel()?.setValue(version)
        }
    }

    private fun VirtualFile.toCatalogModel() =
        ProjectBuildModel.get(project).versionCatalogsModel.getVersionCatalogModel(this, CATALOG_NAME)

    /**
     * Updates the root project's scripts for the new module in [moduleDir], once its own files are written. Every kind
     * of Kobweb module needs these changes.
     */
    private fun updateRootProject(moduleDir: String) {
        declareKotlinPluginInRootBuildScript()
        includeInGradleSettings(moduleDir)
    }

    /**
     * Declares the Kotlin Multiplatform plugin in the root build script with `apply false`, unless it's already there,
     * creating the script if needed.
     *
     * Otherwise, Gradle loads the Kotlin Gradle plugin separately for each module that applies it, and a build with
     * two such modules fails with "The Kotlin Gradle plugin was loaded multiple times". Declaring it in the root build
     * script makes all modules share one copy. The other plugins Kobweb modules apply (e.g. Compose Compiler) work
     * either way, so they're left out.
     */
    private fun declareKotlinPluginInRootBuildScript() {
        val buildFile = BUILD_FILE_NAMES.firstNotNullOfOrNull { rootDir.findChild(it) }
            ?: rootDir.createChildData(this, BUILD_FILE_NAMES.first())
        val buildModel = ProjectBuildModel.get(project).getModuleBuildModel(buildFile)
        val userCatalog = findOrCreateUserCatalog()
        val alias = userCatalog.findPluginAlias(KOTLIN_MULTIPLATFORM_PLUGIN_ID)!!

        // The DSL only lists an `alias(...)` declaration if it finds the catalog, which it looks for under the IDE
        // project's base path, so a build in a subdirectory of the project needs the text check.
        val aliasCall = "alias(${userCatalog.pluginAccessor(alias)})"
        val isDeclared = buildModel.plugins().any { it.name().forceString() == KOTLIN_MULTIPLATFORM_PLUGIN_ID } ||
            buildModel.pluginsPsiElement?.text.orEmpty().filterNot { it.isWhitespace() }.contains(aliasCall)
        if (isDeclared) return

        val pluginModel = userCatalog.model.pluginDeclarations().getAll().getValue(alias).completeModel()!!
        buildModel.applyPlugin(ReferenceTo(pluginModel, buildModel), /* apply = */ false)
        buildModel.applyChanges()
    }

    private fun includeInGradleSettings(moduleDir: String) {
        val gradlePath = moduleDir.toGradlePath()
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
        /**
         * The version catalog entries that Kobweb module templates need. Entries missing from a user's catalog are
         * copied from here, and a new project's catalog starts as a copy of this file.
         */
        const val TEMPLATE_CATALOG_RESOURCE = "/kobweb/templates/libs.versions.toml"
        const val FAVICON_RESOURCE = "/kobweb/templates/app/favicon.ico"
        val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")
        val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")
        const val KOTLIN_MULTIPLATFORM_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"

        /** Template property name to its alias in the template catalog. */
        val APP_TEMPLATE_LIBRARIES = mapOf(
            "COMPOSE_RUNTIME" to "compose-runtime",
            "COMPOSE_HTML_CORE" to "compose-html-core",
            "KOBWEB_CORE" to "kobweb-core",
            "KOBWEB_SILK" to "kobweb-silk",
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

        /** Template property name to its alias in the template catalog. */
        val WORKER_TEMPLATE_LIBRARIES = mapOf(
            "KOBWEB_WORKER" to "kobweb-worker",
            "KOTLINX_SERIALIZATION_JSON" to "kotlinx-serialization-json",
            "KOBWEBX_SERIALIZATION_KOTLINX" to "kobwebx-serialization-kotlinx",
        )

        /** Template property name to its alias in the template catalog. */
        val WORKER_TEMPLATE_PLUGINS = mapOf(
            "KOTLIN_MULTIPLATFORM_PLUGIN" to "kotlin-multiplatform",
            "KOBWEB_WORKER_PLUGIN" to "kobweb-worker",
            "KOTLINX_SERIALIZATION_PLUGIN" to "kotlinx-serialization",
        )
    }
}

/**
 * These template libraries, plus [iconSet]'s `silk-icons-*` library (as template property `SILK_ICONS`), if any.
 *
 * The receiver maps template property names to library aliases in the template catalog.
 */
private fun Map<String, String>.withIconSet(iconSet: SilkIconSet?): Map<String, String> =
    if (iconSet != null) this + ("SILK_ICONS" to "silk-icons-${iconSet.id}") else this

/**
 * The icons of the new app's color mode button, from its icon set (or Silk's own icons, if it has none).
 *
 * @property moon The function for an icon that means "switch to dark mode".
 * @property sun The function for an icon that means "switch to light mode".
 */
private class ColorModeIcons(val packageName: String, val moon: String, val sun: String) {
    fun toTemplateProperties() = mapOf(
        "COLOR_MODE_ICONS_PACKAGE" to packageName,
        "MOON_ICON" to moon,
        "SUN_ICON" to sun,
    )

    companion object {
        fun of(iconSet: SilkIconSet?): ColorModeIcons {
            val packageName = iconSet?.packageName?.asString() ?: "com.varabyte.kobweb.silk.components.icons"
            return when (iconSet) {
                null -> ColorModeIcons(packageName, "MoonIcon", "SunIcon")
                SilkIconSet.FONT_AWESOME -> ColorModeIcons(packageName, "FaMoon", "FaSun")
                SilkIconSet.MATERIAL_ICONS -> ColorModeIcons(packageName, "MdiDarkMode", "MdiLightMode")
                SilkIconSet.MATERIAL_SYMBOLS -> ColorModeIcons(packageName, "MsDarkMode", "MsLightMode")
                SilkIconSet.LUCIDE -> ColorModeIcons(packageName, "LucideMoon", "LucideSun")
            }
        }
    }
}

/** The Gradle path of the module in this directory, e.g. `:libs:mylib` for `libs/mylib`. */
private fun String.toGradlePath() = ":" + replace('/', ':')
