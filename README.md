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

You can then open up a project and test your features. You can find sample projects under [sample-projects/]() which you
can use.

## Building

To build the plugin, run the `Build Plugin` run configuration.

The built plugin will be located in the [plugin/build/distributions]() directory.

## Installing

Once built, you can install the plugin from disk onto a real IDE environment.

Follow the official
instructions [here](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).

## Publishing

TODO
