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
    private const val SOBJECT_NAME = "Recepcion__c"
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
            val response = api.composite(body)
            val respObj = JSONObject(response.string())
            val results = respObj.optJSONArray("compositeResponse") ?: JSONArray()
            val byRef = chunk.associateBy { "new_${it.id}" }

            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val refId = r.optString("referenceId")
                val statusCode = r.optInt("httpStatusCode", -1)
                val resultBody = r.optJSONObject("body")
                val item = byRef[refId]
                if (item == null) continue

                if (statusCode in 200..299) {
                    successes.add(item.id)
                } else {
                    val errors = r.optJSONArray("body") ?: JSONArray()
                    val msg = errors.joinMessages()
                    failures.add(item.id to msg)
                }
            }
        }
        return Pair(successes, failures)
    }

    //Crea una recepcion__c y retorna id
    suspend fun createRecepcion(api: SalesforceApi, recepcionName: String, extraFields: Map<String, Any> = emptyMap()): String{
        val composite = JSONArray()
        val sobjectBody = JSONObject().apply {
            put("Name", recepcionName)
            for ((k, v) in extraFields) put(k, v)
        }
        composite.put(
            JSONObject().apply {
                put("method", "POST")
                put("url", "/services/data/v56.0/sobjects/$SOBJECT_NAME")
                put("referenceId", "new_recepcion")
                put("body", sobjectBody)
            }
        )
        val envelope = JSONObject().apply {
            put("allOrNone", false)
            put("compositeRequest", composite)
        }
        val body: RequestBody = envelope.toString().toRequestBody(JSON)
        val response = api.composite(body)
        val respObj = JSONObject(response.string())
        val results = respObj.optJSONArray("compositeResponse") ?: JSONArray()
        if (results.length() > 0) {
            val r = results.optJSONObject(0)
            val status = r?.optInt("httpStatusCode", -1) ?: -1
            if (status in 200..299) {
                val id = r.optJSONObject("body")?.optString("id") ?: ""
                return id
            } else {
                val errors = r?.optJSONArray("body") ?: JSONArray()
                throw RuntimeException("Error creating Recepcion: ${errors.joinMessages()}")
            }
        } else {
            throw RuntimeException("Empty composite response when creating Recepcion")
        }
    }

    suspend fun createGuia(api: SalesforceApi, recepcionId: String, guiaNumber: String, items: List<Map<String, Any>> = emptyList(), extraFields: Map<String, Any> = emptyMap()): String {
        val composite = JSONArray()
        val guiaBody = JSONObject().apply {
            put("Recepcion__c", recepcionId)
            put("Code__c", guiaNumber)
            for ((k, v) in extraFields) put(k, v)
        }

        composite.put(
            JSONObject().apply {
                put("method", "POST")
                put("url", "/services/data/v56.0/sobjects/Guia_Recepcion__c")
                put("referenceId", "new_guia")
                put("body", guiaBody)
            }
        )

        val envelope = JSONObject().apply {
            put("allOrNone", false)
            put("compositeRequest", composite)
        }
        val body: RequestBody = envelope.toString().toRequestBody(JSON)
        val response = api.composite(body)
        val respObj = JSONObject(response.string())
        val results = respObj.optJSONArray("compositeResponse") ?: JSONArray()
        if (results.length() > 0) {
            val r = results.optJSONObject(0)
            val status = r?.optInt("httpStatusCode", -1) ?: -1
            if (status in 200..299) {
                val id = r.optJSONObject("body")?.optString("id") ?: ""
                return id
            } else {
                val errors = r?.optJSONArray("body") ?: JSONArray()
                throw RuntimeException("Error creating Guia: ${errors.joinMessages()}")
            }
        } else {
            throw RuntimeException("Empty composite response when creating Guia")
        }
    }


    private fun buildCompositeCreateBody(items: List<ScanItem>): JSONObject {
        val allOrNone = false
        val compositeRequest = JSONArray()

        items.forEach { item ->
            val sobjectBody = JSONObject().apply {
                put(FIELD_CODE, item.code)
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