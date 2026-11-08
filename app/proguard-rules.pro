# Proguard and R8 optimization rules for "As" Maze Game

# Keep Room generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface * { *; }
-keep class * implements androidx.room.RoomDatabase { *; }

# Keep data models
-keepclassmembers class com.example.domain.model.** { *; }
-keepclassmembers class com.example.data.db.** { *; }
-keepclassmembers class com.example.data.preferences.** { *; }

# Kotlin Coroutines
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }

# Compose rules
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
