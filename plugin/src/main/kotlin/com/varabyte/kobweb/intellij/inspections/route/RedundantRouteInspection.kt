package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.quickfixes.route.DeletePackageMappingFileQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemovePackageMappingQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.RemoveRouteOverrideQuickFix
import com.varabyte.kobweb.intellij.quickfixes.route.ReplaceRouteValueQuickFix
import com.varabyte.kobweb.intellij.util.kobweb.api.isRegisteredApiStream
import com.varabyte.kobweb.intellij.util.kobweb.api.mightBeApiStream
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteKind
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueProblem
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteValueSimplification
import com.varabyte.kobweb.intellij.util.kobweb.route.displayValue
import com.varabyte.kobweb.intellij.util.kobweb.route.findRootPackage
import com.varabyte.kobweb.intellij.util.kobweb.route.findRouteAnnotation
import com.varabyte.kobweb.intellij.util.kobweb.route.packageMappingFqn
import com.varabyte.kobweb.intellij.util.kobweb.route.resolveRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.routeFileName
import com.varabyte.kobweb.intellij.util.kobweb.route.routeValueExpression
import com.varabyte.kobweb.intellij.util.psi.isAnnotationOfType
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.name.isSubpackageOf
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports route overrides (e.g. in `@Page` or `@Api`) and `@file:PackageMapping` values that don't change anything
 * (e.g. `@Page("post")` in `Post.kt`), or that could be written more simply (e.g. `@Page("{post}")` in `Post.kt` can be
 * `@Page("{}")`).
 *
 * Kobweb recommends leaving routes to be generated from file and package names where possible, so that it's easy to
 * tell where a URL comes from.
 */
class RedundantRouteInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                super.visitAnnotationEntry(annotationEntry)
                if (annotationEntry.useSiteTarget?.getAnnotationUseSiteTarget() != AnnotationUseSiteTarget.FILE) return
                // Streams share their package mappings with API endpoints
                val kind = listOf(RouteKind.PAGE, RouteKind.API)
                    .firstOrNull { kind -> annotationEntry.isAnnotationOfType(kind.packageMappingFqn) }
                    ?: return
                checkPackageMapping(annotationEntry, kind)
            }

            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (!function.isTopLevel) return
                // Streams are properties, so they're checked separately (see `visitProperty`)
                val kind = listOf(RouteKind.PAGE, RouteKind.API)
                    .firstOrNull { kind -> function.findRouteAnnotation(kind) != null }
                    ?: return
                checkRouteOverride(function, kind)
            }

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                // Analysis is expensive, so skip properties that obviously aren't streams (or have no override to
                // check)
                if (!property.isTopLevel || !property.mightBeApiStream) return
                if (property.findRouteAnnotation(RouteKind.STREAM) == null) return
                if (!analyze(property) { property.isRegisteredApiStream() }) return
                checkRouteOverride(property, RouteKind.STREAM)
            }

            private fun checkRouteOverride(declaration: KtCallableDeclaration, kind: RouteKind) {
                val routeOverrideExpr = declaration.findRouteAnnotation(kind)?.routeValueExpression ?: return
                val routeOverride = routeOverrideExpr.unescapedText
                // Invalid overrides are reported separately, so don't pile on
                if (RouteValueProblem.findInRouteOverride(routeOverride).isNotEmpty()) return

                val simplification = RouteValueSimplification.forRouteOverride(
                    declaration.routeFileName,
                    routeOverride,
                ) { override -> declaration.resolveRoute(kind, override) }
                when (simplification) {
                    RouteValueSimplification.Redundant -> holder.registerProblem(
                        routeOverrideExpr,
                        UxGlobals.textBundle.message(
                            "inspection.redundant.route.override.display.text",
                            declaration.resolveRoute(kind, routeOverride = null)?.displayValue.orEmpty(),
                        ),
                        ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        RemoveRouteOverrideQuickFix(),
                    )

                    is RouteValueSimplification.Shorten -> holder.registerProblem(
                        routeOverrideExpr,
                        UxGlobals.textBundle.message(
                            "inspection.redundant.route.shorten.display.text",
                            simplification.simplerValue,
                        ),
                        ReplaceRouteValueQuickFix(simplification.simplerValue),
                    )

                    null -> {}
                }
            }

            private fun checkPackageMapping(annotationEntry: KtAnnotationEntry, kind: RouteKind) {
                val packageMappingExpr = annotationEntry.routeValueExpression ?: return
                val packageMapping = packageMappingExpr.unescapedText
                // Invalid package mappings are reported separately, so don't pile on
                if (RouteValueProblem.findInPackageMapping(packageMapping).isNotEmpty()) return

                val file = annotationEntry.containingKtFile
                val pkg = file.packageFqName
                // Kobweb ignores package mappings outside the root package, which is reported separately
                val rootPackage = annotationEntry.findRootPackage(kind) ?: return
                if (!pkg.isSubpackageOf(rootPackage)) return

                when (val simplification = RouteValueSimplification.forPackageMapping(pkg, packageMapping)) {
                    RouteValueSimplification.Redundant -> {
                        // Package mappings usually get a file to themselves, which there's no point keeping around
                        val isOnlyContent = file.declarations.isEmpty()
                            && file.fileAnnotationList?.annotationEntries == listOf(annotationEntry)
                        holder.registerProblem(
                            annotationEntry,
                            UxGlobals.textBundle.message("inspection.redundant.route.package.mapping.display.text"),
                            ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                            *listOfNotNull<LocalQuickFix>(
                                DeletePackageMappingFileQuickFix().takeIf { isOnlyContent },
                                RemovePackageMappingQuickFix(),
                            ).toTypedArray(),
                        )
                    }

                    is RouteValueSimplification.Shorten -> holder.registerProblem(
                        packageMappingExpr,
                        UxGlobals.textBundle.message(
                            "inspection.redundant.route.shorten.display.text",
                            simplification.simplerValue,
                        ),
                        ReplaceRouteValueQuickFix(simplification.simplerValue),
                    )

                    null -> {}
                }
            }
        }
    }
}
