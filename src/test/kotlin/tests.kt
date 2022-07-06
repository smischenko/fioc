import org.example.fioc.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

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
            bean(name("priceCatalog")) {
                object : PriceCatalog() {
                    override fun getPrice(productId: Int): Int =
                        when (productId) {
                            1 -> 100
                            else -> throw RuntimeException("Unknown productId: $productId")
                        }
                }
            }
            bean(name("priceCalculator")) {
                val priceCatalog: PriceCatalog = container.get(name("priceCatalog"))
                object : PriceCalculator() {
                    override fun calculatePrice(productId: Int, count: Int): Int =
                        priceCatalog.getPrice(productId) * count
                }
            }
            // Price discount as price calculator decorator
            beanDecorator { bean ->
                if (annotations[Name]?.value == "priceCalculator")
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

        val priceCalculator: PriceCalculator = container.get(name("priceCalculator"))
        assertEquals(900, priceCalculator.calculatePrice(productId = 1, count = 10))
    }

    @Test
    fun `test container create single bean instance`() {
        class DataSource
        class Service(val dataSource: DataSource)

        val container = container {
            bean(name("service")) {
                Service(container.get(name("dataSource")))
            }
            bean(name("dataSource")) {
                DataSource()
            }
        }

        val dataSource: DataSource = container.get(name("dataSource"))
        val service: Service = container.get(name("service"))
        assertSame(dataSource, service.dataSource)
    }

    @Test
    fun `test cycle dependency detected`() {
        val exception = assertFailsWith<RuntimeException> {
            container {
                bean(name("serviceA")) {
                    object {
                        private val serviceB: Any = container.get(name("serviceB"))
                    }
                }
                bean(name("serviceB")) {
                    object {
                        private val serviceC: Any = container.get(name("serviceC"))
                    }
                }
                bean(name("serviceC")) {
                    object {
                        private val serviceA: Any = container.get(name("serviceA"))
                    }
                }
            }
        }
        assertEquals("Cycle dependency detected: [Name{serviceA}]->[Name{serviceB}]->[Name{serviceC}]", exception.message)
    }

    @Test
    fun `test cycle dependency detected 2`() {
        val exception = assertFailsWith<RuntimeException> {
            container {
                bean(name("serviceA")) {
                    object {
                        private val serviceB: Any = container.get(name("serviceB"))
                    }
                }
                bean(name("serviceB")) {
                    object {
                        private val serviceC: Any = container.get(name("serviceC"))
                    }
                }
                bean(name("serviceC")) {
                    object {
                        private val serviceB: Any = container.get(name("serviceB"))
                    }
                }
            }
        }
        assertEquals("Cycle dependency detected: [Name{serviceB}]->[Name{serviceC}]", exception.message)
    }
}