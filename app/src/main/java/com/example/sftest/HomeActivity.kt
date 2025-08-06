package com.example.sftest

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.TextView
import android.content.Intent
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
class HomeActivity : AppCompatActivity() {
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_home)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        prefs = getSharedPreferences("SF_PREFS", MODE_PRIVATE)
        val accessToken = prefs.getString("access_token", "No token found")

        findViewById<TextView>(R.id.tvToken).text = "Token:\n$accessToken"
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean{
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean{
        return when (item.itemId){
            R.id.action_logistica -> {
                //startActivity(Intent(this, LogisticaActivity::class.java))
                true
            }
            R.id.action_settings -> {
                //startActivity(Intent(this, LogisticaActivity::class.java))
                true
            }
            R.id.action_logout -> {
                prefs.edit().clear().apply()
                //startActivity(Intent(this, LogisticaActivity::class.java))
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}