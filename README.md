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
        bean(name("priceCatalog")) {
            object : PriceCatalog {
                override fun getPrice(productId: Int): Int =
                    when (productId) {
                        1 -> 100
                        else -> throw RuntimeException("Unknown productId: $productId")
                    }
                }
        }
        bean(name("priceCalculator")) {
            val priceCatalog: PriceCatalog = container.get(name("priceCatalog"))
            object : PriceCalculator {
                override fun calculatePrice(productId: Int, count: Int): Int =
                    priceCatalog.getPrice(productId) * count
            }
        }
        // Price discount as price calculator decorator
        beanDecorator { bean ->
            if (annotations[Name]?.value == "priceCalculator")
                object : PriceCalculator {
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
    
    val price = priceCalculator.calculatePrice(productId = 1, count = 10)
