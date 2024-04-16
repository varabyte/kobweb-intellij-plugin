# [Kobweb](https://github.com/varabyte/kobweb) IntelliJ Plugin
- - -

This plugin exists to support projects using Kobweb, by offering IDE UX enhancements, Kobweb-specific inspections,
useful refactoring actions, and more.

The list of features includes but is not limited to:

- Suppression of irrelevant warnings that don't apply to Kobweb projects, like unused functions with annotations like
  `@Page`, `@App` and so on.
- Surfacing color badges in the gutter for Kobweb RGB and HSL colors.
- (WAY MORE TO COME SOON)

## Developing

So much of IntelliJ plugin development is outside the scope of anything we can hope to cover here. However, we'll share
a few links, tips, and high level concepts.

First:

* Read the [official documentation](https://plugins.jetbrains.com/docs/intellij/developing-plugins.html)
* *(Optional)* Install the [DevKit Plugin](https://plugins.jetbrains.com/plugin/22851-plugin-devkit)
* Plugin source is located under the `plugin` subdirectory.

The IntelliJ API surfaces an astounding number of extension hooks. To add your own custom logic, 
search for your desired extension point [here](https://plugins.jetbrains.com/docs/intellij/extension-point-list.html),
implement it, and then register your implementation in the plugin's `resources/META-INF/plugin.xml`
file.

The IntelliJ codebase is unfortunately not the most well documented, especially some of the older parts, so usually the
best advice is to search open source plugins for examples of how to do what you want.

Consider referencing the [IntelliJ Community codebase](https://github.com/JetBrains/intellij-community) and
the [Android Studio codebase](https://cs.android.com/android-studio). You might also try searching through GitHub or
[grep.app](https://grep.app/) to see how other plugins are implemented.

## Running

A `Run Plugin` run configuration is provided for testing during development.

This will open up a new IntelliJ instance with the plugin installed. (This feels weird the first time you try it, but
you'll get used to it!)

You can then open up a project and test your features. You can find sample projects under [sample-projects/](sample-projects/) which you
can use.

## Building

To build the plugin, run the `Build Plugin` run configuration.

The built plugin will be located in the [plugin/build/distributions](plugin/build/distributions) directory.

## Installing

Once built, you can install the plugin from disk onto a real IDE environment.

Follow the official
instructions [here](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).

## Publishing

The Gradle task associated with publishing a plugin is `:plugin:publishPlugin`.

Publishing the plugin requires either credentials be set on your local machine *or* by using the project's GitHub
publish action (preferred), as the credentials have been installed into the repository as a secret.

Before publishing, you should make sure you've reviewed the following checklist:

* The `kobweb-ide-plugin` version in the `libs.version.toml` is correct (and does *not* end in a `-SNAPSHOT` suffix).
* The `intellij-platform` version is up-to-date.
  * See [this page](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html) for the latest version.
* The `intellijPlatform.intellijIdeaCommunity(<version>)` declaration in the `dependencies` block of the `build.gradle.kts` file is set to as low a version as possible (to maximize
  compatibility).
* The `ideaVersion.untilBuild` property still encompasses the latest EAP version.
* The [CHANGELOG.md](CHANGELOG.md) file has been updated with the new version and its changes.
* You've tested the plugin locally by running `:plugin:buildPlugin` and installing it from disk.
* You've verified plugin compatibility by running `:plugin:verifyPlugin` (or
  checking [GitHub CI](https://github.com/varabyte/kobweb-intellij-plugin/actions/workflows/verify.yml)).
* You've created a release on https://github.com/varabyte/kobweb-intellij-plugin
  * The version should have the pattern "vX.Y.Z".
  * The "What's New" section in the plugin UI links to commits associated with the tag created by the release.

Finally, publish the plugin using the `publishPlugin` task (or
using [GitHub CI](https://github.com/varabyte/kobweb-intellij-plugin/actions/workflows/publish.yml)).

When finished, verify https://plugins.jetbrains.com/plugin/23883-kobweb is up to date.
