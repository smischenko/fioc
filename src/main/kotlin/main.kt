package org.example.fioc

import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf

interface Container {
    fun <T> get(predicate: Predicate<BeanDescription>): T
}

typealias Predicate<T> = (T) -> Boolean

data class BeanDescription(
    val name: String,
    val type: KClass<*>,
    val annotations: Annotations
)

typealias Annotations = List<Annotation>

interface Annotation {
    interface Key<T: Annotation>
    val key: Key<*>
}

fun <T> Container.get(name: String): T = get { it.name == name }

inline fun <reified T> Container.get(): T = get { it.type.isSubclassOf(T::class) }

data class BeanContainer(val beans: List<Pair<BeanDescription, Any>>) : Container {
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(predicate: Predicate<BeanDescription>): T =
        beans.single { predicate(it.first) }.second as T
}