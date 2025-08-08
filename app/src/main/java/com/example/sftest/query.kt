package com.example.sftest

import Interfaces.SalesforceApi
import Objects.RetrofitClient
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class query : Fragment(R.layout.fragment_query) {

    private lateinit var prefs: SharedPreferences
    private lateinit var api: SalesforceApi
    private lateinit var adapter: SimpleMapAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // No hacer trabajo de UI aquí
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireContext().getSharedPreferences("SF_PREFS", Context.MODE_PRIVATE)
        try {
            api = RetrofitClient.create(prefs, requireContext().applicationContext)
        } catch (e: IllegalStateException){
            Toast.makeText(requireContext(), "No hay url, Inicia sesión nuevameente", Toast.LENGTH_LONG).show()
            prefs.edit().clear().apply()
            startActivity(Intent(requireContext(), MainActivity::class.java))
            requireActivity().finish()
            return
        }


        val etSoql = view.findViewById<EditText>(R.id.etSoql)
        val btnQuery = view.findViewById<Button>(R.id.btnQuery)
        val rv = view.findViewById<RecyclerView>(R.id.rvResults)

        adapter = SimpleMapAdapter()
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        btnQuery.setOnClickListener {
            val soql = etSoql.text.toString().trim()
            if (soql.isEmpty()) {
                Toast.makeText(requireContext(), "Escribe una consulta SOQL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            doQuery(soql)
        }

        // Ejemplo opcional: precarga una consulta por defecto
        if (etSoql.text.isEmpty()) {
            etSoql.setText("SELECT Id, Name FROM Account LIMIT 10")
        }
    }

    private fun doQuery(soql: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = api.query(soql)
                adapter.submitList(resp.records)
                if (resp.totalSize == 0) {
                    Toast.makeText(requireContext(), "No se encontraron registros.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
