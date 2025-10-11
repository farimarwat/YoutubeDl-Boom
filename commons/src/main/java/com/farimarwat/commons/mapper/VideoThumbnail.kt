package com.farimarwat.commons
import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class VideoThumbnail {
    val url: String? = null
    val id: String? = null
}