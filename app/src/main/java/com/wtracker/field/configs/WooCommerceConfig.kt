package com.wtracker.field.configs

import com.wtracker.field.BuildConfig

object WooCommerceConfig {
    val baseUrl: String = BuildConfig.WOOCOMMERCE_BASE_URL
    val consumerKey: String = BuildConfig.WOOCOMMERCE_CONSUMER_KEY
    val consumerSecret: String = BuildConfig.WOOCOMMERCE_CONSUMER_SECRET

    const val CONNECT_TIMEOUT_SECONDS = 20L
    const val READ_TIMEOUT_SECONDS = 30L
    const val WRITE_TIMEOUT_SECONDS = 30L

    const val DEFAULT_ORDER_STATUS = "pending"
    const val DEFAULT_CREATED_VIA = "App móvil"
    const val DEFAULT_PAYMENT_METHOD = "cod"
    const val DEFAULT_PAYMENT_METHOD_TITLE = "Pago contra entrega"
    const val DEFAULT_CURRENCY_SYMBOL = "Bs."

    const val DEFAULT_PAGE_SIZE = 100
    const val PRODUCT_STATUS_PUBLISH = "publish"

    /** Role required to take orders */
    const val REQUIRED_VENDOR_ROLE = "vendedor"
}
