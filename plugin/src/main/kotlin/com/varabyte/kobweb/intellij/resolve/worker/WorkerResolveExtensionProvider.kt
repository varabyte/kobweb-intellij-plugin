package com.varabyte.kobweb.intellij.resolve.worker

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import com.varabyte.kobweb.intellij.model.KobwebProjectType
import com.varabyte.kobweb.intellij.util.kobweb.worker.WorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.worker.computeWorkerClassId
import com.varabyte.kobweb.intellij.util.kobweb.worker.findWorkerFactoryClass
import com.varabyte.kobweb.intellij.util.kobweb.worker.synthesizeWorkerClassSource
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
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
        // Only synthesize the worker class for main (production) sources. Test sources can already see it from there,
        // so providing it again would declare it twice.
        if (module !is KaSourceModule || module.sourceModuleKind != KaSourceModuleKind.PRODUCTION) return emptyList()

        val ideaModule = module.openapiModule
        val kobwebProject = ideaModule.findKobwebProject() ?: return emptyList()
        if (kobwebProject.type != KobwebProjectType.Worker) return emptyList()

        return listOf(WorkerResolveExtension(ideaModule, kobwebProject.worker?.fqcn))
    }
}

@OptIn(KaExperimentalApi::class)
private class WorkerResolveExtension(
    private val module: Module,
    private val fqcnOverride: String?,
) : KaResolveExtension() {
    // Computing the worker file is relatively expensive (it searches indices and analyzes the factory), and the Kotlin
    // plugin queries this extension often, so we compute it once and reuse it. This is safe because the Kotlin plugin
    // throws this extension away and requests a new one whenever code that could change the result is edited (e.g. the
    // factory's name or type arguments), and after a Gradle sync (see `KobwebPostStartupProjectActivity`).
    //
    // We use `PUBLICATION` mode to avoid holding a lock while searching indices, since the Kotlin plugin may call this
    // extension from multiple threads. (At worst, two threads compute equivalent results, and the first one wins.)
    private val workerFileLazy by lazy(LazyThreadSafetyMode.PUBLICATION) {
        // Throw (instead of returning null) so that nothing gets cached, and we try again once indexing is finished
        if (DumbService.isDumb(module.project)) throw IndexNotReadyException.create()
        createWorkerFile()
    }

    private val workerFile: WorkerResolveExtensionFile?
        get() = try {
            workerFileLazy
        } catch (_: IndexNotReadyException) {
            null
        }

    private fun createWorkerFile(): WorkerResolveExtensionFile? {
        val factoryClass = module.findWorkerFactoryClass() ?: return null
        val workerClassId = factoryClass.computeWorkerClassId(fqcnOverride) ?: return null

        return WorkerResolveExtensionFile(
            workerClassId,
            factoryClass.synthesizeWorkerClassSource(workerClassId),
            SmartPointerManager.createPointer(factoryClass.ktClass),
        )
    }

    private val kspOutputScope = KspOutputScope(module)

    override fun getKtFiles(): List<KaResolveExtensionFile> = listOfNotNull(workerFile)

    override fun getContainedPackages(): Set<FqName> = setOfNotNull(workerFile?.classId?.packageFqName)

    // NOTE: Unlike the methods above, this one must NOT depend on the worker file, as computing that requires searching
    // indices, and the Kotlin plugin calls this method at arbitrary times (including in the middle of PSI changes, when
    // searching indices isn't allowed).
    override fun getShadowedScope(): GlobalSearchScope = kspOutputScope
}

@OptIn(KaExperimentalApi::class)
private class WorkerResolveExtensionFile(
    val classId: ClassId,
    private val source: String,
    private val factoryClass: SmartPsiElementPointer<KtClassOrObject>,
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
 * A scope that matches all Kotlin files KSP generates for the target worker module.
 *
 * KSP output lives somewhere like `build/generated/ksp/js/jsMain/kotlin/com/example/CalculatePiWorker.kt`.
 *
 * Our synthesized worker class replaces the one KSP generates. We hide all of the module's KSP output, instead of only
 * the file for the current worker class, which also hides stale output (e.g. if the factory was renamed since the last
 * build). The only other file Kobweb generates there is the worker's entry point, which user code never references.
 *
 * This scope must be cheap to query and must not search indices, since the Kotlin plugin uses it at arbitrary times.
 */
private class KspOutputScope(private val module: Module) : GlobalSearchScope(module.project) {
    private val fileIndex = ProjectFileIndex.getInstance(module.project)

    override fun contains(file: VirtualFile): Boolean {
        // e.g. "js/jsMain/kotlin/com/example/CalculatePiWorker.kt" -> ["js", "jsMain", "kotlin", "com/example/..."]
        val segments = file.path.substringAfter("/generated/ksp/", missingDelimiterValue = "").split('/', limit = 4)
        @Suppress("RedundantIf") // Breaking up if condition is more readable
        if (segments.size != 4 || segments[2] != "kotlin") return false

        // Other modules (e.g. a Kobweb site) generate KSP output too, which we must leave alone
        return fileIndex.getModuleForFile(file) == module
    }

    override fun isSearchInModuleContent(aModule: Module) = aModule == module
    override fun isSearchInLibraries() = false
}

/**
 * Returns the synthesized `Worker` class (provided by [WorkerResolveExtensionProvider]) with the given [ClassId], if
 * any.
 *
 * This function should be called inside an [analyze] block for the factory's module (e.g. `analyze(ktClass)`), since
 * that's where the synthesized class lives.
 *
 * @param workerClassId The ID of the worker class generated from this factory (see `computeWorkerClassId`).
 */
@Suppress("UnusedReceiverParameter") // Useful for scoping functionality
context(kaSession: KaSession)
fun WorkerFactoryClass.findSynthesizedWorkerClass(workerClassId: ClassId): KtClassOrObject? = with(kaSession) {
    findClass(workerClassId)?.psi as? KtClassOrObject
}

/**
 * If this is a synthesized `Worker` class (provided by [WorkerResolveExtensionProvider]), returns the factory it was
 * generated from.
 */
fun KtClassOrObject.findSourceWorkerFactoryClass(): WorkerFactoryClass? {
    // Synthesized classes only exist in memory, which lets us quickly skip pretty much every other class
    if (containingFile.virtualFile !is LightVirtualFile) return null
    val classId = getClassId() ?: return null

    // NOTE: Only local modules get synthesized worker classes (library workers ship with the real one already)
    return ModuleManager.getInstance(project).modules.asSequence()
        .mapNotNull { module ->
            val kobwebProject = module.findKobwebProject() ?: return@mapNotNull null
            if (kobwebProject.type != KobwebProjectType.Worker) return@mapNotNull null
            module.findWorkerFactoryClass()?.takeIf { it.computeWorkerClassId(kobwebProject.worker?.fqcn) == classId }
        }
        .firstOrNull()
}
