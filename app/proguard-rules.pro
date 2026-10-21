# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# SQLCipher JNI & driver classes
-keep class net.zetetic.** { *; }
-dontwarn net.zetetic.**

# Room entities and database implementations
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep @androidx.room.Entity class *

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.controlcal.**$$serializer { *; }
-keepclassmembers class com.controlcal.** {
    *** Companion;
}
-keepclasseswithmembers class com.controlcal.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit bundled models
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_* { *; }

# Strip verbose/debug/info logs in release builds (Section 6 of PLAN.md)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}