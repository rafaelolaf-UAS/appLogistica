package com.example.sftest

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import androidx.work.WorkManager
import clases.BarcodeAnalizer
import com.example.sftest.adapters.ScansAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Fragment de escaneo:
 * - One-shot scan (el usuario pulsa FAB para activar el próximo escaneo)
 * - Indicación visual en overlay (verde) cuando hay detección
 * - NO permite insertar el mismo código dos veces (repositorio con unique index)
 * - No muestra resultCard; los resultados se ven en la lista (ScansListActivity)
 *
 * IDs requeridos en fragment_scan.xml:
 * - PreviewView -> @+id/previewView
 * - ScannerOverlay -> @+id/scannerOverlay
 * - FloatingActionButton (scan) -> @+id/btnScanOnce
 * - Button (upload) -> @+id/btnUploadNow
 * - TextView opcional para pendientes -> @+id/tvPendingCount
 * - RecyclerView opcional en el mismo fragment para preview -> @+id/rvPreview (opcional)
 */
class scan : Fragment(R.layout.fragment_scan) {

    companion object {
        private const val TAG = "ScanFragment"
        private const val PREFS_NAME = "SF_PREFS"
        private const val PREF_KEY_CAMERA_REQUESTED = "camera_permission_requested"
        private const val UNIQUE_UPLOAD_WORK_NAME = "upload_scans"
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var previewView: PreviewView
    private lateinit var tvPendingCount: android.widget.TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var btnUploadNow: android.widget.Button
    private lateinit var btnScanOnce: FloatingActionButton
    private lateinit var overlay: ScannerOverlay

    // permiso
    private var permissionRequestInProgress = false
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRequestInProgress = false
            Log.d(TAG, "Permission result: granted=$granted")
            if (granted) {
                prefs.edit().putBoolean(PREF_KEY_CAMERA_REQUESTED, false).apply()
                startCameraSafe()
            } else {
                handlePermissionDenied()
            }
        }

    // one-shot manual
    private var scanEnabled = false

    // debounce
    private var lastScanValue: String? = null
    private var lastScanTime = 0L
    private val DEBOUNCE_MS = 500L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        previewView = view.findViewById(R.id.previewView)
        overlay = view.findViewById(R.id.scannerOverlay)
        btnUploadNow = view.findViewById(R.id.btnUploadNow)
        btnScanOnce = view.findViewById(R.id.btnScanOnce)
        tvPendingCount = view.findViewById(R.id.tvPendingCount)

        // asegurar FAB arriba y con elevación para no taparse
        btnScanOnce.bringToFront()
        ViewCompat.setElevation(btnScanOnce, 30f)
        btnScanOnce.translationZ = 30f

        cameraExecutor = Executors.newSingleThreadExecutor()

        btnScanOnce.setOnClickListener {
            // habilita el próximo scan (one-shot)
            scanEnabled = true
            // feedback visual inmediato
            overlay.setDetecting(true)
            // apagar indicador si no se detecta en X ms
            viewLifecycleOwner.lifecycleScope.launch {
                delay(900)
                overlay.setDetecting(false)
            }
            Snackbar.make(requireView(), "Listo para escanear. Alinea el código y espera la detección.", Snackbar.LENGTH_SHORT).show()
        }

        btnUploadNow.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Subir Elementos")
                .setMessage("¿Deseas subir todos los items escaneados ahora?")
                .setPositiveButton("Sí") { _, _ -> triggerUploadNow() }
                .setNegativeButton("No", null)
                .show()
        }

        // Observa WorkManager (si ya lo tenías)
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(UNIQUE_UPLOAD_WORK_NAME)
            .observe(viewLifecycleOwner) { workInfos ->
                if (workInfos == null || workInfos.isEmpty()) {
                    btnUploadNow.isEnabled = true
                    return@observe
                }
                val info = workInfos[0]
                when (info.state) {
                    WorkInfo.State.ENQUEUED -> {
                        btnUploadNow.isEnabled = false
                        btnUploadNow.text = "En cola..."
                    }
                    WorkInfo.State.RUNNING -> {
                        btnUploadNow.isEnabled = false
                        btnUploadNow.text = "Subiendo..."
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        btnUploadNow.isEnabled = true
                        btnUploadNow.text = "Subir ahora"
                        val output = info.outputData
                        val sentFinal = output.getInt("sent", -1)
                        val failedFinal = output.getInt("failed", -1)
                        val msg = if (sentFinal >= 0) "Subida completada. Enviados: $sentFinal. Fallidos: $failedFinal" else "Subida completada."
                        Snackbar.make(requireView(), msg, Snackbar.LENGTH_LONG).show()
                        updatePendingCount()
                    }
                    WorkInfo.State.FAILED -> {
                        btnUploadNow.isEnabled = true
                        btnUploadNow.text = "Subir ahora"
                        Snackbar.make(requireView(), "La subida falló. Reintenta", Snackbar.LENGTH_LONG).show()
                        updatePendingCount()
                    }
                    WorkInfo.State.CANCELLED -> {
                        btnUploadNow.isEnabled = true
                        btnUploadNow.text = "Subir ahora"
                        updatePendingCount()
                    }
                    else -> {}
                }
            }

        // Siempre actualiza pendientes al crear vista
        updatePendingCount()

        tvPendingCount.setOnClickListener {
            val intent = Intent(requireContext(), ScansListActivity::class.java)
            intent.putExtra("filter", "pending")
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissionAndStart()
        updatePendingCount()
        btnScanOnce.bringToFront()
    }

    private fun checkPermissionAndStart() {
        val ctx = requireContext()
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val requestedBefore = prefs.getBoolean(PREF_KEY_CAMERA_REQUESTED, false)
        val shouldShowRationale = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        val permDenied = requestedBefore && !shouldShowRationale && !granted

        Log.d(TAG, "checkPermissionAndStart -> granted=$granted, requestedBefore=$requestedBefore, shouldShowRationale=$shouldShowRationale, inProgress=$permissionRequestInProgress")

        when {
            granted -> {
                prefs.edit().putBoolean(PREF_KEY_CAMERA_REQUESTED, false).apply()
                startCameraSafe()
            }
            permissionRequestInProgress -> {
                Log.d(TAG, "Permiso ya en progreso, esperando resultado")
            }
            permDenied -> {
                showPermanentDeniedDialog()
            }
            shouldShowRationale -> {
                showRationaleDialog()
            }
            else -> {
                requestCameraPermission()
            }
        }
    }

    private fun requestCameraPermission() {
        permissionRequestInProgress = true
        prefs.edit().putBoolean(PREF_KEY_CAMERA_REQUESTED, true).apply()
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permiso de cámara requerido")
            .setMessage("La aplicación necesita acceder a la cámara para escanear códigos y documentos. Por favor concede el permiso.")
            .setPositiveButton("Permitir") { _, _ -> requestCameraPermission() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showPermanentDeniedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permiso bloqueado")
            .setMessage("Has bloqueado el permiso de cámara para esta app. Abre Ajustes para habilitarlo.")
            .setPositiveButton("Abrir Ajustes") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun handlePermissionDenied() {
        val requestedBefore = prefs.getBoolean(PREF_KEY_CAMERA_REQUESTED, false)
        val permanentlyDenied = requestedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)

        if (permanentlyDenied) {
            showPermanentDeniedDialog()
        } else {
            Snackbar.make(requireView(), "Permiso denegado", Snackbar.LENGTH_LONG)
                .setAction("Intentar de nuevo") { requestCameraPermission() }
                .show()
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(requireView(), "No se pueden abrir ajustes", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun startCameraSafe() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Log.w(TAG, "startCameraSafe: permiso NO concedido")
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            val analyzer = BarcodeAnalizer { barcode ->
                // Si hay texto -> detection visual (verde) y si scanEnabled true -> guardar
                val raw = barcode.rawValue ?: return@BarcodeAnalizer

                // Mostrar borde verde momentáneamente cuando el detector "ve" algo
                requireActivity().runOnUiThread { overlay.setDetecting(true) }
                viewLifecycleOwner.lifecycleScope.launch {
                    delay(700)
                    requireActivity().runOnUiThread { overlay.setDetecting(false) }
                }

                // Si el usuario no pidió guardar, no guardamos (solo visual)
                if (!scanEnabled) return@BarcodeAnalizer

                // debounce para evitar guardados rápidos duplicados
                val now = System.currentTimeMillis()
                if (raw == lastScanValue && now - lastScanTime < DEBOUNCE_MS) return@BarcodeAnalizer
                lastScanValue = raw
                lastScanTime = now
                scanEnabled = false // one-shot: requerir pulsar FAB otra vez

                // Intentar insertar en DB (ScanRepository evita duplicados)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val repo = data.ScanRepository.getInstance(requireContext())
                        val added = repo.addScan(raw, 1)
                        requireActivity().runOnUiThread {
                            if (added) {
                                Snackbar.make(requireView(), "Escaneado: $raw", Snackbar.LENGTH_SHORT).show()
                                updatePendingCount()
                            } else {
                                // ya existía
                                Snackbar.make(requireView(), "Código ya escaneado", Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    } catch (ex: Exception) {
                        Log.e(TAG, "Error guardando escaneo: ${ex.message}", ex)
                        requireActivity().runOnUiThread {
                            Snackbar.make(requireView(), "Error guardando escaneo", Snackbar.LENGTH_LONG).show()
                        }
                    } finally {
                        // feedback final
                        viewLifecycleOwner.lifecycleScope.launch {
                            delay(500)
                            requireActivity().runOnUiThread { overlay.setDetecting(false) }
                        }
                    }
                }
            }

            analysis.setAnalyzer(cameraExecutor, analyzer)
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, analysis)
            } catch (exc: Exception) {
                Log.e(TAG, "Error binding camera use cases", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun updatePendingCount() {
        tvPendingCount?.let { tv ->
            viewLifecycleOwner.lifecycleScope.launch {
                val repo = data.ScanRepository.getInstance(requireContext())
                val count = repo.countPending()
                tv.text = "Pendientes: $count"
            }
        }
    }

    private fun triggerUploadNow() {
        viewLifecycleOwner.lifecycleScope.launch {
            val repo = data.ScanRepository.getInstance(requireContext())
            val pendingCount = repo.countPending()
            if (pendingCount <= 0) {
                Snackbar.make(requireView(), "No hay items pendientes para subir", Snackbar.LENGTH_SHORT).show()
                return@launch
            }

            btnUploadNow.isEnabled = false
            btnUploadNow.text = "En cola..."
            workers.UploadScheduler.enqueueOnce(requireContext())
            Snackbar.make(requireView(), "Se encoló la subida ($pendingCount items).", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}
