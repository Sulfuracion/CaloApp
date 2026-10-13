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

    private fun createMockOkHttpClient(interceptorAction: (Interceptor.Chain) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain -> interceptorAction(chain) }
            .build()
    }
}
