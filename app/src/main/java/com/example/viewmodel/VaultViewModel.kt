package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoManager
import com.example.data.VaultDatabase
import com.example.data.VaultMetaEntity
import com.example.data.VaultRepository
import com.example.model.VaultEntry
import com.example.model.UnlockMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.crypto.Cipher

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VaultRepository(VaultDatabase.getDatabase(application).vaultDao())
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _unlockMode = MutableStateFlow(UnlockMode.PASSWORD_ONLY)
    val unlockMode: StateFlow<UnlockMode> = _unlockMode.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(false)
    val isBiometricEnabled: StateFlow<Boolean> = _isBiometricEnabled.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    private var vaultKeyInMemory: ByteArray? = null
    private var entriesJob: Job? = null

    private val _entries = MutableStateFlow<List<VaultEntry>>(emptyList())
    val allEntries: StateFlow<List<VaultEntry>> = _entries.asStateFlow()
    val entries: StateFlow<List<VaultEntry>> = combine(_entries, _searchQuery, _selectedCategory) { list, query, category ->
        list.filter { entry ->
            val matchesCategory = category == "All" || entry.category.equals(category, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                    entry.title.contains(query, ignoreCase = true) ||
                    entry.username.contains(query, ignoreCase = true) ||
                    entry.url.contains(query, ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        checkInitialization()
    }

    fun checkInitialization() {
        viewModelScope.launch {
            val meta = repository.getMeta()
            _isInitialized.value = meta != null
            _unlockMode.value = UnlockMode.fromMeta(meta)
            _isBiometricEnabled.value = _unlockMode.value.requiresFingerprint
            _isLoading.value = false
        }
    }

    fun lockVault() {
        entriesJob?.cancel()
        entriesJob = null
        vaultKeyInMemory?.fill(0)
        vaultKeyInMemory = null
        _isUnlocked.value = false
        _entries.value = emptyList()
    }

    private fun xor(a: ByteArray, b: ByteArray): ByteArray {
        require(a.size == 32 && b.size == 32)
        return ByteArray(32) { (a[it].toInt() xor b[it].toInt()).toByte() }
    }

    private fun wrapKey(key: ByteArray, mode: UnlockMode, password: CharArray, cipher: Cipher?): VaultMetaEntity {
        require(!mode.requiresPassword || password.size >= 6)
        require(!mode.requiresFingerprint || cipher != null)
        val salt = CryptoManager.generateSalt()
        // In combined mode neither encrypted share can recover the vault key on its own.
        val passwordShare = if (mode == UnlockMode.PASSWORD_AND_FINGERPRINT) CryptoManager.generateVaultKey() else key
        val masterWrap = if (mode.requiresPassword) CryptoManager.encryptBytesWithKey(passwordShare,
            CryptoManager.deriveKeyFromPassword(password, salt)) else null
        val biometricShare = if (mode == UnlockMode.PASSWORD_AND_FINGERPRINT) xor(key, passwordShare) else key
        val biometricWrap = if (mode.requiresFingerprint) cipher!!.doFinal(biometricShare) else null
        return VaultMetaEntity(salt = salt,
            encryptedVaultKeyMaster = masterWrap?.cipherText ?: byteArrayOf(),
            masterKeyIv = masterWrap?.iv ?: byteArrayOf(),
            encryptedVaultKeyBiometric = biometricWrap,
            biometricKeyIv = if (mode.requiresFingerprint) cipher!!.iv else null,
            isBiometricEnabled = mode.requiresFingerprint, unlockMode = mode.name)
    }

    private fun openSession(key: ByteArray) {
        require(key.size == 32)
        vaultKeyInMemory?.fill(0)
        vaultKeyInMemory = key
        _isUnlocked.value = true
        loadEntries()
    }

    suspend fun setupVault(masterPassword: CharArray, mode: UnlockMode, biometricCipher: Cipher? = null): Boolean {
        return try {
            if (repository.getMeta() != null) return false
            val key = CryptoManager.generateVaultKey()
            repository.insertMeta(wrapKey(key, mode, masterPassword, biometricCipher))
            _unlockMode.value = mode
            _isBiometricEnabled.value = mode.requiresFingerprint
            _isInitialized.value = true
            openSession(key)
            true
        } catch (e: Exception) { false }
    }

    suspend fun unlockWithMasterPassword(masterPassword: CharArray): Boolean {
        return try {
            val meta = repository.getMeta() ?: return false
            if (UnlockMode.fromMeta(meta) != UnlockMode.PASSWORD_ONLY) return false
            val masterKey = CryptoManager.deriveKeyFromPassword(masterPassword, meta.salt)
            openSession(CryptoManager.decryptBytesWithKey(meta.encryptedVaultKeyMaster, meta.masterKeyIv, masterKey))
            true
        } catch (e: Exception) { false }
    }

    suspend fun unlockWithBiometric(cipher: Cipher, password: CharArray = charArrayOf()): Boolean {
        return try {
            val meta = repository.getMeta() ?: return false
            val mode = UnlockMode.fromMeta(meta)
            if (!mode.requiresFingerprint) return false
            val encrypted = meta.encryptedVaultKeyBiometric ?: return false
            val biometricShare = cipher.doFinal(encrypted)
            val key = if (mode == UnlockMode.PASSWORD_AND_FINGERPRINT) {
                val masterKey = CryptoManager.deriveKeyFromPassword(password, meta.salt)
                val passwordShare = CryptoManager.decryptBytesWithKey(meta.encryptedVaultKeyMaster, meta.masterKeyIv, masterKey)
                xor(passwordShare, biometricShare)
            } else biometricShare
            openSession(key)
            true
        } catch (e: Exception) { false }
    }

    suspend fun setUnlockMode(mode: UnlockMode, password: CharArray, cipher: Cipher? = null): Boolean {
        return try {
            if (!_isUnlocked.value) return false
            val key = vaultKeyInMemory ?: return false
            repository.insertMeta(wrapKey(key, mode, password, cipher))
            _unlockMode.value = mode
            _isBiometricEnabled.value = mode.requiresFingerprint
            true
        } catch (e: Exception) { false }
    }

    suspend fun getMeta(): VaultMetaEntity? = repository.getMeta()

    private fun loadEntries() {
        val key = vaultKeyInMemory ?: return
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            repository.getEntries(key).collect { list ->
                _entries.value = list
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addEntry(entry: VaultEntry) {
        val key = vaultKeyInMemory ?: return
        viewModelScope.launch {
            repository.addEntry(entry, key)
        }
    }

    fun updateEntry(entry: VaultEntry) {
        val key = vaultKeyInMemory ?: return
        viewModelScope.launch {
            repository.updateEntry(entry, key)
        }
    }

    suspend fun deleteEntry(id: Long): Boolean {
        if (!_isUnlocked.value || vaultKeyInMemory == null) return false
        return try {
            repository.deleteEntry(id)
            true
        } catch (e: Exception) { false }
    }

    suspend fun changeMasterPassword(oldPassword: CharArray, newPassword: CharArray): Boolean {
        try {
            val meta = repository.getMeta() ?: return false
            if (!_isUnlocked.value || newPassword.size < 6) return false
            if (!UnlockMode.fromMeta(meta).requiresPassword) return false
            val rawVaultKey = run {
                val oldMasterKey = CryptoManager.deriveKeyFromPassword(oldPassword, meta.salt)
                CryptoManager.decryptBytesWithKey(meta.encryptedVaultKeyMaster, meta.masterKeyIv, oldMasterKey)
            }

            val newSalt = CryptoManager.generateSalt()
            val newMasterKey = CryptoManager.deriveKeyFromPassword(newPassword, newSalt)
            val newMasterWrap = CryptoManager.encryptBytesWithKey(rawVaultKey, newMasterKey)

            val updatedMeta = meta.copy(
                salt = newSalt,
                encryptedVaultKeyMaster = newMasterWrap.cipherText,
                masterKeyIv = newMasterWrap.iv
            )
            repository.insertMeta(updatedMeta)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun getVaultKey(): ByteArray? = vaultKeyInMemory
}
