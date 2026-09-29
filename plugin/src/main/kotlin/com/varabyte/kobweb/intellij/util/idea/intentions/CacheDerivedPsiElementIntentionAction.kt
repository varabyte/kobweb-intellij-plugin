package com.varabyte.kobweb.intellij.util.idea.intentions

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.PsiElement
import com.varabyte.kobweb.intellij.util.ux.UxGlobals
import javax.swing.SwingUtilities
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * A helper base class for intentions that create an expensive derivative element based on the source element.
 *
 * This can be especially helpful since [isAvailable] is always called before [invoke] and we should be able to share
 * work here.
 *
 * The value will be cached only until the intention is invoked.
 *
 * @param cacheKey A key which will be used to store the intermediate result of this intention action. Use
 *   `val YOUR_CACHE_KEY by CacheDerivedPsiElementIntentionAction.key<SomePsiElementType>()` to create it. Ideally the
 *   variable name you use for your key will be globally unique within your plugin.
 */
abstract class CacheDerivedPsiElementIntentionAction<E: PsiElement>(private val cacheKey: Key<Pair<PsiElement, E>>) : PsiElementBaseIntentionAction() {
    companion object {
        fun <E: PsiElement> key(): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Key<Pair<PsiElement, E>>>> {
            return PropertyDelegateProvider { _, property ->
                val createdKey = Key.create<Pair<PsiElement, E>>(property.name)
                ReadOnlyProperty { _, _ -> createdKey }
            }
        }
    }

    final override fun getFamilyName(): String = UxGlobals.FAMILY_NAME
    final override fun startInWriteAction(): Boolean = false

    /**
     * Attempt to create the derivative element.
     *
     * If it can't be created, then this means the action will be treated as unavailable.
     */
    protected abstract fun PsiElement.tryDerivingElement(): E?
    protected abstract fun handleElementIsInvoked(editor: Editor, element: E)

    open fun handleElementIsAvailable(element: E) {}

    private fun getCachedDerivedElement(editor: Editor, sourceElement: PsiElement): E? {
        val (keyElement, cachedElement) = editor.getUserData(cacheKey) ?: return null

        if (keyElement != sourceElement) {
            editor.removeUserData(cacheKey)
            return null
        }

        return cachedElement
    }

    // Never create on the EDT!
    private fun getCachedDerivedElementOrCreateIfPossible(editor: Editor, sourceElement: PsiElement): E? {
        getCachedDerivedElement(editor, sourceElement)?.let { return it }
        if (SwingUtilities.isEventDispatchThread()) {
            return null
        }

        return sourceElement.tryDerivingElement()
            .also { derivedElement ->
                if (derivedElement != null) {
                    editor.putUserData(cacheKey, sourceElement to derivedElement)
                } else {
                    editor.removeUserData(cacheKey)
                }
            }
    }

    final override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        val derived = getCachedDerivedElementOrCreateIfPossible(editor, element)
        derived?.let { handleElementIsAvailable(it) }

        return (derived != null)
    }

    final override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val derived = getCachedDerivedElement(editor, element)
        derived?.let { handleElementIsInvoked(editor, it) }
        editor.removeUserData(cacheKey)
    }
}