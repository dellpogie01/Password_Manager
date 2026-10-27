package com.example.model

import com.example.data.VaultMetaEntity

enum class UnlockMode(val label: String, val requiresPassword: Boolean, val requiresFingerprint: Boolean) {
    PASSWORD_ONLY("Master password only", true, false),
    FINGERPRINT_ONLY("Biometric fingerprint only", false, true),
    PASSWORD_AND_FINGERPRINT("Master password + fingerprint", true, true);

    companion object {
        fun fromMeta(meta: VaultMetaEntity?): UnlockMode =
            entries.find { it.name == meta?.unlockMode }
                ?: if (meta?.isBiometricEnabled == true && meta.encryptedVaultKeyBiometric != null && meta.biometricKeyIv != null)
                    FINGERPRINT_ONLY else PASSWORD_ONLY
    }
}
