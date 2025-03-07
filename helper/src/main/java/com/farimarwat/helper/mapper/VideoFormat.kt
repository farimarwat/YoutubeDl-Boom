package com.farimarwat.helper.mapper

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoFormat @JsonCreator constructor(
    @JsonProperty("asr") val asr: Int = 0,
    @JsonProperty("tbr") val tbr: Int = 0,
    @JsonProperty("abr") val abr: Int = 0,
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("format_id") val formatId: String? = null,
    @JsonProperty("format_note") val formatNote: String? = null,
    @JsonProperty("ext") val ext: String? = null,
    @JsonProperty("preference") val preference: Int = 0,
    @JsonProperty("vcodec") val vcodec: String? = null,
    @JsonProperty("acodec") val acodec: String? = null,
    @JsonProperty("width") val width: Int = 0,
    @JsonProperty("height") val height: Int = 0,
    @JsonProperty("filesize") val fileSize: Long = 0,
    @JsonProperty("filesize_approx") val fileSizeApproximate: Long = 0,
    @JsonProperty("fps") val fps: Int = 0,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("http_headers") val httpHeaders: Map<String, String>? = null,
){
    override fun toString(): String {
        return """
        VideoFormat(
            asr = $asr,
            tbr = $tbr,
            abr = $abr,
            format = $format,
            formatId = $formatId,
            formatNote = $formatNote,
            ext = $ext,
            preference = $preference,
            vcodec = $vcodec,
            acodec = $acodec,
            width = $width,
            height = $height,
            fileSize = $fileSize,
            fileSizeApproximate = $fileSizeApproximate,
            fps = $fps,
            url = $url,
            manifestUrl = $manifestUrl
        )
    """.trimIndent()
    }
}