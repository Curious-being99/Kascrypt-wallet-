# KASCRYPT - Next-Generation Kaspa & Post-Quantum Wallet

[![Kaspa BlockDAG](https://img.shields.io/badge/Kaspa-BlockDAG-00d4aa.svg)](https://kaspa.org)
[![NIST FIPS 204](https://img.shields.io/badge/PQC-ML--DSA--65-7950f2.svg)](https://csrc.nist.gov/pubs/fips/204/final)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7f52ff.svg)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack-Compose%20M3-4285f4.svg)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

**KASCRYPT** is a secure, open-source, non-custodial Android wallet built for the Kaspa high-throughput GHOSTDAG / BlockDAG network. Combining native consensus cryptography with **NIST FIPS 204 (ML-DSA-65)** post-quantum digital signatures, KASCRYPT delivers enterprise-grade key derivation, real-time DAG telemetry, multi-output batch transactions, and UTXO consolidation in a fluid Material Design 3 interface.

---

## Key Features

### 1. Non-Custodial Core & Key Management
- **BIP-39 Mnemonic Architecture:** Generate 12- or 24-word standard recovery phrases or import existing keys.
- **Hierarchical Deterministic (HD) Derivation:** Fully implements BIP-44 path derivation (`m/44'/111111'/account'/branch/index`) for external receive and internal change addresses.
- **AES-256-GCM Keystore Encryption:** Mnemonics are encrypted with 256-bit AES-GCM using 65,536-iteration PBKDF2-HMAC-SHA256 derived keys and cryptographically secure random 12-byte IVs.
- **Hardware Biometrics & PIN:** Gated authorization utilizing Android BiometricPrompt (fingerprint and face authentication) with password fallbacks.
- **Clipboard Leak Prevention:** Sensitive clipboard copy operations enforce Android 13+ `PersistableBundle` with `ClipDescription.EXTRA_IS_SENSITIVE` to suppress clipboard snooping and cloud syncing.

### 2. Quantum-Resistant & Dual-Hybrid Cryptography (NIST FIPS 204)
- **ML-DSA-65 (Dilithium) Lattice Primitives:** Powered by Bouncy Castle 1.79, implementing NIST FIPS 204 Module-LWE/SIS polynomial lattices:
  - Matrix Dimension: $k = 6, \ell = 5$
  - Ring Modulus: $q = 8,380,417$
  - Public Key: 1,952 bytes | Private Key: 4,032 bytes | Signature: 3,309 bytes
- **Deterministic BIP-39 PQC Derivation:** Derives reproducible ML-DSA keypairs directly from the wallet's master mnemonic seed via Blake2b domain separation (`Kaspa_FIPS204_ML_DSA_65_Account_<index>`).
- **Dual-Hybrid Signatures:** Signs payloads simultaneously with **BIP-340 Schnorr** (secp256k1) and **ML-DSA-65**, providing classical and post-quantum quantum-resistant validation.
- **Hedged & Deterministic Signing Modes:** Choose between hedged signing (salted with external CSPRNG entropy to defend against side-channel/fault attacks) and pure deterministic signing (FIPS 204 Section 5.2).
- **Interactive Verification Suite:** Real-time signature validator with parameter inspection and bit-flip tamper detection.

### 3. Authentic Kaspa BlockDAG Consensus Engine
- **BIP-340 Schnorr Signatures:** Native secp256k1 elliptic curve point arithmetic with RFC 6979 / BIP-340 tagged nonce derivation (`BIP0340/nonce`, `BIP0340/aux`, `BIP0340/challenge`).
- **Consensus Sighash:** Computes Blake2b-256 keyed sighashes with `"TransactionSigningHash"` domain separation matching official Rusty Kaspa consensus.
- **Multi-Input Key Resolution:** Pre-derives and maps signing keys across receive and change branches (gap limit 30) for reliable multi-UTXO transactions.
- **Accurate Consensus Mass Estimation:** Real-time computation of compute and storage mass based on input/output counts, script pubkeys, and signature sizes.

### 4. Real-Time Telemetry & Multi-Network Support
- **Multi-Network Routing:** Instant switching between:
  - **Mainnet** (`https://api.kaspa.org`)
  - **Testnet 10** (`https://api-tn10.kaspa.org`)
  - **Testnet 11** (`https://api-tn11.kaspa.org`)
  - **Devnet & Simnet** (Local / Custom RPC)
  - **Custom Node Support:** Connect to self-hosted Kaspa nodes.
- **Live DAG Metrics Dashboard:**
  - Virtual DAA (Difficulty Adjustment Algorithm) Blue Score
  - Global BlockDAG Difficulty & Hashrate (PH/s)
  - Dynamic Block Reward calculation reflecting Kaspa's chromatic halving schedule
  - Network latency (ms) and connected peer count

### 5. Advanced Wallet Utilities
- **Live UTXO Scanner & Compounder:** Inspect active unspent outputs on-chain and consolidate fragmented UTXOs into a single output to optimize future transaction fees.
- **Mass Batch Transaction Builder:** Construct, sign, and broadcast multi-output transactions to multiple recipients in a single atomic transaction.
- **Address Explorer:** Query live balances, confirm UTXOs, and view on-chain activity for any Kaspa address.
- **Address Book & Contact Management:** Store and label frequently used Kaspa addresses locally.
- **QR Code Scanner & Generator:** Fast QR generation and camera scanning for instant address and payment requests.

---

## Technical Architecture

The application follows the **Model-View-ViewModel (MVVM)** pattern with Clean Architecture principles:

```
app/src/main/java/com/example/kaspawallet/
├── data/
│   ├── api/
│   │   └── KaspaApiClient.kt          # OkHttp REST client for Kaspa nodes & BlockDAG info
│   ├── crypto/
│   │   ├── KaspaCrypto.kt             # BIP-39, BIP-44, Blake2b, AES-GCM encryption
│   │   ├── KaspaSigner.kt             # BIP-340 Schnorr, consensus sighashes & batch builder
│   │   ├── KaspaMlDsa.kt              # NIST FIPS 204 (ML-DSA-65) & Dual-Hybrid engine
│   │   └── KaspaUtils.kt              # Unit conversion (Sompi/KAS), address formatting
│   ├── local/
│   │   ├── KaspaDatabase.kt           # Room Database definition with migrations
│   │   └── Dao.kt                     # DAOs for Wallets, Accounts, Transactions, Contacts
│   ├── model/
│   │   └── Models.kt                  # Domain entities, enums, network configurations
│   └── repository/
│       └── KaspaWalletRepository.kt   # Central repository orchestrating state, sync & crypto
└── ui/
    ├── KaspaViewModel.kt              # StateFlow UI state management & business logic
    ├── theme/                         # Material Design 3 palette, typography & shapes
    ├── dialogs/                       # Send, Receive, Transfer, Biometric & Backup Dialogs
    └── screens/
        ├── MainScreen.kt              # Primary navigation container with bottom navigation
        ├── HomeScreen.kt              # Balance hero, DAG metrics & quick action buttons
        ├── AccountsTab.kt             # Multi-account management & derivation explorer
        ├── TransactionsTab.kt         # Paginated transaction history with status filters
        ├── ToolsTab.kt                # UTXO compounder, batch sender, keygen & PQC suite
        ├── SettingsTab.kt             # Network switcher, custom RPC, biometric & security
        └── components/
            ├── MlDsaQuantumTools.kt   # Interactive NIST FIPS 204 PQC testing & signing UI
            └── QrCodeScanner.kt       # CameraX-based QR code reader
```

---

## Technology Stack

| Layer | Technologies |
| :--- | :--- |
| **Language** | Kotlin 2.0+ with Coroutines & StateFlow |
| **UI Framework** | Jetpack Compose with Material Design 3 (M3) |
| **PQC Cryptography** | Bouncy Castle 1.79 (`bcpkix-jdk18on`, `bcprov-jdk18on`) |
| **Classical Crypto** | BIP-39, BIP-44, BIP-340 Schnorr on Secp256k1, Blake2b-256 |
| **Local Persistence** | Android Jetpack Room with SQLite |
| **Networking** | OkHttp 4.12 with JSON serialization |
| **Camera & QR** | AndroidX CameraX & ZXing Core |
| **Security** | AndroidX Biometric, Android KeyStore, AES-256-GCM |

---

## Building and Running

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17 or JDK 21
- Android SDK 35 (compileSdk 35, minSdk 26)

### Build Commands

```bash
# Compile and build debug APK
gradle assembleDebug

# Run all local unit tests (cryptography, PQC, Room)
gradle :app:testDebugUnitTest

# Lint and static analysis
gradle lint
```

---

## Security Model & Best Practices

1. **Zero Telemetry / Zero Tracking:** No third-party analytics, tracking SDKs, or external advertising libraries are present.
2. **Deterministic Derivation:** All sensitive private keys (classical and post-quantum) are deterministically generated on-device from the user's master entropy.
3. **No Plaintext Key Storage:** Sensitive key material is never written to disk unencrypted.
4. **Memory Hygiene:** Sensitive byte arrays (seeds, private keys) are cleared and garbage collected promptly after cryptographic operations.

---

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.

```text
Copyright (c) 2026 Kaspa Wallet Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
```
