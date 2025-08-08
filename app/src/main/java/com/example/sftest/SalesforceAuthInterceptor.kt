package com.example.sftest

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.ContextCompat
import okhttp3.Interceptor
import okhttp3.Response

class SalesforceAuthInterceptor(
    private val appContext: Context,
    private val prefs: SharedPreferences
) : Interceptor {

    companion object {
        private const val TAG = "SF_INTERCEPTOR"
        const val ACTION_SESSION_EXPIRED = "com.example.sftest.ACTION_SESSION_EXPIRED"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = prefs.getString("access_token", null)
        val newRequestBuilder = chain.request().newBuilder()

        if (!token.isNullOrEmpty()) {
            newRequestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(newRequestBuilder.build())

        if (response.code == 401) {
            Log.w(TAG, "401 Unauthorized - session invalid/expired. Forzar logout.")
            // Limpia credenciales y manda broadcast SOLO a tu app
            prefs.edit().clear().apply()
            val intent = Intent(ACTION_SESSION_EXPIRED).setPackage(appContext.packageName)
            appContext.sendBroadcast(intent)
        }
        return response
    }
}
