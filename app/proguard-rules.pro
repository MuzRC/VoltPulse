# Mengekalkan struktur pangkalan data Room semasa pengoptimuman R8
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Mengekalkan maklumat versi untuk semakan kemas kini GitHub
-keep class com.muzrc.voltpulse.BuildConfig { *; }
