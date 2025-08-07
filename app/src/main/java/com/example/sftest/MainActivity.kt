package com.example.sftest

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
class MainActivity : AppCompatActivity() {
    private val clientId    = "3MVG96LA2t1yu9WJURmDwETIOPukz0ilhyA2EgsM22LToWw8dLi9IJ4XXnXS.aKwTeaqy2uk9xB13B9K6lU2j"
    private val redirectUri = "androidApp://auth/success"
    private val loginUrl    =
        "https://test.salesforce.com/services/oauth2/authorize" +
                "?response_type=token" +
                "&client_id=$clientId" +
                "&redirect_uri=$redirectUri"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this).also {
            it.settings.javaScriptEnabled = true
            it.settings.domStorageEnabled = true

            it.webViewClient = object : WebViewClient() {
                // Usar esta versión para mejor compatibilidad
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.let { url ->
                        if (handleRedirect(url.toString())) return true
                    }
                    return false
                }

                // Versión para dispositivos antiguos
                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && handleRedirect(url)) return true
                    return false
                }

                private fun handleRedirect(url: String): Boolean {
                    // 1. Usar comparación insensible a mayúsculas
                    if (url.lowercase().startsWith(redirectUri.lowercase())) {
                        // 2. Extraer parámetros del fragmento correctamente
                        val uri = Uri.parse(url)
                        val fragment = uri.fragment ?: ""

                        // 3. Parsear manualmente el fragmento
                        val params = fragment.split("&").associate { param ->
                            val parts = param.split("=")
                            if (parts.size >= 2) parts[0] to parts[1] else "" to ""
                        }

                        val token = params["access_token"]
                        val instanceUrl = params["instance_url"]
                        if (!token.isNullOrBlank()) {
                            getSharedPreferences("SF_PREFS", MODE_PRIVATE).edit()
                                .putString("access_token", token)
                                .putString("instance_url", instanceUrl)
                                .apply()

                            startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                            finish()
                        }
                        return true // Indica que hemos manejado la URL
                    }
                    return false
                }
            }
        }

        setContentView(webView)
        // 4. Codificar correctamente la redirect_uri
        val encodedRedirect = Uri.encode(redirectUri)
        val fullLoginUrl = "$loginUrl&redirect_uri=$encodedRedirect"
        webView.loadUrl(fullLoginUrl)
    }
}

