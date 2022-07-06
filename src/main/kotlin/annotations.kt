package org.example.fioc

operator fun <T: Annotation> Annotations.get(key: Annotation.Key<T>): T? =
    firstOrNull { it.key == key}?.let {
        @Suppress("UNCHECKED_CAST")
        it as T
    }

fun <T> Container.get(annotation: Annotation): T = get(annotation.toPredicate())

fun Annotation.toPredicate(): Predicate<Annotations> =
    { annotations -> annotations[key] == this }

fun <T, U> Annotated<T>.map(f: (T) -> U): Annotated<U> = Annotated(annotations, f(get))

data class Name(val value: String): Annotation {
    companion object Key: Annotation.Key<Name>
    override val key: Annotation.Key<*> = Key
    override fun toString(): String = "Name{$value}"
}

fun name(value: String): Name = Name(value)
