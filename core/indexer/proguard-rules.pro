# ProGuard rules for core:indexer module
#
# These rules prevent R8/ProGuard from breaking:
# - Ktor HTTP client (uses reflection)
# - kotlinx.serialization (uses code generation)
# - GraphQL JSON parsing

# ============================================================
# Ktor Client
# ============================================================

# Keep Ktor engine implementations
-keep class io.ktor.client.engine.cio.** { *; }
-keep class io.ktor.client.engine.** { *; }

# Keep Ktor plugins
-keep class io.ktor.client.plugins.** { *; }
-keep class io.ktor.client.features.** { *; }

# Keep WebSocket support
-keep class io.ktor.websocket.** { *; }

# Keep Ktor serialization
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.serialization.kotlinx.** { *; }

# ============================================================
# kotlinx.serialization
# ============================================================

# Keep serializers for data models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Serializer classes
-keep,includedescriptorclasses class com.midnight.kuira.core.indexer.model.**$$serializer { *; }
-keepclassmembers class com.midnight.kuira.core.indexer.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.midnight.kuira.core.indexer.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep @Serializable classes
-keep @kotlinx.serialization.Serializable class com.midnight.kuira.core.indexer.** { *; }

# Keep JSON serializers
-keep class kotlinx.serialization.json.** { *; }
-keep class kotlinx.serialization.internal.** { *; }

# ============================================================
# Coroutines
# ============================================================

-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ============================================================
# Midnight Indexer Models
# ============================================================

# Keep all model classes (contain blockchain data)
-keep class com.midnight.kuira.core.indexer.model.** { *; }

# Keep API interfaces
-keep interface com.midnight.kuira.core.indexer.api.** { *; }

# Keep verification classes (may use reflection in future)
-keep class com.midnight.kuira.core.indexer.verification.** { *; }

# Keep reorg detection classes
-keep class com.midnight.kuira.core.indexer.reorg.** { *; }

# ============================================================
# Security
# ============================================================

# Don't obfuscate security-sensitive classes (for debugging)
-keepnames class com.midnight.kuira.core.indexer.api.TlsConfiguration { *; }
-keepnames class com.midnight.kuira.core.indexer.verification.EventVerifier { *; }
-keepnames class com.midnight.kuira.core.indexer.reorg.ReorgDetector { *; }

# ============================================================
# Logging
# ============================================================

# Keep class names for better crash logs
-keepattributes SourceFile,LineNumberTable

# Keep Android Log calls
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Don't remove Log.w and Log.e (important errors)
-keep class android.util.Log {
    public static *** w(...);
    public static *** e(...);
}
