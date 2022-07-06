package org.example.fioc

data class Config(
    val providers: List<Annotated<Provider>>,
    val decorators: List<Ordered<(Annotated<Provider>) -> Provider>>
)

typealias Provider = (Container) -> Any

data class Ordered<T>(val order: Int, val get: T)

fun Config(): Config = Config(emptyList(), emptyList())

fun Config.provider(provider: Annotated<Provider>): Config =
    copy(providers = this.providers.plus(provider))

fun Config.decorator(order: Int, decorator: (Annotated<Provider>) -> Provider): Config =
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
    config = config.decorator(order) { provider ->
        { container ->
            val bean = provider.get(container)
            DecoratorDsl(container, provider.annotations).block(bean)
        }
    }
}

fun Container(config: Config): Container {
    val decorators = config.decorators.sortedBy { it.order }.map { it.get }
        .plus { provider -> memoized(provider.get) }
    val providers = config.providers.map { provider ->
        decorators.fold(provider) { acc, decorator -> acc.map { decorator(acc) } }
    }
    val container = object : Container {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(predicate: Predicate<Annotations>): T =
            providers.single { predicate(it.annotations) }.get(this) as T
    }
    val beans = providers.map { provider -> provider.map { it(container) } }
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