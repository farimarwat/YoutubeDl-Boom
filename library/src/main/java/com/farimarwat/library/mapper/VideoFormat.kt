package com.farimarwat.library

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoFormat (
    val asr:Int = 0,
    val tbr:Int = 0,
    val abr:Int = 0,
    val format: String? = null,

    @JsonProperty("format_id")
    val formatId: String? = null,

    @JsonProperty("format_note")
    val formatNote: String? = null,
    val ext: String? = null,
    val preference:Int = 0,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width:Int = 0,
    val height:Int = 0,

    @JsonProperty("filesize")
    val fileSize: Long = 0,

    @JsonProperty("filesize_approx")
    val fileSizeApproximate: Long = 0,
    val fps:Int = 0,
    val url: String? = null,

    @JsonProperty("manifest_url")
    val manifestUrl: String? = null,

    @JsonProperty("http_headers")
    val httpHeaders: Map<String, String>? = null,
)