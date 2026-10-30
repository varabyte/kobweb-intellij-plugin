package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.api.API_FQN
import com.varabyte.kobweb.intellij.util.kobweb.api.API_PACKAGE_MAPPING_FQN
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiRouteResolver
import com.varabyte.kobweb.intellij.util.kobweb.api.isRegisteredApiStream
import com.varabyte.kobweb.intellij.util.kobweb.api.mightBeApiStream
import com.varabyte.kobweb.intellij.util.kobweb.api.resolveApiRoute
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.layout.PAGE_FQN
import com.varabyte.kobweb.intellij.util.kobweb.layout.kobwebDisplayPath
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.PACKAGE_MAPPING_FQN
import com.varabyte.kobweb.intellij.util.kobweb.route.Route
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueProblem
import com.varabyte.kobweb.intellij.util.kobweb.route.findDuplicatePackageMappings
import com.varabyte.kobweb.intellij.util.kobweb.route.findPageRouteResolver
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.hasDynamicSegments
import com.varabyte.kobweb.intellij.util.kobweb.route.routeValueExpression
import com.varabyte.kobweb.intellij.util.kobweb.route.segments
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports the routes of pages, API endpoints, and API streams that Kobweb rejects (e.g. a route override of "a//b" or
 * "{post"), or that it accepts but are probably mistakes (e.g. a package mapping with a slash in it).
 */
class InvalidRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                if (annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget() != AnnotationUseSiteTarget.FILE) return
                when {
                    annotationEntry.isAnnotationOfType(PACKAGE_MAPPING_FQN) ->
                        checkPackageMapping(annotationEntry, RouteKind.PAGE)
                    annotationEntry.isAnnotationOfType(API_PACKAGE_MAPPING_FQN) ->
                        checkPackageMapping(annotationEntry, RouteKind.API)
                }
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (!function.isTopLevel) return
                when {
                    function.findAnnotation(PAGE_FQN) != null -> checkDeclaration(function, RouteKind.PAGE)
                    function.findAnnotation(API_FQN) != null -> checkDeclaration(function, RouteKind.API)
                }
            }

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                // Analysis is expensive, so skip properties that obviously aren't streams
                if (!property.isTopLevel || !property.mightBeApiStream) return
                if (!analyze(property) { property.isRegisteredApiStream() }) return
                checkDeclaration(property, RouteKind.STREAM)
            }

            /**
             * Reports any problems with the route of this [declaration], e.g. one with an invalid route override, or
             * one that lives outside of the package its routes are relative to (e.g. the pages package).
             */
            private fun checkDeclaration(declaration: KtCallableDeclaration, kind: RouteKind) {
                // The annotation that can override the declaration's route, which streams don't need to have
                val annotation = declaration.findAnnotation(
                    when (kind) {
                        RouteKind.PAGE -> PAGE_FQN
                        RouteKind.API, RouteKind.STREAM -> API_FQN
                    }
                )
                // If the route override isn't a string literal (e.g. it's a constant), we can't check it
                val routeOverrideExpr = annotation?.routeValueExpression
                if (annotation != null && annotation.valueArguments.isNotEmpty() && routeOverrideExpr == null) return
                // Kobweb treats blank overrides the same as no override
                val routeOverride = routeOverrideExpr?.unescapedText?.takeIf { it.isNotBlank() }

                if (routeOverrideExpr != null && routeOverride != null) {
                    RouteValueProblem.findInRouteOverride(routeOverride).reportAll(routeOverrideExpr, routeOverride)
                }

                val problemElement = routeOverrideExpr ?: annotation ?: declaration.nameIdentifier ?: declaration
                val rootPackage = declaration.findRootPackage(kind) ?: return
                val isAbsolute = routeOverride?.startsWith('/') == true
                if (!isAbsolute && !declaration.containingKtFile.packageFqName.isSubpackageOf(rootPackage)) {
                    val messageKey = when (kind) {
                        RouteKind.PAGE -> "inspection.invalid.route.outside.pages.package.display.text"
                        RouteKind.API, RouteKind.STREAM -> "inspection.invalid.route.outside.api.package.display.text"
                    }
                    holder.registerProblem(
                        problemElement,
                        UxGlobals.textBundle.message(messageKey, rootPackage.asString()),
                        *listOfNotNull(routeOverride?.let { ReplaceRouteValueQuickFix("/$it") }).toTypedArray(),
                    )
                    return
                }

                val route = when (kind) {
                    RouteKind.PAGE -> (declaration as? KtNamedFunction)?.findRoute()
                    RouteKind.API, RouteKind.STREAM -> declaration.resolveApiRoute()
                } ?: return
                when (kind) {
                    RouteKind.PAGE -> if (route.segments.first() == "api") {
                        holder.registerProblem(
                            problemElement,
                            UxGlobals.textBundle.message(
                                "inspection.invalid.route.reserved.api.display.text",
                                route.value,
                            ),
                        )
                    }
                    RouteKind.API -> {}
                    // Streams are looked up by their exact route, so there's no way to capture a dynamic value
                    RouteKind.STREAM -> if (route.hasDynamicSegments) {
                        holder.registerProblem(
                            problemElement,
                            UxGlobals.textBundle.message(
                                "inspection.invalid.route.stream.dynamic.display.text",
                                route.displayValue(kind),
                            ),
                        )
                        return
                    }
                }

                // Dynamic values are read by name, so only one of each repeated name's values can be read. This is
                // easiest to hit by inferring the same name twice, e.g. `@Page("{}")` in "User.kt" under a package
                // "user" that's mapped to "{}"
                val repeatedNames = route.segments
                    .mapNotNull { segment -> DynamicRouteSegment.tryCreate(segment)?.name }
                    .groupingBy { it }.eachCount()
                    .filterValues { count -> count > 1 }.keys
                if (repeatedNames.isNotEmpty()) {
                    val messageKey = when (kind) {
                        RouteKind.PAGE -> "inspection.invalid.route.repeated.dynamic.names.display.text"
                        RouteKind.API, RouteKind.STREAM ->
                            "inspection.invalid.route.api.repeated.dynamic.names.display.text"
                    }
                    holder.registerProblem(
                        problemElement,
                        UxGlobals.textBundle.message(
                            messageKey,
                            route.displayValue(kind),
                            repeatedNames.joinToString { "\"$it\"" },
                        ),
                    )
                }
            }

            /**
             * Reports any problems with this `@file:PackageMapping` annotation, which maps packages of the given [kind]
             * (i.e. pages, or API endpoints and streams, which each have their own annotation).
             */
            private fun checkPackageMapping(annotationEntry: KtAnnotationEntry, kind: RouteKind) {
                val packageMappingExpr = annotationEntry.routeValueExpression ?: return
                val packageMapping = packageMappingExpr.unescapedText
                // A blank package mapping, if allowed, would be destructive to the final route (unlike a blank route
                // override, which just means no override)
                if (packageMapping.isBlank()) {
                    holder.registerProblem(
                        packageMappingExpr,
                        UxGlobals.textBundle.message("inspection.invalid.route.package.mapping.blank.display.text"),
                        RemovePackageMappingQuickFix(),
                    )
                    return
                }

                val packageMappingFqn = when (kind) {
                    RouteKind.PAGE -> PACKAGE_MAPPING_FQN
                    RouteKind.API, RouteKind.STREAM -> API_PACKAGE_MAPPING_FQN
                }
                val duplicates = annotationEntry.findDuplicatePackageMappings(packageMappingFqn)
                if (duplicates.isNotEmpty()) {
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message(
                            "inspection.invalid.route.package.mapping.duplicate.display.text",
                            annotationEntry.containingKtFile.packageFqName.asString(),
                            duplicates.joinToString { "\"${it.containingKtFile.kobwebDisplayPath}\"" },
                        ),
                        RemovePackageMappingQuickFix(),
                    )
                }

                RouteValueProblem.findInPackageMapping(packageMapping).reportAll(
                    packageMappingExpr,
                    packageMapping,
                    isPackageMapping = true,
                )

                val rootPackage = annotationEntry.findRootPackage(kind) ?: return
                if (!annotationEntry.containingKtFile.packageFqName.isSubpackageOf(rootPackage)) {
                    val messageKey = when (kind) {
                        RouteKind.PAGE -> "inspection.invalid.route.package.mapping.outside.pages.package.display.text"
                        RouteKind.API, RouteKind.STREAM ->
                            "inspection.invalid.route.package.mapping.outside.api.package.display.text"
                    }
                    holder.registerProblem(
                        annotationEntry,
                        UxGlobals.textBundle.message(messageKey, rootPackage.asString()),
                    )
                }
            }

            private fun List<RouteValueProblem>.reportAll(
                valueExpr: KtStringTemplateExpression,
                value: String,
                isPackageMapping: Boolean = false,
            ) {
                val problems = this
                val valueRange = ElementManipulators.getValueTextRange(valueExpr)
                // Problem ranges are within the (decoded) value, so they only line up with the string's text if it
                // doesn't contain escape sequences, e.g. "\u003F" for "?". Otherwise, just highlight the whole value.
                // (Escape sequences are always longer than the characters they stand for, so if they're present, the
                // lengths differ.)
                val hasEscapes = valueRange.length != value.length

                for (problem in problems) {
                    val segment = problem.range.substring(value)
                    val (message, fixes) = when (problem.kind) {
                        RouteValueProblem.Kind.MISPLACED_DYNAMIC_SEGMENT -> if (isPackageMapping) {
                            val dynamicSegment = DynamicRouteSegment.tryCreate(segment)
                            if (dynamicSegment != null && !dynamicSegment.isCatchAll && !dynamicSegment.isOptional) {
                                UxGlobals.textBundle.message("inspection.invalid.route.package.mapping.misplaced.inferred.display.text")
                            } else {
                                UxGlobals.textBundle.message("inspection.invalid.route.package.mapping.misplaced.display.text")
                            }
                        } else {
                            UxGlobals.textBundle.message("inspection.invalid.route.misplaced.display.text")
                        } to emptyList()

                        RouteValueProblem.Kind.MALFORMED_DYNAMIC_SEGMENT ->
                            UxGlobals.textBundle.message("inspection.invalid.route.malformed.display.text", segment) to
                                listOfNotNull(problem.guessFix(value))

                        RouteValueProblem.Kind.STRAY_CURLY_BRACES ->
                            UxGlobals.textBundle.message("inspection.invalid.route.stray.curly.braces.display.text", segment) to
                                listOfNotNull(
                                    problem.guessFix(value),
                                    // Less likely than a typo in a dynamic segment, which is what the braces suggest
                                    ReplaceRouteValueQuickFix(
                                        value.percentEncodeInvalidCharacters(),
                                        PriorityAction.Priority.LOW,
                                    ),
                                )

                        RouteValueProblem.Kind.DOT_SEGMENT ->
                            UxGlobals.textBundle.message("inspection.invalid.route.dot.segment.display.text", segment) to
                                emptyList()

                        RouteValueProblem.Kind.EMPTY_SEGMENT ->
                            UxGlobals.textBundle.message("inspection.invalid.route.empty.segment.display.text") to
                                listOf(ReplaceRouteValueQuickFix(value.replace(Regex("/{2,}"), "/")))

                        RouteValueProblem.Kind.INVALID_CHARACTER -> {
                            val chars = RouteValueProblem.findInvalidCharacters(segment)
                            UxGlobals.textBundle.message(
                                "inspection.invalid.route.invalid.character.display.text",
                                segment,
                                chars.joinToString { char -> "${char.describe()} (`${char.percentEncode()}`)" },
                            ) to listOf(ReplaceRouteValueQuickFix(value.percentEncodeInvalidCharacters()))
                        }

                        RouteValueProblem.Kind.MULTIPLE_SEGMENTS ->
                            UxGlobals.textBundle.message("inspection.invalid.route.package.mapping.slash.display.text") to emptyList()
                    }

                    holder.registerProblem(
                        valueExpr,
                        message,
                        if (problem.isError) ProblemHighlightType.GENERIC_ERROR_OR_WARNING else ProblemHighlightType.WARNING,
                        if (hasEscapes) valueRange else problem.range.shiftRight(valueRange.startOffset),
                        *fixes.toTypedArray<LocalQuickFix>(),
                    )
                }
            }
        }
    }

    /** What a route belongs to, which decides which rules apply to it. */
    private enum class RouteKind { PAGE, API, STREAM }

    private fun KtCallableDeclaration.findAnnotation(fqn: FqName): KtAnnotationEntry? =
        annotationEntries.firstOrNull { it.isAnnotationOfType(fqn) }

    /**
     * Returns the package that routes of this [kind] are relative to in this element's module, e.g. the pages package.
     */
    private fun PsiElement.findRootPackage(kind: RouteKind): FqName? = when (kind) {
        RouteKind.PAGE -> findPageRouteResolver()?.pagesPackage
        RouteKind.API, RouteKind.STREAM -> findApiRouteResolver()?.rootPackage
    }

    /**
     * Returns this route as users write it for its [kind], e.g. "/api/users/{user}" for an API endpoint, or "chat" for
     * a stream (which is connected to without a leading slash, e.g. `ApiStream("chat")`).
     */
    private fun Route.displayValue(kind: RouteKind) = when (kind) {
        RouteKind.PAGE -> value
        RouteKind.API -> "/api$value"
        RouteKind.STREAM -> value.removePrefix("/")
    }

    private fun String.describe() = when {
        this == " " -> "a space"
        length == 1 && single().let { it.isWhitespace() || it.isISOControl() } -> "U+%04X".format(single().code)
        else -> "`$this`"
    }

    private fun String.percentEncode() = toByteArray(Charsets.UTF_8).joinToString("") { "%%%02X".format(it) }

    /**
     * Returns this value with every character that can't be part of a URL path replaced by its percent-encoding,
     * including curly braces outside of dynamic segments.
     */
    private fun String.percentEncodeInvalidCharacters(): String {
        return split('/').joinToString("/") { segment ->
            // Dynamic segment names never appear in the URL, so they can contain anything
            if (DynamicRouteSegment.tryCreate(segment) != null) return@joinToString segment
            (RouteValueProblem.findInvalidCharacters(segment) + listOf("{", "}"))
                .fold(segment) { encoded, char -> encoded.replace(char, char.percentEncode()) }
        }
    }

    /**
     * Returns a fix that replaces this problem's segment with the dynamic segment it was probably meant to be, e.g.
     * "{post}" for "{post" or "{...rest}" for "{rest...}", or null if there's no clear guess.
     */
    private fun RouteValueProblem.guessFix(value: String): ReplaceRouteValueQuickFix? {
        val segment = range.substring(value)
        val guess = when {
            segment.startsWith('{') && '}' !in segment -> "$segment}"
            segment.endsWith('}') && '{' !in segment -> "{$segment"
            else -> DynamicRouteSegment.tryCreate(segment)
                // A catch-all's "..." goes at the start of its name, not the end
                ?.takeIf { dynamicSegment -> !dynamicSegment.isCatchAll && dynamicSegment.name.endsWith("...") }
                ?.let { dynamicSegment ->
                    "{..." + dynamicSegment.name.removeSuffix("...") + (if (dynamicSegment.isOptional) "?" else "") + "}"
                }
        }
        // On its own, a segment is the last one in the route, so only problems with the segment itself are reported
        return guess
            ?.takeIf { RouteValueProblem.findInRouteOverride(it).isEmpty() }
            ?.let { ReplaceRouteValueQuickFix(range.replace(value, it)) }
    }
}
