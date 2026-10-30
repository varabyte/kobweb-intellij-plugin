package com.varabyte.kobweb.intellij.references.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.varabyte.kobweb.intellij.util.kobweb.api.ApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiDefinitions
import com.varabyte.kobweb.intellij.util.kobweb.api.findApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.mayHaveApiPathTarget
import com.varabyte.kobweb.intellij.util.kobweb.api.path
import com.varabyte.kobweb.intellij.util.kobweb.route.LinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.linkPathRange
import com.varabyte.kobweb.intellij.util.kobweb.route.parseLinkPath
import com.varabyte.kobweb.intellij.util.kobweb.route.pickMostSpecific
import com.varabyte.kobweb.intellij.util.psi.unescapedText
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

/**
 * A reference from a string that holds an API path, e.g. the `"users/bob"` in `window.api.get("users/bob")`, to the
 * `@Api` function it leads to, or from a string that holds an API stream path, e.g. the `"chat"` in
 * `ApiStream("chat")`, to the `ApiStream` property.
 *
 * API paths are matched like Kobweb's backend does: relative to "/api/" (so a leading slash makes no difference),
 * without any query parameters, and with static segments taking priority over dynamic ones. Segments that aren't known
 * until runtime (e.g. the `$id` in `"users/$id"`) only match dynamic segments (see [LinkPath.matches]).
 *
 * Stream paths must match a stream's route exactly (again, a leading slash makes no difference), so ones with segments
 * that aren't known until runtime don't resolve.
 *
 * This reference is created for strings whose use suggests they might hold an API path (see [mayHaveApiPathTarget]),
 * but it only resolves if they actually do (see [findApiPathTarget]), which requires Kotlin analysis to check.
 *
 * Note that an endpoint's route comes from its file and package, not its declaration's name, so renaming an `@Api`
 * function or `ApiStream` property doesn't change any strings.
 */
class ApiPathReference(element: KtStringTemplateExpression) : PsiReferenceBase<KtStringTemplateExpression>(
    element,
    element.linkPathRange(),
    // Soft, as paths that don't lead to any endpoint aren't necessarily errors (e.g. ones served by a library we can't
    // see into)
    /* soft = */ true,
) {
    override fun resolve(): PsiElement? {
        return ResolveCache.getInstance(element.project).resolveWithCaching(
            this,
            { reference, _ -> reference.doResolve() },
            /* needToPreventRecursion = */ false,
            /* incompleteCode = */ false,
        )
    }

    private fun doResolve(): PsiElement? {
        val target = analyze(element) { element.findApiPathTarget() } ?: return null
        val definitions = element.findApiDefinitions()

        return when (target.kind) {
            ApiPathTarget.Kind.API -> {
                val path = element.parseLinkPath(allowRelativePath = true) ?: return null
                definitions.apis
                    .filter { api -> path.matches(api.route) }
                    .pickMostSpecific { api -> api.route }
            }

            ApiPathTarget.Kind.STREAM -> {
                if (element.hasInterpolation()) return null
                val path = element.unescapedText.removePrefix("/")
                definitions.streams.firstOrNull { stream -> stream.route.path == path }
            }
        }?.declaration
    }

    // Renaming an endpoint's declaration doesn't change its route -- it is derived from the file name! So leave the
    // string alone
    override fun handleElementRename(newElementName: String): PsiElement = element

    override fun bindToElement(element: PsiElement): PsiElement = this.element
}
