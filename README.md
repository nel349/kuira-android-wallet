# Kuira Wallet - Midnight Blockchain Android Wallet

A production-grade Android wallet for the Midnight blockchain, built with modern Android architecture and best practices.

## Project Overview

**Technology Stack:**
- Kotlin
- Jetpack Compose (pure Compose, no XML)
- Material3 Design
- Multi-module Clean Architecture
- Hilt Dependency Injection
- Coroutines & Flow

**Current Phase:** Phase 1 - Crypto Module
- BIP-39 mnemonic generation
- BIP-32 HD key derivation
- Schnorr signatures over secp256k1
- Bech32m address formatting

## Project Structure

```
kuira-android-wallet/
├── app/                              # Main application module
├── core/
│   ├── crypto/                       # Cryptography (BIP-39, BIP-32, Schnorr)
│   └── testing/                      # Shared test utilities
├── guidelines/                       # Engineering guidelines
│   ├── ARCHITECTURE_GUIDELINES.md
│   ├── KOTLIN_GUIDELINES.md
│   ├── SECURITY_GUIDELINES.md
│   ├── TESTING_GUIDELINES.md
│   ├── COMPOSE_GUIDELINES.md
│   └── MIDNIGHT_GUIDELINES.md
├── CLAUDE.md                         # Claude AI context
└── LEARNING_STRATEGY.md              # Collaboration approach
```

## Architecture Reference

This project uses [Now in Android](../now-in-android-reference/) as an architectural reference for:
- Multi-module structure
- Convention plugins
- Testing patterns
- Compose best practices

## Getting Started

### Prerequisites
- Android Studio Ladybug | 2024.2.1 or later
- JDK 17
- Android SDK 35
- Minimum Android version: API 24 (Android 7.0)

### Build and Run

```bash
# Clone the repository
git clone <repository-url>
cd kuira-android-wallet

# Build the project
./gradlew build

# Run tests
./gradlew test

# Install on device/emulator
./gradlew installDebug
```

## Development Guidelines

All code must follow the guidelines in the `/guidelines` directory:

- **Architecture:** Clean Architecture with multi-module structure
- **Kotlin:** Immutability, null safety, sealed classes
- **Security:** Never log secrets, wipe keys after use
- **Testing:** TDD with given-when-then structure
- **Compose:** State hoisting, side effects management

See `CLAUDE.md` for complete development context.

## Roadmap

### Phase 1: Crypto Module (Current) ✅
- BIP-39 mnemonic generation
- BIP-32 HD key derivation
- Schnorr signatures
- Secure storage

### Phase 2: Unshielded Transactions
- Midnight node RPC client
- Transaction building
- Balance fetching

### Phase 3: Shielded Transactions
- ZK proof integration
- Shielded wallet operations

### Phase 4: Indexer Integration
- Fast state synchronization
- Transaction history

### Phase 5: DApp Connector
- Deep link handling
- Contract signing

### Phase 6: UI & Polish
- Complete Compose UI
- Material3 design system
- Accessibility

## Contributing

This is a learning/production project. Development follows:
- Test-first approach when possible
- Security-first mindset
- Clean Architecture principles
- Modern Android best practices

## License

[License to be determined]

## Contact

[Contact information to be added]
