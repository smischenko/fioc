package org.example.fioc

data class Config(
    val beanDefinitions: List<BeanDefinition>,
    val beanDefinitionTransformers: List<Ordered<(BeanDefinition) -> BeanDefinition>>
)

typealias BeanDefinition = Annotated<BeanFactory>

typealias BeanFactory = (Container) -> Any

data class Ordered<T>(val order: Int, val get: T)

fun Config(): Config = Config(emptyList(), emptyList())

fun Config.beanDefinition(beanDefinition: BeanDefinition): Config =
    copy(beanDefinitions = this.beanDefinitions.plus(beanDefinition))

fun Config.beanDefinitionTransformer(order: Int, transformer: (BeanDefinition) -> BeanDefinition): Config =
    copy(beanDefinitionTransformers = this.beanDefinitionTransformers.plus(Ordered(order, transformer)))

data class ConfigDsl(var config: Config)

data class BeanDsl(val container: Container)

data class BeanPostProcessorDsl(
    val container: Container,
    val annotations: Annotations
)

fun container(block: ConfigDsl.() -> Unit): Container {
    val dsl = ConfigDsl(Config())
    dsl.block()
    return Container(dsl.config)
}

fun ConfigDsl.bean(vararg annotations: Annotation, block: BeanDsl.() -> Any) {
    config = config.beanDefinition(Annotated(listOf(*annotations)) { container -> BeanDsl(container).block() })
}

fun ConfigDsl.beanPostProcessor(order: Int = 0, block: BeanPostProcessorDsl.(Any) -> Any) {
    config = config.beanDefinitionTransformer(order) {
        it.map { beanFactory ->
            { container ->
                val bean = beanFactory(container)
                BeanPostProcessorDsl(container, it.annotations).block(bean)
            }
        }
    }
}

fun Container(config: Config): Container {
    val transformers = config.beanDefinitionTransformers.sortedBy { it.order }.map { it.get }
        .plus { it.map(::memoized) }
    val beanDefinitions = config.beanDefinitions.map { beanDefinition ->
        transformers.fold(beanDefinition) { acc, transformer -> transformer(acc) }
    }

    class State(val path: List<BeanDefinition>) : Container {
        @Suppress("UNCHECKED_CAST")
        override fun <T> get(predicate: Predicate<Annotations>): T =
            beanDefinitions.single { predicate(it.annotations) }.let { beanDefinition ->
                if (path.contains(beanDefinition)) throw RuntimeException(
                    "Cycle dependency detected: ${
                        path.drop(path.indexOf(beanDefinition)).map { it.annotations }.joinToString("->")
                    }"
                )
                else beanDefinition.get(State(path.plus(beanDefinition))) as T
            }
    }

    val beans = beanDefinitions.map { it.map { beanFactory -> beanFactory(State(listOf(it))) } }
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