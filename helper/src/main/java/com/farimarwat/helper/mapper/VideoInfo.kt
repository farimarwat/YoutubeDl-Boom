package com.farimarwat.helper.mapper

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoInfo @JsonCreator constructor(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("fulltitle") val fulltitle: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("upload_date") val uploadDate: String? = null,
    @JsonProperty("display_id") val displayId: String? = null,
    @JsonProperty("duration") val duration: Int = 0,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("thumbnail") val thumbnail: String? = null,
    @JsonProperty("license") val license: String? = null,
    @JsonProperty("extractor") val extractor: String? = null,
    @JsonProperty("extractor_key") val extractorKey: String? = null,
    @JsonProperty("view_count") val viewCount: String? = null,
    @JsonProperty("like_count") val likeCount: String? = null,
    @JsonProperty("dislike_count") val dislikeCount: String? = null,
    @JsonProperty("repost_count") val repostCount: String? = null,
    @JsonProperty("average_rating") val averageRating: String? = null,
    @JsonProperty("uploader_id") val uploaderId: String? = null,
    @JsonProperty("uploader") val uploader: String? = null,
    @JsonProperty("player_url") val playerUrl: String? = null,
    @JsonProperty("webpage_url") val webpageUrl: String? = null,
    @JsonProperty("webpage_url_basename") val webpageUrlBasename: String? = null,
    @JsonProperty("resolution") val resolution: String? = null,
    @JsonProperty("width") val width: Int = 0,
    @JsonProperty("height") val height: Int = 0,
    @JsonProperty("format") val format: String? = null,
    @JsonProperty("format_id") val formatId: String? = null,
    @JsonProperty("ext") val ext: String? = null,
    @JsonProperty("filesize") val fileSize: Long = 0,
    @JsonProperty("filesize_approx") val fileSizeApproximate: Long = 0,
    @JsonProperty("http_headers") val httpHeaders: Map<String, String>? = null,
    @JsonProperty("categories") val categories: ArrayList<String>? = null,
    @JsonProperty("tags") val tags: ArrayList<String>? = null,
    @JsonProperty("requested_formats") val requestedFormats: ArrayList<VideoFormat>? = null,
    @JsonProperty("formats") val formats: List<VideoFormat>? = null,
    @JsonProperty("thumbnails") val thumbnails: ArrayList<VideoThumbnail>? = null,
    @JsonProperty("manifest_url") val manifestUrl: String? = null,
    @JsonProperty("url") val url: String? = null,
    @JsonProperty("is_live") val isLive: Boolean? = null
){
    override fun toString(): String {
        return """
            VideoInfo(
                id = $id,
                fulltitle = $fulltitle,
                title = $title,
                uploadDate = $uploadDate,
                displayId = $displayId,
                duration = $duration,
                description = $description,
                thumbnail = $thumbnail,
                license = $license,
                extractor = $extractor,
                extractorKey = $extractorKey,
                viewCount = $viewCount,
                likeCount = $likeCount,
                dislikeCount = $dislikeCount,
                repostCount = $repostCount,
                averageRating = $averageRating,
                uploaderId = $uploaderId,
                uploader = $uploader,
                playerUrl = $playerUrl,
                webpageUrl = $webpageUrl,
                webpageUrlBasename = $webpageUrlBasename,
                resolution = $resolution,
                width = $width,
                height = $height,
                format = $format,
                formatId = $formatId,
                ext = $ext,
                fileSize = $fileSize,
                fileSizeApproximate = $fileSizeApproximate,
                httpHeaders = $httpHeaders,
                categories = $categories,
                tags = $tags,
                thumbnails = $thumbnails,
                manifestUrl = $manifestUrl,
                url = $url,
                isLive = $isLive
            )
        """.trimIndent()
    }
}
