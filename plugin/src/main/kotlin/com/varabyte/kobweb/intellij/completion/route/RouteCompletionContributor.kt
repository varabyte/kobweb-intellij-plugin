package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import com.varabyte.kobweb.intellij.util.kobweb.isInWritableKobwebProject
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment
import com.varabyte.kobweb.intellij.util.kobweb.route.PageDefinition
import com.varabyte.kobweb.intellij.util.kobweb.route.PageRoute
import com.varabyte.kobweb.intellij.util.kobweb.route.findPages
import com.varabyte.kobweb.intellij.util.kobweb.route.findPublicFolders
import com.varabyte.kobweb.intellij.util.kobweb.route.hasRoutePathTarget
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * Suggests the routes of pages, as well as public files, inside strings used with something that holds a route (see
 * [hasRoutePathTarget]), e.g. "/blog/post" while typing `Link("/bl")` or `ctx.route.path == "/bl"`.
 *
 * Routes with dynamic segments (e.g. "/users/{user}/posts") are inserted as a template, so that the value of each
 * dynamic segment can be typed in turn.
 */
class RouteCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(KtStringTemplateExpression::class.java),
            RouteCompletionProvider,
        )
    }
}

/**
 * A folder in a site's public folder that Kobweb generates files into.
 *
 * For example, Kobweb's self-hosting feature uses this folder to download and bundle files downloaded from the
 * internet.
 *
 * Even though they are in a public folder (in order for their functionality to be made available to the final site),
 * they should be treated as hidden as far as the user is concerned.
 */
private const val KOBWEB_PUBLIC_FOLDER = "_kobweb"

private object RouteCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val stringExpr = parameters.position.parentOfType<KtStringTemplateExpression>() ?: return
        val prefix = stringExpr.findRoutePrefix(parameters.offset) ?: return

        // During completion, `stringExpr` lives in a temporary copy of the file (with a dummy identifier inserted at
        // the caret). Map it back to the real file, so we can analyze it and look up which Kobweb project it belongs to.
        val originalStringExpr = CompletionUtil.getOriginalOrSelf(stringExpr)
        if (!originalStringExpr.isInWritableKobwebProject()) return
        if (!analyze(originalStringExpr) { originalStringExpr.hasRoutePathTarget() }) return

        // Match against everything typed so far in the string, not just the text after the last slash
        val routeResult = result.withPrefixMatcher(prefix)
        val pages = originalStringExpr.findPages().sortedBy { page -> page.route.value }
        pages.forEach { page ->
            routeResult.addElement(PrioritizedLookupElement.withPriority(page.toLookupElement(), 1.0))
        }

        // Public files come after pages, as sites can have lots of them (e.g. images)
        val routes = pages.mapTo(mutableSetOf()) { page -> page.route.value }
        for (folder in originalStringExpr.findPublicFolders()) {
            // Kobweb can generate lots of files under "_kobweb" (e.g. when self-hosting), which people rarely link to
            // by hand, so leave them out (although links to them are still valid)
            val kobwebFolder = folder.findChild(KOBWEB_PUBLIC_FOLDER)
            VfsUtilCore.iterateChildrenRecursively(folder, { file -> file != kobwebFolder }) { file ->
                if (!file.isDirectory) {
                    val route = "/" + VfsUtilCore.getRelativePath(file, folder)
                    // Skip duplicates, e.g. a file that both the site and one of its libraries have
                    if (routes.add(route)) {
                        routeResult.addElement(
                            LookupElementBuilder.create(file, route).withIcon(file.fileType.icon)
                        )
                    }
                }
                true
            }
        }

        // Otherwise, IntelliJ suggests the project's file paths too, which aren't routes
        result.stopHere()
    }

    private fun PageDefinition.toLookupElement(): LookupElement {
        val hasDynamicSegments = route.segments.any { segment -> DynamicRouteSegment.tryCreate(segment) != null }
        return LookupElementBuilder.create(function ?: fqn, route.value)
            .withTypeText(fqn.substringAfterLast('.'))
            .withIcon(KotlinIcons.FUNCTION)
            .withInsertHandler(if (hasDynamicSegments) DynamicSegmentsInsertHandler else null)
    }
}

/**
 * Replaces an inserted route with a template whose variables are its dynamic segments, e.g. "/users/{user}/posts"
 * becomes "/users/user/posts" with "user" selected, ready to be typed over.
 */
private object DynamicSegmentsInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val route = PageRoute(item.lookupString)
        context.document.deleteString(context.startOffset, context.tailOffset)
        context.editor.caretModel.moveToOffset(context.startOffset)

        val templateManager = TemplateManager.getInstance(context.project)
        val template = templateManager.createTemplate("", "").apply {
            isToReformat = false
            route.segments.forEachIndexed { i, segment ->
                addTextSegment("/")
                val dynamicSegment = DynamicRouteSegment.tryCreate(segment)
                if (dynamicSegment == null) {
                    addTextSegment(segment)
                } else {
                    addVariable("segment$i", ConstantNode(dynamicSegment.name), true)
                }
            }
        }
        templateManager.startTemplate(context.editor, template)
    }
}
