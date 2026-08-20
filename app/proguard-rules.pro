# Room database proguard rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Firestore DTO models
-keepclassmembers class com.pumaterial.app.data.remote.dto.** {
    <fields>;
    <init>(...);
}
