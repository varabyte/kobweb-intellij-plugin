package com.varabyte.kobweb.intellij.util.kobweb.api

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.psi.search.PsiSearchHelper
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.project.findKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.readKobwebBackendMetadata
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.util.module
import org.jetbrains.kotlin.idea.stubindex.KotlinAnnotationsIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierList
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isPublic

private val API_STREAM_CLASS_ID = ClassId(FqName("com.varabyte.kobweb.api.stream"), Name.identifier("ApiStream"))

/**
 * An API endpoint, i.e. an `@Api` function or an `ApiStream` property, and the route Kobweb serves it at.
 *
 * @property fqn The fully qualified name of the declaration, e.g. "com.example.api.users.user".
 * @property route The route the endpoint is served at, relative to "/api", e.g. "/users/{user}".
 * @property declaration The endpoint's declaration. This can be null if a library advertises an endpoint that we
 *   couldn't find a declaration for.
 */
class ApiDefinition(val fqn: String, val route: String, val declaration: KtCallableDeclaration?)

/**
 * The API endpoints of a Kobweb project.
 *
 * @property apis The `@Api` functions, which are called with `window.api.get(...)` and friends.
 * @property streams The `ApiStream` properties, which are connected to with `ApiStream(...)`.
 */
class ApiDefinitions(val apis: List<ApiDefinition>, val streams: List<ApiDefinition>)

/** Returns the endpoints that paths of the given [kind] lead to. */
operator fun ApiDefinitions.get(kind: ApiPathTarget.Kind): List<ApiDefinition> = when (kind) {
    ApiPathTarget.Kind.API -> apis
    ApiPathTarget.Kind.STREAM -> streams
}

/**
 * Returns true if this property is an `ApiStream` that Kobweb registers, i.e. a public, top-level property whose type
 * is exactly `ApiStream` (e.g. `val chat = object : ApiStream() { ... }`).
 *
 * This opens its own `analyze` block, as it's only called while collecting (and caching) a project's API endpoints.
 */
private fun KtProperty.isApiStream(): Boolean {
    if (!isTopLevel || !isPublic) return false
    return analyze(this) { (returnType as? KaClassType)?.classId == API_STREAM_CLASS_ID }
}

/**
 * Returns the API endpoints defined in this module's sources, plus those of any modules and libraries it depends on.
 */
private fun Module.collectApiDefinitions(): ApiDefinitions {
    val sourceScope = moduleWithDependenciesScope
    val sourceApis = KotlinAnnotationsIndex[API_FQN.shortName().asString(), project, sourceScope]
        .asSequence()
        .filter { entry -> entry.isAnnotationOfType(API_FQN) }
        .mapNotNull { entry -> (entry.parent as? KtModifierList)?.parent as? KtNamedFunction }
        .mapNotNull { function ->
            val fqn = function.fqName?.asString() ?: return@mapNotNull null
            // Note: Each endpoint's route is resolved using its own module's settings (e.g. its api package)
            val route = function.findApiRoute() ?: return@mapNotNull null
            ApiDefinition(fqn, route, function)
        }
        .toList()

    // Streams don't need an annotation, so look for properties in files that mention them instead
    val sourceStreams = mutableListOf<ApiDefinition>()
    PsiSearchHelper.getInstance(project).processAllFilesWithWord(API_STREAM_CLASS_ID.shortClassName.asString(), sourceScope, { file ->
        (file as? KtFile)?.declarations.orEmpty().filterIsInstance<KtProperty>().forEach { property ->
            val fqn = property.fqName?.asString() ?: return@forEach
            if (!property.isApiStream()) return@forEach
            val route = property.findApiStreamRoute() ?: return@forEach
            sourceStreams.add(ApiDefinition(fqn, route, property))
        }
        true
    }, true)

    // Annotations in compiled dependencies aren't indexed, but Kobweb libraries tell us what endpoints they define
    val libraryScope = getModuleWithDependenciesAndLibrariesScope(false)
    val libraryMetadata = OrderEnumerator.orderEntries(this).recursively().librariesOnly().classes().roots
        .mapNotNull { root -> root.readKobwebBackendMetadata() }
    val libraryApis = libraryMetadata.flatMap { metadata -> metadata.apiMethods }.map { api ->
        ApiDefinition(api.fqn, api.route, KotlinTopLevelFunctionFqnNameIndex[api.fqn, project, libraryScope].firstOrNull())
    }
    val libraryStreams = libraryMetadata.flatMap { metadata -> metadata.apiStreams }.map { stream ->
        ApiDefinition(
            stream.fqn,
            stream.route,
            KotlinTopLevelPropertyFqnNameIndex[stream.fqn, project, libraryScope].firstOrNull(),
        )
    }

    return ApiDefinitions(
        apis = (sourceApis + libraryApis).distinctBy { it.fqn },
        streams = (sourceStreams + libraryStreams).distinctBy { it.fqn },
    )
}

/**
 * Returns the API endpoints of the Kobweb project this element belongs to.
 *
 * API endpoints are defined in a project's backend (e.g. its `jvmMain` source set), which is a different module from
 * its frontend (e.g. `jsMain`), so this collects them from every module of the same Kobweb project.
 */
fun PsiElement.findApiDefinitions(): ApiDefinitions {
    val module = module ?: return ApiDefinitions(emptyList(), emptyList())
    val project = module.project
    return CachedValuesManager.getManager(project).getCachedValue(module) {
        val kobwebProject = module.findKobwebProject()
        val definitions = ModuleManager.getInstance(project).modules
            .filter { other -> kobwebProject != null && other.findKobwebProject() == kobwebProject }
            .map { other -> other.collectApiDefinitions() }
        CachedValueProvider.Result.create(
            ApiDefinitions(
                apis = definitions.flatMap { it.apis }.distinctBy { it.fqn },
                streams = definitions.flatMap { it.streams }.distinctBy { it.fqn },
            ),
            PsiModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        )
    }
}
