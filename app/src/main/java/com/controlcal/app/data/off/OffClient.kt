package com.controlcal.app.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

interface OffClient {
    suspend fun getProductByBarcode(barcode: String): OffSearchResult
}

@Singleton
class OffClientImpl(
    private val okHttpClient: OkHttpClient,
    private val mapper: OffMapper,
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : OffClient {

    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        mapper: OffMapper
    ) : this(okHttpClient, mapper, DEFAULT_BASE_URL, DEFAULT_USER_AGENT)

    companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
        const val DEFAULT_USER_AGENT = "ControlCal/1.0 (cambiar@gmail.com)"
        private const val FIELDS = "product_name,brands,quantity,serving_size,nutriments"
    }

    init {
        require(baseUrl.startsWith("https://")) {
            "Open Food Facts client only permits secure HTTPS connections."
        }
    }

    override suspend fun getProductByBarcode(barcode: String): OffSearchResult = withContext(Dispatchers.IO) {
        val trimmedBarcode = barcode.trim()
        if (trimmedBarcode.isEmpty()) {
            return@withContext OffSearchResult.NotFound
        }

        val url = "$baseUrl/$trimmedBarcode.json?fields=$FIELDS"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .get()
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200 -> {
                        val bodyString = response.body?.string()
                        if (bodyString.isNullOrBlank()) {
                            OffSearchResult.NotFound
                        } else {
                            mapper.parseResponse(trimmedBarcode, bodyString)
                        }
                    }
                    404 -> OffSearchResult.NotFound
                    else -> OffSearchResult.Error("Error HTTP ${response.code}")
                }
            }
        } catch (e: UnknownHostException) {
            OffSearchResult.Offline
        } catch (e: ConnectException) {
            OffSearchResult.Offline
        } catch (e: NoRouteToHostException) {
            OffSearchResult.Offline
        } catch (e: SocketTimeoutException) {
            OffSearchResult.Error("Tiempo de espera agotado (5 s)", e)
        } catch (e: IOException) {
            OffSearchResult.Error("Error de comunicación con Open Food Facts: ${e.message}", e)
        } catch (e: Exception) {
            OffSearchResult.Error("Error inesperado: ${e.message}", e)
        }
    }
}
