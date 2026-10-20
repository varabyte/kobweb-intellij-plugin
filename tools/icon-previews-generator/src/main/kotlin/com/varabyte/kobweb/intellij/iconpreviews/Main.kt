package com.varabyte.kobweb.intellij.iconpreviews

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

    // Kobweb decides which icons exist; upstream fonts provide what they look like. Material fonts are served unpinned
    // by Google Fonts at runtime, so using their latest commit matches what Kobweb sites actually show.
    val kobweb = GitHubRepo.resolve("varabyte/kobweb", options["kobweb-ref"] ?: "main")
    val material = GitHubRepo.resolve("google/material-design-icons", "master")
    println("Using $kobweb and $material")

    fun kobwebFile(path: String) = downloader.fetchText(kobweb.fileUrl(path))
    fun materialFile(path: String) = downloader.fetch(material.fileUrl(path))

    // Icons Kobweb defines that we couldn't find a glyph for, collected across all icon sets
    val missing = mutableListOf<String>()

    /** @property source Where the icons came from, for the manifest, e.g. "Font Awesome 6.5.2 (cdnjs)". */
    data class GeneratedIconSet(val iconSet: IconSet, val source: String)

    fun fontAwesome(): GeneratedIconSet {
        // Font Awesome is pinned by Kobweb (via the CDN link it adds to a site's <head>), so use that exact version
        val siteIndexTask = "tools/gradle-plugins/application/src/main/kotlin/com/varabyte/kobweb/gradle/application/" +
            "tasks/KobwebGenerateSiteIndexTask.kt"
        val version = Regex("""font-awesome/(\d+\.\d+\.\d+)/""").find(kobwebFile(siteIndexTask))?.groupValues?.get(1)
            ?: error("Could not find the Font Awesome version in Kobweb's KobwebGenerateSiteIndexTask.kt")
        val baseUrl = "https://cdnjs.cloudflare.com/ajax/libs/font-awesome/$version"
        val codepoints = FontIcons.parseFontAwesomeCodepoints(downloader.fetchText("$baseUrl/css/all.css"))
        val fonts = mapOf("fas" to "fa-solid-900.ttf", "far" to "fa-regular-400.ttf", "fab" to "fa-brands-400.ttf")

        val iconList = kobwebFile("frontend/silk-icons-fa/fa-icon-list.txt")
        val namesByStyle = SilkIconList.parseNamesByStyle(iconList, separator = '-')
        val styles = namesByStyle.map { (style, names) ->
            val font = downloader.fetch("$baseUrl/webfonts/${fonts.getValue(style)}")
            IconStyle(style, FontIcons.extract(font, names, codepoints, missing))
        }
        return GeneratedIconSet(IconSet("fa", styles), "Font Awesome $version (cdnjs)")
    }

    fun materialDesignIcons(): GeneratedIconSet {
        val fonts = mapOf(
            "mdi" to "MaterialIcons-Regular.ttf",
            "mdio" to "MaterialIconsOutlined-Regular.otf",
            "mdir" to "MaterialIconsRound-Regular.otf",
            "mdis" to "MaterialIconsSharp-Regular.otf",
            "mdit" to "MaterialIconsTwoTone-Regular.otf",
        )

        val iconList = kobwebFile("frontend/silk-icons-mdi/md-icon-list.txt")
        val namesByStyle = SilkIconList.parseNamesByStyle(iconList, separator = '_')
        val styles = namesByStyle.map { (style, names) ->
            val font = "font/${fonts.getValue(style)}"
            val codepoints = FontIcons.parseGoogleCodepoints(
                materialFile(font.substringBeforeLast('.') + ".codepoints").readText()
            )
            IconStyle(style, FontIcons.extract(materialFile(font), names, codepoints, missing))
        }
        return GeneratedIconSet(IconSet("mdi", styles), material.toString())
    }

    fun materialSymbols(): GeneratedIconSet {
        val fonts = mapOf("mso" to "Outlined", "msr" to "Rounded", "mss" to "Sharp")

        // Every Material Symbols icon comes in every style, so one list of names applies to all three fonts
        val iconList = kobwebFile("frontend/silk-icons-ms/ms-icon-list.txt")
        val names = SilkIconList.parseNamesForAllStyles(iconList, separator = '_')
        val styles = fonts.map { (style, fontStyle) ->
            // Variable fonts; Java2D renders their default instance, which matches the default axes Kobweb renders with
            val font = "variablefont/MaterialSymbols$fontStyle%5BFILL,GRAD,opsz,wght%5D"
            val codepoints = FontIcons.parseGoogleCodepoints(materialFile("$font.codepoints").readText())
            IconStyle(style, FontIcons.extract(materialFile("$font.ttf"), names, codepoints, missing))
        }
        return GeneratedIconSet(IconSet("ms", styles), material.toString())
    }

    fun lucide(): GeneratedIconSet {
        // Unlike the font-based sets, Kobweb's Lucide data includes each icon's geometry, not just its name
        val lucide = LucideIcons.parse(kobwebFile("frontend/silk-icons-lucide/lucide-icons.json"))
        return GeneratedIconSet(lucide.iconSet, "Lucide ${lucide.version} (from Kobweb's lucide-icons.json)")
    }

    val generated = listOf(fontAwesome(), materialDesignIcons(), materialSymbols(), lucide())
    val iconSets = generated.map { it.iconSet }

    // These are worth reporting (upstream may have removed an icon Kobweb still defines, which would also be broken in
    // Kobweb itself), but not worth failing over, since the plugin simply won't show a preview for them.
    if (missing.isNotEmpty()) {
        System.err.println("Warning: Could not find glyphs for ${missing.size} icon(s) Kobweb defines:")
        missing.forEach { System.err.println("  $it") }
    }

    outDir.createDirectories()
    val manifest = buildJsonObject {
        put("formatVersion", IconPreviewFormat.VERSION)
        put("kobwebCommit", kobweb.sha)
        putJsonObject("sets") {
            for ((iconSet, source) in generated) {
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

    // Readable names for the sheets, e.g. "mdi-outlined" rather than Kobweb's "mdio" style prefix
    val sheetNames = mapOf(
        "fas" to "fa-solid",
        "far" to "fa-regular",
        "fab" to "fa-brands",
        "mdi" to "mdi-filled",
        "mdio" to "mdi-outlined",
        "mdir" to "mdi-rounded",
        "mdis" to "mdi-sharp",
        "mdit" to "mdi-two-toned",
        "mso" to "ms-outlined",
        "msr" to "ms-rounded",
        "mss" to "ms-sharp",
        "lucide" to "lucide",
    )

    // Render sheets from the files we just wrote, which doubles as a check that the format round-trips. Clear out old
    // sheets first, so ones for removed (or renamed) styles don't linger.
    sheetsDir.toFile().deleteRecursively()
    for (iconSet in iconSets) {
        val decoded = outDir.resolve("${iconSet.id}.bin").inputStream().use { IconPreviewFormat.read(iconSet.id, it) }
        decoded.writeContactSheets(sheetsDir) { style -> sheetNames.getValue(style.id) }
    }
    println("Contact sheets written to $sheetsDir")
}
