# Kobweb IntelliJ Plugin

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
