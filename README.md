# [Kobweb](https://github.com/varabyte/kobweb) IntelliJ Plugin
- - -

This plugin exists to support projects using Kobweb, by offering IDE UX enhancements, Kobweb-specific inspections,
useful refactoring actions, and more.

The list of features includes but is not limited to:

- Suppression of "Unused" warning for functions with annotations like `@Page`, `@App` and so on.
- (WAY MORE TO COME SOON)

## Running

A `Run Plugin` run configuration is provided for testing during development.

This will open up a new IntelliJ instance with the plugin installed. (This feels weird the first time you try it, but
you'll get used to it!)

You can then open up a project and test your features. You can find sample projects under [sample-projects/]() which you
can use.

## Building

To build the plugin, run the `Build Plugin` run configuration.

The built plugin will be located in the [build/distributions]() directory.

## Installing

Once built, you can install the plugin from disk onto a real IDE environment.

Follow the official
instructions [here](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).

## Publishing

TODO
