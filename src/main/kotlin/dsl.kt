package org.example.fioc

data class Config(
    val beanDefinitions: List<BeanDefinition>,
    val beanDefinitionTransformers: List<Ordered<(BeanDefinition) -> BeanDefinition>>
)

data class BeanDefinition(
    val beanDescription: BeanDescription,
    val beanFactory: BeanFactory
)

typealias BeanFactory = (Container) -> Any

data class Ordered<T>(val order: Int, val get: T)

fun Config(): Config = Config(emptyList(), emptyList())

fun Config.beanDefinition(beanDefinition: BeanDefinition): Config =
    copy(beanDefinitions = this.beanDefinitions.plus(beanDefinition))

fun Config.beanDefinitionTransformer(order: Int, transformer: (BeanDefinition) -> BeanDefinition): Config =
    copy(beanDefinitionTransformers = this.beanDefinitionTransformers.plus(Ordered(order, transformer)))

@DslMarker
annotation class ConfigDslMarker

@ConfigDslMarker
data class ConfigDsl(var config: Config)

@ConfigDslMarker
data class BeanDsl(val container: Container)

@ConfigDslMarker
data class BeanPostProcessorDsl(
    val container: Container,
    val beanDescription: BeanDescription
)

fun container(block: ConfigDsl.() -> Unit): Container {
    val dsl = ConfigDsl(Config())
    dsl.block()
    return Container(dsl.config)
}

inline fun <reified T : Any> ConfigDsl.bean(
    name: String = "anonymous",
    vararg annotations: Annotation = emptyArray(),
    crossinline block: BeanDsl.() -> T
) {
    config = config.beanDefinition(
        BeanDefinition(BeanDescription(name, T::class, listOf(*annotations)))
        { container -> BeanDsl(container).block() }
    )
}

fun ConfigDsl.beanPostProcessor(order: Int = 0, block: BeanPostProcessorDsl.(Any) -> Any) {
    config = config.beanDefinitionTransformer(order) {
        BeanDefinition(it.beanDescription) { container ->
            val bean = it.beanFactory(container)
            BeanPostProcessorDsl(container, it.beanDescription).block(bean)
        }
    }
}

fun Container(config: Config): Container {
    val transformers = config.beanDefinitionTransformers.sortedBy { it.order }.map { it.get }
        .plus { BeanDefinition(it.beanDescription, memoized(it.beanFactory)) }
    val beanDefinitions = config.beanDefinitions.map { beanDefinition ->
        transformers.fold(beanDefinition) { acc, transformer -> transformer(acc) }
    }

    class State(val path: List<BeanDefinition>) : Container {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(predicate: Predicate<BeanDescription>): T =
            beanDefinitions.single { predicate(it.beanDescription) }.let { beanDefinition ->
                if (path.contains(beanDefinition)) throw RuntimeException(
                    "Cycle dependency detected: ${
                        path.drop(path.indexOf(beanDefinition)).map { it.beanDescription }.joinToString("->")
                    }"
                )
                else beanDefinition.beanFactory(State(path.plus(beanDefinition))) as T
            }
    }

    val beans = beanDefinitions.map { it.beanDescription to it.beanFactory(State(listOf(it))) }
    return BeanContainer(beans)
}

fun memoized(beanFactory: BeanFactory): BeanFactory =
    with(object { @Volatile var value: Any? = null }) {
        { container ->
            value ?: synchronized(beanFactory) {
                value ?: run {
                    value = beanFactory(container)
                    value!!
                }
            }
        }
    }