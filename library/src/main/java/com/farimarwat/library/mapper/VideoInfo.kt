package com.farimarwat.library

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class VideoInfo {
    val id: String? = null
    val fulltitle: String? = null
    val title: String? = null

    @JsonProperty("upload_date")
    val uploadDate: String? = null

    @JsonProperty("display_id")
    val displayId: String? = null
    val duration = 0
    val description: String? = null
    val thumbnail: String? = null
    val license: String? = null
    val extractor: String? = null

    @JsonProperty("extractor_key")
    val extractorKey: String? = null

    @JsonProperty("view_count")
    val viewCount: String? = null

    @JsonProperty("like_count")
    val likeCount: String? = null

    @JsonProperty("dislike_count")
    val dislikeCount: String? = null

    @JsonProperty("repost_count")
    val repostCount: String? = null

    @JsonProperty("average_rating")
    val averageRating: String? = null

    @JsonProperty("uploader_id")
    val uploaderId: String? = null
    val uploader: String? = null

    @JsonProperty("player_url")
    val playerUrl: String? = null

    @JsonProperty("webpage_url")
    val webpageUrl: String? = null

    @JsonProperty("webpage_url_basename")
    val webpageUrlBasename: String? = null
    val resolution: String? = null
    val width = 0
    val height = 0
    val format: String? = null

    @JsonProperty("format_id")
    val formatId: String? = null
    val ext: String? = null

    @JsonProperty("filesize")
    val fileSize: Long = 0

    @JsonProperty("filesize_approx")
    val fileSizeApproximate: Long = 0

    @JsonProperty("http_headers")
    val httpHeaders: Map<String, String>? = null
    val categories: ArrayList<String>? = null
    val tags: ArrayList<String>? = null

    @JsonProperty("requested_formats")
    val requestedFormats: ArrayList<VideoFormat>? = null
    val formats: ArrayList<VideoFormat>? = null
    val thumbnails: ArrayList<VideoThumbnail>? = null

    @JsonProperty("manifest_url")
    val manifestUrl: String? = null
    val url: String? = null

    override fun toString(): String {
        return """
            VideoInfo(
                id=$id,
                title=$title,
                fulltitle=$fulltitle,
                uploadDate=$uploadDate,
                displayId=$displayId,
                duration=$duration,
                description=$description,
                thumbnail=$thumbnail,
                license=$license,
                extractor=$extractor,
                extractorKey=$extractorKey,
                viewCount=$viewCount,
                likeCount=$likeCount,
                dislikeCount=$dislikeCount,
                repostCount=$repostCount,
                averageRating=$averageRating,
                uploaderId=$uploaderId,
                uploader=$uploader,
                playerUrl=$playerUrl,
                webpageUrl=$webpageUrl,
                resolution=$resolution,
                width=$width,
                height=$height,
                format=$format,
                formatId=$formatId,
                ext=$ext,
                fileSize=$fileSize,
                fileSizeApproximate=$fileSizeApproximate,
                httpHeaders=$httpHeaders,
                categories=$categories,
                tags=$tags,
                requestedFormats=$requestedFormats,
                formats=$formats,
                thumbnails=$thumbnails,
                manifestUrl=$manifestUrl,
                url=$url
            )
        """.trimIndent()
    }
}
