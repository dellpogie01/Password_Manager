package com.example.data

import com.example.crypto.CryptoManager
import com.example.model.VaultEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.crypto.spec.SecretKeySpec

class VaultRepository(private val dao: VaultDao) {

    fun getEntries(vaultKey: ByteArray): Flow<List<VaultEntry>> {
        val secretKey = SecretKeySpec(vaultKey, "AES")
        return dao.getAllEntries().map { entities ->
            entities.map { entity ->
                try {
                    val title = CryptoManager.decryptWithKey(entity.titleCipher, entity.titleIv, secretKey)
                    val username = CryptoManager.decryptWithKey(entity.usernameCipher, entity.usernameIv, secretKey)
                    val password = CryptoManager.decryptWithKey(entity.passwordCipher, entity.passwordIv, secretKey)
                    val url = CryptoManager.decryptWithKey(entity.urlCipher, entity.urlIv, secretKey)
                    val notes = CryptoManager.decryptWithKey(entity.notesCipher, entity.notesIv, secretKey)
                    VaultEntry(
                        id = entity.id,
                        title = title,
                        username = username,
                        password = password,
                        url = url,
                        category = entity.category,
                        notes = notes,
                        isFavorite = entity.isFavorite,
                        updatedAt = entity.updatedAt
                    )
                } catch (e: Exception) {
                    VaultEntry(
                        id = entity.id,
                        title = "[Decryption Error]",
                        username = "",
                        password = "",
                        category = entity.category
                    )
                }
            }
        }
    }

    suspend fun getMeta(): VaultMetaEntity? = dao.getMeta()

    suspend fun insertMeta(meta: VaultMetaEntity) = dao.insertMeta(meta)

    suspend fun addEntry(entry: VaultEntry, vaultKey: ByteArray): Long {
        val secretKey = SecretKeySpec(vaultKey, "AES")
        val tEnc = CryptoManager.encryptWithKey(entry.title, secretKey)
        val uEnc = CryptoManager.encryptWithKey(entry.username, secretKey)
        val pEnc = CryptoManager.encryptWithKey(entry.password, secretKey)
        val urlEnc = CryptoManager.encryptWithKey(entry.url, secretKey)
        val nEnc = CryptoManager.encryptWithKey(entry.notes, secretKey)

        val entity = VaultEntryEntity(
            titleCipher = tEnc.cipherText,
            titleIv = tEnc.iv,
            usernameCipher = uEnc.cipherText,
            usernameIv = uEnc.iv,
            passwordCipher = pEnc.cipherText,
            passwordIv = pEnc.iv,
            urlCipher = urlEnc.cipherText,
            urlIv = urlEnc.iv,
            category = entry.category,
            notesCipher = nEnc.cipherText,
            notesIv = nEnc.iv,
            isFavorite = entry.isFavorite,
            updatedAt = System.currentTimeMillis()
        )
        return dao.insertEntry(entity)
    }

    suspend fun updateEntry(entry: VaultEntry, vaultKey: ByteArray) {
        val secretKey = SecretKeySpec(vaultKey, "AES")
        val tEnc = CryptoManager.encryptWithKey(entry.title, secretKey)
        val uEnc = CryptoManager.encryptWithKey(entry.username, secretKey)
        val pEnc = CryptoManager.encryptWithKey(entry.password, secretKey)
        val urlEnc = CryptoManager.encryptWithKey(entry.url, secretKey)
        val nEnc = CryptoManager.encryptWithKey(entry.notes, secretKey)

        val entity = VaultEntryEntity(
            id = entry.id,
            titleCipher = tEnc.cipherText,
            titleIv = tEnc.iv,
            usernameCipher = uEnc.cipherText,
            usernameIv = uEnc.iv,
            passwordCipher = pEnc.cipherText,
            passwordIv = pEnc.iv,
            urlCipher = urlEnc.cipherText,
            urlIv = urlEnc.iv,
            category = entry.category,
            notesCipher = nEnc.cipherText,
            notesIv = nEnc.iv,
            isFavorite = entry.isFavorite,
            updatedAt = System.currentTimeMillis()
        )
        dao.updateEntry(entity)
    }

    suspend fun deleteEntry(id: Long) {
        dao.deleteEntryById(id)
    }

    suspend fun clearVault() {
        dao.deleteAllEntries()
        dao.deleteMeta()
    }
}
