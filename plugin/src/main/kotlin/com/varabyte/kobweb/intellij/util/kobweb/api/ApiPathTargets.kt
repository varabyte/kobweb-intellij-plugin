package com.varabyte.kobweb.intellij.util.kobweb.api

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.varabyte.kobweb.intellij.util.kobweb.route.PathAnnotation
import com.varabyte.kobweb.intellij.util.kobweb.route.PathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.findTargetName
import com.varabyte.kobweb.intellij.util.kobweb.route.findTargetNames
import com.varabyte.kobweb.intellij.util.kobweb.route.mayHavePathTarget
import com.varabyte.kobweb.intellij.util.kobweb.route.readBooleanArgument
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

private val KOBWEB_API_PACKAGE = FqName("com.varabyte.kobweb.api")
private val KOBWEB_BROWSER_PACKAGE = FqName("com.varabyte.kobweb.browser")
private val KOBWEB_STREAMS_PACKAGE = FqName("com.varabyte.kobweb.streams")

/**
 * A parameter or property that holds an API path, e.g. `window.api.get`'s `apiPath` parameter, or `ApiStream`'s `route`
 * parameter. Strings used with it (e.g. passed into it or compared against it) should be one, e.g. "hello" in
 * `window.api.get("hello")`.
 *
 * @property kind Which sort of API endpoint the path leads to.
 * @property mustExist Whether the path should lead to an endpoint that exists. False for targets marked
 *   `@ApiPath(mustExist = false)` (or `@ApiStreamPath(mustExist = false)`).
 */
class ApiPathTarget(val kind: Kind, override val mustExist: Boolean) : PathTarget {
    enum class Kind {
        /**
         * An `@Api` function, from `@ApiPath`, e.g. "hello" for `/api/hello`. These paths are relative to "/api/" and
         * may contain query parameters, e.g. "users?id=5".
         */
        API,

        /** An `ApiStream`, from `@ApiStreamPath`, e.g. "chat". These paths are exact, with no query parameters. */
        STREAM,
    }
}

/**
 * Names of the functions that take an API path in versions of Kobweb from before it annotated them with `@ApiPath`.
 *
 * These are both members of `ApiFetcher` (e.g. `window.api.get(...)`) and extension functions on it (from the
 * `kobwebx-serialization-kotlinx` library), and all of them call the path parameter `apiPath`.
 */
private val IMPLICIT_API_FETCHER_FUNCTIONS = listOf(
    "delete", "deleteBytes", "tryDelete", "tryDeleteBytes",
    "get", "getBytes", "tryGet", "tryGetBytes",
    "head", "tryHead",
    "options", "optionsBytes", "tryOptions", "tryOptionsBytes",
    "patch", "patchBytes", "tryPatch", "tryPatchBytes",
    "post", "postBytes", "tryPost", "tryPostBytes",
    "put", "putBytes", "tryPut", "tryPutBytes",
    "query", "tryQuery",
)

/**
 * Hardcoded list of parameters that take an API path in versions of Kobweb from before it annotated them with
 * `@ApiPath`, by the function they belong to.
 */
private val IMPLICIT_API_PARAMETERS = buildMap {
    val target = ApiPathTarget(ApiPathTarget.Kind.API, mustExist = true)
    for (name in IMPLICIT_API_FETCHER_FUNCTIONS.map(Name::identifier)) {
        put(CallableId(KOBWEB_BROWSER_PACKAGE, FqName("ApiFetcher"), name), "apiPath" to target)
        put(CallableId(KOBWEB_BROWSER_PACKAGE, name), "apiPath" to target)
    }
}

/**
 * Hardcoded list of parameters that take an API stream path in versions of Kobweb from before it annotated them with
 * `@ApiStreamPath`, by the function they belong to.
 */
private val IMPLICIT_API_STREAM_PARAMETERS = mapOf(
    CallableId(KOBWEB_STREAMS_PACKAGE, Name.identifier("rememberApiStream"))
        to ("route" to ApiPathTarget(ApiPathTarget.Kind.STREAM, mustExist = true)),
)

/**
 * Hardcoded list of constructor parameters that take an API stream path in versions of Kobweb from before it annotated
 * them with `@ApiStreamPath`, by the class they belong to.
 */
private val IMPLICIT_API_STREAM_CONSTRUCTOR_PARAMETERS = mapOf(
    ClassId(KOBWEB_STREAMS_PACKAGE, Name.identifier("ApiStream"))
        to ("route" to ApiPathTarget(ApiPathTarget.Kind.STREAM, mustExist = true)),
)

/**
 * Hardcoded list of property getters that return an API stream path in versions of Kobweb from before it annotated
 * them with `@ApiStreamPath`, i.e. what strings compared against them should look like.
 */
private val IMPLICIT_API_STREAM_GETTERS = buildMap {
    val target = ApiPathTarget(ApiPathTarget.Kind.STREAM, mustExist = true)
    for (stream in listOf("ImmutableApiStream", "ApiStream")) {
        put(CallableId(KOBWEB_STREAMS_PACKAGE, FqName(stream), Name.identifier("route")), target)
    }
}

/**
 * Kobweb's `@ApiPath` annotation, along with the declarations older versions of Kobweb didn't annotate yet.
 */
private val API_PATH_ANNOTATION = PathAnnotation(
    ClassId(KOBWEB_API_PACKAGE, Name.identifier("ApiPath")),
    toPathTarget = { apiPath ->
        ApiPathTarget(ApiPathTarget.Kind.API, apiPath.readBooleanArgument("mustExist", default = true))
    },
    implicitParameters = IMPLICIT_API_PARAMETERS,
)

/**
 * Kobweb's `@ApiStreamPath` annotation, along with the declarations older versions of Kobweb didn't annotate yet.
 */
private val API_STREAM_PATH_ANNOTATION = PathAnnotation(
    ClassId(KOBWEB_API_PACKAGE, Name.identifier("ApiStreamPath")),
    toPathTarget = { apiStreamPath ->
        ApiPathTarget(ApiPathTarget.Kind.STREAM, apiStreamPath.readBooleanArgument("mustExist", default = true))
    },
    implicitParameters = IMPLICIT_API_STREAM_PARAMETERS,
    implicitConstructorParameters = IMPLICIT_API_STREAM_CONSTRUCTOR_PARAMETERS,
    implicitGetters = IMPLICIT_API_STREAM_GETTERS,
)

/**
 * Returns what this string is used with if that holds an API path (see [ApiPathTarget]), e.g. the `apiPath` parameter
 * of `window.api.get` for the `"hello"` in `window.api.get("hello")`.
 *
 * Such parameters and properties are annotated with Kobweb's `@ApiPath` or `@ApiStreamPath` (which also lets users mark
 * their own), although older versions of Kobweb didn't have them, so we also recognize the declarations they were
 * later added to.
 *
 * Call [mayHavePathTarget] first to skip strings that can't have one without running analysis.
 *
 * This method must be called inside an `analyze` block.
 */
context(_: KaSession)
fun KtStringTemplateExpression.findApiPathTarget(): ApiPathTarget? {
    return findPathTarget(API_PATH_ANNOTATION) ?: findPathTarget(API_STREAM_PATH_ANNOTATION)
}

/**
 * The names of everything in this project that might hold an API path (see [findTargetNames]).
 */
private val Project.apiPathTargetNames: Set<String>
    get() = CachedValuesManager.getManager(this).getCachedValue(this) {
        CachedValueProvider.Result.create(
            findTargetNames(API_PATH_ANNOTATION) + findTargetNames(API_STREAM_PATH_ANNOTATION),
            PsiModificationTracker.getInstance(this),
            ProjectRootModificationTracker.getInstance(this),
        )
    }

/**
 * Returns true if this string might be used with something that holds an API path, judging only by the name of what
 * it's used with (see [findTargetName]), e.g. the `"hello"` in `window.api.get("hello")`.
 *
 * This is a quick check that doesn't require Kotlin analysis (unlike [findApiPathTarget], which gives the real answer),
 * so it can be wrong both ways: it lets through strings that share a name with a target (e.g. the `"key"` in
 * `map.get("key")`), and it misses targets that [findTargetNames] can't see.
 */
fun KtStringTemplateExpression.mayHaveApiPathTarget(): Boolean {
    val name = findTargetName() ?: return false
    return name in project.apiPathTargetNames
}
