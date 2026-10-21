# Keep Kotlin Serialization models
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keep class kotlinx.serialization.json.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
