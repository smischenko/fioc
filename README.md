# FIOC

FIOC is an attempt to implement IoC container in functional style.

Usage example:

    interface PriceCatalog {
        fun getPrice(productId: Int): Int
    }
    
    interface PriceCalculator {
        fun calculatePrice(productId: Int, count: Int): Int
    }
    
    val container = container {
        bean<PriceCatalog> {
            object : PriceCatalog {
                override fun getPrice(productId: Int): Int =
                    when (productId) {
                        1 -> 100
                        else -> throw RuntimeException("Unknown productId: $productId")
                    }
                }
        }
        bean<PriceCalculator> {
            val priceCatalog: PriceCatalog = container.get<PriceCatalog>()
            object : PriceCalculator {
                override fun calculatePrice(productId: Int, count: Int): Int =
                    priceCatalog.getPrice(productId) * count
            }
        }
        // Price discount as price calculator decorator
        beanPostProcessor { bean ->
            if (bean is PriceCalculator)
                object : PriceCalculator {
                    override fun calculatePrice(productId: Int, count: Int): Int {
                        val price = bean.calculatePrice(productId, count)
                        val discount = (price * 0.1).toInt()
                        return price - discount
                    }
                }
            else bean
        }
    }
    
    val priceCalculator = container.get<PriceCalculator>()
    
    val price = priceCalculator.calculatePrice(productId = 1, count = 10)
