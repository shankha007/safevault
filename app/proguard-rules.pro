# Bouncy Castle references JDK classes that don't exist on Android; we only use Argon2.
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# kotlinx.serialization ships its own consumer rules; keep our @Serializable models intact.
-keep,includedescriptorclasses class com.safevault.app.**$$serializer { *; }
-keepclassmembers class com.safevault.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# Strip all android.util.Log calls from release builds so nothing can leak to logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
