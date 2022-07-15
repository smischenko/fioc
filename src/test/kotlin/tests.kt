import org.example.fioc.*
import kotlin.reflect.full.isSubclassOf
import kotlin.test.*

class Tests {
    @Test
    fun `test container with bean dependency and bean decorator`() {
        abstract class PriceCatalog {
            abstract fun getPrice(productId: Int): Int
        }

        abstract class PriceCalculator {
            abstract fun calculatePrice(productId: Int, count: Int): Int
        }

        val container = container {
            bean<PriceCatalog> {
                object : PriceCatalog() {
                    override fun getPrice(productId: Int): Int =
                        when (productId) {
                            1 -> 100
                            else -> throw RuntimeException("Unknown productId: $productId")
                        }
                }
            }
            bean<PriceCalculator> {
                val priceCatalog = container.get<PriceCatalog>()
                object : PriceCalculator() {
                    override fun calculatePrice(productId: Int, count: Int): Int =
                        priceCatalog.getPrice(productId) * count
                }
            }
            // Price discount as price calculator decorator
            beanPostProcessor { bean ->
                if (beanDescription.type == PriceCalculator::class)
                    object : PriceCalculator() {
                        override fun calculatePrice(productId: Int, count: Int): Int {
                            val price = (bean as PriceCalculator).calculatePrice(productId, count)
                            val discount = (price * 0.1).toInt()
                            return price - discount
                        }
                    }
                else bean
            }
        }

        val priceCalculator = container.get<PriceCalculator>()
        assertEquals(900, priceCalculator.calculatePrice(productId = 1, count = 10))
    }

    @Test
    fun `test container create single bean instance`() {
        class DataSource
        class Service(val dataSource: DataSource)

        val container = container {
            bean {
                Service(container.get())
            }
            bean {
                DataSource()
            }
        }

        val dataSource = container.get<DataSource>()
        val service = container.get<Service>()
        assertSame(dataSource, service.dataSource)
    }

    @Test
    fun `test cycle dependency detected`() {
        val exception = assertFailsWith<RuntimeException> {
            container {
                bean("serviceA") {
                    object {
                        private val serviceB: Any = container.get("serviceB")
                    }
                }
                bean("serviceB") {
                    object {
                        private val serviceC: Any = container.get("serviceC")
                    }
                }
                bean("serviceC") {
                    object {
                        private val serviceA: Any = container.get("serviceA")
                    }
                }
            }
        }
        assertContains(exception.message!!, Regex("serviceA(.)*->(.)*serviceB(.)*->(.)*serviceC"))
    }

    @Test
    fun `test cycle dependency detected 2`() {
        val exception = assertFailsWith<RuntimeException> {
            container {
                bean("serviceA") {
                    object {
                        private val serviceB: Any = container.get("serviceB")
                    }
                }
                bean("serviceB") {
                    object {
                        private val serviceC: Any = container.get("serviceC")
                    }
                }
                bean("serviceC") {
                    object {
                        private val serviceB: Any = container.get("serviceB")
                    }
                }
            }
        }
        assertContains(exception.message!!, Regex("serviceB(.)*->(.)*serviceC"))
    }
}