package com.example.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val KEYSTORE_ALIAS = "PWManagerBiometricKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12
    private const val SALT_LENGTH_BYTE = 16
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BIT = 256

    private fun ensureBiometricKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            val spec = KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_LENGTH_BIT)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationValidityDurationSeconds(-1)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTE)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun generateVaultKey(): ByteArray {
        val key = ByteArray(32) // 256-bit
        SecureRandom().nextBytes(key)
        return key
    }

    fun deriveKeyFromPassword(password: CharArray, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BIT)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }

    fun encryptWithKey(plainText: String, secretKey: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return EncryptedData(cipherText, iv)
    }

    fun decryptWithKey(cipherText: ByteArray, iv: ByteArray, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        val plainBytes = cipher.doFinal(cipherText)
        return String(plainBytes, Charsets.UTF_8)
    }

    fun encryptBytesWithKey(data: ByteArray, secretKey: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(data)
        return EncryptedData(cipherText, iv)
    }

    fun decryptBytesWithKey(cipherText: ByteArray, iv: ByteArray, secretKey: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(cipherText)
    }

    fun getBiometricCipherForEncrypt(): Cipher {
        ensureBiometricKey()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    fun getBiometricCipherForDecrypt(iv: ByteArray): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        val secretKey = keyStore.getKey(KEYSTORE_ALIAS, null) as SecretKey
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher
    }

    fun generatePassword(
        length: Int = 16,
        useUpper: Boolean = true,
        useLower: Boolean = true,
        useNumbers: Boolean = true,
        useSymbols: Boolean = true
    ): String {
        val upperChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val lowerChars = "abcdefghijklmnopqrstuvwxyz"
        val numberChars = "0123456789"
        val symbolChars = "!@#$%^&*()_+-=[]{}|;:,.<>?"

        val charPool = StringBuilder()
        if (useUpper) charPool.append(upperChars)
        if (useLower) charPool.append(lowerChars)
        if (useNumbers) charPool.append(numberChars)
        if (useSymbols) charPool.append(symbolChars)

        if (charPool.isEmpty()) charPool.append(lowerChars + numberChars)

        val random = SecureRandom()
        val result = StringBuilder(length)
        for (i in 0 until length) {
            val index = random.nextInt(charPool.length)
            result.append(charPool[index])
        }
        return result.toString()
    }

    fun calculatePasswordStrength(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.WEAK
        var score = 0
        if (password.length >= 10) score++
        if (password.length >= 14) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 2 -> PasswordStrength.WEAK
            score <= 4 -> PasswordStrength.MEDIUM
            else -> PasswordStrength.STRONG
        }
    }
}

data class EncryptedData(
    val cipherText: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedData
        if (!cipherText.contentEquals(other.cipherText)) return false
        if (!iv.contentEquals(other.iv)) return false
        return true
    }
    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

enum class PasswordStrength {
    WEAK, MEDIUM, STRONG
}
