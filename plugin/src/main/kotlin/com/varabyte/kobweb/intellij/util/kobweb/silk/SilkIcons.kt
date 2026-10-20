package com.varabyte.kobweb.intellij.util.kobweb.silk

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * @property id The style's ID in icon preview data, which matches the prefix Kobweb uses in its icon list files.
 * @property enumEntry The name of the style enum entry a user passes in to select this style, if any.
 */
class SilkIconStyle(val id: String, val enumEntry: String?) {
    /**
     * What the UI calls this style, derived from [enumEntry] since that's what users write in code (e.g. "outline" for
     * `IconStyle.OUTLINE`).
     *
     * This is null for styles users can't choose, as the UI never needs to name them.
     */
    val displayName: String? get() = enumEntry?.lowercase()?.replace('_', '-')
}

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
            SilkIconStyle("far", "OUTLINE"),
            SilkIconStyle("fas", "FILLED"),
            SilkIconStyle("fab", null),
        ),
        helperFunctions = setOf("FaIcon"),
    ),
    MATERIAL_DESIGN_ICONS(
        "mdi", "Material Design Icons",
        listOf(
            SilkIconStyle("mdi", "FILLED"),
            SilkIconStyle("mdio", "OUTLINED"),
            SilkIconStyle("mdir", "ROUNDED"),
            SilkIconStyle("mdis", "SHARP"),
            SilkIconStyle("mdit", "TWO_TONED"),
        ),
    ),
    MATERIAL_SYMBOLS(
        "ms", "Material Symbols",
        listOf(
            SilkIconStyle("mso", "OUTLINED"),
            SilkIconStyle("msr", "ROUNDED"),
            SilkIconStyle("mss", "SHARP"),
        ),
        helperFunctions = setOf("MsIcon"),
    ),
    LUCIDE(
        "lucide", "Lucide",
        listOf(SilkIconStyle("lucide", null)),
    );

    val packageName = FqName("com.varabyte.kobweb.silk.components.icons.$id")
    val functionPrefix = id.replaceFirstChar { it.uppercase() }

    /** A link to the icon's page on its project's website, e.g. so the user can see it in more detail. */
    fun browserUrl(iconName: String, style: SilkIconStyle): String = when (this) {
        FONT_AWESOME -> when (style.id) {
            "fab" -> "https://fontawesome.com/icons/$iconName?f=brands&s=solid"
            "far" -> "https://fontawesome.com/icons/$iconName?f=classic&s=regular"
            else -> "https://fontawesome.com/icons/$iconName?f=classic&s=solid"
        }
        // Selecting the icon (via its font family's name) opens its details in a side panel, and the style picks the
        // matching tab. These mirror the links Google Fonts itself creates.
        MATERIAL_DESIGN_ICONS -> when (style.id) {
            "mdio" -> googleFontsIconUrl("Material Icons", "Material Icons Outlined", "Outlined", iconName)
            "mdir" -> googleFontsIconUrl("Material Icons", "Material Icons Round", "Rounded", iconName)
            "mdis" -> googleFontsIconUrl("Material Icons", "Material Icons Sharp", "Sharp", iconName)
            "mdit" -> googleFontsIconUrl("Material Icons", "Material Icons Two Tone", "Two tone", iconName)
            else -> googleFontsIconUrl("Material Icons", "Material Icons", "Filled", iconName)
        }
        MATERIAL_SYMBOLS -> when (style.id) {
            "msr" -> googleFontsIconUrl("Material Symbols", "Material Symbols Rounded", "Rounded", iconName)
            "mss" -> googleFontsIconUrl("Material Symbols", "Material Symbols Sharp", "Sharp", iconName)
            else -> googleFontsIconUrl("Material Symbols", "Material Symbols Outlined", "Outlined", iconName)
        }
        LUCIDE -> "https://lucide.dev/icons/$iconName"
    }

    /**
     * @param iconSet The icon set, as Google Fonts names it, e.g. "Material Icons".
     * @param family The font family for the style, e.g. "Material Icons Outlined".
     * @param style The style, as Google Fonts names it, e.g. "Outlined".
     */
    private fun googleFontsIconUrl(iconSet: String, family: String, style: String, iconName: String): String {
        fun String.encoded() = replace(' ', '+')
        return "https://fonts.google.com/icons?icon.set=${iconSet.encoded()}&icon.query=$iconName" +
            "&selected=${family.encoded()}:$iconName:&icon.style=${style.encoded()}"
    }
}

/**
 * A call to one of the icon functions Kobweb generates, e.g. `FaHouse(style = IconStyle.FILLED)`.
 *
 * @property key The icon's name lowercased and without separators, e.g. "printshop" for `FaPrintShop`. Kobweb's codegen
 *   guarantees these are unique within an icon set.
 * @property styleArgument What, if anything, was passed in for the `style` parameter.
 * @property styleEnum The enum class the called function's `style` parameter takes, or null if it doesn't have one
 *   (Kobweb only generates one for icons that come in more than one style). This comes from the function itself rather
 *   than being hardcoded, since it can differ across Kobweb versions (e.g. Material Symbols' `MsIconStyle` was later
 *   renamed to `IconStyle`).
 * @property parameterNames The name of the parameter each of the call's arguments is passed into.
 */
class SilkIconCall(
    val iconSet: SilkIconSet,
    val key: String,
    val styleArgument: StyleArgument,
    val styleEnum: FqName?,
    val parameterNames: Map<KtValueArgument, Name>,
) {
    sealed interface StyleArgument {
        /** No style was passed in, so the function's default is used, e.g. `FaHouse()`. */
        data object Absent : StyleArgument

        /** A style was passed in, e.g. `FaHouse(style = IconStyle.FILLED)` or `FaHouse(style = myStyle)`. */
        sealed interface Present : StyleArgument {
            val argument: KtValueArgument
        }

        /** A style enum entry was passed in directly, e.g. `FaHouse(style = IconStyle.FILLED)`. */
        class Direct(override val argument: KtValueArgument, val style: SilkIconStyle) : Present

        /** A style was passed in indirectly, via an expression we can't evaluate, e.g. `FaHouse(style = myStyle)`. */
        class Indirect(override val argument: KtValueArgument) : Present
    }

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

    val styleParameter = call.partiallyAppliedSymbol.signature.valueParameters.find { it.name.asString() == "style" }
    // A type alias (e.g. a deprecated old name for the enum) is already expanded to the class it stands for here
    val styleEnum = (styleParameter?.returnType as? KaClassType)?.classId?.asSingleFqName()
    val styleExpression =
        call.argumentMapping.entries.find { (_, param) -> param.symbol.name.asString() == "style" }?.key
    val styleArgument = if (styleExpression == null) {
        SilkIconCall.StyleArgument.Absent
    } else {
        val argument = styleExpression.parent as? KtValueArgument ?: return null
        val nameRef = ((styleExpression as? KtQualifiedExpression)?.selectorExpression ?: styleExpression)
            as? KtNameReferenceExpression
        val enumEntry = nameRef?.mainReference?.resolveToSymbol() as? KaEnumEntrySymbol
        val style = enumEntry?.let { entry -> iconSet.styles.find { it.enumEntry == entry.name.asString() } }
        if (style != null) {
            SilkIconCall.StyleArgument.Direct(argument, style)
        } else {
            SilkIconCall.StyleArgument.Indirect(argument)
        }
    }

    SilkIconCall(
        iconSet,
        SilkIconCall.keyFor(functionName.removePrefix(iconSet.functionPrefix)),
        styleArgument,
        styleEnum,
        parameterNames = call.argumentMapping.entries
            .mapNotNull { (expression, param) -> (expression.parent as? KtValueArgument)?.let { it to param.name } }
            .toMap(),
    )
}
