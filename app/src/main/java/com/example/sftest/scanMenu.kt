package com.example.sftest

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class scanMenu : Fragment(R.layout.fragment_scan_menu) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnRecepcion = view.findViewById<MaterialButton>(R.id.btn_scan_recepcion)
        val btnInventario = view.findViewById<MaterialButton>(R.id.btn_scan_inventario)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btn_scan_cancel)

        btnRecepcion.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, scan())
                .addToBackStack(null)
                .commit()
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
}