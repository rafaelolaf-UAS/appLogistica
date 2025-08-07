package Objects

import Interfaces.SalesforceApi
import android.content.SharedPreferences
import android.util.Log
import com.example.sftest.SalesforceAuthInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    fun create(prefs: SharedPreferences): SalesforceApi{
        val instanceUrl = prefs.getString("instance_url", null)
            ?: throw IllegalStateException("instance_url no encontrada")

        val base = if (instanceUrl.endsWith("/")) instanceUrl else "$instanceUrl/"

        Log.d("SF_INIT", "Retrofit baseUrl = $base")

        val client = OkHttpClient.Builder()
            .addInterceptor(SalesforceAuthInterceptor(prefs))
            .build()

        return Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SalesforceApi::class.java)
    }
}