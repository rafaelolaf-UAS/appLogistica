package Interfaces

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface SalesforceApi {
    @GET("services/data/v56.0/query")
    suspend fun query(
        @Query("q")soql: String
    ): QueryResponse
    @POST("services/data/v56.0/composite")
    suspend fun composite(@Body body: RequestBody): ResponseBody
}

data class QueryResponse(
    val totalSize: Int,
    val done: Boolean,
    val records: List<Map<String, @JvmSuppressWildcards Any>>
)