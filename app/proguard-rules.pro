-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod

-keep class com.ggmacro.app.data.model.** { *; }
-keep class com.ggmacro.app.service.** { *; }

-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
