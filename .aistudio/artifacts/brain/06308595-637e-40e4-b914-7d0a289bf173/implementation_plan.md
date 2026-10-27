# PW Manager - Secure Offline Password Vault

PW Manager is a secure, 100% offline personal password vault built with Kotlin, Jetpack Compose, and Material Design 3. It features AES-256-GCM authenticated encryption, Android Keystore-backed biometrics (`BiometricPrompt` with `CryptoObject`), master password recovery, password generation, category filtering, search, and encrypted backup/restore.

## User Review & Critical Decisions

> [!IMPORTANT]
> The following user preferences have been confirmed and will be fully implemented:
- **Biometric Unlock Behavior**: Prompt automatically upon launching the app when biometric unlock is enabled.
- **Master Password Warning**: Require explicit confirmation checkbox acknowledging that there is no "forgot password" cloud recovery service.

- **Open Decision / Default**: Encrypted backup uses a separate user-defined backup passphrase (with clear warning about losing it).

---

## 1. Overview & Core Concept

- **What It Does**: Stores credentials locally with zero internet access and military-grade encryption (AES-256-GCM). Unlocks with biometrics or master password.
- **Target Audience**: Privacy-conscious Android users seeking an offline, ad-free, zero-knowledge password manager.
- **Key Value**: Complete data privacy, robust cryptographic key wrapping (Keystore + PBKDF2), clipboard auto-clear, screenshot protection, and encrypted export/import.

---

## 2. User Experience & Visual Design

- **Aesthetic Direction**: Premium Deep Navy (`#0A1128`) and Teal (`#00B4D8`) Material 3 theme with Dark, Light, and System modes. Clean typography, high contrast, accessible touch targets (min 48dp).
- **Key User Flows**:
  1. **First Launch / Setup**: Create master password with strength meter and explicit acknowledgement checkbox -> Option to enable biometric unlock.
  2. **Vault Unlock Screen**: Biometric prompt automatically triggers (if enabled) or prompt for master password.
  3. **Main Vault Dashboard**: Top app bar with PocketVault title, lock action, settings action. Search bar, category chips (All, Personal, Work, Banking, Social, Other), searchable entry list, and floating "+" button.
  4. **Entry Detail / Edit**: Title, username/email, password (with show/hide and generator), website URL, category, notes, and favorite toggle. One-tap copy for username and password with 30s auto-clear notification.
  5. **Settings**: Theme selection, biometric toggle, change master password (secure re-wrapping), encrypted backup & restore, and privacy info.
- **Security Protections**: `FLAG_SECURE` on window to prevent recent-app previews and screenshots. Immediate lock when app pauses or goes to background.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Cryptographic Architecture (AES-256-GCM & Key Wrapping)**
  - *Chosen Approach*: Generate random vault key (`SecureRandom`), encrypt all sensitive fields with AES-256-GCM and unique nonces. Protect vault key with two independent wrappers: PBKDF2-derived master key AND an Android Keystore authentication-required key.
  - *Why*: True zero-knowledge security; data cannot be read without master password or biometrics.
- **Decision 2: Local Storage (Room + Encrypted Fields)**
  - *Chosen Approach*: Store encrypted ciphertexts, nonces, and salts in local Room database. No plaintext metadata or index leaks.
  - *Why*: Efficient local search and persistence without cloud risk.
- **Decision 3: Zero Internet Permission**
  - *Chosen Approach*: Omit `INTERNET` permission entirely from `AndroidManifest.xml`.
  - *Why*: Guarantees 100% offline isolation.

---

## 4. Technical Architecture & Data Strategy

```
┌────────────────────────────────────────────────────────┐
│                      Jetpack Compose UI                │
│    (Dashboard, Entry Detail, Settings, Unlock, Setup)  │
└───────────────────────────┬────────────────────────────┘
                            │ StateFlow / Actions
┌───────────────────────────▼────────────────────────────┘
│                     VaultViewModel                     │
└───────────────────────────┬────────────────────────────┘
                            │
        ┌───────────────────┴───────────────────┐
        ▼                                       ▼
┌──────────────────────────────┐       ┌──────────────────────────────┐
│        CryptoManager         │       │        VaultRepository       │
│ (AES-GCM, PBKDF2, Keystore)  │       │       (Room Database)        │
└──────────────────────────────┘       └──────────────────────────────┘
```

- **Data Models**: `VaultEntryEntity` (id, title, username, encryptedPassword, encryptedUrl, category, notes, isFavorite, updatedAt), `VaultMetaEntity` (salt, kdfParams, encryptedVaultKeyMaster, encryptedVaultKeyBiometric, iv).
- **Clipboard Security**: Copies username/password, sets a 30s coroutine timer that checks if clipboard still holds the copied value before clearing.
- **Backup / Restore**: Encrypted JSON format containing versioned KDF parameters, salt, and encrypted entry list, protected by a dedicated backup passphrase.
