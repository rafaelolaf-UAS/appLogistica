package com.example.sftest

import Objects.RetrofitClient
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import data.remote.SalesforceUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class scanMenu : Fragment(R.layout.fragment_scan_menu) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRecepcion = view.findViewById<MaterialButton>(R.id.btn_scan_recepcion)
        val btnInventario = view.findViewById<MaterialButton>(R.id.btn_scan_inventario)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_scan_cancel)

        btnRecepcion.setOnClickListener {
            createRecepcionAndOpenScanner(it)
        }
        btnInventario.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, scan())
                .addToBackStack(null)
                .commit()
        }
        btnCancel.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun createRecepcionAndOpenScanner(anchor: View){
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Creando Recepcion")
            .setView(ProgressBar(requireContext()))
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            try{
                val prefs = requireContext().getSharedPreferences("SF_PREFS", 0)
                val api = RetrofitClient.create(prefs, requireContext())

                val recepcionName = "Recepción ${java.time.Instant.now()}"

                val recepcionId = withContext(Dispatchers.IO){
                    SalesforceUploader.createRecepcion(api, recepcionName, emptyMap())
                }

                dialog.dismiss()

                val scanFrag = scan()
                val args = Bundle().apply { putString("recepcionId", recepcionId) }
                scanFrag.arguments = args
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, scanFrag)
                    .addToBackStack(null)
                    .commit()

                Snackbar.make(requireView(), "Recepción creada: $recepcionId", Snackbar.LENGTH_SHORT).show()
            } catch (ex: Exception){
                dialog.dismiss()
                ex.printStackTrace()
                Snackbar.make(requireView(), "Error creando la recepción: ${ex.message}", Snackbar.LENGTH_LONG).show()
            }
        }
    }
}