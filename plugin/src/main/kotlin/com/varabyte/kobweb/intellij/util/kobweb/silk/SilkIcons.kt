package com.varabyte.kobweb.intellij.util.kobweb.silk

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

/**
 * @property id The style's ID in icon preview data, which matches the prefix Kobweb uses in its icon list files.
 * @property enumEntry The name of the style enum entry a user passes in to select this style, if any.
 */
class SilkIconStyle(val id: String, val displayName: String, val enumEntry: String?)

/**
 * The icon sets provided by Kobweb's `silk-icons-*` modules.
 *
 * @property id The icon set's ID in icon preview data. This intentionally matches Kobweb's own naming for the set (e.g.
 *   `silk-icons-fa`, the `...icons.fa` package, and the `Fa` function prefix), which [packageName] and [functionPrefix]
 *   are derived from.
 * @property styles Ordered by preference, used when a call doesn't specify a style (or specifies one the icon doesn't
 *   support). This mirrors the defaults of the functions Kobweb generates.
 * @property helperFunctions Functions in the icon package that share [functionPrefix] but aren't icons themselves.
 */
enum class SilkIconSet(
    val id: String,
    val displayName: String,
    val styles: List<SilkIconStyle>,
    val helperFunctions: Set<String> = emptySet(),
) {
    FONT_AWESOME(
        "fa", "Font Awesome",
        listOf(
            // Icons that come in both solid and regular variants default to `IconStyle.OUTLINE`
            SilkIconStyle("far", "regular", "OUTLINE"),
            SilkIconStyle("fas", "solid", "FILLED"),
            SilkIconStyle("fab", "brands", null),
        ),
        helperFunctions = setOf("FaIcon"),
    ),
    MATERIAL_DESIGN_ICONS(
        "mdi", "Material Design Icons",
        listOf(
            SilkIconStyle("mdi", "filled", "FILLED"),
            SilkIconStyle("mdio", "outlined", "OUTLINED"),
            SilkIconStyle("mdir", "rounded", "ROUNDED"),
            SilkIconStyle("mdis", "sharp", "SHARP"),
            SilkIconStyle("mdit", "two-toned", "TWO_TONED"),
        ),
    ),
    MATERIAL_SYMBOLS(
        "ms", "Material Symbols",
        listOf(
            SilkIconStyle("mso", "outlined", "OUTLINED"),
            SilkIconStyle("msr", "rounded", "ROUNDED"),
            SilkIconStyle("mss", "sharp", "SHARP"),
        ),
        helperFunctions = setOf("MsIcon"),
    ),
    LUCIDE(
        "lucide", "Lucide",
        listOf(SilkIconStyle("lucide", "default", null)),
    );

    val packageName = FqName("com.varabyte.kobweb.silk.components.icons.$id")
    val functionPrefix = id.replaceFirstChar { it.uppercase() }
}

/**
 * A call to one of the icon functions Kobweb generates, e.g. `FaHouse(style = IconStyle.FILLED)`.
 *
 * @property key The icon's name lowercased and without separators, e.g. "printshop" for `FaPrintShop`. Kobweb's codegen
 *   guarantees these are unique within an icon set.
 * @property requestedStyle The style explicitly passed into the call, if any (and if we could figure out which).
 */
class SilkIconCall(val iconSet: SilkIconSet, val key: String, val requestedStyle: SilkIconStyle?) {
    companion object {
        /** Normalize an icon name (e.g. "print-shop" or "print_shop") into a [key]. */
        fun keyFor(name: String) = name.filter { it.isLetterOrDigit() }.lowercase()
    }
}

/**
 * Returns information about this call if it invokes one of Kobweb's generated icon functions.
 *
 * This function should be called inside an [org.jetbrains.kotlin.analysis.api.analyze] block.
 */
context(kaSession: KaSession)
fun KtCallExpression.asSilkIconCall(): SilkIconCall? = with(kaSession) {
    val call = resolveToCall()?.successfulFunctionCallOrNull() ?: return null
    val callableId = call.partiallyAppliedSymbol.signature.symbol.callableId ?: return null
    if (callableId.classId != null) return null

    val iconSet = SilkIconSet.entries.find { it.packageName == callableId.packageName } ?: return null
    val functionName = callableId.callableName.asString()
    if (!functionName.startsWith(iconSet.functionPrefix) || functionName in iconSet.helperFunctions) return null

    val styleArg = call.argumentMapping.entries.find { (_, param) -> param.symbol.name.asString() == "style" }?.key
    val requestedStyle = styleArg?.let { arg ->
        val nameRef = ((arg as? KtQualifiedExpression)?.selectorExpression ?: arg) as? KtNameReferenceExpression
        val enumEntry = nameRef?.mainReference?.resolveToSymbol() as? KaEnumEntrySymbol
        enumEntry?.let { entry -> iconSet.styles.find { it.enumEntry == entry.name.asString() } }
    }

    SilkIconCall(iconSet, SilkIconCall.keyFor(functionName.removePrefix(iconSet.functionPrefix)), requestedStyle)
}
