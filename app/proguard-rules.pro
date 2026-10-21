# Tink (via androidx.security-crypto) references errorprone annotations that are compile-only
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.CheckReturnValue
-dontwarn com.google.errorprone.annotations.Immutable
-dontwarn com.google.errorprone.annotations.RestrictedApi

# Keep Kotlin Serialization models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep,includedescriptorclasses class org.parkjw.apps.oot.**$$serializer { *; }
-keepclassmembers class org.parkjw.apps.oot.** {
    *** Companion;
}
-keepclasseswithmembers class org.parkjw.apps.oot.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.json.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
