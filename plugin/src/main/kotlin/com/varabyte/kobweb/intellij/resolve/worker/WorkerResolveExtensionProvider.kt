package com.varabyte.kobweb.intellij.resolve.worker

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.project.KobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.worker.computeWorkerClassId
import com.varabyte.kobweb.intellij.util.kobweb.worker.findWorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.worker.synthesizeWorkerClassSource
import com.varabyte.kobweb.intellij.util.kobweb.worker.toWorkerFactoryInfo
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtension
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionFile
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionNavigationTargetsProvider
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.idea.base.projectStructure.KaSourceModuleKind
import org.jetbrains.kotlin.idea.base.projectStructure.openapiModule
import org.jetbrains.kotlin.idea.base.projectStructure.sourceModuleKind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtElement

/**
 * Provides the `Worker` class that Kobweb normally generates (via KSP) for a worker module's `WorkerFactory`
 * implementation.
 *
 * For example, if a user's worker module defines a `CalculatePiWorkerFactory` class, Kobweb generates a
 * `CalculatePiWorker` class at build time, and the user's site depends on it. Without this extension, references to
 * that class would show up as errors until the project was built, and they would go stale whenever the user changes
 * their factory (until the next build).
 *
 * Our synthesized class is always considered the source of truth, so we hide the real KSP output from the IDE if it
 * exists.
 */
@OptIn(KaExperimentalApi::class)
class WorkerResolveExtensionProvider : KaResolveExtensionProvider() {
    override fun provideExtensionsFor(module: KaModule): List<KaResolveExtension> {
        if (module !is KaSourceModule || module.sourceModuleKind != KaSourceModuleKind.PRODUCTION) return emptyList()

        val ideaModule = module.openapiModule
        val kobwebProject = ideaModule.findKobwebProject() ?: return emptyList()
        if (kobwebProject.type != KobwebProjectType.Worker || kobwebProject.source !is KobwebProject.Source.Local) {
            return emptyList()
        }

        return listOf(WorkerResolveExtension(ideaModule, kobwebProject.worker?.fqcn))
    }
}

@OptIn(KaExperimentalApi::class)
private class WorkerResolveExtension(
    private val module: Module,
    private val fqcnOverride: String?,
) : KaResolveExtension() {
    private class Computed(val file: WorkerResolveExtensionFile?)

    // Resolve extensions are recreated whenever the module they belong to has an out-of-block modification (such as the
    // user changing their worker factory's type arguments), so it's safe to cache this result for the lifetime of this
    // instance.
    @Volatile
    private var computed: Computed? = null

    private val workerFile: WorkerResolveExtensionFile?
        get() {
            computed?.let { return it.file }

            if (DumbService.isDumb(module.project)) return null
            val file = try {
                createWorkerFile()
            } catch (_: IndexNotReadyException) {
                return null
            }
            computed = Computed(file)
            return file
        }

    private fun createWorkerFile(): WorkerResolveExtensionFile? {
        val factoryClass = module.findWorkerFactoryClass() ?: return null
        val factoryInfo = factoryClass.toWorkerFactoryInfo() ?: return null
        val workerClassId =
            computeWorkerClassId(factoryInfo.factoryFqName, factoryInfo.packageFqName, fqcnOverride) ?: return null

        return WorkerResolveExtensionFile(
            workerClassId,
            synthesizeWorkerClassSource(workerClassId, factoryInfo),
            SmartPointerManager.createPointer(factoryClass),
            KspOutputFileScope(module.project, workerClassId),
        )
    }

    override fun getKtFiles(): List<KaResolveExtensionFile> = listOfNotNull(workerFile)

    override fun getContainedPackages(): Set<FqName> = setOfNotNull(workerFile?.classId?.packageFqName)

    override fun getShadowedScope(): GlobalSearchScope = workerFile?.kspOutputScope ?: GlobalSearchScope.EMPTY_SCOPE
}

@OptIn(KaExperimentalApi::class)
private class WorkerResolveExtensionFile(
    val classId: ClassId,
    private val source: String,
    private val factoryClass: SmartPsiElementPointer<KtClassOrObject>,
    /** A scope matching the real KSP output for this class, which our synthesized version replaces. */
    val kspOutputScope: GlobalSearchScope,
) : KaResolveExtensionFile() {
    override fun getFileName() = "${classId.shortClassName}.kt"
    override fun getFilePackageName() = classId.packageFqName
    override fun getTopLevelClassifierNames() = setOf(classId.shortClassName)
    override fun getTopLevelCallableNames() = emptySet<Name>()
    override fun buildFileText() = source

    // Since the synthesized class is derived entirely from the user's factory, navigate there instead
    override fun createNavigationTargetsProvider() = object : KaResolveExtensionNavigationTargetsProvider() {
        override fun KaSession.getNavigationTargets(element: KtElement): Collection<PsiElement> {
            return listOfNotNull(factoryClass.element)
        }
    }
}

/**
 * A scope that matches the file KSP generates for the worker class with the given [ClassId].
 *
 * KSP output lives somewhere like `build/generated/ksp/js/jsMain/kotlin/com/example/CalculatePiWorker.kt`.
 */
private class KspOutputFileScope(project: Project, workerClassId: ClassId) : GlobalSearchScope(project) {
    private val relativePath = buildString {
        if (!workerClassId.packageFqName.isRoot) {
            append(workerClassId.packageFqName.asString().replace('.', '/'))
            append('/')
        }
        append("${workerClassId.shortClassName}.kt")
    }

    override fun contains(file: VirtualFile): Boolean {
        // e.g. "js/jsMain/kotlin/com/example/CalculatePiWorker.kt" -> ["js", "jsMain", "kotlin", "com/example/..."]
        val segments = file.path.substringAfter("/generated/ksp/", missingDelimiterValue = "").split('/', limit = 4)
        return segments.size == 4 && segments[2] == "kotlin" && segments[3] == relativePath
    }

    override fun isSearchInModuleContent(aModule: Module) = true
    override fun isSearchInLibraries() = false
}
