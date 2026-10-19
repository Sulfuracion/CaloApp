package com.controlcal.app.data.off

import com.controlcal.core.model.ProductSource
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.InputStreamReader
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking

class OffClientMappingTest {

    private lateinit var mapper: OffMapper

    @Before
    fun setUp() {
        mapper = OffMapper()
    }

    private fun readFixture(filename: String): String {
        val resourceStream = javaClass.classLoader?.getResourceAsStream("off_fixtures/$filename")
            ?: error("Fixture off_fixtures/$filename not found in test resources")
        return InputStreamReader(resourceStream, Charsets.UTF_8).use { it.readText() }
    }

    @Test
    fun parseCompleteProduct_mapsAllFieldsCorrectly() {
        val json = readFixture("complete_product.json")
        val result = mapper.parseResponse("8410000000001", json)

        assertTrue(result is OffSearchResult.Success)
        val product = (result as OffSearchResult.Success).product

        assertEquals("8410000000001", product.barcode)
        assertEquals("Copos de avena integrales", product.name)
        assertEquals("ControlBio", product.brand)
        assertEquals(375.0, product.kcal100g ?: 0.0, 0.001)
        assertEquals(13.5, product.protein100g ?: 0.0, 0.001)
        assertEquals(58.7, product.carbs100g ?: 0.0, 0.001)
        assertEquals(7.0, product.fat100g ?: 0.0, 0.001)
        assertEquals(10.0, product.fiber100g ?: 0.0, 0.001)
        assertEquals(1.2, product.sugars100g ?: 0.0, 0.001)
        assertEquals(1.3, product.satFat100g ?: 0.0, 0.001)
        assertEquals(0.02, product.salt100g ?: 0.0, 0.001)
        assertEquals(40.0, product.servingG ?: 0.0, 0.001)
        assertFalse(product.isLiquid)
        assertFalse(product.hasMissingMandatoryFields)

        val domainProduct = product.toProductOrNull()
        assertNotNull(domainProduct)
        assertEquals(ProductSource.OFF, domainProduct?.source)
        assertEquals("Copos de avena integrales", domainProduct?.name)
    }

    @Test
    fun parseStringNutriments_convertsStringsAndCommasToDouble() {
        val json = readFixture("string_nutriments_product.json")
        val result = mapper.parseResponse("8410000000002", json)

        assertTrue(result is OffSearchResult.Success)
        val product = (result as OffSearchResult.Success).product

        assertEquals("Yogur natural", product.name)
        assertEquals("Lácteos del Valle", product.brand)
        assertEquals(61.0, product.kcal100g ?: 0.0, 0.001)
        assertEquals(3.5, product.protein100g ?: 0.0, 0.001)
        assertEquals(4.7, product.carbs100g ?: 0.0, 0.001)
        assertEquals(3.2, product.fat100g ?: 0.0, 0.001)
        assertEquals(0.0, product.fiber100g ?: 0.0, 0.001)
        assertEquals(4.7, product.sugars100g ?: 0.0, 0.001)
        assertEquals(2.1, product.satFat100g ?: 0.0, 0.001)
        assertEquals(0.1, product.salt100g ?: 0.0, 0.001)
        assertEquals(125.0, product.servingG ?: 0.0, 0.001)
        assertFalse(product.hasMissingMandatoryFields)
        assertNotNull(product.toProductOrNull())
    }

    @Test
    fun parseKjOnlyProduct_convertsKjToKcalDividingBy4184() {
        val json = readFixture("kj_only_product.json")
        val result = mapper.parseResponse("8410000000003", json)

        assertTrue(result is OffSearchResult.Success)
        val product = (result as OffSearchResult.Success).product

        assertEquals("Bebida de almendras", product.name)
        // 1046.0 kJ / 4.184 = 250.0 kcal
        assertEquals(250.0, product.kcal100g ?: 0.0, 0.05)
        assertEquals(1.0, product.protein100g ?: 0.0, 0.001)
        assertEquals(3.0, product.carbs100g ?: 0.0, 0.001)
        assertEquals(2.0, product.fat100g ?: 0.0, 0.001)
        assertEquals(200.0, product.servingG ?: 0.0, 0.001)
        assertTrue(product.isLiquid)
        assertFalse(product.hasMissingMandatoryFields)
    }

    @Test
    fun parseMissingMacros_marksFieldsAsNullAndToProductOrNullFails() {
        val json = readFixture("missing_macros_product.json")
        val result = mapper.parseResponse("8410000000004", json)

        assertTrue(result is OffSearchResult.Success)
        val product = (result as OffSearchResult.Success).product

        assertNull(product.fat100g)
        assertNull(product.name) // Empty name is converted to null
        assertTrue(product.hasMissingMandatoryFields)
        assertNull(product.toProductOrNull())
    }

    @Test
    fun parseNotFoundResponse_returnsNotFoundResult() {
        val json = readFixture("not_found_product.json")
        val result = mapper.parseResponse("8410000000000", json)

        assertTrue(result is OffSearchResult.NotFound)
    }

    @Test
    fun parseMalformedJson_returnsErrorResult() {
        val result = mapper.parseResponse("123", "{ malformed json")
        assertTrue(result is OffSearchResult.Error)
    }

    @Test
    fun offClient_whenUnknownHostException_returnsOffline() = runBlocking {
        val mockClient = createMockOkHttpClient {
            throw UnknownHostException("world.openfoodfacts.org not found")
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.getProductByBarcode("8410000000001")
        assertTrue(result is OffSearchResult.Offline)
    }

    @Test
    fun offClient_whenConnectException_returnsOffline() = runBlocking {
        val mockClient = createMockOkHttpClient {
            throw ConnectException("Connection refused")
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.getProductByBarcode("8410000000001")
        assertTrue(result is OffSearchResult.Offline)
    }

    @Test
    fun offClient_whenSocketTimeout_returnsErrorWithTimeoutMessage() = runBlocking {
        val mockClient = createMockOkHttpClient {
            throw SocketTimeoutException("timeout 5s")
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.getProductByBarcode("8410000000001")
        assertTrue(result is OffSearchResult.Error)
        assertTrue((result as OffSearchResult.Error).message.contains("5 s"))
    }

    @Test
    fun offClient_whenHttp404_returnsNotFound() = runBlocking {
        val mockClient = createMockOkHttpClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(404)
                .message("Not Found")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.getProductByBarcode("8410000000001")
        assertTrue(result is OffSearchResult.NotFound)
    }

    @Test
    fun offClient_whenHttp500_returnsError() = runBlocking {
        val mockClient = createMockOkHttpClient { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message("Internal Server Error")
                .body("Server error".toResponseBody("text/plain".toMediaType()))
                .build()
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.getProductByBarcode("8410000000001")
        assertTrue(result is OffSearchResult.Error)
        assertTrue((result as OffSearchResult.Error).message.contains("500"))
    }

    @Test
    fun offClient_sendsCorrectUserAgentAndParsesSuccess() = runBlocking {
        var recordedUserAgent: String? = null
        val completeJson = readFixture("complete_product.json")

        val mockClient = createMockOkHttpClient { chain ->
            recordedUserAgent = chain.request().header("User-Agent")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(completeJson.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.getProductByBarcode("8410000000001")
        assertEquals("ControlCal/1.0 (cambiar@gmail.com)", recordedUserAgent)
        assertTrue(result is OffSearchResult.Success)
        assertEquals("Copos de avena integrales", (result as OffSearchResult.Success).product.name)
    }

    @Test(expected = IllegalArgumentException::class)
    fun offClient_rejectsNonHttpsUrl() {
        val mockClient = OkHttpClient()
        OffClientImpl(mockClient, mapper, baseUrl = "http://world.openfoodfacts.org")
    }

    @Test
    fun parseSearchComplete_mapsAllItemsAndFieldsCorrectly() {
        val json = readFixture("search_complete.json")
        val result = mapper.parseSearchResponse(json)

        assertTrue(result is OffSearchByNameResult.Success)
        val items = (result as OffSearchByNameResult.Success).items
        assertEquals(2, items.size)

        val first = items[0]
        assertEquals("8410000000001", first.code)
        assertEquals("Copos de avena integrales", first.name)
        assertEquals("ControlBio", first.brand)
        assertEquals(375.0, first.kcal100g ?: 0.0, 0.001)

        val second = items[1]
        assertEquals("8410000000002", second.code)
        assertEquals("Avena suave", second.name)
        assertEquals("Hacendado", second.brand)
        assertEquals(360.0, second.kcal100g ?: 0.0, 0.001)
    }

    @Test
    fun parseSearchNoKcal_mapsProductWithNullKcal() {
        val json = readFixture("search_no_kcal.json")
        val result = mapper.parseSearchResponse(json)

        assertTrue(result is OffSearchByNameResult.Success)
        val items = (result as OffSearchByNameResult.Success).items
        assertEquals(1, items.size)

        val item = items[0]
        assertEquals("8410000000003", item.code)
        assertEquals("Infusión de manzanilla", item.name)
        assertEquals("Herbalia", item.brand)
        assertNull(item.kcal100g)
    }

    @Test
    fun parseSearchKjOnly_convertsKjToKcalDividingBy4184() {
        val json = readFixture("search_kj_only.json")
        val result = mapper.parseSearchResponse(json)

        assertTrue(result is OffSearchByNameResult.Success)
        val items = (result as OffSearchByNameResult.Success).items
        assertEquals(1, items.size)

        val item = items[0]
        assertEquals("8410000000004", item.code)
        assertEquals("Bebida isotónica limón", item.name)
        // 418.4 kJ / 4.184 = 100.0 kcal
        assertEquals(100.0, item.kcal100g ?: 0.0, 0.05)
    }

    @Test
    fun parseSearchEmpty_returnsEmptyResult() {
        val json = readFixture("search_empty.json")
        val result = mapper.parseSearchResponse(json)

        assertTrue(result is OffSearchByNameResult.Empty)
    }

    @Test
    fun parseSearchInvalidJson_returnsError() {
        val result = mapper.parseSearchResponse("{\"malformed\": [}")
        assertTrue(result is OffSearchByNameResult.Error)
    }

    @Test
    fun offClient_searchProductsByName_sendsCorrectParamsAndUserAgent() = runBlocking {
        var recordedUrl: String? = null
        var recordedUserAgent: String? = null
        val completeJson = readFixture("search_complete.json")

        val mockClient = createMockOkHttpClient { chain ->
            val request = chain.request()
            recordedUrl = request.url.toString()
            recordedUserAgent = request.header("User-Agent")
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(completeJson.toResponseBody("application/json".toMediaType()))
                .build()
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.searchProductsByName("avena integral")
        assertTrue(result is OffSearchByNameResult.Success)
        val items = (result as OffSearchByNameResult.Success).items
        assertEquals(2, items.size)

        assertEquals("ControlCal/1.0 (cambiar@gmail.com)", recordedUserAgent)
        assertNotNull(recordedUrl)
        assertTrue(recordedUrl!!.contains("search_terms=avena+integral"))
        assertTrue(recordedUrl!!.contains("fields=code%2Cproduct_name%2Cbrands%2Cnutriments%2Cquantity%2Cserving_size") || recordedUrl!!.contains("fields=code,product_name,brands,nutriments,quantity,serving_size"))
        assertTrue(recordedUrl!!.contains("page_size=20"))
        assertTrue(recordedUrl!!.contains("tagtype_0=countries&tag_contains_0=contains&tag_0=spain"))
    }

    @Test
    fun offClient_searchProductsByName_whenRateLimited_returnsRateLimitedWithoutCallingNetwork() = runBlocking {
        var networkCallCount = 0
        val mockClient = createMockOkHttpClient { chain ->
            networkCallCount++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(readFixture("search_empty.json").toResponseBody("application/json".toMediaType()))
                .build()
        }

        val rateLimiter = OffRateLimiter(maxRequests = 1, windowMillis = 60_000L)
        val offClient = OffClientImpl(mockClient, mapper, rateLimiter = rateLimiter)

        // First call succeeds
        val firstResult = offClient.searchProductsByName("avena")
        assertTrue(firstResult is OffSearchByNameResult.Empty)
        assertEquals(1, networkCallCount)

        // Second call is immediately rate limited
        val secondResult = offClient.searchProductsByName("avena")
        assertTrue(secondResult is OffSearchByNameResult.RateLimited)
        // Network was NOT called again
        assertEquals(1, networkCallCount)
    }

    @Test
    fun offClient_searchProductsByName_whenOffline_returnsOffline() = runBlocking {
        val mockClient = createMockOkHttpClient {
            throw UnknownHostException("No address associated with hostname")
        }
        val offClient = OffClientImpl(mockClient, mapper)

        val result = offClient.searchProductsByName("avena")
        assertTrue(result is OffSearchByNameResult.Offline)
    }

    private fun createMockOkHttpClient(interceptorAction: (Interceptor.Chain) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain -> interceptorAction(chain) }
            .build()
    }
}
