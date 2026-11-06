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
import com.varabyte.kobweb.intellij.util.text.camelCaseToKebabCase

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

        includeInGradleSettings(moduleDir)
        return buildFile
    }

    /**
     * Adds the module at [moduleDir] to the `jsMain` dependencies of each module in [dependentDirs], unless it's
     * already there. Both are paths relative to the build's root, e.g. `"site"`.
     *
     * Must be called inside a write command.
     *
     * @param includeJvm Whether to also add it to the dependents' `jvmMain` dependencies, for a module with a JVM
     *   target. Dependents without a `jvmMain` block are left alone: most of them are JS-only.
     * @return the dependents whose build script couldn't be edited, because it has no `jsMain` block the DSL can find
     *   (e.g. it uses `val jsMain by getting`, or has no such block at all).
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
            val jsMain = sourceSets.find { it.name() == "jsMain" }
            if (buildModel == null || jsMain == null) {
                skippedDirs.add(dependentDir)
                continue
            }
            val jvmMain = if (includeJvm) sourceSets.find { it.name() == "jvmMain" } else null
            for (sourceSet in listOfNotNull(jsMain, jvmMain)) {
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
        const val TEMPLATE_CATALOG_RESOURCE = "/kobweb/templates/libs.versions.toml"
        const val FAVICON_RESOURCE = "/kobweb/templates/app/favicon.ico"
        val SETTINGS_FILE_NAMES = listOf("settings.gradle.kts", "settings.gradle")
        val BUILD_FILE_NAMES = listOf("build.gradle.kts", "build.gradle")

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

/** The Gradle path of the module in this directory, e.g. `:libs:mylib` for `libs/mylib`. */
private fun String.toGradlePath() = ":" + replace('/', ':')
