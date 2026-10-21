package com.wtracker.field.services.woocommerce

import com.wtracker.field.configs.NetworkConfig
import com.wtracker.field.configs.WooCommerceConfig
import com.wtracker.field.middlewares.WooCommerceAuthInterceptor
import com.wtracker.field.services.woocommerce.dto.WooCreateCustomerRequestDto
import com.wtracker.field.services.woocommerce.dto.WooCustomerDto
import com.wtracker.field.services.woocommerce.dto.WooOrderRequestDto
import com.wtracker.field.services.woocommerce.dto.WooOrderResponseDto
import com.wtracker.field.services.woocommerce.dto.WooProductDto
import com.wtracker.field.utils.AppError
import com.wtracker.field.utils.AppResult
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Contrato de comunicación HTTP agnóstico a la librería de red (SoC / Dependency Agnosticism).
 */
interface WooCommerceClient {
    suspend fun fetchProducts(search: String?): AppResult<List<WooProductDto>>
    suspend fun findCustomerByEmail(email: String): AppResult<WooCustomerDto?>
    suspend fun createCustomer(request: WooCreateCustomerRequestDto): AppResult<WooCustomerDto>
    suspend fun postOrder(request: WooOrderRequestDto): AppResult<WooOrderResponseDto>
}

/**
 * Implementación del cliente HTTP usando Retrofit + OkHttp con interceptor de credenciales WooCommerce.
 */
class RetrofitWooCommerceClient(
    private val api: WooCommerceApi,
) : WooCommerceClient {

    override suspend fun fetchProducts(search: String?): AppResult<List<WooProductDto>> {
        return try {
            val allProducts = mutableListOf<WooProductDto>()
            var page = 1
            var keepFetching = true
            while (keepFetching) {
                val batch = api.getProducts(
                    search = search?.takeIf { it.isNotBlank() },
                    perPage = WooCommerceConfig.DEFAULT_PAGE_SIZE,
                    page = page,
                )
                allProducts.addAll(batch)
                if (batch.size < WooCommerceConfig.DEFAULT_PAGE_SIZE || search?.isNotBlank() == true) {
                    keepFetching = false
                } else {
                    page++
                    if (page > 5) keepFetching = false
                }
            }
            AppResult.Success(allProducts)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network("Error de conexión al cargar productos de WooCommerce."))
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Server("Error del servidor WooCommerce: ${e.code()}", e.code()))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Error desconocido al obtener productos."))
        }
    }

    override suspend fun findCustomerByEmail(email: String): AppResult<WooCustomerDto?> {
        return try {
            val customers = api.getCustomers(email = email)
            AppResult.Success(customers.firstOrNull())
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network("Error de conexión al verificar usuario de WooCommerce."))
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Server("Error al consultar cliente en WooCommerce: ${e.code()}", e.code()))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Error desconocido al buscar cliente."))
        }
    }

    override suspend fun createCustomer(request: WooCreateCustomerRequestDto): AppResult<WooCustomerDto> {
        return try {
            val created = api.createCustomer(request)
            AppResult.Success(created)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network("Error de conexión al registrar usuario en WooCommerce."))
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Server("Error al registrar cliente en WooCommerce: ${e.code()}", e.code()))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Error desconocido al registrar cliente."))
        }
    }

    override suspend fun postOrder(request: WooOrderRequestDto): AppResult<WooOrderResponseDto> {
        return try {
            val response = api.createOrder(request)
            AppResult.Success(response)
        } catch (e: IOException) {
            AppResult.Failure(AppError.Network("No fue posible enviar el pedido a WooCommerce. Revisa tu conexión."))
        } catch (e: HttpException) {
            AppResult.Failure(AppError.Server("WooCommerce rechazó el pedido: HTTP ${e.code()}", e.code()))
        } catch (e: Exception) {
            AppResult.Failure(AppError.Unknown(e.message ?: "Error desconocido al crear el pedido."))
        }
    }

    companion object {
        fun create(
            baseUrl: String = WooCommerceConfig.baseUrl,
            consumerKey: String = WooCommerceConfig.consumerKey,
            consumerSecret: String = WooCommerceConfig.consumerSecret,
            json: Json,
        ): RetrofitWooCommerceClient {
            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(WooCommerceConfig.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(WooCommerceConfig.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(WooCommerceConfig.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addInterceptor(WooCommerceAuthInterceptor(consumerKey, consumerSecret))
                .build()

            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory(NetworkConfig.JSON_MEDIA_TYPE.toMediaType()))
                .build()

            val api = retrofit.create(WooCommerceApi::class.java)
            return RetrofitWooCommerceClient(api)
        }
    }
}
