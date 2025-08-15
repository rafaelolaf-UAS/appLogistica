package com.example.sftest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sftest.adapters.ScansAdapter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.RecyclerView
import data.ScanRepository
import data.local.ScanItem
import com.google.android.material.snackbar.Snackbar

class ScansListActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: ScansAdapter
    private lateinit var fabClearAll: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scans_list)

        // toolbar (opcional)
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        rv = findViewById(R.id.rvScans)
        fabClearAll = findViewById(R.id.fabClearAll)

        adapter = ScansAdapter { item -> confirmDelete(item) }

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter
        rv.addItemDecoration(DividerItemDecoration(this, DividerItemDecoration.VERTICAL))
        rv.setHasFixedSize(true)

        fabClearAll.setOnClickListener {
            confirmClearAll()
        }

        loadList()
    }

    private fun loadList() {
        lifecycleScope.launch {
            try {
                val repo = ScanRepository.getInstance(this@ScansListActivity)
                val list = repo.getAllScans()
                adapter.submitList(list)
            } catch (ex: Exception) {
                ex.printStackTrace()
                Snackbar.make(rv, "Error cargando lista", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmDelete(item: ScanItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Eliminar")
            .setMessage("¿Eliminar ${item.code}?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val repo = ScanRepository.getInstance(this@ScansListActivity)
                        repo.delete(item)
                        loadList()
                        Snackbar.make(rv, "Eliminado", Snackbar.LENGTH_SHORT).show()
                    } catch (ex: Exception) {
                        Snackbar.make(rv, "Error al eliminar", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun confirmClearAll() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Borrar todo")
            .setMessage("¿Eliminar todos los escaneos pendientes?")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        val repo = ScanRepository.getInstance(this@ScansListActivity)
                        val all = repo.getAllScans()
                        val ids = all.map { it.id }
                        if (ids.isNotEmpty()) {
                            repo.deleteByIds(ids)
                        }
                        loadList()
                        Snackbar.make(rv, "Todos los escaneos eliminados", Snackbar.LENGTH_SHORT).show()
                    } catch (ex: Exception) {
                        Snackbar.make(rv, "Error al borrar", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
