# youtubedl-boom-helper finds YoutubeDL by name (Class.forName) when the library lives in a
# Dynamic Feature Module; the rest is kept so stack traces and reflection keep working.
-keep class com.farimarwat.library.** { *; }
-keep class com.farimarwat.ffmpeg.** { *; }
-keep class com.farimarwat.aria2c.** { *; }
-keep class com.farimarwat.common.** { *; }
-keep class com.yausername.youtubedl_android.** { *; }

# Process.getProcessId() reads the private "pid" field of the platform Process class.
-keepclassmembers class java.lang.Process { *; }

# Jackson: optional integrations it references but apps rarely ship.
-dontwarn com.fasterxml.jackson.databind.**
# commons-compress: optional codecs (xz, brotli, zstd, ...) it references but the library never uses.
-dontwarn org.apache.commons.compress.**
