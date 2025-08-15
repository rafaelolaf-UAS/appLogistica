package data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_items",
    indices = [Index(value = ["code"], unique = true)]
)

data class ScanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val quantity: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING",
    val lastError: String? = null
)
