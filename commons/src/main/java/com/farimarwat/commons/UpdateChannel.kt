package com.farimarwat.commons

open class UpdateChannel(val apiUrl: String) {
        object STABLE : UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
        object NIGHTLY :
            UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp-nightly-builds/releases/latest")

        object MASTER :
            UpdateChannel("https://api.github.com/repos/yt-dlp/yt-dlp-master-builds/releases/latest")
    }
