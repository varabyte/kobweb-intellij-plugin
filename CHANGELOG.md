# Kobweb IntelliJ Plugin

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
