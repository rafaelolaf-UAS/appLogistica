package com.example.sftest

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import clases.BarcodeAnalizer
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class scan : Fragment(R.layout.fragment_scan) {

    companion object {
        private const val TAG = "ScanFragment"
    }

    private lateinit var previewView: PreviewView
    private lateinit var resultCard: View
    private lateinit var tvResult: android.widget.TextView
    private lateinit var cameraExecutor: ExecutorService

    // Flag para no re-intentar inmediatamente en bucle
    private var permissionRequestInProgress = false

    // Registrar launcher como propiedad (antes de onCreate/onViewCreated)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            permissionRequestInProgress = false
            Log.d(TAG, "Permission result: granted=$granted")
            if (granted) {
                startCameraSafe()
            } else {
                // Si el usuario denegó -> comprobar si mostramos rationale o abrir ajustes
                handlePermissionDenied()
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        previewView = view.findViewById(R.id.previewView)
        resultCard = view.findViewById(R.id.resultCard)
        tvResult = view.findViewById(R.id.tvResult)
        cameraExecutor = Executors.newSingleThreadExecutor()

        resultCard.setOnClickListener {
            val text = tvResult.text.toString()
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Resultado")
                .setMessage(text)
                .setNeutralButton("Copiar") { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("barcode", text))
                    Snackbar.make(requireView(), "Copiado al portapapeles", Snackbar.LENGTH_SHORT).show()
                }
                .setPositiveButton("Cerrar", null)
                .show()
        }

        // Inicia el flujo de permisos una sola vez (no en bucle)
        checkPermissionAndStart()
    }

    private fun checkPermissionAndStart() {
        val ctx = requireContext()
        when {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                // permiso ya concedido
                startCameraSafe()
            }
            permissionRequestInProgress -> {
                // ya pedimos permiso y estamos esperando resultado -> no pedir de nuevo
                Log.d(TAG, "Permiso ya en progreso, esperando resultado")
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Mostrar explicación y luego solicitar permiso
                showRationaleDialog()
            }
            else -> {
                // Primera vez o usuario seleccionó "No preguntar" anteriormente
                requestCameraPermission()
            }
        }
    }

    private fun requestCameraPermission() {
        permissionRequestInProgress = true
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun showRationaleDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permiso de cámara requerido")
            .setMessage("La aplicación necesita acceder a la cámara para escanear códigos y documentos. Por favor concede el permiso.")
            .setPositiveButton("Permitir") { _, _ ->
                requestCameraPermission()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun handlePermissionDenied() {
        // Si shouldShowRequestPermissionRationale devuelve false tras una denegación previa,
        // es probable que el usuario seleccionó "No preguntar de nuevo"
        if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            // Mostrar diálogo con opción de ir a Ajustes
            AlertDialog.Builder(requireContext())
                .setTitle("Permiso denegado permanentemente")
                .setMessage("El permiso de cámara fue denegado permanentemente. Abre Ajustes para habilitarlo.")
                .setPositiveButton("Abrir Ajustes") { _, _ -> openAppSettings() }
                .setNegativeButton("Cancelar", null)
                .show()
        } else {
            // Denegó sin marcar "no preguntar" — puede volver a solicitar en otro momento
            Snackbar.make(requireView(), "Permiso denegado", Snackbar.LENGTH_LONG)
                .setAction("Intentar de nuevo") { requestCameraPermission() }
                .show()
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(requireView(), "No se pueden abrir ajustes", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun startCameraSafe() {
        // Verificar permiso justo antes de iniciar la cámara
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
                val raw = barcode.rawValue ?: ""
                showResult(raw)
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

    private fun showResult(text: String) {
        requireActivity().runOnUiThread {
            tvResult.text = text
            resultCard.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}
