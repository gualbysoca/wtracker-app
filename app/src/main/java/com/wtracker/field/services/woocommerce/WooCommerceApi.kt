package com.wtracker.field.services.woocommerce

import com.wtracker.field.configs.WooCommerceConfig
import com.wtracker.field.services.woocommerce.dto.WooCreateCustomerRequestDto
import com.wtracker.field.services.woocommerce.dto.WooCustomerDto
import com.wtracker.field.services.woocommerce.dto.WooOrderRequestDto
import com.wtracker.field.services.woocommerce.dto.WooOrderResponseDto
import com.wtracker.field.services.woocommerce.dto.WooProductDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface WooCommerceApi {

    @GET("wp-json/wc/v3/products")
    suspend fun getProducts(
        @Query("search") search: String? = null,
        @Query("per_page") perPage: Int = WooCommerceConfig.DEFAULT_PAGE_SIZE,
        @Query("page") page: Int = 1,
        @Query("status") status: String = WooCommerceConfig.PRODUCT_STATUS_PUBLISH,
    ): List<WooProductDto>

    @GET("wp-json/wc/v3/customers")
    suspend fun getCustomers(
        @Query("email") email: String,
    ): List<WooCustomerDto>

    @POST("wp-json/wc/v3/customers")
    suspend fun createCustomer(
        @Body request: WooCreateCustomerRequestDto,
    ): WooCustomerDto

    @POST("wp-json/wc/v3/orders")
    suspend fun createOrder(
        @Body request: WooOrderRequestDto,
    ): WooOrderResponseDto
}
