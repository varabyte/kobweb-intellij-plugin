package com.varabyte.kobweb.intellij.util.kobweb.silk

import com.intellij.openapi.roots.ProjectFileIndex
import com.varabyte.kobweb.intellij.util.kobweb.KOBWEB_REPO_URL
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaEnumEntrySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgument

/**
 * @property id The style's ID in icon preview data, e.g. "fas" or "mdio".
 * @property enumEntries The names of the style enum entries a user passes in to select this style, if any. There can
 *   be more than one because Kobweb has renamed some across versions (e.g. Font Awesome's `OUTLINE` became `REGULAR`).
 */
class SilkIconStyle(val id: String, vararg enumEntries: String) {
    val enumEntries: Set<String> = enumEntries.toSet()
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
            // Icons that come in both solid and regular variants default to regular. Kobweb 0.25.2 renamed the style
            // enum's entries to match Font Awesome's own names for them.
            SilkIconStyle("far", "REGULAR", "OUTLINE"),
            SilkIconStyle("fas", "SOLID", "FILLED"),
            SilkIconStyle("fab"),
        ),
        helperFunctions = setOf("FaIcon", "FaBrandIcon"),
    ),
    MATERIAL_ICONS(
        "mdi", "Material Icons",
        listOf(
            SilkIconStyle("mdi", "FILLED"),
            SilkIconStyle("mdio", "OUTLINED"),
            SilkIconStyle("mdir", "ROUNDED"),
            SilkIconStyle("mdis", "SHARP"),
            SilkIconStyle("mdit", "TWO_TONED"),
        ),
        helperFunctions = setOf("MdiIcon"),
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
        listOf(SilkIconStyle("lucide")),
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
        MATERIAL_ICONS -> when (style.id) {
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
 * A call to one of the icon functions Kobweb generates, e.g. `FaHouse(style = FaIconStyle.SOLID)`.
 *
 * @property key The icon's name lowercased and without separators, e.g. "printshop" for `FaPrintShop`. Kobweb's codegen
 *   guarantees these are unique within an icon set.
 * @property styleArgument What, if anything, was passed in for the `style` parameter.
 * @property styleEnum The enum the called function's `style` parameter takes, or null if it doesn't have one
 *   (Kobweb only generates one for icons that come in more than one style). This comes from the function itself rather
 *   than being hardcoded, since it can differ across Kobweb versions (e.g. Material Symbols' `MsIconStyle` was later
 *   renamed to `IconStyle`).
 * @property parameterNames The name of the parameter each of the call's arguments is passed into.
 */
class SilkIconCall(
    val iconSet: SilkIconSet,
    val key: String,
    val styleArgument: StyleArgument,
    val styleEnum: StyleEnum?,
    val parameterNames: Map<KtValueArgument, Name>,
) {
    /**
     * @property fqName The enum class's fully qualified name.
     * @property entryNames The names of the enum's entries.
     */
    class StyleEnum(val fqName: FqName, val entryNames: Set<String>) {
        /** The name of the entry that selects [style], or null if this enum has none. */
        fun entryFor(style: SilkIconStyle): String? = style.enumEntries.find { it in entryNames }

        /**
         * What the UI calls [style], derived from its entry in this enum since that's what users write in code (e.g.
         * "regular" for `FaIconStyle.REGULAR`).
         */
        fun displayNameFor(style: SilkIconStyle): String? = entryFor(style)?.lowercase()?.replace('_', '-')
    }

    sealed interface StyleArgument {
        /** No style was passed in, so the function's default is used, e.g. `FaHouse()`. */
        data object Absent : StyleArgument

        /** A style was passed in, e.g. `FaHouse(style = FaIconStyle.SOLID)` or `FaHouse(style = myStyle)`. */
        sealed interface Present : StyleArgument {
            val argument: KtValueArgument
        }

        /** A style enum entry was passed in directly, e.g. `FaHouse(style = FaIconStyle.SOLID)`. */
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
    val callableId = call.symbol.callableId ?: return null
    if (callableId.classId != null) return null

    val iconSet = SilkIconSet.entries.find { it.packageName == callableId.packageName } ?: return null
    val functionName = callableId.callableName.asString()
    if (!functionName.startsWith(iconSet.functionPrefix) || functionName in iconSet.helperFunctions) return null

    val styleParameter = call.symbol.valueParameters.find { it.name.asString() == "style" }
    // A type alias (e.g. a deprecated old name for the enum) is already expanded to the class it stands for here
    val styleEnum = (styleParameter?.returnType as? KaClassType)?.let { type ->
        val classSymbol = type.expandedSymbol as? KaNamedClassSymbol ?: return@let null
        val entryNames = classSymbol.staticDeclaredMemberScope.callables
            .filterIsInstance<KaEnumEntrySymbol>()
            .mapTo(mutableSetOf()) { it.name.asString() }
        SilkIconCall.StyleEnum(type.classId.asSingleFqName(), entryNames)
    }
    val styleExpression =
        call.argumentMapping.entries.find { (_, param) -> param.symbol.name.asString() == "style" }?.key
    val styleArgument = if (styleExpression == null) {
        SilkIconCall.StyleArgument.Absent
    } else {
        val argument = styleExpression.parent as? KtValueArgument ?: return null
        val nameRef = ((styleExpression as? KtQualifiedExpression)?.selectorExpression ?: styleExpression)
            as? KtNameReferenceExpression
        val enumEntry = nameRef?.mainReference?.resolveToSymbol() as? KaEnumEntrySymbol
        val style = enumEntry?.let { entry -> iconSet.styles.find { entry.name.asString() in it.enumEntries } }
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

/** The package of the SVG icons Silk defines in code (e.g. `ArrowBackIcon`), as opposed to its icon font modules. */
private val SILK_SVG_ICONS_PACKAGE = FqName("com.varabyte.kobweb.silk.components.icons")

/** The package of Kobweb's SVG composables, e.g. `Svg` and `Path`. */
private val KOBWEB_SVG_PACKAGE = FqName("com.varabyte.kobweb.compose.dom.svg")

/**
 * A call to an icon made with Silk's `createIcon` or Kobweb's `Svg`, or to `createIcon` or `Svg` itself.
 *
 * Icons are recognized by name (Kobweb's convention is that they end in "Icon", e.g. `ArrowBackIcon`), so that only
 * calls that look like icons need to be resolved.
 */
sealed interface SvgIconCall {
    /** A call to `createIcon` itself, which is where an icon is defined, e.g. `createIcon { Path { ... } }`. */
    data object CreateIcon : SvgIconCall

    /** A call to Kobweb's `Svg`, which can define an icon directly, e.g. `Svg(attrs = { ... }) { Path { ... } }`. */
    data object Svg : SvgIconCall

    /**
     * A call to an icon function, which is named after the icon, e.g. `ArrowBackIcon()`.
     *
     * @property function The icon's function, whose body defines the icon.
     */
    sealed class NamedIcon(val function: KtNamedFunction) : SvgIconCall {
        /** The icon's name, e.g. "ArrowBack" for `ArrowBackIcon`. */
        val name: String = function.name.orEmpty().removeSuffix("Icon")
    }

    /**
     * A call to one of Silk's icons, e.g. `ArrowBackIcon()`.
     *
     * Its [function] comes from Kobweb's sources when they're available, so its body can be read.
     */
    class SilkIcon(function: KtNamedFunction) : NamedIcon(function) {
        /** A link to the icon's source in the Kobweb repo, where each icon has its own file. */
        val sourceUrl: String =
            "$KOBWEB_REPO_URL/blob/main/frontend/silk-widgets/src/jsMain/kotlin/" +
                "${SILK_SVG_ICONS_PACKAGE.asString().replace('.', '/')}/${function.containingFile.name}"
    }

    /**
     * A call to a function in the user's own code, which may define an icon.
     *
     * It's only really an icon if its body can be read as one (see [readSvgIconShape]).
     */
    class UserIcon(function: KtNamedFunction) : NamedIcon(function)
}

/**
 * Returns information about this call if it's to an SVG icon (e.g. `ArrowBackIcon()`), or to `createIcon` or `Svg`.
 *
 * This resolves the call (which is expensive), so callers should first filter out calls that can't be icons (e.g. by
 * name) where they can.
 */
fun KtCallExpression.asSvgIconCall(): SvgIconCall? {
    val callee = calleeExpression as? KtNameReferenceExpression ?: return null
    val function = callee.mainReference.resolve() as? KtNamedFunction ?: return null
    // Silk declares `createIcon` and all of its icons as top-level functions in its icons package. (Calls to them can
    // be anywhere.)
    if (function.isTopLevel && function.containingKtFile.packageFqName == SILK_SVG_ICONS_PACKAGE) {
        if (function.name == "createIcon") return SvgIconCall.CreateIcon
        return SvgIconCall.SilkIcon(function.navigationElement as? KtNamedFunction ?: function)
    }
    if (function.isTopLevel && function.containingKtFile.packageFqName == KOBWEB_SVG_PACKAGE) {
        return if (function.name == "Svg") SvgIconCall.Svg else null
    }
    // Only the user's own code, not libraries, since those would need their sources loaded just to check
    val file = function.containingFile.virtualFile ?: return null
    if (!ProjectFileIndex.getInstance(function.project).isInSourceContent(file)) return null
    return SvgIconCall.UserIcon(function)
}
