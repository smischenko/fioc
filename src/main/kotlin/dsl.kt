package org.example.fioc

data class Config(
    val providers: List<Annotated<Provider>>,
    val decorators: List<Ordered<(Annotated<Provider>) -> Annotated<Provider>>>
)

typealias Provider = (Container) -> Any

data class Ordered<T>(val order: Int, val get: T)

fun Config(): Config = Config(emptyList(), emptyList())

fun Config.provider(provider: Annotated<Provider>): Config =
    copy(providers = this.providers.plus(provider))

fun Config.decorator(order: Int, decorator: (Annotated<Provider>) -> Annotated<Provider>): Config =
    copy(decorators = this.decorators.plus(Ordered(order, decorator)))

data class ConfigDsl(var config: Config)

data class BeanDsl(val container: Container)

data class DecoratorDsl(
    val container: Container,
    val annotations: Annotations
)

fun container(block: ConfigDsl.() -> Unit): Container {
    val dsl = ConfigDsl(Config())
    dsl.block()
    return Container(dsl.config)
}

fun ConfigDsl.bean(vararg annotations: Annotation, block: BeanDsl.() -> Any) {
    config = config.provider(Annotated(listOf(*annotations)) { container -> BeanDsl(container).block() })
}

fun ConfigDsl.beanDecorator(order: Int = 0, block: DecoratorDsl.(Any) -> Any) {
    config = config.decorator(order) {
        it.map { provider ->
            { container ->
                val bean = provider(container)
                DecoratorDsl(container, it.annotations).block(bean)
            }
        }
    }
}

fun Container(config: Config): Container {
    val decorators = config.decorators.sortedBy { it.order }.map { it.get }
        .plus { it.map(::memoized) }
    val providers = config.providers.map { provider ->
        decorators.fold(provider) { acc, decorator -> decorator(acc) }
    }

    class State(val path: List<Annotated<Provider>>) : Container {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(predicate: Predicate<Annotations>): T =
            providers.single { predicate(it.annotations) }.let { provider ->
                if (path.contains(provider)) throw RuntimeException(
                    "Cycle dependency detected: ${
                        path.drop(path.indexOf(provider)).map { it.annotations }.joinToString("->")
                    }"
                )
                else provider.get(State(path.plus(provider))) as T
            }
    }

    val beans = providers.map { provider -> provider.map { it(State(listOf(provider))) } }
    return BeanContainer(beans)
}

fun memoized(provider: Provider): Provider =
    with(object { @Volatile var value: Any? = null }) {
        { container ->
            value ?: synchronized(provider) {
                value ?: run {
                    value = provider(container)
                    value!!
                }
            }
        }
    }