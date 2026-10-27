package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY isFavorite DESC, updatedAt DESC")
    fun getAllEntries(): Flow<List<VaultEntryEntity>>

    @Query("SELECT * FROM vault_meta WHERE id = 1")
    suspend fun getMeta(): VaultMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeta(meta: VaultMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: VaultEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: VaultEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: VaultEntryEntity)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM vault_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM vault_meta")
    suspend fun deleteMeta()
}
