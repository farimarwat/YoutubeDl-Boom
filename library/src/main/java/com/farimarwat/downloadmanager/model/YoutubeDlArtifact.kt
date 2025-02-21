package com.farimarwat.downloadmanager.model

/**
 * Contains static URLs for additional downloadable artifacts.
 */
object YoutubeDlArtifact {
    /** URL for downloading FFmpeg. */
    val FFMPEG =
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/ffmpeg/src/main/jniLibs/{arch}/libffmpeg.zip.so"

    /** URL for downloading Aria2c. */
    val ARIA2C =
        "https://raw.githubusercontent.com/yausername/youtubedl-android/refs/heads/master/aria2c/src/main/jniLibs/{arch}/libaria2c.zip.so"
}