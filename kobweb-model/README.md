This model is separated from the rest of the project because we built it with a much lower source compatibility target
than the core plugin code (which IntelliJ pushes to be a relatively recent version).

This is because this code is essentially sent over to and run by the Gradle JVM, which can be a lot older than the
IntelliJ JVM for some users.

We don't want people who happen to use an older Gradle JVM version from getting incompatible class version errors
(which are really hard to understand) when they apply the Kobweb plugin.