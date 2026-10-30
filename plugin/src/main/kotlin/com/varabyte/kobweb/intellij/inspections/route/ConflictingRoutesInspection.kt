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
import com.varabyte.kobweb.intellij.util.kobweb.route.RouteDefinition
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
                    function.reportAnyDuplicates("inspection.conflicting.routes.page.display.text", route.value) {
                        findPages().filter { page -> page.route == route }
                    }
                    function.reportAnyPublicFileConflict(route)
                }

                function.findApiRoute()?.let { route ->
                    function.reportAnyDuplicates(
                        "inspection.conflicting.routes.api.display.text",
                        "/api${route.value}",
                    ) {
                        findApiDefinitions().apis.filter { api -> api.route == route }
                    }
                }
            }

            override fun visitProperty(property: KtProperty) {
                super.visitProperty(property)
                // Analysis is expensive, so skip properties that obviously aren't streams
                if (!property.isTopLevel || !property.mightBeApiStream) return
                val route = analyze(property) { property.findApiStreamRoute() } ?: return

                property.reportAnyDuplicates(
                    "inspection.conflicting.routes.stream.display.text",
                    // Streams are connected to without a leading slash, e.g. `ApiStream("chat")`
                    route.value.removePrefix("/"),
                ) {
                    findApiDefinitions().streams.filter { stream -> stream.route == route }
                }
            }

            /**
             * Reports this declaration if any declarations besides it are registered at its route, which Kobweb fails
             * the build for.
             *
             * @param displayRoute The route as it should be shown to the user, e.g. with "/api" prepended.
             *
             * @param findDefinitionsAtRoute Returns the declarations registered at this declaration's route, which may
             *   include it.
             */
            private fun KtCallableDeclaration.reportAnyDuplicates(
                messageKey: String,
                displayRoute: String,
                findDefinitionsAtRoute: KtCallableDeclaration.() -> List<RouteDefinition>,
            ) {
                val ownFqn = fqName?.asString() ?: return
                val others = findDefinitionsAtRoute().filter { definition -> definition.fqn != ownFqn }
                if (others.isEmpty()) return

                holder.registerProblem(
                    nameIdentifier ?: this,
                    UxGlobals.textBundle.message(
                        messageKey,
                        displayRoute,
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
