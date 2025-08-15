package data.remote

import Interfaces.SalesforceApi
import data.local.ScanItem
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object SalesforceUploader {
    private const val SOBJECT_NAME = "Reception_Scan__c"
    private const val FIELD_CODE = "Code__c"
    private const val FIELD_QTY = "Quantity__c"

    private const val COMPOSITE_LIMIT = 25
    private val JSON: MediaType = "application/json".toMediaType()

    suspend fun uploadCompositeBatch(api: SalesforceApi, items: List<ScanItem>): Pair<List<Long>, List<Pair<Long, String>>> {
        val successes = mutableListOf<Long>()
        val failures = mutableListOf<Pair<Long, String>>()

        val chunks = items.chunked(COMPOSITE_LIMIT)
        for (chunk in chunks) {
            val reqBody = buildCompositeCreateBody(chunk)
            val body: RequestBody = reqBody.toString().toRequestBody(JSON)

            // endpoint típico: /services/data/vXX.X/composite
            // Asegúrate que SalesforceApi tenga un método @POST("services/data/vXX.X/composite")
            val response = api.composite(body) // <-- implementa en tu SalesforceApi

            // Parseo básico
            val respObj = JSONObject(response.string())
            val results = respObj.optJSONArray("compositeResponse") ?: JSONArray()

            // Mapear cada subrequest a su ScanItem por referenceId
            val byRef = chunk.associateBy { "new_${it.id}" }

            for (i in 0 until results.length()) {
                val r = results.getJSONObject(i)
                val refId = r.optString("referenceId")
                val httpStatus = r.optInt("httpStatusCode")
                val item = byRef[refId] ?: continue

                if (httpStatus in 200..299) {
                    successes.add(item.id)
                } else {
                    val bodyErr = r.opt("body")
                    val msg = when (bodyErr) {
                        is JSONArray -> bodyErr.joinMessages()
                        is JSONObject -> bodyErr.optString("message", "Error desconocido")
                        else -> "Error $httpStatus"
                    }
                    failures.add(item.id to msg)
                }
            }
        }
        return successes to failures
    }

    private fun buildCompositeCreateBody(items: List<ScanItem>): JSONObject {
        val allOrNone = false
        val compositeRequest = JSONArray()

        items.forEach { item ->
            val sobjectBody = JSONObject().apply {
                put(FIELD_CODE, item.code)
                put(FIELD_QTY, item.quantity)
            }
            compositeRequest.put(
                JSONObject().apply {
                    put("method", "POST")
                    put("url", "/services/data/v56.0/sobjects/$SOBJECT_NAME")
                    put("referenceId", "new_${item.id}")
                    put("body", sobjectBody)
                }
            )
        }

        return JSONObject().apply {
            put("allOrNone", allOrNone)
            put("compositeRequest", compositeRequest)
        }
    }

    private fun JSONArray.joinMessages(): String {
        val msgs = mutableListOf<String>()
        for (i in 0 until length()) {
            val o = optJSONObject(i)
            if (o != null) {
                val msg = o.optString("message", "")
                val fields = o.optJSONArray("fields")?.let { fArr ->
                    (0 until fArr.length()).joinToString(", ") { idx -> fArr.optString(idx) }
                }
                msgs.add(listOfNotNull(msg, fields).joinToString(" | "))
            }
        }
        return msgs.joinToString("; ")
    }
}