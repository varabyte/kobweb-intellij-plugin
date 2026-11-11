package com.varabyte.kobweb.intellij.util.kobweb.silk

import com.intellij.openapi.roots.ProjectFileIndex
import com.varabyte.kobweb.icons.model.IconSet
import com.varabyte.kobweb.icons.model.fa.FaIconSet
import com.varabyte.kobweb.icons.model.lucide.LucideIconSet
import com.varabyte.kobweb.icons.model.material.MdiIconSet
import com.varabyte.kobweb.icons.model.material.MsIconSet
import com.varabyte.kobweb.intellij.iconpreviews.previewId
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
class SilkIconStyle private constructor(
    val id: String,
    private val iconPageUrl: (iconName: String) -> String,
    val enumEntries: Set<String>,
) {
    constructor(modelStyle: FaIconSet.Style, vararg enumEntries: String) :
        this(modelStyle.previewId, modelStyle::iconPageUrl, enumEntries.toSet())

    constructor(modelStyle: MdiIconSet.Style, vararg enumEntries: String) :
        this(modelStyle.previewId, modelStyle::iconPageUrl, enumEntries.toSet())

    constructor(modelStyle: MsIconSet.Style, vararg enumEntries: String) :
        this(modelStyle.previewId, modelStyle::iconPageUrl, enumEntries.toSet())

    /** A link to the icon's page on its project's website, e.g. so the user can see it in more detail. */
    fun browserUrl(iconName: String): String = iconPageUrl(iconName)

    companion object {
        /**
         * A stand-in style for an icon set without styles of its own (e.g. Lucide), so it can be handled like any other
         * set.
         *
         * @param id The style's ID in icon preview data.
         * @param iconPageUrl The icon's page on its project's website.
         */
        fun unstyled(id: String, iconPageUrl: (iconName: String) -> String) =
            SilkIconStyle(id, iconPageUrl, emptySet())
    }
}

private fun FaIconSet.Style.iconPageUrl(iconName: String): String = when (this) {
    FaIconSet.Style.BRANDS -> "https://fontawesome.com/icons/$iconName?f=brands&s=solid"
    FaIconSet.Style.SOLID, FaIconSet.Style.REGULAR -> "https://fontawesome.com/icons/$iconName?f=classic&s=$styleName"
}

// Selecting the icon (via its font family's name) opens its details in a side panel, and the style picks the matching
// tab. These mirror the links Google Fonts itself creates.

private fun MdiIconSet.Style.iconPageUrl(iconName: String): String {
    val tab = when (this) {
        MdiIconSet.Style.FILLED -> "Filled"
        MdiIconSet.Style.OUTLINED -> "Outlined"
        MdiIconSet.Style.ROUNDED -> "Rounded"
        MdiIconSet.Style.SHARP -> "Sharp"
        MdiIconSet.Style.TWO_TONED -> "Two tone"
    }
    return googleFontsIconUrl("Material Icons", fontFamily, tab, iconName)
}

private fun MsIconSet.Style.iconPageUrl(iconName: String): String {
    val tab = when (this) {
        MsIconSet.Style.OUTLINED -> "Outlined"
        MsIconSet.Style.ROUNDED -> "Rounded"
        MsIconSet.Style.SHARP -> "Sharp"
    }
    return googleFontsIconUrl("Material Symbols", fontFamily, tab, iconName)
}

/**
 * @param iconSet The icon set, as Google Fonts names it, e.g. "Material Icons".
 * @param family The font family for the style, e.g. "Material Icons Outlined".
 * @param tab The style's tab, as Google Fonts names it, e.g. "Outlined".
 */
private fun googleFontsIconUrl(iconSet: String, family: String, tab: String, iconName: String): String {
    fun String.encoded() = replace(' ', '+')
    return "https://fonts.google.com/icons?icon.set=${iconSet.encoded()}&icon.query=$iconName" +
        "&selected=${family.encoded()}:$iconName:&icon.style=${tab.encoded()}"
}

/**
 * The icon sets provided by Kobweb's `silk-icons-*` modules.
 *
 * @property id The icon set's ID in icon preview data. This intentionally matches Kobweb's own naming for the set (e.g.
 *   `silk-icons-fa` and the `...icons.fa` package), which [packageName] is derived from.
 * @property model The upstream icon set, which Kobweb's codegen names the icon functions after.
 * @property styles Ordered by preference, used when a call doesn't specify a style (or specifies one the icon doesn't
 *   support). This mirrors the defaults of the functions Kobweb generates.
 * @property helperFunctions Functions in the icon package that share [functionPrefix] but aren't icons themselves.
 */
enum class SilkIconSet(
    val id: String,
    val model: IconSet,
    val styles: List<SilkIconStyle>,
    val helperFunctions: Set<String> = emptySet(),
) {
    FONT_AWESOME(
        "fa", FaIconSet,
        listOf(
            // Icons that come in both solid and regular variants default to regular. Kobweb 0.25.2 renamed the style
            // enum's entries to match Font Awesome's own names for them.
            SilkIconStyle(FaIconSet.Style.REGULAR, "REGULAR", "OUTLINE"),
            SilkIconStyle(FaIconSet.Style.SOLID, "SOLID", "FILLED"),
            SilkIconStyle(FaIconSet.Style.BRANDS),
        ),
        helperFunctions = setOf("FaIcon", "FaBrandIcon"),
    ),
    MATERIAL_ICONS(
        "mdi", MdiIconSet,
        listOf(
            SilkIconStyle(MdiIconSet.Style.FILLED, "FILLED"),
            SilkIconStyle(MdiIconSet.Style.OUTLINED, "OUTLINED"),
            SilkIconStyle(MdiIconSet.Style.ROUNDED, "ROUNDED"),
            SilkIconStyle(MdiIconSet.Style.SHARP, "SHARP"),
            SilkIconStyle(MdiIconSet.Style.TWO_TONED, "TWO_TONED"),
        ),
        helperFunctions = setOf("MdiIcon"),
    ),
    MATERIAL_SYMBOLS(
        "ms", MsIconSet,
        listOf(
            SilkIconStyle(MsIconSet.Style.OUTLINED, "OUTLINED"),
            SilkIconStyle(MsIconSet.Style.ROUNDED, "ROUNDED"),
            SilkIconStyle(MsIconSet.Style.SHARP, "SHARP"),
        ),
        helperFunctions = setOf("MsIcon"),
    ),
    LUCIDE(
        "lucide", LucideIconSet,
        listOf(SilkIconStyle.unstyled("lucide") { iconName -> "https://lucide.dev/icons/$iconName" }),
    );

    val displayName get() = model.displayName
    val packageName = FqName("com.varabyte.kobweb.silk.components.icons.$id")
    val functionPrefix get() = model.methodNamer.prefix
}

/**
 * A call to one of the icon functions Kobweb generates, e.g. `FaHouse(style = FaIconStyle.SOLID)`.
 *
 * @property functionName The name of the called function, e.g. "FaPrintShop".
 * @property styleArgument What, if anything, was passed in for the `style` parameter.
 * @property styleEnum The enum the called function's `style` parameter takes, or null if it doesn't have one
 *   (Kobweb only generates one for icons that come in more than one style). This comes from the function itself rather
 *   than being hardcoded, since it can differ across Kobweb versions (e.g. Material Symbols' `MsIconStyle` was later
 *   renamed to `IconStyle`).
 * @property parameterNames The name of the parameter each of the call's arguments is passed into.
 */
class SilkIconCall(
    val iconSet: SilkIconSet,
    val functionName: String,
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
        functionName,
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
