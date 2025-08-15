package data.local

import androidx.room.Dao
import androidx.room.*

@Dao
interface ScanDAO {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: ScanItem): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ScanItem>): List<Long>

    @Query("SELECT COUNT(*) FROM scan_items WHERE code = :code")
    suspend fun countByCode(code: String): Int

    @Query("SELECT * FROM scan_items WHERE status = :status ORDER BY createdAt LIMIT :limit")
    suspend fun getByStatusLimit(status: String, limit: Int): List<ScanItem>

    @Query("SELECT COUNT(*) FROM scan_items WHERE status = :status")
    suspend fun countByStatus(status: String): Int

    @Query("UPDATE scan_items SET status = :status WHERE id IN (:ids)")
    suspend fun updateStatusBulk(ids: List<Long>, status: String)

    @Query("UPDATE scan_items SET lastError = :error WHERE id = :id")
    suspend fun setError(id: Long, error: String)

    @Query("DELETE FROM scan_items WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM scan_items WHERE status = 'SENT'")
    suspend fun deleteAllSent()

    @Query("SELECT * FROM scan_items WHERE status = 'FAILED' ORDER BY createdAt")
    suspend fun getFailed(): List<ScanItem>

    @Query("SELECT * FROM scan_items ORDER BY createdAt DESC")
    suspend fun getAll(): List<ScanItem>

    @Delete
    suspend fun delete(item: ScanItem)

    @Query("SELECT * FROM scan_items WHERE status = :status ORDER BY createdAt DESC")
    suspend fun getByStatus(status: String): List<ScanItem>
}