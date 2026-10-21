# Project-specific R8/ProGuard rules.

# Keep source file / line info so crash stack traces stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Room: the database implementation is looked up reflectively by name, and
# entities/DAOs must keep their shapes.
-keep class com.god.ApplicationManager.DB.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Keep the app's model/entity classes (also shielded by @Keep, belt and braces).
-keep class com.god.ApplicationManager.Entity.** { *; }

# libsuperuser spawns shells and parses process output; keep it whole.
-keep class eu.chainfire.libsuperuser.** { *; }

# Strip verbose/debug/info logging from release builds (warnings and errors stay).
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}
