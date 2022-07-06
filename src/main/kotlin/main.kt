package org.example.fioc

interface Container {
    fun <T> get(predicate: Predicate<Annotations>): T
}

typealias Predicate<T> = (T) -> Boolean

typealias Annotations = List<Annotation>

interface Annotation {
    interface Key<T: Annotation>
    val key: Key<*>
}

data class Annotated<T>(val annotations: Annotations, val get: T)

data class BeanContainer(val beans: List<Annotated<*>>) : Container {
    @Suppress("UNCHECKED_CAST")
    override fun <T> get(predicate: Predicate<Annotations>): T =
        beans.single { predicate(it.annotations) }.get as T
}