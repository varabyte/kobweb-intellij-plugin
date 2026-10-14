# Kobweb IntelliJ Plugin

## [0.2.6]

This is the first plugin release where we've added substantial features to help highlight and resolve commonly faced
issues when working on a Kobweb site, particularly around CSS styles and modifier chains.

This release also introduces a fair amount of groundwork behind the scenes that should enable us to iterate more quickly
on future features and improvements moving forward.

Starting with this release, we really hope users will begin noticing helpful inspections and quick fixes while they work
on their Kobweb projects.

### Added

- Introduced a wizard flow that makes it easy to convert an inline `Modifier` chain into a `CssStyle`.
  - For example, say you had <span style="white-space: nowrap">`Modifier.fontFamily("monospace").fontSize(12.px)`</span>
    <br>You can now easily convert this into
    ```kotlin
    val CodeLabelStyle = CssStyle.base {
       Modifier.fontFamily("monospace").fontSize(12.px)
    }
    ```
    by going through the wizard steps.
  - See the [official documentation](https://kobweb.varabyte.com/docs/concepts/presentation/styling-html-elements#inline-vs-stylesheet)
    for more details on when and why we encourage doing this.
  - We currently add Kobweb gutter icons that make it visually obvious when this functionality is available, but you can
    also alt-click on inline modifiers as another way to access this functionality.
- Unused `Modifier` chains are now emphasized with a greyed-out color formatting syntax.
  - It has been occasionally easy to accidentally create modifiers inside style blocks outside a `base` block without
    realizing it:
    ```kotlin
    // Bad
    CssStyle {
       Modifier.color(Colors.Magenta)
    }
    
    // Good
    CssStyle {
       base {
          Modifier.color(Colors.Magenta)
       }
    }
    ```
    Before, if you did this, the modifier simply got dropped without being used. Now, thanks to it being colored as
    unused code, it is obvious that it is happening. We even provide a quick fix to wrap it into a `base` block for you.
- <i>Attribute</i> modifiers found inside `CssStyle` blocks will now be highlighted as errors. This helps you avoid cases that
  would have turned into runtime exceptions later that are harder to track down. (Only <i>style</i> modifiers belong in
  a <i>style</i> block.)
- You can now toggle `CssStyle` blocks between a concise format and a more relaxed format.
  - In other words, <span style="white-space: nowrap">`CssStyle.base { Modifier }`</span> ⟷ <span style="white-space: nowrap">`CssStyle { base { Modifier } }`</span>
  - The concise format is generally preferred (to reduce indentation), but the relaxed format lets you expand the
    style block with additional CSS selectors (e.g. `hover`, `link`, `focus`, etc.).
  - With this feature, now it is just an easy click to switch between the two formats.
  - This works for other kinds of style blocks as well, such as <span style="white-space: nowrap">`CssStyle.extendedBy`</span> ⟷ <span style="white-space: nowrap">`CssStyle.extendedByBase`</span>,
    <span style="white-space: nowrap">`CssStyle.addVariant`</span> ⟷ <span style="white-space: nowrap">`CssStyle.addVariantBase`</span>, and others.
- Inlay hint for style and attribute modifiers has been added and can be configured.
  - In this way, you can visually see that <span style="white-space: nowrap">`Modifier.id`</span> is associated with an
    HTML attribute, while <span style="white-space: nowrap">`Modifier.color`</span> is a CSS style.
  - By default, we only enable inlay hints for attribute modifiers, since they are much rarer.
- Improvements to the HTML / CSS documentation page
  - This builds on the quick docs features added in 0.2.5. 
  - We now support methods that surface multiple doc pages, e.g. <span style="white-space: nowrap">`Modifier.size()`</span>
    shows docs for both `width` and `height` CSS properties.
  - We added a more useful page for when docs are not found, with options to search MDN directly as well as file a bug
    against the Kobweb IntelliJ Plugin project.
  - We now surface docs for event modifiers (e.g. <span style="white-space: nowrap">`Modifier.onClick()`</span>)
  - We now show docs for HTML "data" and "aria" attributes

## [0.2.5]

### Added

- Extended the compatibility range to 2025.3 (and beyond?)
- New additional documentation page on CSS modifiers that pulls information down from the MDN:
  ![MDN docs](https://github.com/varabyte/media/raw/main/kobweb-intellij-plugin/0.2.5/mdn-docs.png)
- Migrated some dictionary code over from an API destined for deletion to a more reliable one 

## [0.2.4]

### Added

- Improved logic around providing Kobweb colors in the gutter
  - Preparing for an upcoming Kobweb which uses named colors (e.g. `Colors.red` now outputs the CSS name `red` instead
    of `rgb(255, 0, 0)`)  
  - We now also surface colors for values that come from Compose HTML (e.g. `Color.aliceblue`)

## [0.2.3]

### Added

- Spurious "unused" warnings for methods annotated with `@Layout` and `@InitRoute` are now suppressed.
- Extended the compatibility range to 2025.2

## [0.2.2]

### Added

- Extended the compatibility range to 2025.1

## [0.2.1]

### Added

- Fixed issue where projects wouldn't load if also using a Gradle version older than 8.3
  - If you saw "Unsupported class major file version 65", congratulations... this was you! (And sorry...)

## [0.2.0]

### Added

- Support for Kotlin [K2 mode](https://blog.jetbrains.com/idea/2024/08/meet-the-renovated-kotlin-support-k2-mode/)
- Updated the compatibility range to 2024.2.1 - 2024.3

## [0.1.3]

### Added

- Fixed issue where plugin functionality stopped working in 2024.2

## [0.1.2]

### Added

- Implementation details around analyzing code have moved to more stable IntelliJ IDEA APIs.
  - This works both with older versions of IDEA and should prevent us from hitting 2024.2 `NoClassDefFoundError` issues.


## [0.1.1]

### Added

- A functionally identical release extending compatibility to include IntelliJ 2024.2

## [0.1.0]

### Added

- Initial release of the plugin!
- Kobweb colors are now displayed in the gutter.<br>
  ![Kobweb gutter colors screenshot](https://github.com/varabyte/media/raw/main/kobweb-intellij-plugin/0.1.0/kobweb-gutter-colors.png)
- Spurious "unused" warnings for methods annotated with `@Page`, `@Api`, and the like are now suppressed automatically.
- Capitalization warnings for methods annotated with `@Composable` are now suppressed automatically.
- A special dictionary of Kobweb-related keywords has been created, to hide some incorrect spelling warnings (like
  "kobweb" for example!).
