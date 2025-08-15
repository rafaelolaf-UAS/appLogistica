package data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import data.local.AppDatabase
import data.local.ScanItem

class ScanRepository private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context.applicationContext)
    private val dao = db.scanDao()

    companion object {
        @Volatile
        private var INSTANCE: ScanRepository? = null

        fun getInstance(context: Context): ScanRepository {
            // Double-check locking pattern
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    suspend fun addScan(code: String, quantity: Int = 1): Boolean {
        val normilized = code.trim()
        val exists = dao.countByCode(normilized) > 0
        if(exists) return false
        val id = dao.insert(ScanItem(code = normilized, quantity = quantity))
        return id >= 0
    }

    suspend fun addScans(codes: List<String>): List<Long> =
        withContext(Dispatchers.IO) {
            val items = codes.map { ScanItem(code = it, quantity = 1) }
            dao.insertAll(items)
        }

    suspend fun getPendingBatch(limit: Int = 500) =
        withContext(Dispatchers.IO) { dao.getByStatusLimit("PENDING", limit) }

    suspend fun markSending(ids: List<Long>) =
        withContext(Dispatchers.IO) { dao.updateStatusBulk(ids, "SENDING") }

    suspend fun markSent(ids: List<Long>) =
        withContext(Dispatchers.IO) { dao.updateStatusBulk(ids, "SENT") }

    suspend fun markFailed(ids: List<Long>, errorMsg: String) =
        withContext(Dispatchers.IO) {
            dao.updateStatusBulk(ids, "FAILED")
            ids.forEach { dao.setError(it, errorMsg.take(500)) }
        }

    suspend fun retryFailed() =
        withContext(Dispatchers.IO) {
            val failed = dao.getFailed()
            if (failed.isNotEmpty()) dao.updateStatusBulk(failed.map { it.id }, "PENDING")
        }

    suspend fun countPending(): Int =
        withContext(Dispatchers.IO) { dao.countByStatus("PENDING") }

    suspend fun purgeSent() =
        withContext(Dispatchers.IO) { dao.deleteAllSent() }

    suspend fun getAllScans() = dao.getAll()
    suspend fun getPending() = dao.getByStatus("PENDING")
    suspend fun delete(item: ScanItem) = dao.delete(item)
    suspend fun deleteByIds(ids: List<Long>) = dao.deleteByIds(ids)
}
