package com.example.sftest

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)

        val prefs: SharedPreferences = getSharedPreferences("SF_PREFS", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "No token found")

        val textView = TextView(this).apply{
            text = "Access Token:\n$accessToken"
            textSize = 16f
            setPadding(16, 16, 16, 16)
        }
        setContentView(textView)
    }
}