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

fun Config.decorator(order: Int, decorator: (Annotated<Provider>) -> Provider): Config {
    val index = decorators.binarySearchBy(order) { it.order }
    val insertionPoint = if (index >=0) index else - index - 1
    val decoratorsMutable = decorators.toMutableList()
    decoratorsMutable.add(insertionPoint, Ordered(order, decorator))
    return copy(decorators = decoratorsMutable.toList())
}

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

fun Container(config: Config): Container =
    with(config) {
        val effectiveProviders = providers.map { provider ->
            decorators.fold(provider) { acc, decorator -> Annotated(acc.annotations, decorator.get(acc)) }
        }
        val evalContainer = object : Container {
            @Suppress("UNCHECKED_CAST")
            override fun <T> get(predicate: Predicate<Annotations>): T =
                effectiveProviders.first { predicate(it.annotations) }.get(this) as T
        }
        val beans = effectiveProviders.map { p -> Annotated(p.annotations, p.get(evalContainer)) }
        BeanContainer(beans)
    }
