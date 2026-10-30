package com.varabyte.kobweb.intellij.inspections.route

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiDefinitions
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiRoute
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiStreamRoute
import com.varabyte.kobweb.intellij.util.kobweb.api.mightBeApiStream
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.PageRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.Route
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteDefinition
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteKind
import com.varabyte.kobweb.intellij.util.kobweb.route.displayValue
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicFolders
import com.varabyte.kobweb.intellij.util.kobweb.route.findRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.hasDynamicSegments
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtVisitorVoid

/**
 * Reports pages, API endpoints, and API streams that are served at the same route as something else, e.g. two pages
 * at "/about", or a page at "/resume.pdf" next to a public `resume.pdf` file.
 */
class ConflictingRoutesInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!holder.file.isInWritableKobwebProject()) return KtVisitorVoid.EMPTY_VISITOR

        return object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)

                function.findRoute()?.let { route ->
                    function.reportAnyDuplicates(route) { findPages() }
                    function.reportAnyPublicFileConflict(route)
                }

                function.findApiRoute()?.let { route ->
                    function.reportAnyDuplicates(route) { findApiDefinitions().apis }
                }
            }

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                // Analysis is expensive, so skip properties that obviously aren't streams
                if (!property.isTopLevel || !property.mightBeApiStream) return
                val route = analyze(property) { property.findApiStreamRoute() } ?: return

                property.reportAnyDuplicates(route) { findApiDefinitions().streams }
            }

            /**
             * Reports this declaration if any declarations besides it are registered at its [route], which Kobweb
             * fails the build for.
             *
             * @param findDefinitions Returns the declarations that might be registered at the same route, e.g. all the
             *   pages this declaration can see (which may include this one).
             */
            private fun KtCallableDeclaration.reportAnyDuplicates(
                route: Route,
                findDefinitions: KtCallableDeclaration.() -> List<RouteDefinition>,
            ) {
                val ownFqn = fqName?.asString() ?: return
                val others = findDefinitions()
                    .filter { definition -> definition.route == route && definition.fqn != ownFqn }
                if (others.isEmpty()) return

                val messageKey = when (route.kind) {
                    RouteKind.PAGE -> "inspection.conflicting.routes.page.display.text"
                    RouteKind.API -> "inspection.conflicting.routes.api.display.text"
                    RouteKind.STREAM -> "inspection.conflicting.routes.stream.display.text"
                }
                holder.registerProblem(
                    nameIdentifier ?: this,
                    UxGlobals.textBundle.message(
                        messageKey,
                        route.displayValue,
                        others.joinToString { definition -> "`${definition.fqn}`" },
                    ),
                )
            }

            /**
             * Reports this page if a public file is served at its route too. The site still builds, but the server
             * serves public files before falling back to the site's pages, so visiting the URL directly shows the file,
             * while Kobweb's router shows the page when navigating within the site.
             */
            private fun KtNamedFunction.reportAnyPublicFileConflict(route: PageRoute) {
                // Only static routes are a single path that can be compared with a file. Also, the server only serves
                // files, never folders, e.g. not "public/blog" for "/blog/".
                if (route.hasDynamicSegments || route.value.endsWith('/')) return
                val relativePath = route.value.removePrefix("/")
                val hasConflict = findPublicFolders().any { folder ->
                    folder.findFileByRelativePath(relativePath)?.isDirectory == false
                }
                if (!hasConflict) return

                holder.registerProblem(
                    nameIdentifier ?: this,
                    UxGlobals.textBundle.message("inspection.conflicting.routes.public.file.display.text", route.value),
                    ProblemHighlightType.WARNING,
                )
            }
        }
    }
}
