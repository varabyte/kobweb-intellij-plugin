package com.varabyte.kobweb.intellij.iconpreviews

import com.varabyte.kobweb.icons.model.fa.FaIconSet
import com.varabyte.kobweb.icons.model.lucide.LucideIconSet
import com.varabyte.kobweb.icons.model.mdi.MdiIconSet
import com.varabyte.kobweb.icons.model.ms.MsIconSet
import com.varabyte.kobweb.icons.parsers.fa.FaIcons
import com.varabyte.kobweb.icons.parsers.lucide.LucideIcons
import com.varabyte.kobweb.icons.parsers.mdi.GoogleCodepoints
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val options = args.toList().chunked(2).associate { (key, value) -> key.removePrefix("--") to value }
    val outDir = Path(options.getValue("out"))
    val sheetsDir = Path(options.getValue("sheets"))
    val downloader = Downloader(Path(options.getValue("cache")))

    // Every icon set comes straight from upstream, the same source Kobweb's codegen uses to create its `silk-icons-*`
    // modules. Sets with versioned releases use the newest release of each major version (one set per major version),
    // since releases within a major version only add icons; breaking changes like renamed icons, or new styles for
    // existing icons, only come with a new major version. That way, the data covers whichever version a project's
    // `silk-icons-*` dependency was created from.
    //
    // Material Icons and Material Symbols share a repo, which has no releases. Google Fonts serves their latest fonts
    // to every site, so using the repo's latest commit matches what Kobweb sites actually show.
    val material = GitHubRepo.resolve(MdiIconSet.gitHubRepo, "master")
    fun materialFile(path: String) = downloader.fetch(material.fileUrl(path))

    // Icons that upstream lists but we couldn't find a glyph for, collected across all icon sets
    val missing = mutableListOf<String>()

    /** @property source Where the icons came from, for the manifest, e.g. "Font Awesome 6.7.2 (FortAwesome/...)". */
    data class GeneratedIconSet(val iconSet: IconSet, val source: String)

    /** Font Awesome's newest release with this [major] version, as the "fa6" or "fa7" set. */
    fun fontAwesome(major: Int): GeneratedIconSet {
        val version = GitHubRepo.latestVersionTag(FaIconSet.gitHubRepo, major)
        val repo = GitHubRepo.resolve(FaIconSet.gitHubRepo, version)
        val icons = FaIcons.parse(downloader.fetchText(repo.fileUrl(FaIconSet.ICONS_JSON_PATH))).icons
        val codepoints = icons.mapValues { (_, icon) -> icon.codepoint }
        val styles = FaIconSet.styles.map { style ->
            val names = FaIconSet.methodNamer.dropSeparatorCollisions(icons.filterValues { style in it.styles }.keys)
            // Desktop fonts, since newer releases only ship web fonts as WOFF2, which Java can't read
            val font = downloader.fetch(repo.fileUrl(style.fontPath(major)))
            IconStyle(style.id, FontIcons.extract(font, names, codepoints, missing))
        }
        return GeneratedIconSet(IconSet("fa$major", styles), "Font Awesome $version ($repo)")
    }

    fun materialDesignIcons(): GeneratedIconSet {
        val styles = MdiIconSet.styles.map { style ->
            // Each font's codepoints file lists its icons, which Kobweb creates a function for each of
            val codepoints = GoogleCodepoints.parse(materialFile(style.codepointsPath).readText()).codepoints
            val names = MdiIconSet.methodNamer.dropSeparatorCollisions(codepoints.keys)
            IconStyle(style.id, FontIcons.extract(materialFile(style.fontPath), names, codepoints, missing))
        }
        return GeneratedIconSet(IconSet("mdi", styles), material.toString())
    }

    fun materialSymbols(): GeneratedIconSet {
        val styles = MsIconSet.styles.map { style ->
            // Each font's codepoints file lists its icons, which Kobweb creates a function for each of
            val codepoints = GoogleCodepoints.parse(materialFile(style.codepointsPath).readText()).codepoints
            val names = MsIconSet.methodNamer.dropSeparatorCollisions(codepoints.keys)
            // Variable fonts; Java2D renders their default instance, which matches the default axes Kobweb renders with
            IconStyle(style.id, FontIcons.extract(materialFile(style.fontPath), names, codepoints, missing))
        }
        return GeneratedIconSet(IconSet("ms", styles), material.toString())
    }

    /**
     * Lucide's newest 1.x release. When Lucide 2 comes out, it should get its own set alongside this one, the same way
     * Font Awesome does.
     */
    fun lucide(): GeneratedIconSet {
        val version = GitHubRepo.latestVersionTag(LucideIconSet.gitHubRepo, major = 1)
        val repo = GitHubRepo.resolve(LucideIconSet.gitHubRepo, version)
        // Unlike the font-based sets, Lucide's icons are SVGs, which we read straight out of the repo
        val icons = LucideIcons.parseRepoZip(downloader.fetch(repo.archiveUrl()).inputStream())
        return GeneratedIconSet(icons.toIconSet(), "Lucide $version ($repo)")
    }

    val fa6 = fontAwesome(6)
    val generated = listOf(fa6, fontAwesome(7), materialDesignIcons(), materialSymbols(), lucide())
    val iconSets = generated.map { it.iconSet }
    // Plugin versions up to 0.8.0 only know a single Font Awesome set, "fa", which they use for FA 6
    val legacyFa = GeneratedIconSet(IconSet("fa", fa6.iconSet.styles), fa6.source)

    // These are worth reporting (upstream's icon list and fonts disagree, so Kobweb's generated function for the icon
    // would render nothing too), but not worth failing over, since the plugin simply won't show a preview for them.
    if (missing.isNotEmpty()) {
        System.err.println("Warning: Could not find glyphs for ${missing.size} icon(s):")
        missing.forEach { System.err.println("  $it") }
    }

    outDir.createDirectories()
    val manifest = buildJsonObject {
        put("formatVersion", IconPreviewFormat.VERSION)
        putJsonObject("sets") {
            for ((iconSet, source) in generated + legacyFa) {
                val file = outDir.resolve("${iconSet.id}.bin")
                file.outputStream().use { IconPreviewFormat.write(iconSet, it) }
                val bytes = file.readBytes()
                println("Wrote $file (${bytes.size / 1024} KB)")
                putJsonObject(iconSet.id) {
                    put("file", file.fileName.toString())
                    put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).toHexString())
                    put("source", source)
                    putJsonObject("styles") { iconSet.styles.forEach { put(it.id, it.icons.size) } }
                }
            }
        }
    }
    outDir.resolve("manifest.json").writeText(Json { prettyPrint = true }.encodeToString(manifest) + "\n")

    // Readable style names for the sheets, e.g. "outlined" rather than the "mdio" style ID
    val styleNames = buildMap {
        FaIconSet.Style.entries.forEach { put(it.id, it.iconsJsonName) }
        MdiIconSet.Style.entries.forEach { put(it.id, it.name.lowercase().replace('_', '-')) }
        MsIconSet.Style.entries.forEach { put(it.id, it.name.lowercase()) }
    }

    // Render sheets from the files we just wrote, which doubles as a check that the format round-trips. Clear out old
    // sheets first, so ones for removed (or renamed) styles don't linger.
    sheetsDir.toFile().deleteRecursively()
    for (iconSet in iconSets) {
        val decoded = outDir.resolve("${iconSet.id}.bin").inputStream().use { IconPreviewFormat.read(iconSet.id, it) }
        // E.g. "fa7-solid", or just "lucide" for a set with only one style
        decoded.writeContactSheets(sheetsDir) { style ->
            styleNames[style.id]?.let { "${iconSet.id}-$it" } ?: iconSet.id
        }
    }
    println("Contact sheets written to $sheetsDir")
}

// The style IDs in icon preview data, which the plugin's `SilkIconSet` looks styles up by. Font Awesome's are its own
// CSS classes; the Material ones are short names the plugin made up.
private val FaIconSet.Style.id get() = cssClass

private val MdiIconSet.Style.id
    get() = when (this) {
        MdiIconSet.Style.FILLED -> "mdi"
        MdiIconSet.Style.OUTLINED -> "mdio"
        MdiIconSet.Style.ROUNDED -> "mdir"
        MdiIconSet.Style.SHARP -> "mdis"
        MdiIconSet.Style.TWO_TONED -> "mdit"
    }

private val MsIconSet.Style.id
    get() = when (this) {
        MsIconSet.Style.OUTLINED -> "mso"
        MsIconSet.Style.ROUNDED -> "msr"
        MsIconSet.Style.SHARP -> "mss"
    }
