package com.varabyte.kobweb.intellij.settings

import com.intellij.openapi.components.*
import com.intellij.util.xmlb.annotations.Property

@Service(Service.Level.APP)
@State(
    name = "KobwebPluginAppSettings",
    storages = [Storage("kobwebAppSettings.xml")]
)
class KobwebAppSettingsService : PersistentStateComponent<KobwebAppSettingsService.State> {

    object ExtractCssStyle {
        enum class Format { CONCISE, RELAXED, ASK_ME }
        enum class AttributeModifiersStrategy { EXTRACT, INLINE, ASK_ME }
    }

    class State {
        class CssStyle {
            var format = ExtractCssStyle.Format.ASK_ME
            var attributeModifiersStrategy = ExtractCssStyle.AttributeModifiersStrategy.ASK_ME
            var showAttributeWarning = true
        }

        @Property
        val extractCssStyle = CssStyle()
    }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    companion object {
        fun getInstance(): KobwebAppSettingsService {
            return service<KobwebAppSettingsService>()
        }
    }
}