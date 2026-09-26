package com.varabyte.kobweb.intellij.util.idea

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.KeyWithDefaultValue
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

/**
 * Creates and caches a Key<T> instance using the property's declared name.
 */
fun <T> key(): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Key<T>>> {
    return PropertyDelegateProvider { _, property ->
        val createdKey = Key.create<T>(property.name)
        ReadOnlyProperty { _, _ -> createdKey }
    }
}

fun <T> key(defaultValue: T): PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, Key<T>>> {
    return PropertyDelegateProvider { _, property ->
        val createdKey = KeyWithDefaultValue.create<T>(property.name, defaultValue)
        ReadOnlyProperty { _, _ -> createdKey }
    }
}
