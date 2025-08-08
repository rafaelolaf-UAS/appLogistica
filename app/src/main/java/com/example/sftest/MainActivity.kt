package com.example.sftest

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val clientId    = "3MVG96LA2t1yu9WJURmDwETIOPukz0ilhyA2EgsM22LToWw8dLi9IJ4XXnXS.aKwTeaqy2uk9xB13B9K6lU2j"
    private val redirectUri = "androidApp://auth/success"

    private val authHost = "https://test.salesforce.com" // sandbox
    // private val authHost = "https://login.salesforce.com" // producción

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this).also {
            it.settings.javaScriptEnabled = true
            it.settings.domStorageEnabled = true

            it.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    request?.url?.toString()?.let { url ->
                        if (handleRedirect(url)) return true
                    }
                    return false
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url != null && handleRedirect(url)) return true
                    return false
                }

                private fun handleRedirect(url: String): Boolean {
                    if (url.startsWith(redirectUri, ignoreCase = true)) {
                        val uri = Uri.parse(url)
                        val fragment = uri.fragment ?: ""
                        val params = fragment.split("&").mapNotNull {
                            val p = it.split("=")
                            if (p.size >= 2) p[0] to Uri.decode(p[1]) else null
                        }.toMap()

                        val token = params["access_token"]
                        val instanceUrl = params["instance_url"] // ya viene en el fragment para implicit flow
                        // Nota: implicit flow no retorna refresh_token
                        if (!token.isNullOrBlank() && !instanceUrl.isNullOrBlank()) {
                            getSharedPreferences("SF_PREFS", MODE_PRIVATE).edit()
                                .putString("access_token", token)
                                .putString("instance_url", instanceUrl)
                                .apply()

                            startActivity(Intent(this@MainActivity, HomeActivity::class.java))
                            finish()
                        } else {
                            // No token — mostrar error o reenviar a login
                        }
                        return true
                    }
                    return false
                }
            }
        }

        setContentView(webView)

        // Construir la URL con redirect ya codificado, solo una vez (evita duplicar redirect_uri)
        val encodedRedirect = Uri.encode(redirectUri)
        val fullLoginUrl = "$authHost/services/oauth2/authorize?response_type=token&client_id=$clientId&redirect_uri=$encodedRedirect"
        webView.loadUrl(fullLoginUrl)
    }
}
