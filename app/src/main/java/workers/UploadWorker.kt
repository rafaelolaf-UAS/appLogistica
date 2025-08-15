package workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import data.ScanRepository
import data.local.ScanItem
import data.remote.SalesforceUploader
import Interfaces.SalesforceApi
import Objects.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(
    private val ctx: Context,
    params: WorkerParameters
) : CoroutineWorker(ctx, params) {

    companion object {
        private const val TAG = "UploadWorker"
        // Ajusta batchSize si quieres (cuántos PENDING tomar por ejecución)
        private const val BATCH_SIZE = 500
        // Tamaño interno de los chunks que sube SalesforceUploader (ej: 25 para Composite)
        private const val CHUNK_SIZE = 25
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repo = ScanRepository.getInstance(ctx)
        val prefs = ctx.getSharedPreferences("SF_PREFS", Context.MODE_PRIVATE)

        try {
            // 1) Obtener pendientes
            val pending: List<ScanItem> = repo.getPendingBatch(limit = BATCH_SIZE)
            if (pending.isEmpty()) {
                Log.d(TAG, "No hay items PENDING. Nothing to upload.")
                return@withContext Result.success()
            }

            val allIds = pending.map { it.id }
            Log.d(TAG, "UploadWorker: found ${pending.size} pending items. Marking as SENDING.")

            // 2) Marcar como SENDING
            repo.markSending(allIds)

            // 3) Preparar API (puede lanzar IllegalStateException si no hay instance_url)
            val api: SalesforceApi = RetrofitClient.create(prefs, ctx)

            // 4) Iterar por chunks (esto permite reportar progreso tras cada chunk)
            val total = pending.size
            var sentSoFar = 0
            val failedAcc = mutableListOf<Pair<Long, String>>()
            val sentAcc = mutableListOf<Long>()

            // chunked por CHUNK_SIZE
            val chunks: List<List<ScanItem>> = pending.chunked(CHUNK_SIZE)
            for ((idx, chunk) in chunks.withIndex()) {
                Log.d(TAG, "Uploading chunk ${idx + 1}/${chunks.size} (size=${chunk.size})")

                // Intentar subir el chunk
                try {
                    val (sentIds, failedPairs) = SalesforceUploader.uploadCompositeBatch(api, chunk)
                    // marcar resultados parciales
                    if (sentIds.isNotEmpty()) {
                        repo.markSent(sentIds)
                        sentAcc.addAll(sentIds)
                    }
                    if (failedPairs.isNotEmpty()) {
                        // failedPairs: List<Pair<Long, String>>
                        val failedIds = failedPairs.map { it.first }
                        // Guardar estado FAILED y el mensaje
                        repo.markFailed(failedIds, failedPairs.joinToString(" | ") { it.second })
                        failedAcc.addAll(failedPairs)
                    }
                    sentSoFar += sentIds.size

                    // Reportar progreso: cada vez que termina un chunk
                    val prog = workDataOf("progress_sent" to sentSoFar, "progress_total" to total)
                    setProgressAsync(prog)

                } catch (chunkEx: Exception) {
                    Log.e(TAG, "Error subiendo chunk ${idx + 1}: ${chunkEx.message}", chunkEx)
                    // Marcar los ids del chunk como FAILED (por ahora con el mensaje de excepción)
                    val ids = chunk.map { it.id }
                    repo.markFailed(ids, "Chunk upload error: ${chunkEx.message}")
                    // Agregarlos al acumulador de fallos
                    failedAcc.addAll(ids.map { it to (chunkEx.message ?: "Error desconocido") })
                    // Seguimos con siguiente chunk (no rompemos todo), pero podría ser prudente retry global:
                    // return@withContext Result.retry()
                }
            }

            // 5) Resumen final
            val sentCount = sentAcc.size
            val failedCount = failedAcc.size
            Log.d(TAG, "UploadWorker finished: sent=$sentCount failed=$failedCount")

            // Opcional: limpiar enviados muy antiguos (repo.purgeSent()) si quieres
            // repo.purgeSent()

            val output = workDataOf("sent" to sentCount, "failed" to failedCount)
            return@withContext Result.success(output)

        } catch (e: IllegalStateException) {
            // Ej: instance_url no encontrada en prefs -> forzar re-login antes de reintentar
            Log.e(TAG, "UploadWorker fatal: ${e.message}", e)
            return@withContext Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "UploadWorker exception: ${e.message}", e)
            // Si ocurre un error general, marcar todos como PENDING para reintento
            try {
                val pendingAll = repo.getPendingBatch(limit = BATCH_SIZE)
                if (pendingAll.isNotEmpty()) repo.markFailed(pendingAll.map { it.id }, "Worker exception: ${e.message}")
            } catch (inner: Exception) {
                Log.w(TAG, "Failed to markFailed after exception: ${inner.message}", inner)
            }
            return@withContext Result.retry()
        }
    }
}
