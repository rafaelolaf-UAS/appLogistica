package com.example.sftest

import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.Response

class SalesforceAuthInterceptor(private val prefs: SharedPreferences) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = prefs.getString("access_token", "") ?: ""
        val originalRequest = chain.request()

        android.util.Log.d("SF_REQ", "request url: ${originalRequest.url()}")

        val newReq = originalRequest.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()

        val response = chain.proceed(newReq)
        android.util.Log.d("SF_RESP", "Response code: ${response.code()} for ${newReq.url()}")
        return response
    }
}
