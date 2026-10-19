package com.controlcal.app.data.off

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

interface OffClient {
    suspend fun getProductByBarcode(barcode: String): OffSearchResult
    suspend fun searchProductsByName(query: String): OffSearchByNameResult
}

@Singleton
class OffClientImpl(
    private val okHttpClient: OkHttpClient,
    private val mapper: OffMapper,
    private val rateLimiter: OffRateLimiter = OffRateLimiter(),
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val searchBaseUrl: String = DEFAULT_SEARCH_BASE_URL,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : OffClient {

    @Inject
    constructor(
        okHttpClient: OkHttpClient,
        mapper: OffMapper,
        rateLimiter: OffRateLimiter,
    ) : this(okHttpClient, mapper, rateLimiter, DEFAULT_BASE_URL, DEFAULT_SEARCH_BASE_URL, DEFAULT_USER_AGENT)

    companion object {
        const val DEFAULT_BASE_URL = "https://world.openfoodfacts.org/api/v2/product"
        const val DEFAULT_SEARCH_BASE_URL = "https://world.openfoodfacts.org/cgi/search.pl"
        const val DEFAULT_USER_AGENT = "ControlCal/1.0 (cambiar@gmail.com)"
        private const val FIELDS = "product_name,brands,quantity,serving_size,nutriments"
        private const val SEARCH_FIELDS = "code,product_name,brands,nutriments,quantity,serving_size"
    }

    init {
        require(baseUrl.startsWith("https://") && searchBaseUrl.startsWith("https://")) {
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

    override suspend fun searchProductsByName(query: String): OffSearchByNameResult = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return@withContext OffSearchByNameResult.Empty
        }

        when (val limitResult = rateLimiter.tryAcquire()) {
            is RateLimitResult.Allowed -> Unit
            is RateLimitResult.Limited -> {
                return@withContext OffSearchByNameResult.RateLimited(limitResult.secondsRemaining)
            }
        }

        val encodedQuery = try {
            URLEncoder.encode(trimmedQuery, StandardCharsets.UTF_8.name())
        } catch (e: Exception) {
            return@withContext OffSearchByNameResult.Error("Error al codificar el texto de búsqueda", e)
        }

        val url = "$searchBaseUrl?search_terms=$encodedQuery&search_simple=1&action=process&json=1&page_size=20&page=1&fields=$SEARCH_FIELDS&tagtype_0=countries&tag_contains_0=contains&tag_0=spain"
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
                            OffSearchByNameResult.Empty
                        } else {
                            mapper.parseSearchResponse(bodyString)
                        }
                    }
                    404 -> OffSearchByNameResult.Empty
                    else -> OffSearchByNameResult.Error("Error HTTP ${response.code}")
                }
            }
        } catch (e: UnknownHostException) {
            OffSearchByNameResult.Offline
        } catch (e: ConnectException) {
            OffSearchByNameResult.Offline
        } catch (e: NoRouteToHostException) {
            OffSearchByNameResult.Offline
        } catch (e: SocketTimeoutException) {
            OffSearchByNameResult.Error("Tiempo de espera agotado (5 s)", e)
        } catch (e: IOException) {
            OffSearchByNameResult.Error("Error de comunicación con Open Food Facts: ${e.message}", e)
        } catch (e: Exception) {
            OffSearchByNameResult.Error("Error inesperado: ${e.message}", e)
        }
    }
}
