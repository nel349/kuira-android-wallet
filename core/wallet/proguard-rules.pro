# ProGuard rules for core:wallet module
#
# These rules prevent R8/ProGuard from breaking:
# - Balance calculation logic
# - Event deserialization (future JNI/FFI)
# - BigInteger arithmetic

# ============================================================
# Midnight Wallet Models
# ============================================================

# Keep all model classes (contain financial data)
-keep class com.midnight.kuira.core.wallet.model.** { *; }

# Keep balance calculation classes
-keep class com.midnight.kuira.core.wallet.balance.** { *; }

# Keep deserializer interfaces (JNI/FFI will use these)
-keep interface com.midnight.kuira.core.wallet.deserializer.** { *; }
-keep class com.midnight.kuira.core.wallet.deserializer.** { *; }

# ============================================================
# BigInteger (Financial Calculations)
# ============================================================

# Keep BigInteger methods (used for balance calculations)
-keep class java.math.BigInteger { *; }
-keep class java.math.BigDecimal { *; }

# Keep BigInteger constructors and operators
-keepclassmembers class java.math.BigInteger {
    public <init>(...);
    public *** add(...);
    public *** subtract(...);
    public *** multiply(...);
    public *** divide(...);
    public *** compareTo(...);
}

# ============================================================
# Exception Classes
# ============================================================

# Keep exception classes for error handling
-keep class com.midnight.kuira.core.wallet.balance.BalanceException { *; }
-keep class com.midnight.kuira.core.wallet.balance.BalanceUnderflowException { *; }
-keep class com.midnight.kuira.core.wallet.balance.BalanceOverflowException { *; }
-keep class com.midnight.kuira.core.wallet.deserializer.DeserializationException { *; }

# Keep exception stack traces
-keepattributes SourceFile,LineNumberTable

# ============================================================
# JNI/FFI (Phase 4B)
# ============================================================

# Keep native methods for future JNI integration
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep classes that will interface with native code
# (Uncomment when implementing real deserializer in Phase 4B)
# -keep class com.midnight.kuira.core.wallet.deserializer.NativeDeserializer { *; }

# ============================================================
# Kotlin
# ============================================================

# Keep Kotlin metadata for reflection
-keepattributes *Annotation*

# Keep data class copy methods
-keepclassmembers class com.midnight.kuira.core.wallet.model.** {
    *** copy(...);
    *** component*();
}

# Keep companion objects
-keepclassmembers class com.midnight.kuira.core.wallet.** {
    *** Companion;
}

# ============================================================
# Coroutines
# ============================================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================
# Security
# ============================================================

# Don't obfuscate financial calculation classes (for auditing)
-keepnames class com.midnight.kuira.core.wallet.balance.BalanceCalculator { *; }
-keepnames class com.midnight.kuira.core.wallet.model.Balance { *; }
-keepnames class com.midnight.kuira.core.wallet.model.LedgerEvent { *; }

# ============================================================
# Logging
# ============================================================

# Remove debug logs in release builds
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

# Keep warning and error logs
-keep class android.util.Log {
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ============================================================
# Optimization
# ============================================================

# Allow aggressive optimization for balance calculations
# (BigInteger operations are already heavily optimized)
-optimizations !code/simplification/arithmetic

# Don't warn about missing classes from other modules
-dontwarn com.midnight.kuira.core.crypto.**
-dontwarn com.midnight.kuira.core.indexer.**
