package com.varabyte.kobweb.intellij.services.project

import com.intellij.openapi.components.Service
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Launches coroutines that run until they finish or the project closes (or this plugin unloads), whichever is first.
 *
 * The platform only gives plugins such a scope through a service's constructor, so this service exists to share one.
 * Use it only for one-off work with no better home. Work that has its own state or lifecycle belongs in a dedicated
 * service with its own scope instead.
 *
 * The scope is a supervisor scope, so one coroutine failing doesn't cancel the others.
 */
@Service(Service.Level.PROJECT)
class KobwebProjectScopeService(private val coroutineScope: CoroutineScope) {
    /** Launches [block], with [name] identifying it in coroutine dumps and logs. */
    fun launch(name: String, block: suspend CoroutineScope.() -> Unit): Job =
        coroutineScope.launch(CoroutineName(name), block = block)
}
