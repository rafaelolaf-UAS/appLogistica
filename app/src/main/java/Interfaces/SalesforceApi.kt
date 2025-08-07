package Interfaces

import retrofit2.http.GET
import retrofit2.http.Query

interface SalesforceApi {
    @GET("services/data/v56.0/query")
    suspend fun query(
        @Query("q")soql: String
    ): QueryResponse
}

data class QueryResponse(
    val totalSize: Int,
    val done: Boolean,
    val records: List<Map<String, @JvmSuppressWildcards Any>>
)