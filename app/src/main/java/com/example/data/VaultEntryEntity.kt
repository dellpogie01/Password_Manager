package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_entries")
data class VaultEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val titleCipher: ByteArray,
    val titleIv: ByteArray,
    val usernameCipher: ByteArray,
    val usernameIv: ByteArray,
    val passwordCipher: ByteArray,
    val passwordIv: ByteArray,
    val urlCipher: ByteArray,
    val urlIv: ByteArray,
    val category: String, // Personal, Work, Banking, Social, Other
    val notesCipher: ByteArray,
    val notesIv: ByteArray,
    val isFavorite: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VaultEntryEntity
        if (id != other.id) return false
        if (!titleCipher.contentEquals(other.titleCipher)) return false
        if (!usernameCipher.contentEquals(other.usernameCipher)) return false
        if (!passwordCipher.contentEquals(other.passwordCipher)) return false
        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}
