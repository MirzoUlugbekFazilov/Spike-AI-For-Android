# ============================================================================
# Spike AI — R8 / ProGuard keep rules for release (minify + resource shrink).
# Most AndroidX / Compose / MLKit / CameraX artifacts ship their own consumer
# rules; the entries below cover the reflection- and serialization-heavy parts
# (kotlinx.serialization, Ktor, Supabase) that R8 cannot infer on its own.
# ============================================================================

# Preserve metadata R8 needs for generics, annotations and serialization.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault, *Annotation*

# Keep just enough to symbolicate crash reports from the Play Console.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ---------------------------------------------------------------------------
# kotlinx.serialization
# (generic rules so every @Serializable type keeps its generated serializer)
# ---------------------------------------------------------------------------
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.spikeai.app.**$$serializer { *; }
-keepclassmembers class com.spikeai.app.** {
    *** Companion;
}
# The app's data-transfer models are (de)serialized by name — keep them whole.
-keep @kotlinx.serialization.Serializable class com.spikeai.app.data.** { *; }
-dontwarn kotlinx.serialization.**

# ---------------------------------------------------------------------------
# Kotlin coroutines (ships consumer rules; keep these as a safety net)
# ---------------------------------------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ---------------------------------------------------------------------------
# Ktor (HTTP client used by Supabase) + OkHttp engine
# ---------------------------------------------------------------------------
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# Supabase (supabase-kt: postgrest / auth / functions)
# ---------------------------------------------------------------------------
-keep class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# ---------------------------------------------------------------------------
# Google Identity / Credential Manager (Google sign-in)
# ---------------------------------------------------------------------------
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# ---------------------------------------------------------------------------
# ML Kit pose detection (Sweat mode) — ships consumer rules; safety net only.
# ---------------------------------------------------------------------------
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ---------------------------------------------------------------------------
# WorkManager + Room (transitive dependency).
# WorkManager auto-initializes at process start via androidx.startup's
# InitializationProvider and builds a Room-backed database (WorkDatabase). Room
# loads its generated implementation reflectively as "<DatabaseName>_Impl", so R8
# must NOT rename or strip those classes — otherwise WorkDatabase fails to
# construct and the app crashes on launch ("Unable to get provider
# androidx.startup.InitializationProvider"). The libraries' own consumer rules are
# not sufficient under R8 full mode, so keep them explicitly here.
# ---------------------------------------------------------------------------
-keep class androidx.work.** { *; }
-keep class androidx.work.impl.** { *; }
-dontwarn androidx.work.**
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.** { *; }
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.room.**
-keep class androidx.startup.** { *; }

# ---------------------------------------------------------------------------
# App entry points referenced from the manifest (kept by AGP, declared for clarity)
# ---------------------------------------------------------------------------
-keep class com.spikeai.app.SpikeApp
-keep class com.spikeai.app.MainActivity
-keep class com.spikeai.app.blocking.AppBlockerAccessibilityService
-keep class com.spikeai.app.blocking.FocusForegroundService
-keep class com.spikeai.app.notifications.NotificationReceiver
-keep class com.spikeai.app.notifications.BootReceiver
