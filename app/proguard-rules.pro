# Room — keep generated schema classes
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers @androidx.room.Entity class * { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class *

# WorkManager
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker

# Apache Commons CSV
-dontwarn org.apache.commons.csv.**
-keep class org.apache.commons.csv.** { *; }
