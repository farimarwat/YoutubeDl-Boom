package com.farimarwat.helper.mapper

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class VideoFormat constructor(
    val asr: Int = 0,
    val tbr: Int = 0,
    val abr: Int = 0,
    val format: String? = null,
    val formatId: String? = null,
    val formatNote: String? = null,
    val ext: String? = null,
    val preference: Int = 0,
    val vcodec: String? = null,
    val acodec: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val fileSize: Long = 0,
    val fileSizeApproximate: Long = 0,
    val fps: Int = 0,
    val url: String? = null,
    val manifestUrl: String? = null,
    val httpHeaders: Map<String, String>? = null,
) {
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