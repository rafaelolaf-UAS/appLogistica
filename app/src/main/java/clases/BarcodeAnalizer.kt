package clases

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.atomic.AtomicLong

class BarcodeAnalizer(
    private val onBarcodeDetected: (Barcode) -> Unit
) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_ALL_FORMATS
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    private val lasthandled = AtomicLong(0)
    private var lastValue: String? = null
    private val debounceMillis = 800L

    override fun analyze(imageProxy: ImageProxy){
        val mediaImage = imageProxy.image
        if( mediaImage == null){
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                for (bc in barcodes){
                    val raw = bc.rawValue ?: continue
                    val now = System.currentTimeMillis()
                    val last = lasthandled.get()
                    if(raw != lastValue || now - last > debounceMillis){
                        lastValue
                        lasthandled.set(now)
                        onBarcodeDetected(bc)
                        break
                    }
                }
            }
            .addOnFailureListener {

            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

}