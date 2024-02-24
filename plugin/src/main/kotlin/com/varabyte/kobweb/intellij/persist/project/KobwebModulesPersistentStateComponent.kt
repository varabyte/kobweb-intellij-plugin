package com.varabyte.kobweb.intellij.persist.project

import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.util.xmlb.annotations.XMap
import com.varabyte.kobweb.intellij.model.KobwebModel
import com.varabyte.kobweb.intellij.model.KobwebProjectType

/**
 * A [BaseState] wrapper around a [KobwebModel] so it plays nicer with IntelliJ's persistence system.
 */
class KobwebModelState : BaseState() {
    companion object {
        fun from(kobwebModel: KobwebModel): KobwebModelState {
            return KobwebModelState().apply {
                projectType = kobwebModel.projectType
            }
        }
    }

    var projectType by enum<KobwebProjectType>()
}

fun KobwebModelState.intoKobwebModel(): KobwebModel? {
    val projectType = this.projectType ?: return null
    return object : KobwebModel {
        override val projectType = projectType
    }
}

/**
 * A persistable class which represents a mapping of IntelliJ modules to their KobwebModel metadata, if present.
 */
class KobwebModulesState : BaseState() {
    companion object {
        // NOTE: If you increment this version, then the user will get a request to resync their project to rebuild
        // this state object. You should use it if you need it but consider it carefully.
        const val VERSION = 1
    }

    /**
     * A mapping of the module's name to state describing the Kobweb model it is associated with.
     */
    @get:XMap
    val modelMap by map<String, KobwebModelState>()
    var isInitialized by property(false)
    var version by property(0)
}

/**
 * Persisted metadata for which modules have Kobweb models associated with them.
 *
 * Calls should be sure to call [initialize] before registering any models. In this way, the caller will be able to
 * distinguish between a fresh state and a state built for a project that simply doesn't have any Kobweb metadata in it
 * (which is common if you installed the Kobweb plugin but naturally use the IDE for many other non-Kobweb projects).
 */
@Service(Service.Level.PROJECT)
@State(
    name = "KobwebModules",
    storages = [Storage("kobwebModules.xml")]

)
class KobwebModulesPersistentStateComponent : SimplePersistentStateComponent<KobwebModulesState>(KobwebModulesState()) {
    /**
     * Return whether the current state is valid, which means that it represents real data from a previous sync.
     *
     * This can become invalid over time if [KobwebModulesState.VERSION] ever changes or if any of the paths to any of
     * the modules can't be found.
     */
    fun isStateValid(project: Project) =
        state.isInitialized && state.version == KobwebModulesState.VERSION && state.modelMap.all { (name, state) ->
            project.moduleNamed(name) != null && state.intoKobwebModel() != null
        }

    /**
     * Reset the state of this component to a fresh state.
     *
     * This will schedule the XML file for removal as a side effect.
     *
     * While a rare case, it could be useful to reset the state if you had a Kobweb dependency in your project, then
     * removed it, and did a Gradle sync.
     */
    fun reset() {
        loadState(KobwebModulesState())
    }

    /**
     * Initialize this component's state, which you are expected to do before calling [addKobwebModel].
     */
    fun initialize() {
        state.modelMap.clear()
        state.isInitialized = true
        state.version = KobwebModulesState.VERSION
    }

    private fun Project.moduleNamed(name: String): Module? {
        return this.modules.firstOrNull { it.name == name }
    }

    /**
     * Register a [KobwebModel] with this persistent state component.
     *
     * It is an error if you do this without first calling [initialize].
     */
    fun addKobwebModel(module: Module, kobwebModel: KobwebModel) {
        check(state.isInitialized)
        state.modelMap[module.name] = KobwebModelState.from(kobwebModel)
    }

    /**
     * Returns an iterator that you can use to run over the mapping of all Kobweb modules in the project.
     *
     * Note that this iterator is empty if [isStateValid] returns false.
     */
    fun iterator(project: Project): Iterator<Map.Entry<Module, KobwebModel>> {
        val sourceMap = state.modelMap.takeIf { isStateValid(project) } ?: emptyMap()
        return sourceMap.map { (moduleName, kobwebModelState) ->
            // Guaranteed non-null because of `isStateValid`
            project.moduleNamed(moduleName)!! to kobwebModelState.intoKobwebModel()!!
        }.toMap().iterator()
    }
}
