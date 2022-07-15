package org.example.fioc

operator fun <T: Annotation> Annotations.get(key: Annotation.Key<T>): T? =
    firstOrNull { it.key == key}?.let {
        @Suppress("UNCHECKED_CAST")
        it as T
    }
