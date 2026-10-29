package com.varabyte.kobweb.intellij.completion.route

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.varabyte.kobweb.intellij.util.kobweb.route.DynamicRouteSegment

/**
 * Replaces an inserted path with a template whose variables are its dynamic segments, e.g. "/users/{user}/posts"
 * becomes "/users/user/posts" with "user" selected, ready to be typed over.
 *
 * This works for paths with or without a leading slash, e.g. "users/{user}" for an API path.
 */
internal object DynamicSegmentsInsertHandler : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        val path = item.lookupString
        context.document.deleteString(context.startOffset, context.tailOffset)
        context.editor.caretModel.moveToOffset(context.startOffset)

        val templateManager = TemplateManager.getInstance(context.project)
        val template = templateManager.createTemplate("", "").apply {
            path.split('/').forEachIndexed { i, segment ->
                if (i > 0) addTextSegment("/")
                val dynamicSegment = DynamicRouteSegment.tryCreate(segment)
                if (dynamicSegment == null) {
                    // e.g. the empty segment before a route's leading slash
                    if (segment.isNotEmpty()) addTextSegment(segment)
                } else {
                    addVariable("segment$i", ConstantNode(dynamicSegment.name), true)
                }
            }
        }
        templateManager.startTemplate(context.editor, template)
    }
}
