package com.varabyte.kobweb.intellij.test.fixtures

import com.varabyte.kobweb.intellij.model.KobwebProjectType

/** A test case for code inside a Kobweb application module (e.g. a site). */
abstract class KobwebApplicationTestCase : KobwebProjectTestCase(KobwebProjectType.Application)
