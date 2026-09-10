# Default ProGuard rules for Android applications

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKd
-keep,allowobfuscation,allowshrinking class kotlinx.serialization.internal.**
-keep,allowobfuscation,allowshrinking class * implements kotlinx.serialization.KSerializer {
    <init>();
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Compose
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room
-dontwarn androidx.room.**
-keep class * extends androidx.room.RoomDatabase {
    *;
}

# Keep Data Models and API Responses for Serialization/Deserialization
-keep class com.example.kaspawallet.data.model.** { *; }
-keep class com.example.kaspawallet.data.api.** { *; }

# Keep specific cryptography objects if needed
-keep class com.example.kaspawallet.data.crypto.** { *; }

# Strip all Android Log statements in the release build for maximum security
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int w(...);
    public static int d(...);
    public static int e(...);
}

# Strip console print statements
-assumenosideeffects class java.io.PrintStream {
    public void println(%);
    public void println(**);
    public void print(%);
    public void print(**);
}
